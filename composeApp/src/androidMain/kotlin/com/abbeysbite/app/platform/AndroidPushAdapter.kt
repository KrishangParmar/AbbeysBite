package com.abbeysbite.app.platform

import android.content.Context
import com.onesignal.OneSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OneSignal adapter for Android. When no app id is configured this becomes a
 * complete no-op so the app runs fine without push credentials.
 */
class AndroidPushAdapter(private val context: Context) : PushNotificationsAdapter {

    private var initialized = false

    override fun initialize(appId: String) {
        if (appId.isBlank() || initialized) return
        runCatching {
            OneSignal.initWithContext(context, appId)
            initialized = true
        }
    }

    override suspend fun requestPermission(): Boolean {
        if (!initialized) {
            // Fall back to the raw Android permission prompt so meal reminders
            // still work locally even without OneSignal configured.
            return ActivityResultBridge.host?.ensureNotificationPermission() ?: false
        }
        return withContext(Dispatchers.IO) {
            runCatching { OneSignal.Notifications.requestPermission(true) }.getOrDefault(false)
        }
    }

    override fun login(userId: String) {
        if (initialized) runCatching { OneSignal.login(userId) }
    }

    override fun logout() {
        if (initialized) runCatching { OneSignal.logout() }
    }

    override fun setReminderCategory(key: String, enabled: Boolean) {
        if (initialized) runCatching { OneSignal.User.addTag(key, enabled.toString()) }
    }
}
