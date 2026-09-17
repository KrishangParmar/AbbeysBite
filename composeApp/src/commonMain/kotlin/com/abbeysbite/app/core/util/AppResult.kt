package com.abbeysbite.app.core.util

/**
 * Error taxonomy for the whole app. Every failure surfaced to the UI goes
 * through this so screens can render friendly, specific messages — never raw
 * stack traces.
 */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Network(override val cause: Throwable? = null) :
        AppError("We couldn’t reach the internet. Check your connection and try again.")

    data class Timeout(override val cause: Throwable? = null) :
        AppError("That took longer than expected. Please try again.")

    data class AiUnavailable(override val cause: Throwable? = null) :
        AppError("The kitchen assistant is briefly unavailable. Try again in a moment.")

    data class ModelNotReady(
        override val message: String = "Your private AI isn’t ready yet — finish setting it up first.",
    ) : AppError(message)

    data class ModelFailed(override val message: String, override val cause: Throwable? = null) :
        AppError(message)

    data class AiInvalidResponse(override val cause: Throwable? = null) :
        AppError("We couldn’t read that analysis. Please try again.")

    data class Unauthorized(override val cause: Throwable? = null) :
        AppError("Your session expired. Please sign in again.")

    data class UploadFailed(override val cause: Throwable? = null) :
        AppError("The photo didn’t upload. Please try again.")

    data class PermissionDenied(val permission: String) :
        AppError("Permission needed to continue.")

    data class NotFound(override val message: String = "That content is no longer available.") :
        AppError(message)

    data class Conflict(override val message: String) : AppError(message)

    data class LimitReached(override val message: String) : AppError(message)

    data class BillingUnavailable(override val cause: Throwable? = null) :
        AppError("Purchases are temporarily unavailable. Please try again later.")

    data class Validation(override val message: String) : AppError(message)

    data class Unknown(override val cause: Throwable? = null) :
        AppError("Something went wrong. Please try again.")
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Failure)?.error

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) block(error)
        return this
    }
}

inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(e.toAppError())
}

fun Throwable.toAppError(): AppError {
    val name = this::class.simpleName.orEmpty()
    val text = message.orEmpty()
    return when {
        name.contains("Timeout", ignoreCase = true) -> AppError.Timeout(this)
        name.contains("UnknownHost", ignoreCase = true) ||
            name.contains("Connect", ignoreCase = true) ||
            name.contains("IO", ignoreCase = false) && text.contains("network", true) ->
            AppError.Network(this)
        text.contains("JWT", ignoreCase = true) ||
            text.contains("unauthorized", ignoreCase = true) -> AppError.Unauthorized(this)
        else -> AppError.Unknown(this)
    }
}
