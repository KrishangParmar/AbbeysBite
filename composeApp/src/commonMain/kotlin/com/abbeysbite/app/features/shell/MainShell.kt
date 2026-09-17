package com.abbeysbite.app.features.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.abbeysbite.app.features.community.CommunityTab
import com.abbeysbite.app.features.improve.ImproveTab
import com.abbeysbite.app.features.journal.JournalTab
import com.abbeysbite.app.features.profile.YouNavActions
import com.abbeysbite.app.features.profile.YouTab
import com.abbeysbite.app.navigation.MainTab
import com.abbeysbite.app.navigation.Route

private fun MainTab.icon(selected: Boolean): ImageVector = when (this) {
    MainTab.IMPROVE -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    MainTab.COMMUNITY -> if (selected) Icons.Filled.People else Icons.Outlined.People
    MainTab.JOURNAL -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
    MainTab.YOU -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

@Composable
fun MainShell(
    onSignedOut: () -> Unit,
    navigate: (Route) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.IMPROVE) }

    // Deep-linked tab switches (e.g. weekly-reflection notification → Journal).
    val requestedTab by com.abbeysbite.app.navigation.DeepLinkBus.tabRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(requestedTab) {
        requestedTab?.let {
            selectedTab = it
            com.abbeysbite.app.navigation.DeepLinkBus.consumeTabRequest()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon(selected),
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabContent",
        ) { tab ->
            when (tab) {
                MainTab.IMPROVE -> ImproveTab(
                    onOpenChat = { navigate(Route.MealChat("current")) },
                    onOpenVoice = { navigate(Route.VoiceMode("current")) },
                    onOpenPaywall = { navigate(Route.Paywall) },
                )
                MainTab.COMMUNITY -> CommunityTab(
                    onOpenRecipe = { id -> navigate(Route.RecipeDetail(id)) },
                    onOpenSearch = { navigate(Route.CommunitySearch) },
                    onCreateRecipe = { navigate(Route.RecipeEditor(null)) },
                )
                MainTab.JOURNAL -> JournalTab()
                MainTab.YOU -> YouTab(
                    onSignedOut = onSignedOut,
                    actions = YouNavActions(
                        openFriends = { navigate(Route.Friends) },
                        openMyRecipes = { navigate(Route.MyRecipes) },
                        openSavedRecipes = { navigate(Route.SavedRecipes) },
                        openPreferences = { navigate(Route.Preferences) },
                        openPantry = { navigate(Route.Pantry) },
                        openNotifications = { navigate(Route.NotificationSettings) },
                        openPrivacy = { navigate(Route.PrivacySettings) },
                        openPaywall = { navigate(Route.Paywall) },
                        openHelp = { navigate(Route.Help) },
                        openDeleteAccount = { navigate(Route.DeleteAccount) },
                        openLegal = { kind -> navigate(Route.Legal(kind)) },
                    ),
                )
            }
        }
    }
}
