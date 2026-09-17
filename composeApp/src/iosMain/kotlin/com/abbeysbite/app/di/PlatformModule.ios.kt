package com.abbeysbite.app.di

import com.abbeysbite.app.platform.HapticsService
import com.abbeysbite.app.platform.IosHapticsService
import com.abbeysbite.app.platform.IosMediaPicker
import com.abbeysbite.app.platform.IosPushAdapter
import com.abbeysbite.app.platform.IosShareService
import com.abbeysbite.app.platform.IosSpeechRecognitionService
import com.abbeysbite.app.platform.IosTextToSpeechService
import com.abbeysbite.app.platform.IosUrlOpener
import com.abbeysbite.app.platform.MediaPicker
import com.abbeysbite.app.platform.PlatformInfo
import com.abbeysbite.app.platform.PushNotificationsAdapter
import com.abbeysbite.app.platform.ShareService
import com.abbeysbite.app.platform.SpeechRecognitionService
import com.abbeysbite.app.platform.TextToSpeechService
import com.abbeysbite.app.platform.UrlOpener
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

private class IosPlatformInfo : PlatformInfo {
    override val platformName: String = "ios"
    override val osVersion: String = UIDevice.currentDevice.systemVersion
    override val appVersion: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?: "1.0.0"

    @OptIn(ExperimentalNativeApi::class)
    override val isDebug: Boolean = Platform.isDebugBinary

    override val storeVariant: String = "appstore"
}

actual fun platformModule(): Module = module {
    single<PlatformInfo> { IosPlatformInfo() }
    single<com.abbeysbite.app.ai.local.LocalAiEngine> {
        com.abbeysbite.app.ai.local.IosGemmaEngine()
    }
    single<com.abbeysbite.app.ai.local.ModelStore> {
        com.abbeysbite.app.ai.local.IosModelStore()
    }
    single<com.abbeysbite.app.ads.AdsManager> {
        com.abbeysbite.app.ads.IosAdsManager(get(), get(), get())
    }
    single<MediaPicker> { IosMediaPicker() }
    single<HapticsService> { IosHapticsService() }
    single<ShareService> { IosShareService() }
    single<UrlOpener> { IosUrlOpener() }
    single<TextToSpeechService> { IosTextToSpeechService() }
    single<SpeechRecognitionService> { IosSpeechRecognitionService() }
    single<PushNotificationsAdapter> { IosPushAdapter() }
}
