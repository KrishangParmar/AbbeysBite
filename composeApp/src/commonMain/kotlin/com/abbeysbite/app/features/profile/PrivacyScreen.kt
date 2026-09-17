package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.InitialsAvatar
import com.abbeysbite.app.data.model.ProgressSharingPreferences
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.repository.FriendsRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Progress sharing controls + blocked users. Everything is opt-in and off by
 * default; private data (photos, chats, allergies) is never shareable at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject(),
    friendsRepository: FriendsRepository = koinInject(),
) {
    var prefs by remember { mutableStateOf<ProgressSharingPreferences?>(null) }
    var blocked by remember { mutableStateOf<List<Profile>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        prefs = preferencesRepository.loadSharingPreferences().getOrNull()
            ?: ProgressSharingPreferences()
        blocked = friendsRepository.blockedUsers().getOrNull() ?: emptyList()
    }

    fun save(updated: ProgressSharingPreferences) {
        prefs = updated
        scope.launch { preferencesRepository.saveSharingPreferences(updated) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & sharing") },
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
        val current = prefs ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
        ) {
            Text(
                "What accepted friends can see",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Dimens.xs))
            Text(
                "Everything is off until you turn it on. Meal photos, chats, allergies and health details are never shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.md))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    ShareToggle("Active days", current.shareActiveDays) {
                        save(current.copy(shareActiveDays = it))
                    }
                    ShareToggle("Meals logged", current.shareMealsLogged) {
                        save(current.copy(shareMealsLogged = it))
                    }
                    ShareToggle("Plant variety", current.sharePlantVariety) {
                        save(current.copy(sharePlantVariety = it))
                    }
                    ShareToggle("Recipes published", current.shareRecipesPublished) {
                        save(current.copy(shareRecipesPublished = it))
                    }
                    ShareToggle("Recipe achievements", current.shareRecipeAchievements) {
                        save(current.copy(shareRecipeAchievements = it))
                    }
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            Text("Blocked users", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.sm))
            if (blocked.isEmpty()) {
                Text(
                    "You haven’t blocked anyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    blocked.forEach { profile ->
                        AppCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = Dimens.md, vertical = Dimens.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InitialsAvatar(name = profile.displayOrUsername, size = 36.dp)
                                Spacer(Modifier.width(Dimens.md))
                                Text(
                                    "@${profile.username}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            friendsRepository.unblock(profile.id)
                                            blocked = friendsRepository.blockedUsers().getOrNull() ?: emptyList()
                                        }
                                    },
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@Composable
private fun ShareToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
