package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.SatisfactionLevel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

class SupabaseJournalRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : JournalRepository, UserScopedState {

    private val _entries = MutableStateFlow<List<MealEntry>>(emptyList())
    override val entries: StateFlow<List<MealEntry>> = _entries.asStateFlow()

    override fun clearUserState() {
        _entries.value = emptyList()
    }

    override suspend fun load(from: LocalDate, to: LocalDate): AppResult<List<MealEntry>> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val list = supabase.from("meal_entries")
                .select {
                    filter {
                        eq("user_id", uid)
                        gte("entry_date", from.toString())
                        lte("entry_date", to.toString())
                    }
                    order("eaten_at", Order.DESCENDING)
                }
                .decodeList<MealEntry>()
            _entries.value = list
            AppResult.Success(list)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun add(entry: MealEntry): AppResult<MealEntry> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val inserted = supabase.from("meal_entries")
                .insert(entry.copy(id = "", userId = uid).toInsertMap()) {
                    select(Columns.ALL)
                }
                .decodeSingle<MealEntry>()
            _entries.value = listOf(inserted) + _entries.value
            AppResult.Success(inserted)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun updateNote(
        entryId: String,
        note: String?,
        satisfaction: SatisfactionLevel?,
    ): AppResult<Unit> = try {
        supabase.from("meal_entries").update(
            {
                set("note", note)
                set("satisfaction", satisfaction?.name?.lowercase())
            }
        ) {
            filter { eq("id", entryId) }
        }
        _entries.value = _entries.value.map {
            if (it.id == entryId) it.copy(note = note, satisfaction = satisfaction) else it
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun delete(entryId: String): AppResult<Unit> = try {
        supabase.from("meal_entries").delete { filter { eq("id", entryId) } }
        _entries.value = _entries.value.filterNot { it.id == entryId }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }
}

/** Insert payload without empty id/created_at so Postgres defaults apply. */
private fun MealEntry.toInsertMap(): Map<String, kotlinx.serialization.json.JsonElement> {
    val json = kotlinx.serialization.json.Json { encodeDefaults = true; explicitNulls = false }
    val obj = json.encodeToJsonElement(MealEntry.serializer(), this) as kotlinx.serialization.json.JsonObject
    return obj.filterKeys { it !in setOf("id", "created_at") }
}
