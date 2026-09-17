package com.abbeysbite.app

import androidx.compose.ui.window.ComposeUIViewController
import com.abbeysbite.app.di.appModule
import com.abbeysbite.app.di.initKoin
import platform.UIKit.UIViewController

private var koinStarted = false

/** Called from Swift before creating the view controller. */
fun startKoinIfNeeded() {
    if (!koinStarted) {
        val koin = initKoin(extraModules = listOf(appModule)).koin
        koin.get<com.abbeysbite.app.platform.PushNotificationsAdapter>()
            .initialize(com.abbeysbite.app.core.config.AppSecrets.onesignalAppId)
        koin.get<com.abbeysbite.app.core.session.AppSessionCoordinator>().start()
        koin.get<com.abbeysbite.app.ai.local.ModelManager>().refresh()
        koinStarted = true
    }
}

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController {
    startKoinIfNeeded()
    return ComposeUIViewController { App() }
}
