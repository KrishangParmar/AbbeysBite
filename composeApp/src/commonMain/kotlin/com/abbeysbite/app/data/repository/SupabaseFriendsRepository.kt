package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.FriendProgress
import com.abbeysbite.app.data.model.FriendWithProfile
import com.abbeysbite.app.data.model.Friendship
import com.abbeysbite.app.data.model.FriendshipStatus
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.model.Report
import com.abbeysbite.app.data.model.ReportTargetType
import com.abbeysbite.app.domain.FriendshipRules
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import kotlinx.serialization.json.Json

class SupabaseFriendsRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : FriendsRepository, ReportsRepository {

    private fun uid(): String? = auth.currentUserId

    private suspend fun myFriendships(): List<Friendship> {
        val me = uid() ?: return emptyList()
        return supabase.from("friendships")
            .select {
                filter {
                    or {
                        eq("requester_id", me)
                        eq("addressee_id", me)
                    }
                }
            }
            .decodeList<Friendship>()
    }

    private suspend fun profilesByIds(ids: List<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return supabase.from("profiles")
            .select { filter { isIn("id", ids) } }
            .decodeList<Profile>()
            .associateBy { it.id }
    }

    private suspend fun joinProfiles(friendships: List<Friendship>): List<FriendWithProfile> {
        val me = uid() ?: return emptyList()
        val otherIds = friendships.map { if (it.requesterId == me) it.addresseeId else it.requesterId }
        val profiles = profilesByIds(otherIds.distinct())
        return friendships.mapNotNull { f ->
            val otherId = if (f.requesterId == me) f.addresseeId else f.requesterId
            profiles[otherId]?.let { FriendWithProfile(f, it, outgoing = f.requesterId == me) }
        }
    }

    override suspend fun friends(): AppResult<List<FriendWithProfile>> = try {
        AppResult.Success(joinProfiles(myFriendships().filter { it.status == FriendshipStatus.ACCEPTED }))
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun pendingRequests(): AppResult<List<FriendWithProfile>> = try {
        AppResult.Success(joinProfiles(myFriendships().filter { it.status == FriendshipStatus.PENDING }))
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun searchUsers(username: String): AppResult<List<Profile>> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        val q = username.trim().lowercase()
        if (q.length < 2) return AppResult.Success(emptyList())
        return try {
            val blockedPairs = myFriendships().filter { it.status == FriendshipStatus.BLOCKED }
            val blockedIds = blockedPairs.flatMap { listOf(it.requesterId, it.addresseeId) }.toSet()
            val results = supabase.from("profiles")
                .select {
                    filter { ilike("username", "%$q%") }
                    limit(20)
                }
                .decodeList<Profile>()
                .filter { it.id != me && it.id !in blockedIds }
            AppResult.Success(results)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun sendRequest(toUserId: String): AppResult<Unit> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        if (toUserId == me) return AppResult.Failure(AppError.Validation("You can’t add yourself."))
        return try {
            val existing = myFriendships().find {
                (it.requesterId == me && it.addresseeId == toUserId) ||
                    (it.requesterId == toUserId && it.addresseeId == me)
            }
            if (!FriendshipRules.canSendRequest(existing)) {
                return AppResult.Failure(AppError.Conflict("A request already exists with this person."))
            }
            if (existing != null) {
                supabase.from("friendships").delete { filter { eq("id", existing.id) } }
            }
            supabase.from("friendships").insert(
                mapOf("requester_id" to me, "addressee_id" to toUserId, "status" to "pending")
            )
            AppResult.Success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("duplicate", ignoreCase = true) || msg.contains("unique", ignoreCase = true)) {
                AppResult.Failure(AppError.Conflict("A request already exists with this person."))
            } else {
                AppResult.Failure(e.toAppError())
            }
        }
    }

    private suspend fun applyAction(
        friendshipId: String,
        action: FriendshipRules.Action,
    ): AppResult<Unit> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val friendship = supabase.from("friendships")
                .select { filter { eq("id", friendshipId) } }
                .decodeList<Friendship>()
                .firstOrNull() ?: return AppResult.Failure(AppError.NotFound())
            val next = FriendshipRules.transition(friendship, me, action)
                ?: return AppResult.Failure(AppError.Conflict("That action isn’t possible anymore."))
            supabase.from("friendships").update(
                { set("status", next.name.lowercase()) }
            ) {
                filter { eq("id", friendshipId) }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun accept(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.ACCEPT)
    override suspend fun decline(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.DECLINE)
    override suspend fun cancel(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.CANCEL)
    override suspend fun unfriend(friendshipId: String) = applyAction(friendshipId, FriendshipRules.Action.UNFRIEND)

    override suspend fun block(userId: String): AppResult<Unit> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val existing = myFriendships().find {
                (it.requesterId == me && it.addresseeId == userId) ||
                    (it.requesterId == userId && it.addresseeId == me)
            }
            if (existing != null) {
                supabase.from("friendships").update(
                    { set("status", "blocked"); set("blocked_by", me) }
                ) {
                    filter { eq("id", existing.id) }
                }
            } else {
                supabase.from("friendships").insert(
                    mapOf(
                        "requester_id" to me, "addressee_id" to userId,
                        "status" to "blocked", "blocked_by" to me,
                    )
                )
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun unblock(userId: String): AppResult<Unit> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val existing = myFriendships().find {
                it.status == FriendshipStatus.BLOCKED &&
                    ((it.requesterId == me && it.addresseeId == userId) ||
                        (it.requesterId == userId && it.addresseeId == me))
            } ?: return AppResult.Failure(AppError.NotFound())
            supabase.from("friendships").update(
                { set("status", "declined"); set("blocked_by", null as String?) }
            ) {
                filter { eq("id", existing.id) }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun blockedUsers(): AppResult<List<Profile>> = try {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        val blocked = myFriendships().filter { it.status == FriendshipStatus.BLOCKED }
        val ids = blocked.map { if (it.requesterId == me) it.addresseeId else it.requesterId }
        AppResult.Success(profilesByIds(ids).values.toList())
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun friendProgress(): AppResult<List<FriendProgress>> = try {
        // Progress is computed server-side (friend-progress Edge Function) so the
        // sharing-preference + friendship checks are enforced by RLS/service code,
        // never by the client.
        val response = supabase.functions.invoke("friend-progress")
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<FriendProgressDto>>(response.body())
        AppResult.Success(payload.map { it.toModel() })
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String?,
    ): AppResult<Report> {
        val me = uid() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val inserted = supabase.from("reports")
                .insert(
                    mapOf(
                        "reporter_id" to me,
                        "target_type" to targetType.name.lowercase(),
                        "target_id" to targetId,
                        "reason" to reason,
                        "details" to details,
                    )
                ) { select(Columns.ALL) }
                .decodeSingle<Report>()
            AppResult.Success(inserted)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }
}

@kotlinx.serialization.Serializable
private data class FriendProgressDto(
    val profile: Profile,
    val active_days: Int? = null,
    val meals_logged: Int? = null,
    val plant_variety: Int? = null,
    val recipes_published: Int? = null,
) {
    fun toModel() = FriendProgress(
        profile = profile,
        activeDaysThisWeek = active_days,
        mealsLoggedThisWeek = meals_logged,
        plantVariety = plant_variety,
        recipesPublished = recipes_published,
    )
}
