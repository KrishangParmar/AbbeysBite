package com.abbeysbite.app.core.session

import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.AuthState
import com.abbeysbite.app.data.repository.UserScopedState
import com.abbeysbite.app.platform.PushNotificationsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Binds the authenticated Supabase identity to every downstream service so
 * one stable id (the Supabase Auth UUID) is used everywhere:
 *
 *  - RevenueCat App User ID  → entitlements follow the account, never the
 *    device, and switching accounts can't contaminate billing state
 *  - OneSignal External ID   → pushes target the account
 *  - Analytics user id       → coarse product analytics
 *
 * On sign-out it also wipes every [UserScopedState] holder (journal/pantry/
 * preferences caches, the Improve chat session) so a later login as a
 * different user can never see the previous user's data.
 *
 * Started once at app startup (both platforms).
 */
class AppSessionCoordinator(
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager,
    private val push: PushNotificationsAdapter,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
    private val userScopedState: List<UserScopedState> = emptyList(),
) {
    private var started = false
    private var lastSignedInUserId: String? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.authState.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> {
                        if (state.userId.isNotBlank() && state.userId != lastSignedInUserId) {
                            lastSignedInUserId = state.userId
                            billingManager.loginUser(state.userId)
                            push.login(state.userId)
                            analytics.setUserId(state.userId)
                        }
                    }
                    is AuthState.SignedOut -> {
                        if (lastSignedInUserId != null) {
                            lastSignedInUserId = null
                            billingManager.logoutUser()
                            push.logout()
                            analytics.setUserId(null)
                        }
                        // Always wipe per-user caches on sign-out, even if the
                        // sign-in was never observed (e.g. cold-start races).
                        userScopedState.forEach { it.clearUserState() }
                    }
                    else -> Unit
                }
            }
        }
    }
}
