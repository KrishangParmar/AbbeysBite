package com.abbeysbite.app.di

import com.abbeysbite.app.BuildConfig
import com.abbeysbite.app.platform.AndroidHapticsService
import com.abbeysbite.app.platform.AndroidMediaPicker
import com.abbeysbite.app.platform.AndroidPushAdapter
import com.abbeysbite.app.platform.AndroidShareService
import com.abbeysbite.app.platform.AndroidSpeechRecognitionService
import com.abbeysbite.app.platform.AndroidTextToSpeechService
import com.abbeysbite.app.platform.AndroidUrlOpener
import com.abbeysbite.app.platform.HapticsService
import com.abbeysbite.app.platform.MediaPicker
import com.abbeysbite.app.platform.PlatformInfo
import com.abbeysbite.app.platform.PushNotificationsAdapter
import com.abbeysbite.app.platform.ShareService
import com.abbeysbite.app.platform.SpeechRecognitionService
import com.abbeysbite.app.platform.TextToSpeechService
import com.abbeysbite.app.platform.UrlOpener
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private class AndroidPlatformInfo : PlatformInfo {
    override val platformName: String = "android"
    override val osVersion: String = android.os.Build.VERSION.RELEASE ?: "unknown"
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val storeVariant: String = BuildConfig.STORE
}

actual fun platformModule(): Module = module {
    single<PlatformInfo> { AndroidPlatformInfo() }
    single<com.abbeysbite.app.ai.local.LocalAiEngine> {
        com.abbeysbite.app.ai.local.AndroidGemmaEngine(androidContext())
    }
    single<com.abbeysbite.app.ai.local.ModelStore> {
        com.abbeysbite.app.ai.local.AndroidModelStore(androidContext())
    }
    single<com.abbeysbite.app.ads.AdsManager> {
        com.abbeysbite.app.ads.AndroidAdsManager(
            context = androidContext(),
            billingManager = get(),
            platformInfo = get(),
            analytics = get(),
            scope = get(),
        )
    }
    single<MediaPicker> { AndroidMediaPicker(androidContext()) }
    single<HapticsService> { AndroidHapticsService(androidContext()) }
    single<ShareService> { AndroidShareService(androidContext()) }
    single<UrlOpener> { AndroidUrlOpener(androidContext()) }
    single<TextToSpeechService> { AndroidTextToSpeechService(androidContext()) }
    single<SpeechRecognitionService> { AndroidSpeechRecognitionService(androidContext()) }
    single<PushNotificationsAdapter> { AndroidPushAdapter(androidContext()) }
}
