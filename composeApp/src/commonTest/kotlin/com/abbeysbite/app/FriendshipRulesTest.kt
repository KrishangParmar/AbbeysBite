package com.abbeysbite.app

import com.abbeysbite.app.data.model.Friendship
import com.abbeysbite.app.data.model.FriendshipStatus
import com.abbeysbite.app.data.model.ProgressSharingPreferences
import com.abbeysbite.app.domain.FriendshipRules
import com.abbeysbite.app.domain.FriendshipRules.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendshipRulesTest {

    private val pending = Friendship(requesterId = "alice", addresseeId = "bob", status = FriendshipStatus.PENDING)
    private val accepted = pending.copy(status = FriendshipStatus.ACCEPTED)
    private val blocked = pending.copy(status = FriendshipStatus.BLOCKED)

    @Test
    fun addresseeCanAcceptPending() {
        assertEquals(FriendshipStatus.ACCEPTED, FriendshipRules.transition(pending, "bob", Action.ACCEPT))
    }

    @Test
    fun requesterCannotAcceptOwnRequest() {
        assertNull(FriendshipRules.transition(pending, "alice", Action.ACCEPT))
    }

    @Test
    fun strangerCannotActOnFriendship() {
        assertNull(FriendshipRules.transition(pending, "mallory", Action.ACCEPT))
        assertNull(FriendshipRules.transition(pending, "mallory", Action.BLOCK))
    }

    @Test
    fun addresseeCanDeclineRequesterCanCancel() {
        assertEquals(FriendshipStatus.DECLINED, FriendshipRules.transition(pending, "bob", Action.DECLINE))
        assertEquals(FriendshipStatus.DECLINED, FriendshipRules.transition(pending, "alice", Action.CANCEL))
        assertNull(FriendshipRules.transition(pending, "alice", Action.DECLINE))
    }

    @Test
    fun cannotAcceptAlreadyAccepted() {
        assertNull(FriendshipRules.transition(accepted, "bob", Action.ACCEPT))
    }

    @Test
    fun eitherPartyCanBlockAndUnfriend() {
        assertEquals(FriendshipStatus.BLOCKED, FriendshipRules.transition(accepted, "alice", Action.BLOCK))
        assertEquals(FriendshipStatus.BLOCKED, FriendshipRules.transition(accepted, "bob", Action.BLOCK))
        assertEquals(FriendshipStatus.DECLINED, FriendshipRules.transition(accepted, "alice", Action.UNFRIEND))
    }

    @Test
    fun unblockOnlyFromBlocked() {
        assertEquals(FriendshipStatus.DECLINED, FriendshipRules.transition(blocked, "alice", Action.UNBLOCK))
        assertNull(FriendshipRules.transition(accepted, "alice", Action.UNBLOCK))
    }

    @Test
    fun newRequestAllowedOnlyWhenNoActiveRow() {
        assertTrue(FriendshipRules.canSendRequest(null))
        assertTrue(FriendshipRules.canSendRequest(pending.copy(status = FriendshipStatus.DECLINED)))
        assertFalse(FriendshipRules.canSendRequest(pending))
        assertFalse(FriendshipRules.canSendRequest(accepted))
        assertFalse(FriendshipRules.canSendRequest(blocked))
    }

    // ------------------------------------------------- privacy authorization

    private val allShared = ProgressSharingPreferences(
        shareActiveDays = true,
        shareMealsLogged = true,
        sharePlantVariety = true,
        shareRecipesPublished = true,
        shareRecipeAchievements = true,
    )

    @Test
    fun progressHiddenWithoutAcceptedFriendship() {
        assertFalse(FriendshipRules.visibleProgress(pending, allShared).anythingVisible)
        assertFalse(FriendshipRules.visibleProgress(null, allShared).anythingVisible)
        assertFalse(FriendshipRules.visibleProgress(blocked, allShared).anythingVisible)
    }

    @Test
    fun progressHiddenWithoutSharingOptIn() {
        assertFalse(FriendshipRules.visibleProgress(accepted, ProgressSharingPreferences()).anythingVisible)
        assertFalse(FriendshipRules.visibleProgress(accepted, null).anythingVisible)
    }

    @Test
    fun progressVisibleRequiresBothFriendshipAndOptIn() {
        val visible = FriendshipRules.visibleProgress(accepted, allShared)
        assertTrue(visible.activeDays && visible.mealsLogged && visible.plantVariety)
    }

    @Test
    fun perStatOptInRespected() {
        val onlyDays = FriendshipRules.visibleProgress(
            accepted, ProgressSharingPreferences(shareActiveDays = true),
        )
        assertTrue(onlyDays.activeDays)
        assertFalse(onlyDays.mealsLogged)
    }

    @Test
    fun blockedUsersCannotInteract() {
        assertFalse(FriendshipRules.interactionAllowed(blocked))
        assertTrue(FriendshipRules.interactionAllowed(accepted))
        assertTrue(FriendshipRules.interactionAllowed(null))
    }
}
