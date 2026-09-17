package com.abbeysbite.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.abbeysbite.app.features.auth.AuthScreen
import com.abbeysbite.app.features.auth.SplashScreen
import com.abbeysbite.app.features.community.CommunitySearchScreen
import com.abbeysbite.app.features.community.RecipeDetailScreen
import com.abbeysbite.app.features.community.RecipeEditorScreen
import com.abbeysbite.app.features.improve.MealChatScreen
import com.abbeysbite.app.features.improve.VoiceScreen
import com.abbeysbite.app.features.onboarding.OnboardingScreen
import com.abbeysbite.app.features.paywall.PaywallScreen
import com.abbeysbite.app.features.profile.DeleteAccountScreen
import com.abbeysbite.app.features.profile.FriendsScreen
import com.abbeysbite.app.features.profile.HelpScreen
import com.abbeysbite.app.features.profile.MyRecipesScreen
import com.abbeysbite.app.features.profile.NotificationSettingsScreen
import com.abbeysbite.app.features.profile.PantryScreen
import com.abbeysbite.app.features.profile.PreferencesScreen
import com.abbeysbite.app.features.profile.PrivacyScreen
import com.abbeysbite.app.features.profile.SavedRecipesScreen
import com.abbeysbite.app.features.shell.MainShell

@Composable
fun RootNavigation() {
    val navController = rememberNavController()

    // Notification deep links (emitted by platform entry points).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        DeepLinkBus.routes.collect { route ->
            runCatching { navController.navigate(route) }
        }
    }

    fun goToAuth() {
        navController.navigate(Route.Auth) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Auth> {
            AuthScreen(
                onAuthenticated = { needsOnboarding ->
                    val next: Route = if (needsOnboarding) Route.Onboarding else Route.Main
                    navController.navigate(next) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Route.Main) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Route.Main> {
            MainShell(
                onSignedOut = ::goToAuth,
                navigate = { route -> navController.navigate(route) },
            )
        }

        // ------------------------------------------------ improve flow
        composable<Route.MealChat> {
            MealChatScreen(
                onBack = { navController.popBackStack() },
                onOpenVoice = { navController.navigate(Route.VoiceMode("current")) },
                onOpenPaywall = { navController.navigate(Route.Paywall) },
            )
        }
        composable<Route.VoiceMode>(
            enterTransition = { slideInVertically(tween(280)) { it } + fadeIn() },
            exitTransition = { slideOutVertically(tween(240)) { it } + fadeOut() },
        ) {
            VoiceScreen(
                onClose = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Route.Paywall) },
            )
        }

        // ------------------------------------------------ community
        composable<Route.RecipeDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.RecipeDetail>()
            RecipeDetailScreen(
                recipeId = route.recipeId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.RecipeEditor(id)) },
            )
        }
        composable<Route.RecipeEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.RecipeEditor>()
            RecipeEditorScreen(
                recipeId = route.recipeId,
                onBack = { navController.popBackStack() },
                onPublished = { id ->
                    navController.popBackStack()
                    navController.navigate(Route.RecipeDetail(id))
                },
            )
        }
        composable<Route.CommunitySearch> {
            CommunitySearchScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
            )
        }

        // ------------------------------------------------ profile / settings
        composable<Route.Friends> {
            FriendsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.Pantry> {
            PantryScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.Preferences> {
            PreferencesScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.NotificationSettings> {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.PrivacySettings> {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.MyRecipes> {
            MyRecipesScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
                onCreateRecipe = { navController.navigate(Route.RecipeEditor(null)) },
            )
        }
        composable<Route.SavedRecipes> {
            SavedRecipesScreen(
                onBack = { navController.popBackStack() },
                onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
            )
        }
        composable<Route.Help> {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable<Route.Legal> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Legal>()
            com.abbeysbite.app.features.profile.LegalScreen(
                kindName = route.kindName,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.DeleteAccount> {
            DeleteAccountScreen(
                onBack = { navController.popBackStack() },
                onDeleted = ::goToAuth,
            )
        }

        // ------------------------------------------------ billing
        composable<Route.Paywall>(
            enterTransition = { slideInVertically(tween(300)) { it } + fadeIn() },
            exitTransition = { slideOutVertically(tween(240)) { it } + fadeOut() },
        ) {
            PaywallScreen(onClose = { navController.popBackStack() })
        }
    }
}
