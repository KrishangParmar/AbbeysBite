package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.FriendProgress
import com.abbeysbite.app.data.model.FriendWithProfile
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.model.Report
import com.abbeysbite.app.data.model.ReportTargetType

interface FriendsRepository {
    /** Accepted friends with profiles. */
    suspend fun friends(): AppResult<List<FriendWithProfile>>

    /** Incoming + outgoing pending requests. */
    suspend fun pendingRequests(): AppResult<List<FriendWithProfile>>

    suspend fun searchUsers(username: String): AppResult<List<Profile>>

    suspend fun sendRequest(toUserId: String): AppResult<Unit>

    suspend fun accept(friendshipId: String): AppResult<Unit>

    suspend fun decline(friendshipId: String): AppResult<Unit>

    suspend fun cancel(friendshipId: String): AppResult<Unit>

    suspend fun unfriend(friendshipId: String): AppResult<Unit>

    suspend fun block(userId: String): AppResult<Unit>

    suspend fun unblock(userId: String): AppResult<Unit>

    suspend fun blockedUsers(): AppResult<List<Profile>>

    /** Progress for accepted friends, already filtered by their sharing prefs. */
    suspend fun friendProgress(): AppResult<List<FriendProgress>>
}

interface ReportsRepository {
    suspend fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reason: String,
        details: String?,
    ): AppResult<Report>
}
