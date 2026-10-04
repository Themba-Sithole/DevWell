package za.co.devwell.data.manager

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.co.devwell.data.ManagerRepository
import za.co.devwell.model.*

/** Join-request review for managers. Reads only team and join_request rows, never wellbeing data. */
class SupabaseManagerRepository(
    private val client: SupabaseClient?,
    scope: CoroutineScope,
) : ManagerRepository {
    override val joinRequests = MutableStateFlow<List<JoinRequest>>(emptyList())
    override val managedTeam = MutableStateFlow<Team?>(null)
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            client?.auth?.sessionStatus?.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> try {
                        lock.withLock { load() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The interface has no error channel; an uncaught error here would crash the app.
                        clear()
                    }
                    is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> clear()
                    else -> Unit
                }
            }
        }
    }

    private fun connection() = client ?: error(
        "Add the Supabase project URL and public key to local.properties, then rebuild the app."
    )

    private fun clear() {
        joinRequests.value = emptyList()
        managedTeam.value = null
    }

    private suspend fun load() {
        val supabase = connection()
        val uid = supabase.auth.currentUserOrNull()?.id ?: return clear()
        // A manager may be assigned several teams; the screen shows one, so take the first by name.
        val team = supabase.from("team").select(Columns.list("id", "company_id", "name", "manager_id")) {
            filter { eq("manager_id", uid) }
            order("name", Order.ASCENDING)
            limit(1)
        }.decodeList<ManagerTeamRow>().firstOrNull()?.toTeam()
        // RLS also limits these rows to teams the caller may review.
        val requests = if (team == null) emptyList() else {
            supabase.from("join_request")
                .select(Columns.list("id", "user_id", "company_id", "team_id", "status", "created_at")) {
                    filter { eq("team_id", team.id) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<JoinRequestRow>().map { it.toJoinRequest() }
        }
        managedTeam.value = team
        joinRequests.value = requests
    }

    override suspend fun reviewJoinRequest(requestId: String, approve: Boolean) = lock.withLock {
        // The server verifies the JWT and the manager's team, then updates request + profile in one transaction.
        val response = try {
            connection().functions.invoke("review-join-request", body = buildJsonObject {
                put("request_id", requestId)
                put("approve", approve)
            })
        } catch (error: RestException) {
            throw readableReviewError(error) ?: error
        }
        val result = json.decodeFromString<ReviewJoinRequestResult>(response.bodyAsText())
        val expected = if (approve) "approved" else "rejected"
        check(result.requestId == requestId && result.status == expected) {
            "The review could not be confirmed. Refresh and try again."
        }
        try {
            load()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The review is already saved; a failed reload only leaves the list stale until the next load.
        }
    }

    // SDK 3.0.3 puts the raw response body in RestException.error. The function's 403/409 messages are written for users.
    private fun readableReviewError(error: RestException): IllegalStateException? {
        if (error.statusCode != 403 && error.statusCode != 409) return null
        val message = runCatching {
            json.parseToJsonElement(error.error).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return if (message.isNullOrBlank()) null else IllegalStateException(message, error)
    }

    override suspend fun updateTeamSettings(team: Team) =
        error("Team settings cannot be saved to the server yet.")
}
