package za.co.devwell.data.manager

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.model.*
import za.co.devwell.viewmodel.friendlyError

class SupabaseManagerRepositoryTest {
    private val token = """{"access_token":"test-token","refresh_token":"test-refresh","token_type":"bearer","expires_in":3600,"user":{"id":"m1","email":"manager@example.com","aud":"authenticated","role":"authenticated"}}"""
    private val team = """[{"id":"t1","company_id":"c1","name":"Mobile","manager_id":"m1"}]"""
    private val pending = """[{"id":"r2","user_id":"u2","company_id":"c1","team_id":"t1","status":"pending","created_at":"2026-09-26T10:00:00+00:00"},{"id":"r1","user_id":"u1","company_id":"c1","team_id":"t1","status":"approved","created_at":"2026-09-25T10:00:00+00:00"}]"""

    @Test fun signInLoadsManagedTeamAndRequestsNewestFirst() = runTest {
        val queries = mutableListOf<String>()
        val client = client(MockEngine { request ->
            queries.add("${request.url.encodedPath}?${request.url.encodedQuery}")
            json(when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/rest/v1/team" -> team
                "/rest/v1/join_request" -> pending
                else -> error("Unexpected request: ${request.url.encodedPath}")
            })
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            assertEquals("t1", awaitValue(repo.managedTeam) { it != null }?.id)
            assertEquals(listOf("r2", "r1"), awaitValue(repo.joinRequests) { it.isNotEmpty() }.map { it.id })
            val teamQuery = queries.first { it.startsWith("/rest/v1/team") }
            assertTrue(teamQuery, "manager_id=eq.m1" in teamQuery && "order=name.asc" in teamQuery)
            val requestQuery = queries.first { it.startsWith("/rest/v1/join_request") }
            assertTrue(requestQuery, "team_id=eq.t1" in requestQuery && "order=created_at.desc" in requestQuery)
        } finally { client.close() }
    }

    @Test fun reviewSendsOnlyRequestIdAndApproveThenReloads() = runTest {
        var functionBody: String? = null
        var requestLoads = 0
        val client = client(MockEngine { request ->
            json(when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/rest/v1/team" -> team
                "/rest/v1/join_request" -> { requestLoads++; pending }
                "/functions/v1/review-join-request" -> {
                    functionBody = bodyText(request)
                    """{"request_id":"r2","status":"approved"}"""
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            })
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.joinRequests) { it.isNotEmpty() }
            val loadsBefore = requestLoads
            repo.reviewJoinRequest("r2", approve = true)
            val sent = Json.parseToJsonElement(functionBody!!).jsonObject
            assertEquals(setOf("request_id", "approve"), sent.keys)
            assertEquals("\"r2\"", sent["request_id"].toString())
            assertEquals("true", sent["approve"].toString())
            assertTrue(requestLoads > loadsBefore)
        } finally { client.close() }
    }

    @Test fun mismatchedReviewResultIsRejected() = runTest {
        val client = client(MockEngine { request ->
            json(when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/functions/v1/review-join-request" -> """{"request_id":"r2","status":"rejected"}"""
                else -> "[]"
            })
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val error = runCatching { repo.reviewJoinRequest("r2", approve = true) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
        } finally { client.close() }
    }

    @Test fun conflictShowsTheServerMessage() = runTest {
        val conflict = """{"error":"This request has already been reviewed."}"""
        val client = client(MockEngine { request ->
            if (request.url.encodedPath == "/functions/v1/review-join-request") {
                respond(conflict, HttpStatusCode.Conflict, headersOf("Content-Type", "application/json"))
            } else json(if (request.url.encodedPath == "/auth/v1/token") token else "[]")
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val error = runCatching { repo.reviewJoinRequest("r2", approve = true) }.exceptionOrNull()
            assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
            assertTrue(error?.cause is RestException)
            assertEquals("This request has already been reviewed.", friendlyError(error as Exception))
        } finally { client.close() }
    }

    @Test fun teamSettingsAreNotSavedYet() = runTest {
        val repo = SupabaseManagerRepository(client = null, scope = backgroundScope)
        val error = runCatching { repo.updateTeamSettings(Team("t1", "c1", "Mobile")) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Team settings cannot be saved to the server yet.", error?.message)
    }

    @Test fun signOutClearsTeamAndRequests() = runTest {
        val client = client(MockEngine { request ->
            json(when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/rest/v1/team" -> team
                "/rest/v1/join_request" -> pending
                else -> "{}"
            })
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.joinRequests) { it.isNotEmpty() }
            client.auth.signOut()
            assertNull(awaitValue(repo.managedTeam) { it == null })
            assertEquals(emptyList<JoinRequest>(), awaitValue(repo.joinRequests) { it.isEmpty() })
        } finally { client.close() }
    }

    private suspend fun signIn(client: io.github.jan.supabase.SupabaseClient) =
        client.auth.signInWith(Email) { email = "manager@example.com"; password = "password123" }

    // The repository loads on a background coroutine, so wait on real time instead of test time.
    private suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    private fun bodyText(request: HttpRequestData): String = when (val body = request.body) {
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> error("Unexpected body type: ${body::class.simpleName}")
    }

    private fun client(engine: MockEngine) = createSupabaseClient("https://example.supabase.co", "test-key") {
        httpEngine = engine
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            alwaysAutoRefresh = false
            autoLoadFromStorage = false
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        install(Functions)
    }
}
