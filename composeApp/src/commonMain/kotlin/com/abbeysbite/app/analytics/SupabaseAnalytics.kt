package com.abbeysbite.app.analytics

import com.abbeysbite.app.platform.PlatformInfo
import io.github.jan.supabase.SupabaseClient
import kotlin.concurrent.Volatile
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
private data class AnalyticsRow(
    @SerialName("user_id") val userId: String?,
    val event: String,
    val properties: JsonObject,
    val platform: String,
    @SerialName("app_version") val appVersion: String,
)

/**
 * First-party, privacy-conscious analytics: coarse product events written to
 * the `analytics_events` table (insert-only under RLS; clients can never read
 * them back). NEVER logs chat content, meal photos, health data or tokens —
 * enforced by only accepting short string properties from call sites that
 * pass event names + coarse labels.
 */
class SupabaseAnalytics(
    private val supabase: SupabaseClient,
    private val platformInfo: PlatformInfo,
    private val scope: CoroutineScope,
) : Analytics {

    @Volatile
    private var userId: String? = null

    override fun setUserId(userId: String?) {
        this.userId = userId
    }

    override fun track(event: String, properties: Map<String, String>) {
        if (platformInfo.isDebug) {
            println("[analytics] $event ${if (properties.isEmpty()) "" else properties}")
        }
        val row = AnalyticsRow(
            userId = userId,
            event = event.take(64),
            properties = JsonObject(
                properties.entries.take(8).associate { (k, v) ->
                    k.take(32) to JsonPrimitive(v.take(64))
                }
            ),
            platform = platformInfo.platformName,
            appVersion = platformInfo.appVersion,
        )
        // Fire-and-forget; analytics must never affect the product experience.
        scope.launch {
            runCatching { supabase.from("analytics_events").insert(row) }
        }
    }
}
