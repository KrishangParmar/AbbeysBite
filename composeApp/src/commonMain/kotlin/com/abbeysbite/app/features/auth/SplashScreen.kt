package com.abbeysbite.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.AuthState
import com.abbeysbite.app.data.repository.PreferencesRepository
import com.abbeysbite.app.navigation.Route
import org.koin.compose.koinInject

/**
 * Decides where the user lands: Auth when signed out, Onboarding when signed
 * in but not yet onboarded, otherwise the main shell.
 */
@Composable
fun SplashScreen(
    onNavigate: (Route) -> Unit,
    authRepository: AuthRepository = koinInject(),
    preferencesRepository: PreferencesRepository = koinInject(),
) {
    val authState by authRepository.authState.collectAsState()

    LaunchedEffect(Unit) {
        authRepository.restoreSession()
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Loading -> Unit // keep showing the brand mark
            is AuthState.SignedOut -> onNavigate(Route.Auth)
            is AuthState.AwaitingEmailConfirmation -> onNavigate(Route.Auth)
            is AuthState.SignedIn -> {
                val prefs = preferencesRepository.load().getOrNull()
                onNavigate(if (prefs?.onboardingCompleted == true) Route.Main else Route.Onboarding)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                Brand.appName,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                Brand.tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
