import Foundation
import ComposeApp
#if canImport(OneSignalFramework)
import OneSignalFramework
#endif

/// Bridges the shared Kotlin `PushNotificationsAdapter` to the OneSignal iOS
/// SDK. The OneSignal package is added via Swift Package Manager
/// (https://github.com/OneSignal/OneSignal-iOS-SDK). Until it's added, every
/// call is a safe no-op so the app builds and runs without push credentials.
enum PushBridge {

    static func register() {
        IosPushBridgeHolder.shared.bridge = Bridge()
    }

    private final class Bridge: IosPushBridgeHolderBridge {
        func initialize(appId: String) {
            #if canImport(OneSignalFramework)
            OneSignal.initialize(appId, withLaunchOptions: nil)
            #endif
        }

        func requestPermission(callback: @escaping (KotlinBoolean) -> Void) {
            #if canImport(OneSignalFramework)
            OneSignal.Notifications.requestPermission({ accepted in
                callback(KotlinBoolean(bool: accepted))
            }, fallbackToSettings: false)
            #else
            callback(KotlinBoolean(bool: false))
            #endif
        }

        func login(userId: String) {
            #if canImport(OneSignalFramework)
            OneSignal.login(userId)
            #endif
        }

        func logout() {
            #if canImport(OneSignalFramework)
            OneSignal.logout()
            #endif
        }

        func setTag(key: String, value: String) {
            #if canImport(OneSignalFramework)
            OneSignal.User.addTag(key: key, value: value)
            #endif
        }
    }
}
