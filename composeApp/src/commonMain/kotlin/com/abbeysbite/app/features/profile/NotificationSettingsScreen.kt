package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.platform.PushNotificationsAdapter
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Notification categories the user controls. Permission is requested
 * contextually — the first time they enable any category — never on launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    push: PushNotificationsAdapter = koinInject(),
    settings: Settings = koinInject(),
) {
    var permissionGranted by remember { mutableStateOf(settings.getBoolean("notif_permission", false)) }
    var mealReminders by remember { mutableStateOf(settings.getBoolean("notif_meal_reminders", false)) }
    var weeklyReflection by remember { mutableStateOf(settings.getBoolean("notif_weekly", false)) }
    var friendActivity by remember { mutableStateOf(settings.getBoolean("notif_friends", false)) }
    var recipeActivity by remember { mutableStateOf(settings.getBoolean("notif_recipes", false)) }
    val scope = rememberCoroutineScope()

    fun toggle(key: String, tag: String, value: Boolean, apply: (Boolean) -> Unit) {
        scope.launch {
            if (value && !permissionGranted) {
                val granted = push.requestPermission()
                permissionGranted = granted
                settings.putBoolean("notif_permission", granted)
                if (!granted) return@launch
            }
            apply(value)
            settings.putBoolean(key, value)
            push.setReminderCategory(tag, value)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
                "Choose what’s worth a ping. We’d rather send you nothing than spam you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.md))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    NotifToggle(
                        "Gentle meal reminder",
                        "One nudge a day, only if you haven’t logged anything",
                        mealReminders,
                    ) { v -> toggle("notif_meal_reminders", "meal_reminders", v) { mealReminders = it } }
                    NotifToggle(
                        "Weekly reflection ready",
                        "When your week’s patterns are ready to read",
                        weeklyReflection,
                    ) { v -> toggle("notif_weekly", "weekly_reflection", v) { weeklyReflection = it } }
                    NotifToggle(
                        "Friend requests & accepts",
                        "When someone wants to connect",
                        friendActivity,
                    ) { v -> toggle("notif_friends", "friend_activity", v) { friendActivity = it } }
                    NotifToggle(
                        "Recipe likes & saves",
                        "When your recipes get some love",
                        recipeActivity,
                    ) { v -> toggle("notif_recipes", "recipe_activity", v) { recipeActivity = it } }
                }
            }
            if (!permissionGranted && (mealReminders || weeklyReflection || friendActivity || recipeActivity)) {
                Spacer(Modifier.height(Dimens.md))
                PrimaryButton(
                    text = "Allow notifications",
                    onClick = {
                        scope.launch {
                            permissionGranted = push.requestPermission()
                            settings.putBoolean("notif_permission", permissionGranted)
                        }
                    },
                )
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@Composable
private fun NotifToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
