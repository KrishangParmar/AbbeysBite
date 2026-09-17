package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only auth used when Supabase credentials are not configured
 * (fresh checkout / demo builds). Persists a fake session in settings so the
 * full app flow — including sign-out and deletion — works offline.
 */
class DemoAuthRepository(
    private val settings: Settings,
) : AuthRepository {

    private companion object {
        const val KEY_EMAIL = "demo_auth_email"
        const val DEMO_USER_ID = "demo-user-0000-0000-000000000001"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUserId: String?
        get() = (authState.value as? AuthState.SignedIn)?.userId

    override suspend fun restoreSession() {
        val email = settings.getStringOrNull(KEY_EMAIL)
        _authState.value = if (email != null) {
            AuthState.SignedIn(DEMO_USER_ID, email)
        } else {
            AuthState.SignedOut
        }
    }

    override suspend fun signUp(email: String, password: String): AppResult<Unit> =
        signIn(email, password)

    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        delay(600)
        settings.putString(KEY_EMAIL, email)
        _authState.value = AuthState.SignedIn(DEMO_USER_ID, email)
        return AppResult.Success(Unit)
    }

    override suspend fun signOut(): AppResult<Unit> {
        settings.remove(KEY_EMAIL)
        _authState.value = AuthState.SignedOut
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAccount(): AppResult<Unit> {
        settings.clear()
        _authState.value = AuthState.SignedOut
        return AppResult.Success(Unit)
    }
}
