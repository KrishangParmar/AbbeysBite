package com.abbeysbite.app.data.repository

/**
 * Anything holding per-user state in memory (or under user-agnostic keys)
 * that must be wiped when the account signs out, so a subsequent login as a
 * different user can never observe the previous user's data.
 *
 * Cleared centrally by AppSessionCoordinator on [AuthState.SignedOut].
 */
interface UserScopedState {
    fun clearUserState()
}
