@file:Suppress("CONFLICTING_OVERLOADS") // AVSpeechSynthesizer delegate methods share a Kotlin signature

package com.abbeysbite.app.platform

import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSURL
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosHapticsService : HapticsService {
    override fun lightTap() {
        dispatch_async(dispatch_get_main_queue()) {
            UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
        }
    }

    override fun success() {
        dispatch_async(dispatch_get_main_queue()) {
            UINotificationFeedbackGenerator().notificationOccurred(
                UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
            )
        }
    }

    override fun warning() {
        dispatch_async(dispatch_get_main_queue()) {
            UINotificationFeedbackGenerator().notificationOccurred(
                UINotificationFeedbackType.UINotificationFeedbackTypeWarning
            )
        }
    }
}

class IosUrlOpener : UrlOpener {
    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}

class IosShareService : ShareService {

    private fun present(items: List<*>) {
        dispatch_async(dispatch_get_main_queue()) {
            var top = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (top?.presentedViewController != null) top = top.presentedViewController
            val controller = UIActivityViewController(
                activityItems = items.filterNotNull(),
                applicationActivities = null,
            )
            top?.presentViewController(controller, animated = true, completion = null)
        }
    }

    override fun shareText(text: String) = present(listOf(text))

    @OptIn(ExperimentalForeignApi::class)
    override fun shareImage(pngBytes: ByteArray, caption: String?) {
        val image = UIImage(data = pngBytes.toNSData())
        present(listOfNotNull(image, caption))
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(
        bytes = if (isEmpty()) null else pinned.addressOf(0),
        length = size.toULong(),
    )
}

class IosTextToSpeechService : TextToSpeechService {

    private val synthesizer = AVSpeechSynthesizer()
    private var delegate: AVSpeechSynthesizerDelegateProtocol? = null

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        _isSpeaking.value = true
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val speechDelegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
                    @ObjCSignatureOverride
                    override fun speechSynthesizer(
                        synthesizer: AVSpeechSynthesizer,
                        didFinishSpeechUtterance: AVSpeechUtterance,
                    ) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }

                    @ObjCSignatureOverride
                    override fun speechSynthesizer(
                        synthesizer: AVSpeechSynthesizer,
                        didCancelSpeechUtterance: AVSpeechUtterance,
                    ) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                delegate = speechDelegate
                synthesizer.delegate = speechDelegate
                val utterance = AVSpeechUtterance(string = text).apply {
                    voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
                    rate = 0.5f
                }
                synthesizer.speakUtterance(utterance)
                cont.invokeOnCancellation {
                    synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
                    _isSpeaking.value = false
                }
            }
        }
    }

    override fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        _isSpeaking.value = false
    }
}

/**
 * OneSignal on iOS is integrated from Swift (SPM package in iosApp). The
 * shared code talks to it through this bridge, registered at startup from
 * Swift. Without a registered bridge everything is a safe no-op.
 */
object IosPushBridgeHolder {
    interface Bridge {
        fun initialize(appId: String)
        fun requestPermission(callback: (Boolean) -> Unit)
        fun login(userId: String)
        fun logout()
        fun setTag(key: String, value: String)
    }

    var bridge: Bridge? = null
}

class IosPushAdapter : PushNotificationsAdapter {
    override fun initialize(appId: String) {
        if (appId.isNotBlank()) IosPushBridgeHolder.bridge?.initialize(appId)
    }

    override suspend fun requestPermission(): Boolean {
        val bridge = IosPushBridgeHolder.bridge ?: return false
        return suspendCancellableCoroutine { cont ->
            bridge.requestPermission { granted ->
                if (cont.isActive) cont.resume(granted)
            }
        }
    }

    override fun login(userId: String) {
        IosPushBridgeHolder.bridge?.login(userId)
    }

    override fun logout() {
        IosPushBridgeHolder.bridge?.logout()
    }

    override fun setReminderCategory(key: String, enabled: Boolean) {
        IosPushBridgeHolder.bridge?.setTag(key, enabled.toString())
    }
}
