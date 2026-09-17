package com.abbeysbite.app.ai.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the on-device model runtime. */
sealed class EngineState {
    data object NotLoaded : EngineState()
    data object Loading : EngineState()
    data object Ready : EngineState()
    data class Error(val message: String) : EngineState()
}

/** One generation request against the local model. */
data class LocalGenerationRequest(
    val prompt: String,
    /** Optional JPEG bytes for multimodal (meal photo) requests. */
    val imageJpeg: ByteArray? = null,
    val maxOutputTokens: Int = 512,
    val temperature: Float = 0.7f,
)

/**
 * Platform abstraction over the on-device LLM runtime (LiteRT-LM / MediaPipe
 * LLM Inference running Gemma 3n E2B int4). The engine is deliberately dumb:
 * it loads a model file, runs prompt→text generations (optionally with an
 * image), streams tokens, and cancels. All product intelligence (prompts,
 * parsing, history windowing) lives above it in [com.abbeysbite.app.ai.LocalGemmaProvider].
 */
interface LocalAiEngine {

    /** Whether this platform has a working runtime implementation. */
    val isSupported: Boolean

    /** Human-readable runtime description for diagnostics/PERFORMANCE.md. */
    val runtimeDescription: String

    val state: StateFlow<EngineState>

    /**
     * Loads the model file into the runtime. Heavy (seconds); safe to call
     * again when already loaded with the same path (no-op). Throws with a
     * descriptive message on failure — callers surface local failures rather
     * than silently falling back anywhere.
     */
    suspend fun prepareModel(modelPath: String)

    /** One-shot generation; suspends until complete or cancelled. */
    suspend fun generate(request: LocalGenerationRequest): String

    /**
     * Streaming generation: emits incremental text chunks (not guaranteed to
     * be whole tokens/words). Flow completes when generation finishes; flow
     * collection cancellation cancels the underlying generation.
     */
    fun streamGenerate(request: LocalGenerationRequest): Flow<String>

    /** Cancels any in-flight generation (used for Live barge-in). */
    fun cancelGeneration()

    /** Frees runtime memory (model can be prepared again later). */
    fun unload()
}
