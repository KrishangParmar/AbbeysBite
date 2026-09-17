package com.abbeysbite.app.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    onAuthenticated: (needsOnboarding: Boolean) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    // Deep-link email confirmation completes here: the repo flips to SignedIn
    // while this screen is visible → continue into onboarding.
    val authRepo = org.koin.compose.koinInject<com.abbeysbite.app.data.repository.AuthRepository>()
    val authState by authRepo.authState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(authState) {
        val signedIn = authState as? com.abbeysbite.app.data.repository.AuthState.SignedIn
        if (signedIn != null && state.awaitingConfirmationEmail != null) {
            onAuthenticated(true)
        }
    }

    state.awaitingConfirmationEmail?.let { email ->
        CheckEmailScreen(
            email = email,
            resendMessage = state.resendMessage,
            onResend = viewModel::resendConfirmation,
            onBack = viewModel::backToSignIn,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Text(
            Brand.appName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            Brand.tagline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))

        Text(
            if (state.isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.lg))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(Dimens.md))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            supportingText = if (state.isSignUp) {
                { Text("At least 8 characters") }
            } else null,
            shape = MaterialTheme.shapes.medium,
        )

        AnimatedVisibility(visible = state.error != null) {
            Column {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(Dimens.lg))
        PrimaryButton(
            text = if (state.isSignUp) "Create account" else "Sign in",
            onClick = { viewModel.submit(onAuthenticated) },
            enabled = state.canSubmit,
            loading = state.loading,
        )
        Spacer(Modifier.height(Dimens.sm))
        TextButton(onClick = viewModel::toggleMode) {
            Text(
                if (state.isSignUp) "Already have an account? Sign in"
                else "New here? Create an account",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(Dimens.xl))
    }
}

/** Shown after sign-up when the project requires email confirmation. */
@Composable
private fun CheckEmailScreen(
    email: String,
    resendMessage: String?,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📬", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(Dimens.lg))
        Text(
            "Check your email",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            "We sent a confirmation link to\n$email\n\nTap it on this device and you’ll be signed in automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        resendMessage?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Dimens.xl))
        TextButton(onClick = onResend) { Text("Resend email") }
        TextButton(onClick = onBack) {
            Text("Back to sign in", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
