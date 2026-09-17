package com.abbeysbite.app.core.session

import com.abbeysbite.app.core.network.isSupabaseConfigured
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class NotifyRequest(
    val type: String,
    val target_user_id: String,
    /** recipe_liked: the server looks the recipe up and uses ITS title. */
    val recipe_id: String? = null,
)

/**
 * Client trigger for server-side OneSignal sends (the `send-notification`
 * Edge Function holds the REST key and re-validates relationships).
 * Fire-and-forget: a failed push must never affect the user action itself.
 */
class NotificationSender(
    private val supabase: SupabaseClient,
    private val scope: CoroutineScope,
) {
    private fun send(request: NotifyRequest) {
        if (!isSupabaseConfigured) return
        scope.launch {
            runCatching { supabase.functions.invoke("send-notification", body = request) }
        }
    }

    fun friendRequest(targetUserId: String) =
        send(NotifyRequest("friend_request", targetUserId))

    fun friendAccepted(targetUserId: String) =
        send(NotifyRequest("friend_accepted", targetUserId))

    fun recipeLiked(targetUserId: String, recipeId: String) =
        send(NotifyRequest("recipe_liked", targetUserId, recipeId))
}
