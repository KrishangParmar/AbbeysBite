package com.abbeysbite.app.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSpeechRecognitionService(
    private val context: Context,
) : SpeechRecognitionService {

    private val events = MutableSharedFlow<SpeechEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun events(): kotlinx.coroutines.flow.SharedFlow<SpeechEvent> = events

    override suspend fun requestPermission(): PermissionStatus {
        val host = ActivityResultBridge.host ?: return PermissionStatus.DENIED
        return if (host.ensureMicPermission()) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    override suspend fun start() = withContext(Dispatchers.Main) {
        stopInternal()
        if (!isAvailable) {
            events.tryEmit(SpeechEvent.Error("Speech recognition isn’t available on this device."))
            return@withContext
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                events.tryEmit(SpeechEvent.Ended)
            }

            override fun onError(error: Int) {
                val permission = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Not really an error for the user — they just went quiet.
                        events.tryEmit(SpeechEvent.Ended)
                        return
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Transient session churn (common with restart-heavy
                        // hot-mic use) — treat as an end, callers restart.
                        events.tryEmit(SpeechEvent.Ended)
                        return
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is needed for voice mode."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Voice needs an internet connection right now."
                    else -> "We couldn’t hear that. Try again?"
                }
                events.tryEmit(SpeechEvent.Error(message, permission))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                events.tryEmit(SpeechEvent.Final(text))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) events.tryEmit(SpeechEvent.Partial(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        r.startListening(intent)
    }

    override fun stop() {
        // Must run on main thread; SpeechRecognizer is main-thread-bound.
        android.os.Handler(android.os.Looper.getMainLooper()).post { stopInternal() }
    }

    private fun stopInternal() {
        recognizer?.let {
            runCatching { it.stopListening() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }
}
