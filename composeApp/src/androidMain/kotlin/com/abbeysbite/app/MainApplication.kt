package com.abbeysbite.app

import android.app.Application
import com.abbeysbite.app.ai.local.ModelManager
import com.abbeysbite.app.core.config.AppSecrets
import com.abbeysbite.app.core.session.AppSessionCoordinator
import com.abbeysbite.app.di.appModule
import com.abbeysbite.app.di.initKoin
import com.abbeysbite.app.platform.PushNotificationsAdapter
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = initKoin(
            extraModules = listOf(appModule),
            platformSetup = { androidContext(this@MainApplication) },
        ).koin

        // OneSignal must exist from process start so pushes/deep links work;
        // permission is still only requested contextually in-app.
        koin.get<PushNotificationsAdapter>().initialize(AppSecrets.onesignalAppId)

        // Bind Supabase identity -> RevenueCat / OneSignal / analytics.
        koin.get<AppSessionCoordinator>().start()

        // Settle model state early so Improve renders the right AI status.
        koin.get<ModelManager>().refresh()

        // Ads init is a no-op for premium users / unconfigured serving.
        koin.get<com.abbeysbite.app.ads.AdsManager>().initialize()
    }
}
