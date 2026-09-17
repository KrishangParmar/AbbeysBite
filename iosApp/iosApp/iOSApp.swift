import SwiftUI
import ComposeApp

@main
struct AbbeysBiteApp: App {

    init() {
        // Register the OneSignal bridge (no-op until the SPM package is added
        // and ONESIGNAL_APP_ID is configured — see README).
        PushBridge.register()
        MainViewControllerKt.startKoinIfNeeded()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
