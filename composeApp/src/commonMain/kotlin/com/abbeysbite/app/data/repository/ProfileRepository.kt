package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.domain.Validators
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

interface ProfileRepository {
    val myProfile: StateFlow<Profile?>

    suspend fun load(): AppResult<Profile>

    suspend fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
    ): AppResult<Profile>

    suspend fun profileOf(userId: String): AppResult<Profile>
}

class SupabaseProfileRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : ProfileRepository {

    private val _myProfile = MutableStateFlow<Profile?>(null)
    override val myProfile: StateFlow<Profile?> = _myProfile.asStateFlow()

    override suspend fun load(): AppResult<Profile> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            // Profile row is created by a DB trigger on signup; retry-read covers races.
            val profile = supabase.from("profiles")
                .select { filter { eq("id", uid) } }
                .decodeList<Profile>()
                .firstOrNull()
                ?: return AppResult.Failure(AppError.NotFound("Profile not ready yet — try again."))
            _myProfile.value = profile
            AppResult.Success(profile)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun updateProfile(
        username: String?,
        displayName: String?,
        bio: String?,
        avatarUrl: String?,
    ): AppResult<Profile> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        if (username != null) {
            Validators.usernameError(username)?.let {
                return AppResult.Failure(AppError.Validation(it))
            }
        }
        return try {
            val updated = supabase.from("profiles").update(
                {
                    username?.let { set("username", it) }
                    displayName?.let { set("display_name", it.trim()) }
                    bio?.let { set("bio", it.trim().take(200)) }
                    avatarUrl?.let { set("avatar_url", it) }
                }
            ) {
                filter { eq("id", uid) }
                select()
            }.decodeSingle<Profile>()
            _myProfile.value = updated
            AppResult.Success(updated)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("unique", true) || msg.contains("duplicate", true)) {
                AppResult.Failure(AppError.Validation("That username is taken — try another."))
            } else {
                AppResult.Failure(e.toAppError())
            }
        }
    }

    override suspend fun profileOf(userId: String): AppResult<Profile> = try {
        val profile = supabase.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeList<Profile>()
            .firstOrNull() ?: return AppResult.Failure(AppError.NotFound())
        AppResult.Success(profile)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }
}

class LocalProfileRepository(
    private val settings: Settings,
    private val auth: AuthRepository,
) : ProfileRepository {

    private companion object {
        const val KEY = "demo_profile"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _myProfile = MutableStateFlow<Profile?>(null)
    override val myProfile: StateFlow<Profile?> = _myProfile.asStateFlow()

    override suspend fun load(): AppResult<Profile> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        val stored = settings.getStringOrNull(KEY)?.let {
            runCatching { json.decodeFromString<Profile>(it) }.getOrNull()
        } ?: Profile(id = uid, username = "you", displayName = "You")
        _myProfile.value = stored
        return AppResult.Success(stored)
    }

    override suspend fun updateProfile(
        username: String?,
        displayName: String?,
        bio: String?,
        avatarUrl: String?,
    ): AppResult<Profile> {
        if (username != null) {
            Validators.usernameError(username)?.let {
                return AppResult.Failure(AppError.Validation(it))
            }
        }
        val current = _myProfile.value ?: load().getOrNull()
            ?: return AppResult.Failure(AppError.Unauthorized())
        val updated = current.copy(
            username = username ?: current.username,
            displayName = displayName ?: current.displayName,
            bio = bio ?: current.bio,
            avatarUrl = avatarUrl ?: current.avatarUrl,
        )
        settings.putString(KEY, json.encodeToString(updated))
        _myProfile.value = updated
        return AppResult.Success(updated)
    }

    override suspend fun profileOf(userId: String): AppResult<Profile> {
        val demo = com.abbeysbite.app.data.demo.DemoData.profiles.find { it.id == userId }
        return if (demo != null) AppResult.Success(demo)
        else AppResult.Failure(AppError.NotFound())
    }
}
