package com.abbeysbite.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FriendshipStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("declined") DECLINED,
    @SerialName("blocked") BLOCKED,
}

@Serializable
data class Friendship(
    val id: String = "",
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** A friend (or request) joined with their profile for display. */
data class FriendWithProfile(
    val friendship: Friendship,
    val profile: Profile,
    /** True when the current user sent the request. */
    val outgoing: Boolean,
)

/**
 * What the user has chosen to share with accepted friends.
 * Everything defaults to OFF — sharing is strictly opt-in.
 */
@Serializable
data class ProgressSharingPreferences(
    @SerialName("user_id") val userId: String = "",
    @SerialName("share_active_days") val shareActiveDays: Boolean = false,
    @SerialName("share_meals_logged") val shareMealsLogged: Boolean = false,
    @SerialName("share_plant_variety") val sharePlantVariety: Boolean = false,
    @SerialName("share_recipes_published") val shareRecipesPublished: Boolean = false,
    @SerialName("share_recipe_achievements") val shareRecipeAchievements: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val anythingShared: Boolean
        get() = shareActiveDays || shareMealsLogged || sharePlantVariety ||
            shareRecipesPublished || shareRecipeAchievements
}

/** Friend progress as visible to the current user (already permission-filtered). */
data class FriendProgress(
    val profile: Profile,
    val activeDaysThisWeek: Int? = null,
    val mealsLoggedThisWeek: Int? = null,
    val plantVariety: Int? = null,
    val recipesPublished: Int? = null,
)

@Serializable
enum class ReportTargetType {
    @SerialName("recipe") RECIPE,
    @SerialName("user") USER,
}

@Serializable
enum class ReportStatus {
    @SerialName("open") OPEN,
    @SerialName("reviewing") REVIEWING,
    @SerialName("resolved") RESOLVED,
    @SerialName("dismissed") DISMISSED,
}

@Serializable
data class Report(
    val id: String = "",
    @SerialName("reporter_id") val reporterId: String = "",
    @SerialName("target_type") val targetType: ReportTargetType,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: ReportStatus = ReportStatus.OPEN,
    @SerialName("created_at") val createdAt: String? = null,
)
