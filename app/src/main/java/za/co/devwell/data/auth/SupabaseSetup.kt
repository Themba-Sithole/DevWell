package za.co.devwell.data.auth

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import za.co.devwell.BuildConfig
import kotlin.time.Duration.Companion.seconds

object SupabaseSetup {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun createClient(context: Context): SupabaseClient? {
        if (!isConfigured) return null
        return createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            httpEngine = OkHttp.create()
            requestTimeout = 20.seconds
            install(Auth) { sessionManager = DeviceSessionManager(context) }
            install(Postgrest)
            install(Functions)
        }
    }
}
