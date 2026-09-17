package com.abbeysbite.app.ai.local

import com.abbeysbite.app.core.config.AppSecrets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Canonical facts about the model we ship against. Size/hash captured from the
 * official artifact at acquisition time (see RELEASE_HANDOFF.md).
 */
object GemmaModelSpec {
    val fileName: String = AppSecrets.gemmaModelFile.ifBlank { "gemma-3n-E2B-it-int4.litertlm" }
    const val VERSION = "gemma-3n-E2B-it-int4-2026-06"
    const val EXPECTED_SIZE_BYTES = 3_655_827_456L // exact size of the official artifact
    const val EXPECTED_SHA256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"

    /** Rounded size used in user-facing copy. */
    const val DISPLAY_SIZE = "3.4 GB"

    /** Free space demanded before starting a download (model + headroom). */
    const val REQUIRED_FREE_BYTES = 4_200_000_000L
}

/** Where a model file can come from. The HF token is NEVER one of these. */
sealed class ModelSource {
    /** First-party hosted artifact (production path once hosting exists). */
    data class Url(val url: String) : ModelSource()

    /**
     * Developer side-load: the file was provisioned into the app's storage
     * out-of-band (adb push / Files app). No network involved.
     */
    data object SideLoaded : ModelSource()

    /** Nothing available: no hosted URL configured and no side-loaded file. */
    data object Unavailable : ModelSource()
}

sealed class ModelState {
    data object Checking : ModelState()

    /** No model on disk. [source] says whether download is possible. */
    data class NotInstalled(val source: ModelSource, val enoughSpace: Boolean) : ModelState()

    data class Downloading(val progressPercent: Int, val downloadedBytes: Long) : ModelState()

    data object Verifying : ModelState()

    /** File present and size-validated; engine may still need loading. */
    data class Installed(val path: String, val version: String) : ModelState()

    data class Failed(val reason: ModelFailure, val message: String) : ModelState()
}

enum class ModelFailure { NO_SPACE, DOWNLOAD_FAILED, CORRUPT, STORAGE_ERROR }

/** Platform file operations for the model store. */
interface ModelStore {
    /** Directory where installed models live (app-private). */
    val modelsDirPath: String

    /** Candidate side-load locations checked in order (dev provisioning). */
    fun sideLoadCandidatePaths(fileName: String): List<String>

    fun fileExists(path: String): Boolean
    fun fileSize(path: String): Long
    fun freeSpaceBytes(): Long
    fun delete(path: String)

    /** SHA-256 of a file (slow for 3.4 GB — used only for explicit verify). */
    suspend fun sha256(path: String): String

    /**
     * Downloads [url] to [destPath] with resume support, reporting progress.
     * Throws on failure; partial file is kept for resume.
     */
    suspend fun download(
        url: String,
        destPath: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    )
}

/**
 * Owns model acquisition + integrity + lifecycle. UI copy stays friendly
 * ("Preparing your private AI…"); technical filenames are never surfaced.
 */
class ModelManager(
    private val store: ModelStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    private val installedPath: String
        get() = "${store.modelsDirPath}/${GemmaModelSpec.fileName}"

    private fun configuredSource(): ModelSource {
        val url = AppSecrets.modelDownloadUrl.trim()
        return when {
            url.isNotBlank() && url != "PENDING" -> ModelSource.Url(url)
            else -> ModelSource.Unavailable
        }
    }

    /** Scans disk and settles into NotInstalled/Installed. Cheap (size check). */
    fun refresh() {
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow(): ModelState {
        _state.value = ModelState.Checking
        val next = runCatching { scan() }.getOrElse {
            ModelState.Failed(ModelFailure.STORAGE_ERROR, it.message ?: "storage error")
        }
        _state.value = next
        return next
    }

    private fun scan(): ModelState {
        // 1. Properly installed model.
        if (store.fileExists(installedPath)) {
            val size = store.fileSize(installedPath)
            if (size == GemmaModelSpec.EXPECTED_SIZE_BYTES) {
                return ModelState.Installed(installedPath, GemmaModelSpec.VERSION)
            }
            // Wrong size: interrupted download or corrupt file. If a download
            // source exists we can resume; otherwise report corruption.
            return if (configuredSource() is ModelSource.Url) {
                ModelState.NotInstalled(configuredSource(), enoughSpace = hasSpace())
            } else {
                ModelState.Failed(
                    ModelFailure.CORRUPT,
                    "The AI model file is incomplete. Delete and provision it again.",
                )
            }
        }
        // 2. Developer side-loaded file (used in place — no 3.4 GB copy).
        for (candidate in store.sideLoadCandidatePaths(GemmaModelSpec.fileName)) {
            if (store.fileExists(candidate) &&
                store.fileSize(candidate) == GemmaModelSpec.EXPECTED_SIZE_BYTES
            ) {
                return ModelState.Installed(candidate, GemmaModelSpec.VERSION)
            }
        }
        // 3. Nothing on disk.
        return ModelState.NotInstalled(configuredSource(), enoughSpace = hasSpace())
    }

    private fun hasSpace(): Boolean =
        store.freeSpaceBytes() > GemmaModelSpec.REQUIRED_FREE_BYTES

    /** Starts (or resumes) the download from the configured source. */
    fun startDownload() {
        val source = configuredSource()
        if (source !is ModelSource.Url) {
            _state.value = ModelState.Failed(
                ModelFailure.DOWNLOAD_FAILED,
                "No model delivery endpoint is configured for this build.",
            )
            return
        }
        // A partial larger than the artifact can never resume (HTTP 416 loop)
        // — clear it before starting over.
        if (store.fileExists(installedPath) &&
            store.fileSize(installedPath) > GemmaModelSpec.EXPECTED_SIZE_BYTES
        ) {
            runCatching { store.delete(installedPath) }
        }
        // Space gate accounts for what a resumable partial already occupies.
        val alreadyOnDisk =
            if (store.fileExists(installedPath)) store.fileSize(installedPath) else 0L
        val stillNeeded =
            (GemmaModelSpec.REQUIRED_FREE_BYTES - alreadyOnDisk).coerceAtLeast(MIN_HEADROOM_BYTES)
        if (store.freeSpaceBytes() < stillNeeded) {
            _state.value = ModelState.Failed(
                ModelFailure.NO_SPACE,
                "Not enough free space — about ${GemmaModelSpec.DISPLAY_SIZE} is needed.",
            )
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _state.value = ModelState.Downloading(0, 0)
            runCatching {
                store.download(source.url, installedPath) { downloaded, total ->
                    val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    _state.value = ModelState.Downloading(pct.coerceIn(0, 100), downloaded)
                }
            }.onFailure { e ->
                // A cancelled download is not a failure — cancelDownload()'s
                // refresh() decides the next state.
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = ModelState.Failed(
                    ModelFailure.DOWNLOAD_FAILED,
                    e.message ?: "Download failed — check your connection and retry.",
                )
                return@launch
            }
            // Size gate (fast). Full hash only via explicit verify(). A
            // completed download with the wrong size can never become valid —
            // delete it so retry starts clean instead of resuming a bad base.
            if (store.fileSize(installedPath) != GemmaModelSpec.EXPECTED_SIZE_BYTES) {
                runCatching { store.delete(installedPath) }
                _state.value = ModelState.Failed(
                    ModelFailure.CORRUPT,
                    "The downloaded model didn’t verify. Please retry.",
                )
                return@launch
            }
            _state.value = ModelState.Installed(installedPath, GemmaModelSpec.VERSION)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        refresh()
    }

    /** Full integrity verification (hashes 3.4 GB — expose behind a button). */
    suspend fun verifyIntegrity(): Boolean {
        val current = _state.value
        if (current !is ModelState.Installed) return false
        _state.value = ModelState.Verifying
        val ok = runCatching {
            store.sha256(current.path).equals(GemmaModelSpec.EXPECTED_SHA256, ignoreCase = true)
        }.getOrDefault(false)
        if (ok) {
            _state.value = current
        } else {
            // Persist the verdict by removing the bad file — otherwise the
            // next refresh() sees a size match and flips back to Installed.
            runCatching { store.delete(current.path) }
            _state.value = ModelState.Failed(
                ModelFailure.CORRUPT,
                "The AI model failed verification and was removed. Download or provision it again.",
            )
        }
        return ok
    }

    /** Deletes the installed model (side-loaded files are left untouched). */
    fun deleteInstalled() {
        runCatching { store.delete(installedPath) }
        refresh()
    }

    private companion object {
        /** Working headroom demanded even when a partial nearly completes. */
        const val MIN_HEADROOM_BYTES = 200_000_000L
    }
}
