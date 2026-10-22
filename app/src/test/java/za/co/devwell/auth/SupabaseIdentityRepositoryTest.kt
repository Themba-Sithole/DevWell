package za.co.devwell.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.data.auth.SupabaseIdentityRepository
import za.co.devwell.model.*

class SupabaseIdentityRepositoryTest {
    private val profile = """[{"id":"u1","name":"Student","email":"user@example.com","role":"engineer","status":"pending","company_id":null,"team_id":null,"onboarding_done":false}]"""
    private val token = """{"access_token":"test-token","refresh_token":"test-refresh","token_type":"bearer","expires_in":3600,"user":{"id":"u1","email":"user@example.com","aud":"authenticated","role":"authenticated"}}"""

    @Test fun signInLoadsTrustedProfileAndDirectory() = runTest {
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            requested.add(request.url.encodedPath)
            val body = when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/rest/v1/user" -> profile
                "/rest/v1/company" -> """[{"id":"c1","name":"DevWell","status":"active"}]"""
                "/rest/v1/team" -> """[{"id":"t1","name":"Mobile","company_id":"c1"},{"id":"t9","name":"Hidden","company_id":"c9"}]"""
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            val result = repo.signIn("user@example.com", "password123")
            assertTrue(result.isSuccess)
            assertEquals(EntryDestination.ONBOARDING, entryDestination(repo.currentUser.value))
            repo.refreshDirectory()
            assertEquals(listOf("t1"), repo.teams.value.map { it.id })
            assertTrue("/rest/v1/user" in requested)
        } finally { client.close() }
    }

    @Test fun directoryShowsTeamMemberCounts() = runTest {
        val client = client(directoryEngine(HttpStatusCode.OK, """[{"team_id":"t1","member_count":3}]"""))
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isSuccess)
            repo.refreshDirectory()
            assertEquals(listOf(3, 0), repo.teams.value.map { it.memberCount })
        } finally { client.close() }
    }

    @Test fun directoryLoadsWhenMemberCountsAreUnavailable() = runTest {
        // Before the migration is applied the live database answers with "function not found".
        val client = client(directoryEngine(HttpStatusCode.NotFound,
            """{"code":"PGRST202","message":"Could not find the function public.team_member_counts"}"""))
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isSuccess)
            repo.refreshDirectory()
            assertEquals(listOf("t1", "t2"), repo.teams.value.map { it.id })
            assertEquals(listOf(0, 0), repo.teams.value.map { it.memberCount })
        } finally { client.close() }
    }

    @Test fun unknownServerRoleFailsClosed() = runTest {
        val engine = MockEngine { request ->
            respond(if (request.url.encodedPath == "/auth/v1/token") token else profile.replace("engineer", "owner"),
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isFailure)
            assertNull(repo.currentUser.value)
        } finally { client.close() }
    }

    @Test fun missingProfileDoesNotUnlockDashboard() = runTest {
        val engine = MockEngine { request ->
            respond(if (request.url.encodedPath == "/auth/v1/token") token else "[]",
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isFailure)
            assertNull(repo.currentUser.value)
        } finally { client.close() }
    }

    @Test fun offlineSignOutErasesLocalSession() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/auth/v1/logout") throw java.io.IOException("Offline")
            respond(if (request.url.encodedPath == "/auth/v1/token") token else profile,
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isSuccess)
            try { repo.signOut() } catch (_: Exception) { /* Revocation cannot reach server offline. */ }
            assertNull(repo.currentUser.value)
            assertNull(client.auth.currentSessionOrNull())
            assertNull(client.auth.sessionManager.loadSession())
        } finally { client.close() }
    }

    @Test fun signUpRefusesAnAddressThatAlreadyHasAnAccount() = runTest {
        // With email confirmation on, the server answers an existing address with a
        // placeholder user that has no identities and no session.
        val engine = MockEngine { request ->
            assertEquals("/auth/v1/signup", request.url.encodedPath)
            respond(
                """{"id":"u9","aud":"authenticated","role":"","email":"user@example.com","phone":"",
                   "confirmation_sent_at":"2026-10-22T09:19:57Z","app_metadata":{"provider":"email"},
                   "user_metadata":{},"identities":[],"is_anonymous":false}""",
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"),
            )
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            val error = runCatching {
                repo.signUp("Student", "user@example.com", "password123", SignupRole.ENGINEER)
            }.exceptionOrNull()
            assertEquals(EMAIL_ALREADY_REGISTERED, error?.message)
            assertNull(client.auth.currentSessionOrNull())
            assertNull(repo.currentUser.value)
        } finally { client.close() }
    }

    @Test fun signUpOfANewAddressAsksForEmailConfirmation() = runTest {
        val engine = MockEngine { request ->
            respond(
                """{"id":"u2","aud":"authenticated","role":"authenticated","email":"new@example.com",
                   "phone":"","confirmation_sent_at":"2026-10-22T09:20:14Z",
                   "app_metadata":{"provider":"email","providers":["email"]},
                   "user_metadata":{"name":"Student","role":"manager"},
                   "identities":[{"identity_id":"i1","id":"u2","user_id":"u2","provider":"email",
                   "identity_data":{"email":"new@example.com"},"created_at":"2026-10-22T09:20:14Z"}],
                   "created_at":"2026-10-22T09:20:14Z","is_anonymous":false}""",
                HttpStatusCode.OK, headersOf("Content-Type", "application/json"),
            )
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            val message = repo.signUp("Student", "new@example.com", "password123", SignupRole.MANAGER)
            assertEquals("Check your email to confirm your account, then return here and sign in.", message)
        } finally { client.close() }
    }

    @Test fun aRejectedEngineerCanRestartOnboarding() = runTest {
        var restarted = false
        var rpcCalls = 0
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/auth/v1/token" -> token
                "/rest/v1/user" -> if (restarted) profile else
                    """[{"id":"u1","name":"Student","email":"user@example.com","role":"engineer","status":"rejected","company_id":"c1","team_id":"t1","onboarding_done":true}]"""
                "/rest/v1/rpc/restart_team_onboarding" -> {
                    rpcCalls++
                    restarted = true
                    """{"restarted":true}"""
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isSuccess)
            assertEquals(AccountStatus.REJECTED, repo.currentUser.value?.status)

            repo.restartTeamOnboarding()

            assertEquals(1, rpcCalls)
            assertEquals(AccountStatus.PENDING, repo.currentUser.value?.status)
            assertEquals(false, repo.currentUser.value?.onboardingDone)
            assertNull(repo.currentUser.value?.companyId)
            assertEquals(EntryDestination.ONBOARDING, entryDestination(repo.currentUser.value))
        } finally { client.close() }
    }

    @Test fun restartOnboardingShowsTheServerMessage() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/v1/token" -> respond(token, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                "/rest/v1/user" -> respond(
                    """[{"id":"u1","name":"Student","email":"user@example.com","role":"engineer","status":"rejected","company_id":"c1","team_id":"t1","onboarding_done":true}]""",
                    HttpStatusCode.OK, headersOf("Content-Type", "application/json"),
                )
                "/rest/v1/rpc/restart_team_onboarding" -> respond(
                    """{"code":"PT409","message":"Only a request that was not approved can choose another team."}""",
                    HttpStatusCode.Conflict, headersOf("Content-Type", "application/json"),
                )
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val client = client(engine)
        try {
            val repo = SupabaseIdentityRepository(client, backgroundScope)
            assertTrue(repo.signIn("user@example.com", "password123").isSuccess)
            val error = runCatching { repo.restartTeamOnboarding() }.exceptionOrNull()
            assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
            assertEquals(
                "Only a request that was not approved can choose another team.",
                error?.message,
            )
            assertEquals(AccountStatus.REJECTED, repo.currentUser.value?.status)
        } finally { client.close() }
    }

    private fun directoryEngine(countStatus: HttpStatusCode, countBody: String) = MockEngine { request ->
        val (status, body) = when (request.url.encodedPath) {
            "/auth/v1/token" -> HttpStatusCode.OK to token
            "/rest/v1/user" -> HttpStatusCode.OK to profile
            "/rest/v1/company" -> HttpStatusCode.OK to """[{"id":"c1","name":"DevWell","status":"active"}]"""
            "/rest/v1/team" -> HttpStatusCode.OK to """[{"id":"t1","name":"Mobile","company_id":"c1"},{"id":"t2","name":"Web","company_id":"c1"}]"""
            "/rest/v1/rpc/team_member_counts" -> countStatus to countBody
            else -> error("Unexpected request: ${request.url.encodedPath}")
        }
        respond(body, status, headersOf("Content-Type", "application/json"))
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
