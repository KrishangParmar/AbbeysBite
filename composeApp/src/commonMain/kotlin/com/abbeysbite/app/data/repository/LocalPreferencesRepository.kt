package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.ProgressSharingPreferences
import com.abbeysbite.app.data.model.UserPreferences
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Settings-backed implementations used when Supabase isn't configured.
 * Also double as the offline cache layer for demo/dev builds.
 */
class LocalPreferencesRepository(
    private val settings: Settings,
) : PreferencesRepository {

    private companion object {
        const val KEY_PREFS = "local_user_preferences"
        const val KEY_SHARING = "local_sharing_preferences"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _preferences = MutableStateFlow<UserPreferences?>(null)
    override val preferences: StateFlow<UserPreferences?> = _preferences.asStateFlow()

    override suspend fun load(): AppResult<UserPreferences> {
        val stored = settings.getStringOrNull(KEY_PREFS)?.let {
            runCatching { json.decodeFromString<UserPreferences>(it) }.getOrNull()
        } ?: UserPreferences()
        _preferences.value = stored
        return AppResult.Success(stored)
    }

    override suspend fun save(preferences: UserPreferences): AppResult<Unit> {
        settings.putString(KEY_PREFS, json.encodeToString(UserPreferences.serializer(), preferences))
        _preferences.value = preferences
        return AppResult.Success(Unit)
    }

    override suspend fun loadSharingPreferences(): AppResult<ProgressSharingPreferences> {
        val stored = settings.getStringOrNull(KEY_SHARING)?.let {
            runCatching { json.decodeFromString<ProgressSharingPreferences>(it) }.getOrNull()
        } ?: ProgressSharingPreferences()
        return AppResult.Success(stored)
    }

    override suspend fun saveSharingPreferences(prefs: ProgressSharingPreferences): AppResult<Unit> {
        settings.putString(KEY_SHARING, json.encodeToString(ProgressSharingPreferences.serializer(), prefs))
        return AppResult.Success(Unit)
    }
}

class LocalPantryRepository(
    private val settings: Settings,
) : PantryRepository {

    private companion object {
        const val KEY_PANTRY = "local_pantry_items"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow<List<PantryItem>>(emptyList())
    override val items: StateFlow<List<PantryItem>> = _items.asStateFlow()

    private var counter = 0

    override suspend fun load(): AppResult<List<PantryItem>> {
        val stored = settings.getStringOrNull(KEY_PANTRY)?.let {
            runCatching { json.decodeFromString<List<PantryItem>>(it) }.getOrNull()
        } ?: emptyList()
        _items.value = stored
        return AppResult.Success(stored)
    }

    override suspend fun add(name: String, category: String?): AppResult<PantryItem> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.Failure(com.abbeysbite.app.core.util.AppError.Validation("Item name can’t be empty."))
        }
        if (_items.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return AppResult.Failure(com.abbeysbite.app.core.util.AppError.Validation("That’s already in your pantry."))
        }
        val item = PantryItem(id = "local-${counter++}-${trimmed.hashCode()}", name = trimmed, category = category)
        persist(_items.value + item)
        return AppResult.Success(item)
    }

    override suspend fun remove(id: String): AppResult<Unit> {
        persist(_items.value.filterNot { it.id == id })
        return AppResult.Success(Unit)
    }

    private fun persist(list: List<PantryItem>) {
        val sorted = list.sortedBy { it.name.lowercase() }
        _items.value = sorted
        settings.putString(KEY_PANTRY, json.encodeToString(sorted))
    }
}
