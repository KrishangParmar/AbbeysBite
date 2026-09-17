package com.abbeysbite.app.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.InitialsAvatar
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.core.designsystem.StatusPill
import com.abbeysbite.app.platform.UrlOpener
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class YouNavActions(
    val openFriends: () -> Unit = {},
    val openMyRecipes: () -> Unit = {},
    val openSavedRecipes: () -> Unit = {},
    val openPreferences: () -> Unit = {},
    val openPantry: () -> Unit = {},
    val openNotifications: () -> Unit = {},
    val openPrivacy: () -> Unit = {},
    val openPaywall: () -> Unit = {},
    val openHelp: () -> Unit = {},
    val openDeleteAccount: () -> Unit = {},
    val openLegal: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTab(
    onSignedOut: () -> Unit,
    actions: YouNavActions = YouNavActions(),
    viewModel: YouViewModel = koinViewModel(),
    urlOpener: UrlOpener = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val profile = state.profile

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding),
    ) {
        Spacer(Modifier.height(Dimens.lg))

        // ------------------------------------------------ profile header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(onClick = viewModel::changeAvatar)) {
                if (profile?.avatarUrl != null && !profile.avatarUrl.startsWith("memory://")) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "Your avatar",
                        modifier = Modifier.size(72.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    InitialsAvatar(name = profile?.displayOrUsername ?: "You", size = 72.dp)
                }
            }
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.displayOrUsername ?: "You",
                    style = MaterialTheme.typography.headlineMedium,
                )
                profile?.username?.let {
                    Text(
                        "@$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            androidx.compose.material3.IconButton(onClick = viewModel::openEditSheet) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit profile")
            }
        }

        Spacer(Modifier.height(Dimens.lg))

        // ------------------------------------------------ premium status
        AppCard(Modifier.fillMaxWidth(), onClick = actions.openPaywall) {
            Row(
                Modifier.padding(Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.isPremium) "Premium active" else "Try Premium",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (state.isPremium) "Unlimited analyses, chat and voice"
                        else "Unlimited meal analyses, chat, voice and more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isPremium) {
                    StatusPill(
                        "Active",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        Spacer(Modifier.height(Dimens.lg))

        // ------------------------------------------------ sections
        SettingsGroup {
            SettingsRow(Icons.Filled.People, "Friends", badge = state.pendingRequestCount, onClick = actions.openFriends)
            SettingsRow(Icons.AutoMirrored.Filled.MenuBook, "My recipes", onClick = actions.openMyRecipes)
            SettingsRow(Icons.Filled.Bookmark, "Saved recipes", onClick = actions.openSavedRecipes)
        }
        Spacer(Modifier.height(Dimens.md))
        SettingsGroup {
            SettingsRow(Icons.Filled.Tune, "Food preferences", onClick = actions.openPreferences)
            SettingsRow(Icons.Filled.Kitchen, "Pantry", onClick = actions.openPantry)
            SettingsRow(Icons.Filled.Notifications, "Notifications", onClick = actions.openNotifications)
            SettingsRow(Icons.Filled.Lock, "Privacy & sharing", onClick = actions.openPrivacy)
        }
        Spacer(Modifier.height(Dimens.md))
        SettingsGroup {
            SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Help & support", onClick = actions.openHelp)
            SettingsRow(Icons.Filled.Description, "Terms of service",
                onClick = { actions.openLegal(com.abbeysbite.app.core.config.LegalContent.Kind.TERMS.name) })
            SettingsRow(Icons.Filled.Shield, "Privacy policy",
                onClick = { actions.openLegal(com.abbeysbite.app.core.config.LegalContent.Kind.PRIVACY.name) })
            SettingsRow(Icons.Filled.People, "Community guidelines",
                onClick = { actions.openLegal(com.abbeysbite.app.core.config.LegalContent.Kind.GUIDELINES.name) })
        }
        Spacer(Modifier.height(Dimens.md))
        SettingsGroup {
            SettingsRow(
                Icons.AutoMirrored.Filled.Logout, "Log out",
                onClick = { viewModel.signOut(onSignedOut) },
            )
            SettingsRow(
                Icons.Filled.Delete, "Delete account",
                tint = MaterialTheme.colorScheme.error,
                onClick = actions.openDeleteAccount,
            )
        }

        // Debug builds only: test-entitlement override for exercising premium
        // features without a store purchase. Never present in release builds.
        val platformInfo = org.koin.compose.koinInject<com.abbeysbite.app.platform.PlatformInfo>()
        if (platformInfo.isDebug) {
            val devSettings = org.koin.compose.koinInject<com.abbeysbite.app.billing.DebugDevSettings>()
            val forced by devSettings.forcePremium.collectAsState()
            Spacer(Modifier.height(Dimens.md))
            SettingsGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.md, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Force premium (debug)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Test-only entitlement override — debug builds only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = forced,
                        onCheckedChange = devSettings::setForcePremium,
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.xl))
    }

    if (state.editSheetOpen) {
        ModalBottomSheet(onDismissRequest = viewModel::closeEditSheet) {
            Column(Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.xl)) {
                Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Dimens.md))
                OutlinedTextField(
                    value = state.editUsername,
                    onValueChange = viewModel::onEditUsername,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("Lowercase letters, numbers and underscores") },
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlinedTextField(
                    value = state.editDisplayName,
                    onValueChange = viewModel::onEditDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(Dimens.sm))
                OutlinedTextField(
                    value = state.editBio,
                    onValueChange = viewModel::onEditBio,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bio") },
                    minLines = 2,
                    maxLines = 3,
                    shape = MaterialTheme.shapes.medium,
                )
                state.editError?.let { error ->
                    Spacer(Modifier.height(Dimens.sm))
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(Dimens.lg))
                PrimaryButton(text = "Save", onClick = viewModel::saveProfile, loading = state.saving)
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    badge: Int = 0,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Dimens.md))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (badge > 0) {
            Badge { Text(badge.toString()) }
            Spacer(Modifier.width(Dimens.sm))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
