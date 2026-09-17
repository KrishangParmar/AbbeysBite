package com.abbeysbite.app.platform

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus

/**
 * iOS speech recognition via SFSpeechRecognizer + AVAudioEngine tap.
 * Emits partial transcripts while listening and a final transcript on stop.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeechRecognitionService : SpeechRecognitionService {

    private val events = MutableSharedFlow<SpeechEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val recognizer = SFSpeechRecognizer()
    private var audioEngine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var lastTranscript: String = ""

    override val isAvailable: Boolean
        get() = recognizer.isAvailable()

    override fun events(): kotlinx.coroutines.flow.SharedFlow<SpeechEvent> = events

    override suspend fun requestPermission(): PermissionStatus =
        suspendCancellableCoroutine { cont ->
            SFSpeechRecognizer.requestAuthorization { status ->
                val speechOk =
                    status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
                if (!speechOk) {
                    if (cont.isActive) cont.resume(PermissionStatus.DENIED)
                    return@requestAuthorization
                }
                AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                    if (cont.isActive) {
                        cont.resume(if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
                    }
                }
            }
        }

    override suspend fun start() {
        stopInternal(emitEnd = false)
        if (!isAvailable) {
            events.tryEmit(SpeechEvent.Error("Speech recognition isn’t available right now."))
            return
        }
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null,
            )
            session.setActive(true, error = null)

            val engine = AVAudioEngine()
            val recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
                shouldReportPartialResults = true
            }
            audioEngine = engine
            request = recognitionRequest
            lastTranscript = ""

            val inputNode = engine.inputNode
            val format = inputNode.outputFormatForBus(0u)
            inputNode.installTapOnBus(0u, bufferSize = 1024u, format = format) { buffer, _ ->
                buffer?.let { recognitionRequest.appendAudioPCMBuffer(it) }
            }

            task = recognizer.recognitionTaskWithRequest(recognitionRequest) { result, error ->
                if (result != null) {
                    val text = result.bestTranscription.formattedString
                    lastTranscript = text
                    if (result.isFinal()) {
                        events.tryEmit(SpeechEvent.Final(text))
                        stopInternal(emitEnd = false)
                    } else if (text.isNotBlank()) {
                        events.tryEmit(SpeechEvent.Partial(text))
                    }
                }
                if (error != null) {
                    // Treat a cancellation with accumulated text as a final result.
                    if (lastTranscript.isNotBlank()) {
                        events.tryEmit(SpeechEvent.Final(lastTranscript))
                    } else {
                        events.tryEmit(SpeechEvent.Ended)
                    }
                    stopInternal(emitEnd = false)
                }
            }

            engine.prepare()
            if (!engine.startAndReturnError(null)) {
                // A dead audio engine must not look like "Listening…".
                events.tryEmit(SpeechEvent.Error("We couldn’t start the microphone."))
                stopInternal(emitEnd = false)
                return
            }
        } catch (e: Exception) {
            events.tryEmit(SpeechEvent.Error("We couldn’t start the microphone."))
            stopInternal(emitEnd = false)
        }
    }

    override fun stop() {
        // Ending audio triggers the final result callback above.
        request?.endAudio()
        stopInternal(emitEnd = false)
    }

    private fun stopInternal(emitEnd: Boolean) {
        audioEngine?.let { engine ->
            runCatching {
                engine.stop()
                engine.inputNode.removeTapOnBus(0u)
            }
        }
        audioEngine = null
        task?.cancel()
        task = null
        request = null
        if (emitEnd) events.tryEmit(SpeechEvent.Ended)
    }
}
