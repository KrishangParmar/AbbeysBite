package com.abbeysbite.app.ai.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Gemma 3n (LiteRT-LM bundle) through MediaPipe LLM Inference.
 * One process-wide [LlmInference]; a fresh [LlmInferenceSession] per request
 * (simple, correct cancellation semantics). Generations are serialized — the
 * runtime supports one generation at a time.
 */
class AndroidGemmaEngine(
    private val context: Context,
) : LocalAiEngine {

    override val isSupported: Boolean = true

    override val runtimeDescription: String =
        "MediaPipe LLM Inference (tasks-genai) / LiteRT-LM · Gemma 3n E2B int4"

    private val _state = MutableStateFlow<EngineState>(EngineState.NotLoaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private var llm: LlmInference? = null
    private var loadedPath: String? = null

    /** Serializes generations; the runtime is single-stream. */
    private val generationMutex = Mutex()

    /** Serializes model loads — concurrent prepareModel calls (provider +
     *  ImproveViewModel warm-up) must never create two multi-GB instances. */
    private val prepareMutex = Mutex()

    @Volatile
    private var activeSession: LlmInferenceSession? = null

    @Volatile
    private var cancelRequested = false

    override suspend fun prepareModel(modelPath: String) = prepareMutex.withLock {
        if (llm != null && loadedPath == modelPath && _state.value is EngineState.Ready) {
            return@withLock
        }
        withContext(Dispatchers.IO) {
            _state.value = EngineState.Loading
            runCatching { llm?.close() }
            llm = null
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOTAL_TOKENS)
                    .setMaxNumImages(1)
                    .build()
                llm = LlmInference.createFromOptions(context, options)
                loadedPath = modelPath
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Error(t.message ?: "model load failed")
                throw IllegalStateException(
                    "Couldn’t load the on-device model: ${t.message}", t,
                )
            }
        }
    }

    private fun requireLlm(): LlmInference =
        llm ?: throw IllegalStateException("Model not loaded — call prepareModel first")

    private fun newSession(request: LocalGenerationRequest): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(48)
            .setTopP(0.95f)
            .setTemperature(request.temperature)
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(request.imageJpeg != null)
                    .build()
            )
            .build()
        val session = LlmInferenceSession.createFromOptions(requireLlm(), sessionOptions)
        session.addQueryChunk(request.prompt)
        request.imageJpeg?.let { bytes ->
            session.addImage(BitmapImageBuilder(decodeForVision(bytes)).build())
        }
        return session
    }

    /**
     * Decodes + downscales a meal JPEG for the vision encoder. Gemma 3n's
     * encoder works at up to 768×768 — feeding more is wasted latency/memory.
     */
    private fun decodeForVision(jpeg: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= VISION_TARGET_PX ||
            bounds.outHeight / (sample * 2) >= VISION_TARGET_PX
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
            ?: throw IllegalArgumentException("Couldn’t decode the meal photo")
        val scale = minOf(
            1f,
            VISION_TARGET_PX.toFloat() / decoded.width,
            VISION_TARGET_PX.toFloat() / decoded.height,
        )
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else decoded
    }

    /** Approximate per-request output cap in characters (~4 chars/token). */
    private fun outputCapChars(request: LocalGenerationRequest): Int =
        if (request.maxOutputTokens > 0) request.maxOutputTokens * 4 else Int.MAX_VALUE

    /**
     * Cancel BEFORE close: closing a session whose generation is still
     * running throws (swallowed) and leaks the native generation, which then
     * rejects the next request. Cancel is idempotent on finished sessions.
     */
    private suspend fun teardown(session: LlmInferenceSession) {
        activeSession = null
        runCatching { session.cancelGenerateResponseAsync() }
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            runCatching { session.close() }
        }
    }

    override suspend fun generate(request: LocalGenerationRequest): String =
        generationMutex.withLock {
            cancelRequested = false
            withContext(Dispatchers.IO) {
                val session = newSession(request)
                activeSession = session
                val cap = outputCapChars(request)
                try {
                    suspendCancellableCoroutine { cont ->
                        val output = StringBuilder()
                        cont.invokeOnCancellation {
                            runCatching { session.cancelGenerateResponseAsync() }
                        }
                        session.generateResponseAsync { partial, done ->
                            output.append(partial)
                            if (done && cont.isActive) {
                                cont.resume(output.toString())
                            }
                            if (!done && (cancelRequested || output.length >= cap)) {
                                runCatching { session.cancelGenerateResponseAsync() }
                                if (cont.isActive) cont.resume(output.toString())
                            }
                        }
                    }
                } finally {
                    teardown(session)
                }
            }
        }

    override fun streamGenerate(request: LocalGenerationRequest): Flow<String> = callbackFlow {
        generationMutex.withLock {
            cancelRequested = false
            val session = withContext(Dispatchers.IO) { newSession(request) }
            activeSession = session
            val cap = outputCapChars(request)
            try {
                val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                var emittedChars = 0
                session.generateResponseAsync { partial, isDone ->
                    if (partial.isNotEmpty()) {
                        emittedChars += partial.length
                        trySendBlocking(partial)
                    }
                    if (isDone) done.complete(Unit)
                    if (!isDone && (cancelRequested || emittedChars >= cap)) {
                        runCatching { session.cancelGenerateResponseAsync() }
                        done.complete(Unit)
                    }
                }
                // Cancellation of the collector cancels this await; teardown
                // below then stops the native generation before closing.
                done.await()
            } finally {
                teardown(session)
            }
        }
        close()
    }.flowOn(Dispatchers.IO)

    override fun cancelGeneration() {
        cancelRequested = true
        runCatching { activeSession?.cancelGenerateResponseAsync() }
    }

    override fun unload() {
        runCatching { activeSession?.cancelGenerateResponseAsync() }
        runCatching { llm?.close() }
        llm = null
        loadedPath = null
        _state.value = EngineState.NotLoaded
    }

    private companion object {
        /** Input + output token budget for a session. */
        const val MAX_TOTAL_TOKENS = 4096

        /** Vision input edge length (benchmarked: see PERFORMANCE.md). */
        const val VISION_TARGET_PX = 768
    }
}
