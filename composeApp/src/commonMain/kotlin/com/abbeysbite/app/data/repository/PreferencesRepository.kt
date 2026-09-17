package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.ProgressSharingPreferences
import com.abbeysbite.app.data.model.UserPreferences
import kotlinx.coroutines.flow.StateFlow

interface PreferencesRepository {
    /** Cached preferences for the signed-in user; refreshed on load. */
    val preferences: StateFlow<UserPreferences?>

    suspend fun load(): AppResult<UserPreferences>
    suspend fun save(preferences: UserPreferences): AppResult<Unit>

    suspend fun loadSharingPreferences(): AppResult<ProgressSharingPreferences>
    suspend fun saveSharingPreferences(prefs: ProgressSharingPreferences): AppResult<Unit>
}

interface PantryRepository {
    val items: StateFlow<List<PantryItem>>

    suspend fun load(): AppResult<List<PantryItem>>
    suspend fun add(name: String, category: String? = null): AppResult<PantryItem>
    suspend fun remove(id: String): AppResult<Unit>
}
