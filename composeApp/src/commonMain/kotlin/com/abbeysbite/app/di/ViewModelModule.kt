package com.abbeysbite.app.di

import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.LoggingAnalytics
import com.abbeysbite.app.features.auth.AuthViewModel
import com.abbeysbite.app.features.community.CommunitySearchViewModel
import com.abbeysbite.app.features.community.CommunityViewModel
import com.abbeysbite.app.features.community.RecipeDetailViewModel
import com.abbeysbite.app.features.community.RecipeEditorViewModel
import com.abbeysbite.app.features.improve.ImproveSession
import com.abbeysbite.app.features.improve.ImproveViewModel
import com.abbeysbite.app.features.improve.MealChatViewModel
import com.abbeysbite.app.features.improve.VoiceViewModel
import com.abbeysbite.app.features.journal.JournalViewModel
import com.abbeysbite.app.features.onboarding.OnboardingViewModel
import com.abbeysbite.app.features.paywall.PaywallViewModel
import com.abbeysbite.app.features.profile.FriendsViewModel
import com.abbeysbite.app.features.profile.YouViewModel
import com.abbeysbite.app.platform.PlatformInfo
import org.koin.dsl.module

val appModule = module {
    single<Analytics> {
        if (com.abbeysbite.app.core.network.isSupabaseConfigured) {
            com.abbeysbite.app.analytics.SupabaseAnalytics(
                supabase = get(), platformInfo = get(), scope = get(),
            )
        } else {
            LoggingAnalytics(get<PlatformInfo>().isDebug)
        }
    }
    single { ImproveSession() }

    factory { AuthViewModel(get(), get(), get()) }
    factory { OnboardingViewModel(get(), get(), get()) }
    factory {
        ImproveViewModel(
            aiProvider = get(),
            session = get(),
            mediaPicker = get(),
            preferencesRepository = get(),
            pantryRepository = get(),
            journalRepository = get(),
            storageRepository = get(),
            billingManager = get(),
            limiter = get(),
            haptics = get(),
            analytics = get(),
            modelManager = get(),
            localEngine = get(),
            devSettings = get(),
            platformInfo = get(),
        )
    }
    factory { MealChatViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory {
        VoiceViewModel(
            aiProvider = get(),
            session = get(),
            speech = get(),
            tts = get(),
            preferencesRepository = get(),
            pantryRepository = get(),
            billingManager = get(),
            limiter = get(),
            analytics = get(),
        )
    }
    factory { JournalViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { CommunityViewModel(get(), get()) }
    factory { CommunitySearchViewModel(get(), get()) }
    factory { RecipeDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { RecipeEditorViewModel(get(), get(), get(), get()) }
    factory { YouViewModel(get(), get(), get(), get(), get(), get()) }
    factory { FriendsViewModel(get(), get(), get()) }
    factory { PaywallViewModel(get(), get()) }
}
