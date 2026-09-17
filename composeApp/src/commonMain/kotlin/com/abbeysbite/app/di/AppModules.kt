package com.abbeysbite.app.di

import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.ai.GemmaGatewayProvider
import com.abbeysbite.app.ai.LocalGemmaProvider
import com.abbeysbite.app.ai.MockAiProvider
import com.abbeysbite.app.ai.local.ModelManager
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.billing.FreeTierLimiter
import com.abbeysbite.app.billing.UnconfiguredBillingManager
import com.abbeysbite.app.core.network.createAppSupabaseClient
import com.abbeysbite.app.core.network.isSupabaseConfigured
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.DemoAuthRepository
import com.abbeysbite.app.data.repository.DemoFriendsRepository
import com.abbeysbite.app.data.repository.DemoRecipeRepository
import com.abbeysbite.app.data.repository.FriendsRepository
import com.abbeysbite.app.data.repository.InMemoryMediaStorageRepository
import com.abbeysbite.app.data.repository.JournalRepository
import com.abbeysbite.app.data.repository.LocalJournalRepository
import com.abbeysbite.app.data.repository.LocalPantryRepository
import com.abbeysbite.app.data.repository.LocalPreferencesRepository
import com.abbeysbite.app.data.repository.LocalProfileRepository
import com.abbeysbite.app.data.repository.MediaStorageRepository
import com.abbeysbite.app.data.repository.PantryRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import com.abbeysbite.app.data.repository.ProfileRepository
import com.abbeysbite.app.data.repository.RecipeRepository
import com.abbeysbite.app.data.repository.ReportsRepository
import com.abbeysbite.app.data.repository.SupabaseAuthRepository
import com.abbeysbite.app.data.repository.SupabaseFriendsRepository
import com.abbeysbite.app.data.repository.SupabaseJournalRepository
import com.abbeysbite.app.data.repository.SupabaseMediaStorageRepository
import com.abbeysbite.app.data.repository.SupabasePantryRepository
import com.abbeysbite.app.data.repository.SupabasePreferencesRepository
import com.abbeysbite.app.data.repository.SupabaseProfileRepository
import com.abbeysbite.app.data.repository.SupabaseRecipeRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule = module {
    single { Settings() }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createAppSupabaseClient() }

    single<AuthRepository> {
        if (isSupabaseConfigured) SupabaseAuthRepository(get(), get())
        else DemoAuthRepository(get())
    }
    single<PreferencesRepository> {
        if (isSupabaseConfigured) SupabasePreferencesRepository(get(), get())
        else LocalPreferencesRepository(get())
    }
    single<PantryRepository> {
        if (isSupabaseConfigured) SupabasePantryRepository(get(), get())
        else LocalPantryRepository(get())
    }
    single<ProfileRepository> {
        if (isSupabaseConfigured) SupabaseProfileRepository(get(), get())
        else LocalProfileRepository(get(), get())
    }
    single<JournalRepository> {
        if (isSupabaseConfigured) SupabaseJournalRepository(get(), get())
        else LocalJournalRepository(
            settings = get(),
            // Sample journal for development only — never seeds in release builds.
            seedDemoData = get<com.abbeysbite.app.platform.PlatformInfo>().isDebug,
        )
    }
    single<MediaStorageRepository> {
        if (isSupabaseConfigured) SupabaseMediaStorageRepository(get(), get())
        else InMemoryMediaStorageRepository()
    }
    single<RecipeRepository> {
        if (isSupabaseConfigured) SupabaseRecipeRepository(get(), get(), get())
        else DemoRecipeRepository(get(), get())
    }
    single<FriendsRepository> {
        if (isSupabaseConfigured) SupabaseFriendsRepository(get(), get())
        else DemoFriendsRepository(get(), get())
    }
    // Both implementations also handle reports; share the same instance.
    single<ReportsRepository> { get<FriendsRepository>() as ReportsRepository }

    // ------------------------------------------------------------------
    // AI: on-device Gemma is the PRIMARY production path (AI_BACKEND=local).
    // Meal photos and conversations never leave the device. The cloud
    // gateway remains compiled ONLY as an explicit debug fallback
    // (AI_BACKEND=gateway + CLOUD_AI_FALLBACK_ENABLED=true + debug build) and
    // is never selected silently. Local failures surface as local failures.
    // ------------------------------------------------------------------
    single { ModelManager(store = get(), scope = get()) }
    single<AiProvider> {
        val platform = get<com.abbeysbite.app.platform.PlatformInfo>()
        val backend = com.abbeysbite.app.core.config.AppSecrets.aiBackend.ifBlank { "local" }
        val cloudFallbackExplicitlyEnabled =
            com.abbeysbite.app.core.config.AppSecrets.cloudAiFallbackEnabled
                .equals("true", ignoreCase = true)
        val primary: AiProvider =
            if (backend == "gateway" && cloudFallbackExplicitlyEnabled && platform.isDebug) {
                GemmaGatewayProvider(get())
            } else {
                LocalGemmaProvider(engine = get(), modelManager = get())
            }
        if (platform.isDebug) {
            com.abbeysbite.app.ai.DebugSwitchingAiProvider(
                real = primary,
                mock = MockAiProvider(),
                devSettings = get(),
            )
        } else {
            primary
        }
    }

    single { com.abbeysbite.app.billing.DebugDevSettings(get()) }
    single<BillingManager> {
        val platform = get<com.abbeysbite.app.platform.PlatformInfo>()
        val secrets = com.abbeysbite.app.core.config.AppSecrets
        // Store-aware public SDK key selection. Galaxy binaries use the galx_
        // key with the native Galaxy configuration; Play uses goog_; iOS uses
        // appl_ (pending Apple setup). Debug PLAY/iOS builds prefer the
        // RevenueCat Test Store key so purchase flows are exercisable without
        // store credentials — Galaxy debug keeps the true Galaxy path
        // (GalaxyBillingMode.TEST needs the real galx_ key on a device).
        val apiKey = com.abbeysbite.app.billing.RevenueCatKeySelection.selectApiKey(
            storeVariant = platform.storeVariant,
            isDebug = platform.isDebug,
            googleKey = secrets.revenuecatAndroidPublicSdkKey,
            galaxyKey = secrets.revenuecatGalaxyPublicSdkKey,
            appleKey = secrets.revenuecatIosPublicSdkKey,
            testStoreKey = secrets.revenuecatTestStoreApiKey,
        )
        val real: BillingManager = if (apiKey.isBlank()) {
            UnconfiguredBillingManager()
        } else {
            com.abbeysbite.app.billing.RevenueCatBillingManager(
                apiKey = apiKey,
                storeVariant = platform.storeVariant,
                isDebugBuild = platform.isDebug,
                scope = get(),
            ).also { it.configure() }
        }
        // Debug builds can force-enable premium to test gated features;
        // impossible in release (guarded by isDebug).
        if (platform.isDebug) {
            com.abbeysbite.app.billing.DebugOverrideBillingManager(real, get(), get())
        } else {
            real
        }
    }

    single { com.abbeysbite.app.core.session.NotificationSender(get(), get()) }

    single {
        com.abbeysbite.app.core.session.AppSessionCoordinator(
            authRepository = get(),
            billingManager = get(),
            push = get(),
            analytics = get(),
            scope = get(),
            userScopedState = listOf(
                get<com.abbeysbite.app.data.repository.JournalRepository>(),
                get<com.abbeysbite.app.data.repository.PreferencesRepository>(),
                get<com.abbeysbite.app.data.repository.PantryRepository>(),
                get<com.abbeysbite.app.features.improve.ImproveSession>(),
            ).filterIsInstance<com.abbeysbite.app.data.repository.UserScopedState>(),
        )
    }

    single {
        val settings = get<Settings>()
        FreeTierLimiter(
            readCount = { key -> settings.getInt(key, 0) },
            writeCount = { key, value -> settings.putInt(key, value) },
        )
    }
}

/** Platform-specific bindings (media picker, speech, TTS, haptics, share…). */
expect fun platformModule(): Module

fun initKoin(
    extraModules: List<Module> = emptyList(),
    platformSetup: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    platformSetup()
    modules(listOf(coreModule, platformModule()) + extraModules)
}
