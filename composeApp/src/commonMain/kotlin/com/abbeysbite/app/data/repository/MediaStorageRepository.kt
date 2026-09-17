package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.hours

/**
 * Media storage abstraction.
 * - Meal photos: PRIVATE bucket, path-scoped per user, accessed via signed URLs.
 * - Recipe covers + avatars: public buckets (CDN-cacheable).
 */
interface MediaStorageRepository {
    /** Uploads a private meal photo; returns the storage path (not a URL). */
    suspend fun uploadMealPhoto(jpegBytes: ByteArray): AppResult<String>

    /** Resolves a signed URL for a private meal photo path. */
    suspend fun signedMealPhotoUrl(path: String): AppResult<String>

    /** Uploads a public recipe cover; returns its public URL. */
    suspend fun uploadRecipeImage(jpegBytes: ByteArray): AppResult<String>

    /** Uploads a public avatar; returns its public URL. */
    suspend fun uploadAvatar(jpegBytes: ByteArray): AppResult<String>
}

class SupabaseMediaStorageRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : MediaStorageRepository {

    private companion object {
        const val BUCKET_MEALS = "meal-photos-private"
        const val BUCKET_RECIPES = "recipe-images-public"
        const val BUCKET_AVATARS = "avatars"
    }

    private var uploadCounter = 0

    private fun uniqueName(): String {
        uploadCounter++
        return "${kotlin.random.Random.nextLong().toString(16)}-$uploadCounter.jpg"
    }

    override suspend fun uploadMealPhoto(jpegBytes: ByteArray): AppResult<String> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val path = "$uid/${uniqueName()}"
            supabase.storage.from(BUCKET_MEALS).upload(path, jpegBytes)
            AppResult.Success(path)
        } catch (e: Exception) {
            AppResult.Failure(AppError.UploadFailed(e))
        }
    }

    override suspend fun signedMealPhotoUrl(path: String): AppResult<String> = try {
        AppResult.Success(
            supabase.storage.from(BUCKET_MEALS).createSignedUrl(path, 12.hours)
        )
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun uploadRecipeImage(jpegBytes: ByteArray): AppResult<String> =
        uploadPublic(BUCKET_RECIPES, jpegBytes)

    override suspend fun uploadAvatar(jpegBytes: ByteArray): AppResult<String> =
        uploadPublic(BUCKET_AVATARS, jpegBytes)

    private suspend fun uploadPublic(bucket: String, bytes: ByteArray): AppResult<String> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val path = "$uid/${uniqueName()}"
            supabase.storage.from(bucket).upload(path, bytes)
            AppResult.Success(supabase.storage.from(bucket).publicUrl(path))
        } catch (e: Exception) {
            AppResult.Failure(AppError.UploadFailed(e))
        }
    }
}

/** Demo-mode storage: keeps bytes in memory and returns stable fake paths. */
class InMemoryMediaStorageRepository : MediaStorageRepository {
    private val store = mutableMapOf<String, ByteArray>()
    private var counter = 0

    /** Read-back for demo image rendering. */
    fun bytesFor(path: String): ByteArray? = store[path]

    override suspend fun uploadMealPhoto(jpegBytes: ByteArray): AppResult<String> {
        val path = "demo-meal-${counter++}"
        store[path] = jpegBytes
        return AppResult.Success(path)
    }

    override suspend fun signedMealPhotoUrl(path: String): AppResult<String> =
        AppResult.Success("memory://$path")

    override suspend fun uploadRecipeImage(jpegBytes: ByteArray): AppResult<String> {
        val path = "demo-recipe-img-${counter++}"
        store[path] = jpegBytes
        return AppResult.Success("memory://$path")
    }

    override suspend fun uploadAvatar(jpegBytes: ByteArray): AppResult<String> {
        val path = "demo-avatar-${counter++}"
        store[path] = jpegBytes
        return AppResult.Success("memory://$path")
    }
}
