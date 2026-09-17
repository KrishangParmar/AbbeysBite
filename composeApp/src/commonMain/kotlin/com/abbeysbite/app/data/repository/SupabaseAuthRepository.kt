package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
    scope: CoroutineScope,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Set when sign-up succeeded but the project requires email confirmation. */
    private var pendingConfirmationEmail: String? = null

    override val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                _authState.value = when (status) {
                    is SessionStatus.Authenticated -> {
                        pendingConfirmationEmail = null
                        val user = status.session.user
                        AuthState.SignedIn(user?.id.orEmpty(), user?.email)
                    }
                    is SessionStatus.NotAuthenticated ->
                        pendingConfirmationEmail
                            ?.let { AuthState.AwaitingEmailConfirmation(it) }
                            ?: AuthState.SignedOut
                    is SessionStatus.Initializing -> AuthState.Loading
                    else -> AuthState.SignedOut
                }
            }
        }
    }

    override suspend fun restoreSession() {
        // supabase-kt restores persisted sessions automatically on client init;
        // sessionStatus above transitions out of Initializing when done.
    }

    override suspend fun signUp(email: String, password: String): AppResult<Unit> = try {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // Supabase projects configured with email confirmation return NO
        // session here — the user must tap the emailed deep link first.
        // Never behave as if sign-up implies an authenticated session.
        if (supabase.auth.currentSessionOrNull() == null) {
            pendingConfirmationEmail = email
            _authState.value = AuthState.AwaitingEmailConfirmation(email)
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(mapAuthError(e))
    }

    override suspend fun resendConfirmationEmail(): AppResult<Unit> {
        val email = pendingConfirmationEmail
            ?: return AppResult.Failure(AppError.Validation("Nothing to resend."))
        return try {
            supabase.auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(mapAuthError(e))
        }
    }

    override fun cancelPendingConfirmation() {
        pendingConfirmationEmail = null
        if (_authState.value is AuthState.AwaitingEmailConfirmation) {
            _authState.value = AuthState.SignedOut
        }
    }

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = try {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(mapAuthError(e))
    }

    override suspend fun signOut(): AppResult<Unit> = try {
        supabase.auth.signOut()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        // Even if the network call fails, clear locally so the user isn't stuck.
        AppResult.Success(Unit)
    }

    override suspend fun deleteAccount(): AppResult<Unit> = try {
        supabase.functions.invoke("delete-account")
        supabase.auth.signOut()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    private fun mapAuthError(e: Exception): AppError {
        val msg = e.message.orEmpty().lowercase()
        return when {
            msg.contains("invalid login") || msg.contains("invalid_credentials") ->
                AppError.Validation("Email or password is incorrect.")
            msg.contains("already registered") || msg.contains("user_already_exists") ->
                AppError.Validation("An account with this email already exists. Try signing in.")
            msg.contains("password") && msg.contains("weak") || msg.contains("at least") ->
                AppError.Validation("Please use a stronger password (at least 8 characters).")
            msg.contains("email") && (msg.contains("invalid") || msg.contains("format")) ->
                AppError.Validation("That email address doesn’t look right.")
            msg.contains("rate") -> AppError.Validation("Too many attempts. Please wait a moment.")
            else -> e.toAppError()
        }
    }
}
