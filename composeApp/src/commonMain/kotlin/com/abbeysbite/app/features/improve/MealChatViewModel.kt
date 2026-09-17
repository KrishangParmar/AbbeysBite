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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription as kotlinxOnSubscription
import kotlinx.coroutines.flow.takeWhile as kotlinxTakeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class MealChatUiState(
    val input: String = "",
    val sending: Boolean = false,
    /** Assistant reply currently streaming in (rendered as a live bubble). */
    val streamingReply: String? = null,
    val error: String? = null,
    val limitReached: Boolean = false,
    /** Microphone dictation into the input field. */
    val dictating: Boolean = false,
)

class MealChatViewModel(
    private val aiProvider: AiProvider,
    val session: ImproveSession,
    private val preferencesRepository: PreferencesRepository,
    private val pantryRepository: PantryRepository,
    private val billingManager: BillingManager,
    private val limiter: FreeTierLimiter,
    private val analytics: Analytics,
    private val speech: SpeechRecognitionService,
) : ViewModel() {

    private val _state = MutableStateFlow(MealChatUiState())
    val state: StateFlow<MealChatUiState> = _state.asStateFlow()

    val messages = session.chatHistory

    private var streamJob: Job? = null
    private var dictationJob: Job? = null

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    fun send(text: String? = null) {
        val message = (text ?: _state.value.input).trim()
        if (message.isEmpty() || _state.value.sending) return
        stopDictation()

        val premium = billingManager.premiumState.value.isPremium
        if (!limiter.canChat(premium, today())) {
            _state.update { it.copy(limitReached = true) }
            return
        }

        _state.update { it.copy(input = "", sending = true, error = null, streamingReply = "") }
        session.appendMessage(
            ChatMessage(ChatRole.USER, message, Clock.System.now().toString())
        )
        limiter.recordChatMessage(today())
        analytics.track(AnalyticsEvents.CHAT_MESSAGE_SENT)

        val context = MealChatContext(
            analysis = session.analysis.value,
            preferences = preferencesRepository.preferences.value,
            pantry = if (limiter.canUsePantryAwareAi(premium)) pantryRepository.items.value else emptyList(),
            // History excludes the just-appended user message: the provider
            // receives it as the new turn.
            history = session.chatHistory.value.dropLast(1),
        )

        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                aiProvider.streamChat(context, message).collect { chunk ->
                    buffer.append(chunk)
                    _state.update { it.copy(streamingReply = buffer.toString()) }
                }
                commitAssistant(buffer.toString())
            } catch (e: AiStreamException) {
                session.removeLastIfUser() // keep history consistent on hard failure
                _state.update {
                    it.copy(sending = false, streamingReply = null, error = e.error.message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Preserve whatever streamed before cancellation; either way
                // the composer must come back out of the sending state.
                if (buffer.isNotBlank()) {
                    commitAssistant(buffer.toString())
                } else {
                    _state.update { it.copy(sending = false, streamingReply = null) }
                }
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(sending = false, streamingReply = null,
                        error = "The assistant hit a snag — try again.")
                }
            }
        }
    }

    private fun commitAssistant(fullText: String) {
        if (fullText.isNotBlank()) {
            session.appendMessage(
                ChatMessage(ChatRole.ASSISTANT, fullText.trim(), Clock.System.now().toString())
            )
        }
        _state.update { it.copy(sending = false, streamingReply = null) }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
    }

    // ---------------------------------------------------------- dictation

    fun toggleDictation() {
        if (_state.value.dictating) stopDictation() else startDictation()
    }

    private fun startDictation() {
        dictationJob?.cancel()
        dictationJob = viewModelScope.launch {
            if (speech.requestPermission() != PermissionStatus.GRANTED) {
                _state.update { it.copy(error = "Microphone permission is needed to dictate.") }
                return@launch
            }
            _state.update { it.copy(dictating = true, error = null) }
            speech.events()
                // Start the recognizer only once we're subscribed, so errors
                // it emits synchronously (e.g. "not available") aren't lost.
                .kotlinxOnSubscription { launch { speech.start() } }
                .kotlinxTakeWhile { event ->
                when (event) {
                    is SpeechEvent.Partial -> {
                        _state.update { it.copy(input = event.text) }
                        true
                    }
                    is SpeechEvent.Final -> {
                        _state.update { it.copy(input = event.text, dictating = false) }
                        speech.stop()
                        false
                    }
                    is SpeechEvent.Error -> {
                        _state.update { it.copy(dictating = false, error = event.message) }
                        false
                    }
                    is SpeechEvent.Ended -> {
                        _state.update { it.copy(dictating = false) }
                        false
                    }
                }
            }.collect {}
        }
    }

    fun stopDictation() {
        if (_state.value.dictating) {
            speech.stop()
            _state.update { it.copy(dictating = false) }
        }
        dictationJob?.cancel()
        dictationJob = null
    }

    fun dismissLimit() = _state.update { it.copy(limitReached = false) }

    override fun onCleared() {
        speech.stop()
        super.onCleared()
    }
}
