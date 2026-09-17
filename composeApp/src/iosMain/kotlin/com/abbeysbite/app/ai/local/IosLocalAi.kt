@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.abbeysbite.app.ai.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS local-AI engine seat. The [LocalAiEngine] contract, model storage and
 * provider wiring are complete on iOS; the actual LiteRT-LM inference bridge
 * is a native framework integration performed alongside the deferred Apple
 * production setup (see INFRASTRUCTURE_HANDOFF.md → Apple). Until that bridge
 * lands, [isSupported] is false and every call reports the honest state —
 * nothing silently falls back to any cloud path.
 */
class IosGemmaEngine : LocalAiEngine {

    override val isSupported: Boolean = false

    override val runtimeDescription: String =
        "LiteRT-LM native iOS bridge (pending — Apple production phase)"

    private val _state = MutableStateFlow<EngineState>(
        EngineState.Error("On-device AI runtime for iOS ships with the Apple production phase.")
    )
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private fun unavailable(): Nothing = throw IllegalStateException(
        "On-device AI isn’t available in this iOS development build yet."
    )

    override suspend fun prepareModel(modelPath: String) = unavailable()

    override suspend fun generate(request: LocalGenerationRequest): String = unavailable()

    override fun streamGenerate(request: LocalGenerationRequest): Flow<String> =
        flow { unavailable() }

    override fun cancelGeneration() = Unit

    override fun unload() = Unit
}

/** iOS model storage under Application Support (backed up exclusion TBD). */
class IosModelStore : ModelStore {

    private val baseDir: String by lazy {
        val dirs = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true,
        )
        val support = (dirs.firstOrNull() as? String) ?: "/tmp"
        val path = "$support/models"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path, withIntermediateDirectories = true, attributes = null, error = null,
        )
        path
    }

    override val modelsDirPath: String get() = baseDir

    override fun sideLoadCandidatePaths(fileName: String): List<String> =
        listOf("$baseDir/$fileName")

    override fun fileExists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    override fun fileSize(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        return (attrs?.get("NSFileSize") as? Long) ?: 0L
    }

    override fun freeSpaceBytes(): Long = Long.MAX_VALUE // refined with native bridge

    override fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override suspend fun sha256(path: String): String =
        throw IllegalStateException("Model verification lands with the iOS AI bridge.")

    override suspend fun download(
        url: String,
        destPath: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        throw IllegalStateException("Model delivery lands with the iOS AI bridge.")
    }
}
