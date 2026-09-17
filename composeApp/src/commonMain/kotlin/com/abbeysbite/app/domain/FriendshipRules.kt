package com.abbeysbite.app.domain

import com.abbeysbite.app.data.model.Friendship
import com.abbeysbite.app.data.model.FriendshipStatus
import com.abbeysbite.app.data.model.ProgressSharingPreferences

/**
 * Friendship state machine + privacy authorization rules.
 * Pure logic — the single source of truth for what transitions are legal
 * and what a friend is allowed to see.
 */
object FriendshipRules {

    enum class Action { ACCEPT, DECLINE, CANCEL, BLOCK, UNBLOCK, UNFRIEND }

    /**
     * Whether [actorId] may perform [action] on [friendship].
     * Returns the resulting status, or null when the transition is illegal.
     */
    fun transition(friendship: Friendship, actorId: String, action: Action): FriendshipStatus? {
        val isRequester = actorId == friendship.requesterId
        val isAddressee = actorId == friendship.addresseeId
        if (!isRequester && !isAddressee) return null

        return when (action) {
            Action.ACCEPT ->
                if (friendship.status == FriendshipStatus.PENDING && isAddressee)
                    FriendshipStatus.ACCEPTED else null

            Action.DECLINE ->
                if (friendship.status == FriendshipStatus.PENDING && isAddressee)
                    FriendshipStatus.DECLINED else null

            Action.CANCEL ->
                if (friendship.status == FriendshipStatus.PENDING && isRequester)
                    FriendshipStatus.DECLINED else null

            Action.BLOCK -> FriendshipStatus.BLOCKED

            Action.UNBLOCK ->
                if (friendship.status == FriendshipStatus.BLOCKED) FriendshipStatus.DECLINED else null

            Action.UNFRIEND ->
                if (friendship.status == FriendshipStatus.ACCEPTED) FriendshipStatus.DECLINED else null
        }
    }

    /** May [viewerId] send a NEW request given an existing [existing] row (or null)? */
    fun canSendRequest(existing: Friendship?): Boolean = when (existing?.status) {
        null, FriendshipStatus.DECLINED -> true
        FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED, FriendshipStatus.BLOCKED -> false
    }

    /**
     * Progress visibility: requires an ACCEPTED friendship AND the owner's
     * sharing toggle for that stat. Everything defaults to hidden.
     */
    data class VisibleProgress(
        val activeDays: Boolean,
        val mealsLogged: Boolean,
        val plantVariety: Boolean,
        val recipesPublished: Boolean,
        val recipeAchievements: Boolean,
    ) {
        val anythingVisible: Boolean
            get() = activeDays || mealsLogged || plantVariety || recipesPublished || recipeAchievements
    }

    fun visibleProgress(
        friendship: Friendship?,
        ownerPrefs: ProgressSharingPreferences?,
    ): VisibleProgress {
        val accepted = friendship?.status == FriendshipStatus.ACCEPTED
        val prefs = ownerPrefs ?: ProgressSharingPreferences()
        return VisibleProgress(
            activeDays = accepted && prefs.shareActiveDays,
            mealsLogged = accepted && prefs.shareMealsLogged,
            plantVariety = accepted && prefs.sharePlantVariety,
            recipesPublished = accepted && prefs.shareRecipesPublished,
            recipeAchievements = accepted && prefs.shareRecipeAchievements,
        )
    }

    /** Blocked either way ⇒ no interactions (search, requests, recipe actions). */
    fun interactionAllowed(friendship: Friendship?): Boolean =
        friendship?.status != FriendshipStatus.BLOCKED
}
