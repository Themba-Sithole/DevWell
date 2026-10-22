package za.co.devwell.data.avatar

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import za.co.devwell.model.DevWellUser

interface AvatarRepository {
    /** The signed-in user's photo as JPEG bytes, or null when they have none. */
    val photo: Flow<ByteArray?>
    suspend fun upload(jpeg: ByteArray)
    suspend fun remove()
}


class SupabaseAvatarRepository(
    private val client: SupabaseClient?,
    currentUser: Flow<DevWellUser?>,
    scope: CoroutineScope,
) : AvatarRepository {
    private val state = MutableStateFlow<ByteArray?>(null)
    override val photo: Flow<ByteArray?> = state

    init {
        // Reload only when the signed-in user changes, and never show the previous user's photo.
        currentUser.map { it?.id }.distinctUntilChanged().onEach { userId ->
            state.value = null
            if (userId != null) load(userId)
        }.launchIn(scope)
    }

    private suspend fun load(userId: String) {
        val supabase = client ?: return
        try {

            state.value = supabase.storage.from(BUCKET).downloadAuthenticated(pathFor(userId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No photo yet, or offline: the profile screen shows initials.
        }
    }

    override suspend fun upload(jpeg: ByteArray) {
        val supabase = client ?: error("Supabase is not configured for this app.")
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Please sign in again.")

        supabase.storage.from(BUCKET).upload(pathFor(userId), jpeg) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        state.value = jpeg
    }

    override suspend fun remove() {
        val supabase = client ?: error("Supabase is not configured for this app.")
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Please sign in again.")

        supabase.storage.from(BUCKET).delete(pathFor(userId))
        state.value = null
    }

    private fun pathFor(userId: String) = "$userId/avatar.jpg"

    private companion object {
        const val BUCKET = "avatars"
    }
}