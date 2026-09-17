package com.abbeysbite.app

import com.abbeysbite.app.ai.local.GemmaModelSpec
import com.abbeysbite.app.ai.local.ModelFailure
import com.abbeysbite.app.ai.local.ModelManager
import com.abbeysbite.app.ai.local.ModelSource
import com.abbeysbite.app.ai.local.ModelState
import com.abbeysbite.app.ai.local.ModelStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeStore(
    val files: MutableMap<String, Long> = mutableMapOf(),
    var free: Long = Long.MAX_VALUE,
    var hash: String = GemmaModelSpec.EXPECTED_SHA256,
) : ModelStore {
    override val modelsDirPath: String = "/models"
    override fun sideLoadCandidatePaths(fileName: String): List<String> =
        listOf("/sideload/$fileName", "/models/$fileName")

    override fun fileExists(path: String): Boolean = files.containsKey(path)
    override fun fileSize(path: String): Long = files[path] ?: 0L
    override fun freeSpaceBytes(): Long = free
    override fun delete(path: String) { files.remove(path) }
    override suspend fun sha256(path: String): String = hash
    override suspend fun download(
        url: String,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ) {
        onProgress(GemmaModelSpec.EXPECTED_SIZE_BYTES / 2, GemmaModelSpec.EXPECTED_SIZE_BYTES)
        files[destPath] = GemmaModelSpec.EXPECTED_SIZE_BYTES
        onProgress(GemmaModelSpec.EXPECTED_SIZE_BYTES, GemmaModelSpec.EXPECTED_SIZE_BYTES)
    }
}

class ModelManagerTest {

    private val installedPath = "/models/${GemmaModelSpec.fileName}"
    private val sideloadPath = "/sideload/${GemmaModelSpec.fileName}"

    @Test
    fun detectsProperlyInstalledModel() = runTest {
        val store = FakeStore(files = mutableMapOf(installedPath to GemmaModelSpec.EXPECTED_SIZE_BYTES))
        val manager = ModelManager(store, TestScope(testScheduler))
        val state = manager.refreshNow()
        val installed = assertIs<ModelState.Installed>(state)
        assertEquals(installedPath, installed.path)
        assertEquals(GemmaModelSpec.VERSION, installed.version)
    }

    @Test
    fun detectsSideLoadedModelWithoutCopying() = runTest {
        val store = FakeStore(files = mutableMapOf(sideloadPath to GemmaModelSpec.EXPECTED_SIZE_BYTES))
        val manager = ModelManager(store, TestScope(testScheduler))
        val installed = assertIs<ModelState.Installed>(manager.refreshNow())
        assertEquals(sideloadPath, installed.path)
    }

    @Test
    fun wrongSizeSideLoadIsIgnored() = runTest {
        val store = FakeStore(files = mutableMapOf(sideloadPath to 123L))
        val manager = ModelManager(store, TestScope(testScheduler))
        assertIs<ModelState.NotInstalled>(manager.refreshNow())
    }

    @Test
    fun truncatedInstallWithoutSourceReportsCorrupt() = runTest {
        val store = FakeStore(files = mutableMapOf(installedPath to 1000L))
        val manager = ModelManager(store, TestScope(testScheduler))
        val failed = assertIs<ModelState.Failed>(manager.refreshNow())
        assertEquals(ModelFailure.CORRUPT, failed.reason)
    }

    @Test
    fun notInstalledReportsSpaceAvailability() = runTest {
        val store = FakeStore(free = 1L)
        val manager = ModelManager(store, TestScope(testScheduler))
        val state = assertIs<ModelState.NotInstalled>(manager.refreshNow())
        assertTrue(!state.enoughSpace)
        assertEquals(ModelSource.Unavailable, state.source)
    }

    @Test
    fun deleteInstalledRemovesFile() = runTest {
        val store = FakeStore(files = mutableMapOf(installedPath to GemmaModelSpec.EXPECTED_SIZE_BYTES))
        val manager = ModelManager(store, TestScope(testScheduler))
        manager.refreshNow()
        manager.deleteInstalled()
        assertTrue(!store.files.containsKey(installedPath))
    }

    @Test
    fun verifyIntegrityFlagsHashMismatch() = runTest {
        val store = FakeStore(
            files = mutableMapOf(installedPath to GemmaModelSpec.EXPECTED_SIZE_BYTES),
            hash = "deadbeef",
        )
        val manager = ModelManager(store, TestScope(testScheduler))
        manager.refreshNow()
        assertTrue(!manager.verifyIntegrity())
        assertIs<ModelState.Failed>(manager.state.value)
    }

    @Test
    fun verifyIntegrityPassesOnMatchingHash() = runTest {
        val store = FakeStore(files = mutableMapOf(installedPath to GemmaModelSpec.EXPECTED_SIZE_BYTES))
        val manager = ModelManager(store, TestScope(testScheduler))
        manager.refreshNow()
        assertTrue(manager.verifyIntegrity())
        assertIs<ModelState.Installed>(manager.state.value)
    }

    @Test
    fun failedVerificationDeletesFileSoItIsNotReloaded() = runTest {
        // A hash-failed model must be removed; otherwise the next refresh sees
        // a matching size and flips back to Installed, loading the bad file.
        val store = FakeStore(
            files = mutableMapOf(installedPath to GemmaModelSpec.EXPECTED_SIZE_BYTES),
            hash = "deadbeef",
        )
        val manager = ModelManager(store, TestScope(testScheduler))
        manager.refreshNow()
        assertTrue(!manager.verifyIntegrity())
        assertTrue(!store.files.containsKey(installedPath), "corrupt file must be deleted")
        assertIs<ModelState.NotInstalled>(manager.refreshNow())
    }
}
