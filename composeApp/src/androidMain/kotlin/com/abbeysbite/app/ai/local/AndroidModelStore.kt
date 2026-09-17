package com.abbeysbite.app.ai.local

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android model storage. Models live in the app-private EXTERNAL files dir
 * (large-capacity, uninstall-cleaned, adb-pushable for development:
 * `adb push model.litertlm /sdcard/Android/data/com.abbeysbite.app/files/models/`),
 * falling back to internal storage when external is unavailable.
 */
class AndroidModelStore(private val context: Context) : ModelStore {

    private val modelsDir: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "models").apply { mkdirs() }
    }

    override val modelsDirPath: String get() = modelsDir.absolutePath

    override fun sideLoadCandidatePaths(fileName: String): List<String> = buildList {
        context.getExternalFilesDir(null)?.let { add(File(File(it, "models"), fileName).absolutePath) }
        add(File(File(context.filesDir, "models"), fileName).absolutePath)
    }.distinct()

    override fun fileExists(path: String): Boolean = File(path).isFile

    override fun fileSize(path: String): Long = File(path).length()

    override fun freeSpaceBytes(): Long =
        runCatching { StatFs(modelsDir.absolutePath).availableBytes }.getOrDefault(0L)

    override fun delete(path: String) {
        File(path).delete()
    }

    override suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun download(
        url: String,
        destPath: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val dest = File(destPath)
        dest.parentFile?.mkdirs()
        val existing = if (dest.exists()) dest.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            val resuming = code == 206
            if (code !in 200..299) error("Model download failed (HTTP $code)")
            if (!resuming && existing > 0) dest.delete()

            val remaining = connection.contentLengthLong
            val total = if (resuming) existing + remaining else remaining
            var downloaded = if (resuming) existing else 0L

            connection.inputStream.use { input ->
                RandomAccessFile(dest, "rw").use { raf ->
                    raf.seek(if (resuming) existing else 0L)
                    val buffer = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        // Throttle progress callbacks to ~every 4 MB.
                        if (downloaded - lastReport > (1L shl 22)) {
                            lastReport = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
        } finally {
            connection.disconnect()
        }
    }
}
