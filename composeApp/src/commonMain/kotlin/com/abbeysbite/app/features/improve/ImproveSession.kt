package com.abbeysbite.app.features.improve

import com.abbeysbite.app.ai.ChatMessage
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.repository.UserScopedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, app-scoped state for the current Improve session: the latest
 * analysis, its photo, and the conversation attached to it. Lets the chat and
 * voice screens pick up exactly where the analysis left off.
 */
class ImproveSession : UserScopedState {

    private val _analysis = MutableStateFlow<MealAnalysis?>(null)
    val analysis: StateFlow<MealAnalysis?> = _analysis.asStateFlow()

    var imageBytes: ByteArray? = null
        private set

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    fun onNewAnalysis(analysis: MealAnalysis, imageBytes: ByteArray?) {
        this._analysis.value = analysis
        this.imageBytes = imageBytes
        _chatHistory.value = emptyList()
    }

    fun appendMessage(message: ChatMessage) {
        _chatHistory.value = _chatHistory.value + message
    }

    /** Removes a trailing user message (used when a send hard-fails). */
    fun removeLastIfUser() {
        val last = _chatHistory.value.lastOrNull() ?: return
        if (last.role == com.abbeysbite.app.ai.ChatRole.USER) {
            _chatHistory.value = _chatHistory.value.dropLast(1)
        }
    }

    fun clear() {
        _analysis.value = null
        imageBytes = null
        _chatHistory.value = emptyList()
    }

    override fun clearUserState() = clear()
}
