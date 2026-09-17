package com.abbeysbite.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.FileProvider
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidHapticsService(private val context: Context) : HapticsService {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrate(millis: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(millis, amplitude))
    }

    override fun lightTap() = vibrate(18, 60)
    override fun success() = vibrate(35, 120)
    override fun warning() = vibrate(60, 160)
}

class AndroidShareService(private val context: Context) : ShareService {

    override fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun shareImage(pngBytes: ByteArray, caption: String?) {
        runCatching {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "share-${System.currentTimeMillis()}.png")
            file.writeBytes(pngBytes)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                caption?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

class AndroidTextToSpeechService(context: Context) : TextToSpeechService {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        // Engine init is async; on first use give it a moment instead of
        // silently dropping the opening sentences of a reply.
        if (!ready) {
            repeat(20) {
                if (ready) return@repeat
                kotlinx.coroutines.delay(100)
            }
            if (!ready) return
        }
        _isSpeaking.value = true
        suspendCancellableCoroutine { cont ->
            val utteranceId = "utt-${text.hashCode()}-${System.nanoTime()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            })
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                // Failed enqueue means no callback will ever fire — resuming
                // here keeps the voice speaker loop from hanging forever.
                _isSpeaking.value = false
                if (cont.isActive) cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                tts.stop()
                _isSpeaking.value = false
            }
        }
    }

    override fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }
}
