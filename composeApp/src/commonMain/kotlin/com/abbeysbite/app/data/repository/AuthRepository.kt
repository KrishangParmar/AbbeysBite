package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import kotlinx.coroutines.flow.StateFlow

sealed class AuthState {
    data object Loading : AuthState()
    data object SignedOut : AuthState()

    /**
     * Sign-up succeeded but the Supabase project requires email confirmation —
     * no session exists yet. The user confirms via the emailed deep link
     * (abbeysbite://auth-callback), which transitions to [SignedIn].
     */
    data class AwaitingEmailConfirmation(val email: String) : AuthState()

    data class SignedIn(val userId: String, val email: String?) : AuthState()
}

interface AuthRepository {
    val authState: StateFlow<AuthState>

    val currentUserId: String?

    /** Restore a persisted session if one exists. Called once at startup. */
    suspend fun restoreSession()

    suspend fun signUp(email: String, password: String): AppResult<Unit>

    /** Re-sends the confirmation email while in [AuthState.AwaitingEmailConfirmation]. */
    suspend fun resendConfirmationEmail(): AppResult<Unit> =
        AppResult.Failure(com.abbeysbite.app.core.util.AppError.Validation("Not supported."))

    /** Leaves the awaiting-confirmation state (back to sign-in). */
    fun cancelPendingConfirmation() {}

    suspend fun signIn(email: String, password: String): AppResult<Unit>

    suspend fun signOut(): AppResult<Unit>

    /**
     * Apple-compliant account deletion: removes the auth user and all owned
     * rows/storage objects (server-side via the `delete-account` Edge Function).
     */
    suspend fun deleteAccount(): AppResult<Unit>
}
