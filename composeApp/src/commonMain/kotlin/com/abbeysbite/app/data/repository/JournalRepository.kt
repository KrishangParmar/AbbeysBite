package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealEntry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

interface JournalRepository {
    /** Entries currently loaded, newest first. */
    val entries: StateFlow<List<MealEntry>>

    /** Loads entries between [from] and [to] inclusive (entry_date). */
    suspend fun load(from: LocalDate, to: LocalDate): AppResult<List<MealEntry>>

    suspend fun add(entry: MealEntry): AppResult<MealEntry>

    suspend fun updateNote(
        entryId: String,
        note: String?,
        satisfaction: com.abbeysbite.app.data.model.SatisfactionLevel?,
    ): AppResult<Unit>

    suspend fun delete(entryId: String): AppResult<Unit>
}
