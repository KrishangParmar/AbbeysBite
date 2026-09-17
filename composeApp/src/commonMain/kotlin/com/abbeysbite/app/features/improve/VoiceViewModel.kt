package com.abbeysbite.app.features.improve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.ai.AiStreamException
import com.abbeysbite.app.ai.ChatMessage
import com.abbeysbite.app.ai.ChatRole
import com.abbeysbite.app.ai.MealChatContext
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.billing.FreeTierLimiter
import com.abbeysbite.app.data.repository.PantryRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import com.abbeysbite.app.platform.PermissionStatus
import com.abbeysbite.app.platform.SpeechEvent
import com.abbeysbite.app.platform.SpeechRecognitionService
import com.abbeysbite.app.platform.TextToSpeechService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * LIVE conversational voice mode:
 *
 *   mic → platform streaming STT → local Gemma (streamed) → sentence-chunked
 *   platform TTS, with true barge-in — speaking over the assistant stops TTS
 *   immediately, cancels stale generation and becomes the next turn.
 *
 * Chat and Live share [ImproveSession] history, so switching between them
 * never loses context.
 */
sealed class VoicePhase {
    data object Idle : VoicePhase()
    data object Listening : VoicePhase()
    data object Thinking : VoicePhase()
    data object Speaking : VoicePhase()
    data class Error(val message: String) : VoicePhase()
    data object PermissionNeeded : VoicePhase()
    data object PremiumNeeded : VoicePhase()
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    /** Live partial transcript of what the USER is saying. */
    val liveTranscript: String = "",
    val lastUserText: String = "",
    /** Assistant reply streamed so far this turn (visible AI transcript). */
    val aiTranscript: String = "",
    val muted: Boolean = false,
)

class VoiceViewModel(
    private val aiProvider: AiProvider,
    val session: ImproveSession,
    private val speech: SpeechRecognitionService,
    private val tts: TextToSpeechService,
    private val preferencesRepository: PreferencesRepository,
    private val pantryRepository: PantryRepository,
    private val billingManager: BillingManager,
    private val limiter: FreeTierLimiter,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var conversationActive = false
    private var sttObserver: Job? = null
    private var generationJob: Job? = null
    private var speakerJob: Job? = null
    private var sentenceQueue: Channel<String>? = null
    private var micRestart: Job? = null

    private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // ------------------------------------------------------------- lifecycle

    fun startConversation() {
        // Idempotent: a configuration change re-runs the screen's launch
        // effect — never disrupt an in-flight conversation/turn.
        if (conversationActive) return
        val premium = billingManager.premiumState.value.isPremium
        if (!limiter.canUseVoice(premium)) {
            _state.update { it.copy(phase = VoicePhase.PremiumNeeded) }
            return
        }
        analytics.track(AnalyticsEvents.VOICE_STARTED)
        conversationActive = true
        viewModelScope.launch {
            if (speech.requestPermission() != PermissionStatus.GRANTED) {
                _state.update { it.copy(phase = VoicePhase.PermissionNeeded) }
                return@launch
            }
            observeSpeech()
            startListening()
        }
    }

    fun stopConversation() {
        conversationActive = false
        cancelAssistantTurn(commitPartial = true)
        speech.stop()
        _state.update { it.copy(phase = VoicePhase.Idle, liveTranscript = "") }
    }

    fun retry() {
        val needsFullRestart = !conversationActive ||
            sttObserver == null ||
            _state.value.phase is VoicePhase.PermissionNeeded
        if (needsFullRestart) {
            // Re-run the whole start path so the permission is re-requested
            // and the STT observer is actually installed.
            conversationActive = false
            startConversation()
        } else {
            viewModelScope.launch { startListening() }
        }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        if (muted) {
            speech.stop()
            if (_state.value.phase is VoicePhase.Listening) {
                _state.update { it.copy(liveTranscript = "") }
            }
        } else if (conversationActive &&
            (_state.value.phase is VoicePhase.Listening || _state.value.phase is VoicePhase.Idle)
        ) {
            viewModelScope.launch { startListening() }
        } else if (conversationActive) {
            // Unmuted mid-reply: re-arm the mic so barge-in works again.
            keepMicHot()
        }
    }

    // ------------------------------------------------------------- STT loop

    private fun observeSpeech() {
        if (sttObserver != null) return
        sttObserver = viewModelScope.launch {
            speech.events().collect { event ->
                if (!conversationActive) return@collect
                when (event) {
                    is SpeechEvent.Partial -> onUserPartial(event.text)
                    is SpeechEvent.Final -> onUserFinal(event.text.trim())
                    is SpeechEvent.Error -> {
                        if (event.permissionDenied) {
                            _state.update { it.copy(phase = VoicePhase.PermissionNeeded) }
                        } else if (_state.value.phase is VoicePhase.Listening) {
                            _state.update { it.copy(phase = VoicePhase.Error(event.message)) }
                        } else {
                            // Errors during Speaking/Thinking are non-fatal,
                            // but the recognizer session is dead — restart it
                            // (with a pause, TTS output often trips the mic)
                            // so barge-in keeps working mid-reply.
                            keepMicHot(delayMs = MIC_RETRY_DELAY_MS)
                        }
                    }
                    is SpeechEvent.Ended -> {
                        // Keep the mic hot between utterances AND while the
                        // assistant is thinking/speaking — barge-in depends
                        // on the recognizer running during those phases.
                        keepMicHot()
                    }
                }
            }
        }
    }

    private suspend fun startListening() {
        if (!conversationActive || _state.value.muted) return
        _state.update { it.copy(phase = VoicePhase.Listening, liveTranscript = "") }
        speech.start()
    }

    /**
     * (Re)starts the mic from event handlers whenever the conversation still
     * wants audio input — during Listening, and also during Thinking/Speaking
     * so barge-in works for the whole assistant turn. Coalesces concurrent
     * restarts and optionally delays (recognizer error backoff).
     */
    private fun keepMicHot(delayMs: Long = 0) {
        if (!conversationActive || _state.value.muted) return
        val phase = _state.value.phase
        val wantsMic = phase is VoicePhase.Listening ||
            phase is VoicePhase.Thinking ||
            phase is VoicePhase.Speaking
        if (!wantsMic) return
        if (micRestart?.isActive == true) return
        micRestart = viewModelScope.launch {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            if (conversationActive && !_state.value.muted) speech.start()
        }
    }

    private fun onUserPartial(text: String) {
        if (_state.value.muted) return
        _state.update { it.copy(liveTranscript = text) }
        val assistantBusy = _state.value.phase is VoicePhase.Speaking ||
            _state.value.phase is VoicePhase.Thinking
        // BARGE-IN: real speech over the assistant interrupts immediately.
        if (assistantBusy && text.trim().length >= BARGE_IN_MIN_CHARS) {
            interruptAssistant()
        }
    }

    private fun onUserFinal(text: String) {
        if (_state.value.muted) {
            // Muting mid-utterance flushes a Final — the user asked us NOT
            // to act on that speech.
            _state.update { it.copy(liveTranscript = "") }
            return
        }
        val phase = _state.value.phase
        val assistantBusy = phase is VoicePhase.Speaking || phase is VoicePhase.Thinking
        when {
            text.length >= BARGE_IN_MIN_CHARS && assistantBusy -> {
                interruptAssistant()
                handleUserTurn(text)
            }
            text.isNotEmpty() && !assistantBusy -> handleUserTurn(text)
            else -> {
                // Silence or echo blip — resume listening (in any active
                // phase, so the mic stays hot for barge-in too).
                keepMicHot()
            }
        }
    }

    // ------------------------------------------------------------- turns

    private fun handleUserTurn(text: String) {
        analytics.track(AnalyticsEvents.CHAT_MESSAGE_SENT, mapOf("mode" to "live"))
        analytics.track("live_voice_turn")
        _state.update {
            it.copy(
                phase = VoicePhase.Thinking,
                lastUserText = text,
                liveTranscript = "",
                aiTranscript = "",
            )
        }
        session.appendMessage(ChatMessage(ChatRole.USER, text, Clock.System.now().toString()))
        limiter.recordChatMessage(today())

        // Mic stays hot during Thinking/Speaking so barge-in works.
        keepMicHot()

        val premium = billingManager.premiumState.value.isPremium
        val context = MealChatContext(
            analysis = session.analysis.value,
            preferences = preferencesRepository.preferences.value,
            pantry = if (limiter.canUsePantryAwareAi(premium)) pantryRepository.items.value else emptyList(),
            history = session.chatHistory.value.dropLast(1),
        )

        val queue = Channel<String>(Channel.UNLIMITED)
        sentenceQueue = queue

        // Speaker: consumes complete sentences → progressive TTS.
        speakerJob = viewModelScope.launch {
            for (sentence in queue) {
                if (!conversationActive) break
                _state.update { it.copy(phase = VoicePhase.Speaking) }
                tts.speak(sentence)
            }
        }

        generationJob = viewModelScope.launch {
            val full = StringBuilder()
            val pending = StringBuilder()
            try {
                aiProvider.streamChat(context, text).collect { chunk ->
                    full.append(chunk)
                    pending.append(chunk)
                    _state.update { it.copy(aiTranscript = full.toString()) }
                    drainSentences(pending, queue)
                }
                if (pending.isNotBlank()) queue.trySend(pending.toString().trim())
                queue.close()
                speakerJob?.join()
                commitAssistant(full.toString())
                if (conversationActive) startListening()
            } catch (e: AiStreamException) {
                failTurn(queue, VoicePhase.Error(e.error.message))
            } catch (e: kotlinx.coroutines.CancellationException) {
                queue.close()
                throw e
            } catch (e: Throwable) {
                failTurn(queue, VoicePhase.Error("The assistant hit a snag — try again."))
            }
        }
    }

    /** Hard-fails the current turn: silence the speaker, then show [error]. */
    private fun failTurn(queue: Channel<String>, error: VoicePhase.Error) {
        queue.close()
        speakerJob?.cancel()
        speakerJob = null
        tts.stop()
        _state.update { it.copy(phase = error) }
    }

    /** Extracts finished sentences from [buffer] into the TTS queue. */
    private fun drainSentences(buffer: StringBuilder, queue: Channel<String>) {
        while (true) {
            val text = buffer.toString()
            var cut = -1
            for (i in text.indices) {
                val c = text[i]
                if (c == '.' || c == '!' || c == '?' || c == '…' || c == '\n') {
                    // Sentence boundary: punctuation followed by end/space,
                    // long enough to be worth speaking on its own.
                    val isBoundary = i == text.lastIndex || text[i + 1].isWhitespace()
                    if (isBoundary && i + 1 >= MIN_SENTENCE_CHARS) {
                        cut = i + 1
                        break
                    }
                }
            }
            if (cut < 0) return
            val sentence = text.substring(0, cut).trim()
            if (sentence.isNotEmpty()) queue.trySend(sentence)
            buffer.deleteRange(0, cut)
        }
    }

    private fun commitAssistant(text: String) {
        if (text.isNotBlank()) {
            session.appendMessage(
                ChatMessage(ChatRole.ASSISTANT, text.trim(), Clock.System.now().toString())
            )
        }
    }

    /** Barge-in: stop speech NOW, cancel stale generation, keep history sane. */
    private fun interruptAssistant() {
        analytics.track("live_voice_interrupted")
        cancelAssistantTurn(commitPartial = true)
        _state.update { it.copy(phase = VoicePhase.Listening) }
    }

    private fun cancelAssistantTurn(commitPartial: Boolean) {
        tts.stop()
        sentenceQueue?.close()
        sentenceQueue = null
        // Only an in-flight turn has uncommitted text; after a completed turn
        // the reply is already in the session and must NOT be committed again.
        val inFlight = generationJob?.isActive == true
        val partial = _state.value.aiTranscript
        generationJob?.cancel()
        generationJob = null
        speakerJob?.cancel()
        speakerJob = null
        if (commitPartial && inFlight && partial.isNotBlank()) {
            // The user heard (part of) this — keep context truthful.
            commitAssistant(partial)
            _state.update { it.copy(aiTranscript = "") }
        }
    }

    override fun onCleared() {
        conversationActive = false
        micRestart?.cancel()
        cancelAssistantTurn(commitPartial = true)
        speech.stop()
        tts.stop()
        super.onCleared()
    }

    private companion object {
        /** Guards against TTS echo / stray noise triggering barge-in. */
        const val BARGE_IN_MIN_CHARS = 10
        const val MIN_SENTENCE_CHARS = 12
        /** Backoff before restarting a recognizer that errored mid-turn. */
        const val MIC_RETRY_DELAY_MS = 600L
    }
}
