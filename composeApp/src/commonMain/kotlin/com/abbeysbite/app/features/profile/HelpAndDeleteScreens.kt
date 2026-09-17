package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.platform.UrlOpener
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    urlOpener: UrlOpener = koinInject(),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Help & support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
        ) {
            val faqs = listOf(
                "How does meal analysis work?" to
                    "Snap a photo (or describe your meal) and our kitchen assistant looks for protein, fibre and healthy fats, then suggests small additions that fit what you already have. Analyses are estimates, not medical advice.",
                "Why don’t you count calories?" to
                    "${Brand.appName} is built on a simple idea: ${Brand.tagline.lowercase()} We focus on adding helpful things to meals you already love, not restricting or scoring them.",
                "Is my food data private?" to
                    "Yes. Meal photos and chats are private to your account. Friends only ever see the coarse stats you explicitly turn on in Privacy & sharing.",
                "How do allergies work?" to
                    "Foods you list under Preferences are excluded from suggestions, but we can’t guarantee allergen safety — always double-check ingredients yourself.",
                "How do I cancel Premium?" to
                    "Subscriptions are managed by the App Store / Google Play. Open your store subscription settings to cancel — access lasts until the end of the billing period.",
            )
            faqs.forEach { (question, answer) ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.md)) {
                        Text(question, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.xs))
                        Text(
                            answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
            }
            Spacer(Modifier.height(Dimens.md))
            PrimaryButton(
                text = "Contact support",
                onClick = {
                    urlOpener.openUrl(
                        com.abbeysbite.app.core.config.LegalContent.supportUrl
                            ?: "mailto:support@abbeysbite.app"
                    )
                },
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

/**
 * Apple-compliant in-app account deletion: explains scope, requires typed
 * confirmation, deletes server-side data via the delete-account function.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    authRepository: AuthRepository = koinInject(),
) {
    var confirmation by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Delete account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
        ) {
            Text(
                "This permanently deletes your account and everything in it:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Dimens.sm))
            listOf(
                "Your profile and settings",
                "All meal photos and journal entries",
                "Your recipes, likes and saves",
                "Friendships and shared progress",
                "Chat history",
            ).forEach {
                Text(
                    "•  $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Text(
                "Active subscriptions must be cancelled separately in the App Store / Google Play. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.lg))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type DELETE to confirm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = MaterialTheme.shapes.medium,
            )
            error?.let {
                Spacer(Modifier.height(Dimens.sm))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Dimens.lg))
            androidx.compose.material3.Button(
                onClick = {
                    deleting = true
                    error = null
                    scope.launch {
                        authRepository.deleteAccount()
                            .onSuccess { onDeleted() }
                            .onFailure {
                                deleting = false
                                error = it.message
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = confirmation.trim().equals("DELETE", ignoreCase = false) && !deleting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (deleting) "Deleting…" else "Permanently delete my account")
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}
