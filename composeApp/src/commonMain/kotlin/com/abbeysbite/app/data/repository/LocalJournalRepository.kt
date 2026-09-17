package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.SatisfactionLevel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * Settings-backed journal used when Supabase isn't configured.
 * [seedDemoData] (DEBUG builds only) fills the previous few days with sample
 * meals on first run so aggregation UI is testable immediately; production
 * builds never seed.
 */
class LocalJournalRepository(
    private val settings: Settings,
    private val seedDemoData: Boolean = false,
    private val today: () -> LocalDate = {
        kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : JournalRepository {

    private companion object {
        const val KEY = "local_journal_entries"
        const val KEY_SEEDED = "local_journal_seeded"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<MealEntry>>(emptyList())
    override val entries: StateFlow<List<MealEntry>> = _entries.asStateFlow()

    private var counter = 0
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        _entries.value = settings.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<List<MealEntry>>(it) }.getOrNull()
        } ?: emptyList()
        loaded = true
        if (seedDemoData && !settings.getBoolean(KEY_SEEDED, false)) {
            settings.putBoolean(KEY_SEEDED, true)
            if (_entries.value.isEmpty()) {
                persist(com.abbeysbite.app.data.demo.DemoData.demoJournal(today()))
            }
        }
    }

    override suspend fun load(from: LocalDate, to: LocalDate): AppResult<List<MealEntry>> {
        ensureLoaded()
        val filtered = _entries.value.filter { entry ->
            val date = runCatching { LocalDate.parse(entry.entryDate) }.getOrNull()
            date != null && date in from..to
        }
        return AppResult.Success(filtered)
    }

    override suspend fun add(entry: MealEntry): AppResult<MealEntry> {
        ensureLoaded()
        val withId = entry.copy(id = "local-entry-${counter++}-${entry.eatenAt.hashCode()}")
        persist(listOf(withId) + _entries.value)
        return AppResult.Success(withId)
    }

    override suspend fun updateNote(
        entryId: String,
        note: String?,
        satisfaction: SatisfactionLevel?,
    ): AppResult<Unit> {
        ensureLoaded()
        persist(_entries.value.map {
            if (it.id == entryId) it.copy(note = note, satisfaction = satisfaction) else it
        })
        return AppResult.Success(Unit)
    }

    override suspend fun delete(entryId: String): AppResult<Unit> {
        ensureLoaded()
        persist(_entries.value.filterNot { it.id == entryId })
        return AppResult.Success(Unit)
    }

    private fun persist(list: List<MealEntry>) {
        val sorted = list.sortedByDescending { it.eatenAt }
        _entries.value = sorted
        settings.putString(KEY, json.encodeToString(sorted))
    }
}
