package com.abbeysbite.app.platform

import kotlinx.coroutines.flow.Flow

/**
 * Platform capability contracts. Implementations live in androidMain/iosMain
 * and are bound through Koin platform modules — common code never touches
 * platform APIs directly.
 */

/** Result of capturing or picking a photo, already downscaled + JPEG-compressed. */
data class PickedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

enum class PermissionStatus { GRANTED, DENIED, DENIED_PERMANENTLY, NOT_DETERMINED }

interface MediaPicker {
    /** Opens the system camera. Returns null if the user cancels. */
    suspend fun capturePhoto(): PickedImage?

    /** Opens the photo library picker. Returns null if the user cancels. */
    suspend fun pickPhoto(): PickedImage?

    suspend fun cameraPermissionStatus(): PermissionStatus
}

sealed class SpeechEvent {
    data class Partial(val text: String) : SpeechEvent()
    data class Final(val text: String) : SpeechEvent()
    data class Error(val message: String, val permissionDenied: Boolean = false) : SpeechEvent()
    data object Ended : SpeechEvent()
}

interface SpeechRecognitionService {
    val isAvailable: Boolean

    /**
     * Hot stream of transcript events (no replay). Typed as [SharedFlow] so
     * callers can use `onSubscription` to start the recognizer only once
     * they're actually subscribed — otherwise synchronously-emitted errors
     * from [start] are lost.
     */
    fun events(): kotlinx.coroutines.flow.SharedFlow<SpeechEvent>

    suspend fun start()
    fun stop()
    suspend fun requestPermission(): PermissionStatus
}

interface TextToSpeechService {
    /** Speaks [text]; suspends until playback completes or is stopped. */
    suspend fun speak(text: String)
    fun stop()
    val isSpeaking: Flow<Boolean>
}

interface HapticsService {
    fun lightTap()
    fun success()
    fun warning()
}

interface ShareService {
    /** Opens the native share sheet with plain text. */
    fun shareText(text: String)

    /** Shares an image (PNG bytes) with optional caption via the share sheet. */
    fun shareImage(pngBytes: ByteArray, caption: String?)
}

/** Adapter over the platform push SDK (OneSignal). */
interface PushNotificationsAdapter {
    /** Initialize the SDK. Safe to call with a blank appId (becomes a no-op). */
    fun initialize(appId: String)

    /** Contextual permission prompt — only call after user opts in. */
    suspend fun requestPermission(): Boolean

    /** Associate the device with the signed-in user for targeting. */
    fun login(userId: String)
    fun logout()

    /** Tag used to control reminder categories server-side. */
    fun setReminderCategory(key: String, enabled: Boolean)
}

interface UrlOpener {
    fun openUrl(url: String)
}

/** Platform metadata used for analytics + diagnostics. */
interface PlatformInfo {
    val platformName: String   // "android" | "ios"
    val osVersion: String
    val appVersion: String
    val isDebug: Boolean

    /**
     * Distribution store this binary targets: "play" | "galaxy" | "appstore".
     * Decides which RevenueCat store configuration is initialized.
     */
    val storeVariant: String
}
