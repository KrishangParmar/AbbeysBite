package com.abbeysbite.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** Non-null → show the "check your email" confirmation screen. */
    val awaitingConfirmationEmail: String? = null,
    val resendMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 8 && !loading
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Mirror the repository's confirmation state (deep-link confirmation
        // flips it to SignedIn, which AuthScreen observes for navigation).
        viewModelScope.launch {
            authRepository.authState.collect { auth ->
                _state.update {
                    it.copy(
                        awaitingConfirmationEmail =
                            (auth as? com.abbeysbite.app.data.repository.AuthState.AwaitingEmailConfirmation)?.email,
                    )
                }
            }
        }
    }

    fun resendConfirmation() {
        viewModelScope.launch {
            authRepository.resendConfirmationEmail()
                .onSuccess { _state.update { it.copy(resendMessage = "Sent — check your inbox.") } }
                .onFailure { e -> _state.update { it.copy(resendMessage = e.message) } }
        }
    }

    fun backToSignIn() {
        authRepository.cancelPendingConfirmation()
        _state.update { it.copy(awaitingConfirmationEmail = null, resendMessage = null, isSignUp = false) }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun toggleMode() = _state.update { it.copy(isSignUp = !it.isSignUp, error = null) }

    fun submit(onAuthenticated: (needsOnboarding: Boolean) -> Unit) {
        val s = _state.value
        if (!isValidEmail(s.email)) {
            _state.update { it.copy(error = "That email address doesn’t look right.") }
            return
        }
        if (s.password.length < 8) {
            _state.update { it.copy(error = "Password needs at least 8 characters.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.isSignUp) {
                authRepository.signUp(s.email.trim(), s.password)
            } else {
                authRepository.signIn(s.email.trim(), s.password)
            }
            result
                .onSuccess {
                    // Email-confirmation projects: sign-up succeeds WITHOUT a
                    // session — stay here and show the check-your-email UI.
                    if (authRepository.currentUserId == null) {
                        analytics.track("sign_up_awaiting_confirmation")
                        _state.update { it.copy(loading = false) }
                        return@launch
                    }
                    analytics.track(if (s.isSignUp) "sign_up_completed" else "sign_in_completed")
                    val prefs = preferencesRepository.load().getOrNull()
                    _state.update { it.copy(loading = false) }
                    onAuthenticated(prefs?.onboardingCompleted != true)
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    companion object {
        fun isValidEmail(email: String): Boolean =
            Regex("^[A-Za-z0-9+_.%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email.trim())
    }
}
