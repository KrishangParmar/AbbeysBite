package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.demo.DemoData
import com.abbeysbite.app.data.model.FriendProgress
import com.abbeysbite.app.data.model.FriendWithProfile
import com.abbeysbite.app.data.model.Friendship
import com.abbeysbite.app.data.model.FriendshipStatus
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.model.Report
import com.abbeysbite.app.data.model.ReportTargetType
import com.abbeysbite.app.domain.FriendshipRules
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Demo friends: seeds one incoming request and one accepted friend so the
 * whole social surface is explorable offline. State persists locally.
 */
class DemoFriendsRepository(
    private val settings: Settings,
    private val auth: AuthRepository,
) : FriendsRepository, ReportsRepository {

    private companion object {
        const val KEY = "demo_friendships"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun me(): String = auth.currentUserId ?: "demo-user"

    private fun stored(): MutableList<Friendship> {
        val existing = settings.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<List<Friendship>>(it) }.getOrNull()
        }
        if (existing != null) return existing.toMutableList()
        // First run: seed one accepted friend + one incoming request.
        val seeded = mutableListOf(
            Friendship(
                id = "demo-fr-1", requesterId = me(), addresseeId = "demo-profile-1",
                status = FriendshipStatus.ACCEPTED,
            ),
            Friendship(
                id = "demo-fr-2", requesterId = "demo-profile-2", addresseeId = me(),
                status = FriendshipStatus.PENDING,
            ),
        )
        persist(seeded)
        return seeded
    }

    private fun persist(list: List<Friendship>) =
        settings.putString(KEY, json.encodeToString(list))

    private fun profileOf(userId: String): Profile =
        DemoData.profiles.find { it.id == userId }
            ?: Profile(id = userId, username = "user_${userId.takeLast(4)}")

    private fun withProfile(f: Friendship): FriendWithProfile {
        val otherId = if (f.requesterId == me()) f.addresseeId else f.requesterId
        return FriendWithProfile(f, profileOf(otherId), outgoing = f.requesterId == me())
    }

    override suspend fun friends(): AppResult<List<FriendWithProfile>> {
        delay(200)
        return AppResult.Success(
            stored().filter { it.status == FriendshipStatus.ACCEPTED }.map(::withProfile)
        )
    }

    override suspend fun pendingRequests(): AppResult<List<FriendWithProfile>> {
        delay(200)
        return AppResult.Success(
            stored().filter { it.status == FriendshipStatus.PENDING }.map(::withProfile)
        )
    }

    override suspend fun searchUsers(username: String): AppResult<List<Profile>> {
        delay(250)
        val q = username.trim().lowercase()
        if (q.length < 2) return AppResult.Success(emptyList())
        val blockedIds = stored()
            .filter { it.status == FriendshipStatus.BLOCKED }
            .flatMap { listOf(it.requesterId, it.addresseeId) }
        return AppResult.Success(
            DemoData.profiles.filter {
                it.username.contains(q) && it.id != me() && it.id !in blockedIds
            }
        )
    }

    override suspend fun sendRequest(toUserId: String): AppResult<Unit> {
        val list = stored()
        val existing = list.find {
            (it.requesterId == me() && it.addresseeId == toUserId) ||
                (it.requesterId == toUserId && it.addresseeId == me())
        }
        if (!FriendshipRules.canSendRequest(existing)) {
            return AppResult.Failure(AppError.Conflict("A request already exists with this person."))
        }
        list.removeAll { it.id == existing?.id }
        list.add(
            Friendship(
                id = "demo-fr-${list.size}-${toUserId.hashCode()}",
                requesterId = me(), addresseeId = toUserId,
                status = FriendshipStatus.PENDING,
            )
        )
        persist(list)
        return AppResult.Success(Unit)
    }

    private fun applyAction(friendshipId: String, action: FriendshipRules.Action): AppResult<Unit> {
        val list = stored()
        val index = list.indexOfFirst { it.id == friendshipId }
        if (index < 0) return AppResult.Failure(AppError.NotFound())
        val next = FriendshipRules.transition(list[index], me(), action)
            ?: return AppResult.Failure(AppError.Conflict("That action isn’t possible anymore."))
        list[index] = list[index].copy(status = next)
        persist(list)
        return AppResult.Success(Unit)
    }

    override suspend fun accept(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.ACCEPT)
    override suspend fun decline(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.DECLINE)
    override suspend fun cancel(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.CANCEL)
    override suspend fun unfriend(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.UNFRIEND)

    override suspend fun block(userId: String): AppResult<Unit> {
        val list = stored()
        val existing = list.indexOfFirst {
            (it.requesterId == me() && it.addresseeId == userId) ||
                (it.requesterId == userId && it.addresseeId == me())
        }
        if (existing >= 0) {
            list[existing] = list[existing].copy(status = FriendshipStatus.BLOCKED)
        } else {
            list.add(
                Friendship(
                    id = "demo-fr-${list.size}-$userId",
                    requesterId = me(), addresseeId = userId,
                    status = FriendshipStatus.BLOCKED,
                )
            )
        }
        persist(list)
        return AppResult.Success(Unit)
    }

    override suspend fun unblock(userId: String): AppResult<Unit> {
        val list = stored()
        val index = list.indexOfFirst {
            it.status == FriendshipStatus.BLOCKED &&
                ((it.requesterId == me() && it.addresseeId == userId) ||
                    (it.requesterId == userId && it.addresseeId == me()))
        }
        if (index < 0) return AppResult.Failure(AppError.NotFound())
        list[index] = list[index].copy(status = FriendshipStatus.DECLINED)
        persist(list)
        return AppResult.Success(Unit)
    }

    override suspend fun blockedUsers(): AppResult<List<Profile>> =
        AppResult.Success(
            stored().filter { it.status == FriendshipStatus.BLOCKED }
                .map { profileOf(if (it.requesterId == me()) it.addresseeId else it.requesterId) }
        )

    override suspend fun friendProgress(): AppResult<List<FriendProgress>> {
        delay(250)
        // Demo friends share a couple of stats to showcase the surface.
        val accepted = stored().filter { it.status == FriendshipStatus.ACCEPTED }
        return AppResult.Success(
            accepted.map { f ->
                val other = profileOf(if (f.requesterId == me()) f.addresseeId else f.requesterId)
                FriendProgress(
                    profile = other,
                    activeDaysThisWeek = 4,
                    mealsLoggedThisWeek = 9,
                    plantVariety = null,      // this friend chose not to share it
                    recipesPublished = DemoData.recipes.count { it.authorId == other.id },
                )
            }
        )
    }

    override suspend fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String?,
    ): AppResult<Report> {
        delay(300)
        return AppResult.Success(
            Report(
                id = "demo-report-${targetId.hashCode()}",
                reporterId = me(),
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                details = details,
            )
        )
    }
}
