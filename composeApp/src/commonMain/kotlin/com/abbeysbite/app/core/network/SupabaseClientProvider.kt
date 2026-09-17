package com.abbeysbite.app.core.network

import com.abbeysbite.app.core.config.AppSecrets
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/** True when Supabase credentials are configured for this build. */
val isSupabaseConfigured: Boolean
    get() = AppSecrets.supabaseUrl.isNotBlank() && AppSecrets.supabaseAnonKey.isNotBlank()

fun createAppSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = AppSecrets.supabaseUrl.ifBlank { "https://unconfigured.supabase.co" },
    supabaseKey = AppSecrets.supabaseAnonKey.ifBlank { "unconfigured" },
) {
    install(Auth) {
        // Deep-link target for email confirmation / recovery:
        // abbeysbite://auth-callback (see AndroidManifest intent-filter and
        // the iOS URL scheme). MainActivity forwards intents via handleDeeplinks.
        scheme = "abbeysbite"
        host = "auth-callback"
    }
    install(Postgrest)
    install(Storage)
    install(Realtime)
    install(Functions)
}
