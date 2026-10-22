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
import io.ktor.http.HttpMethod
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
import za.co.devwell.ui.manager.memberCountLabel
import za.co.devwell.viewmodel.friendlyError

class SupabaseManagerRepositoryTest {
    private val token = """{"access_token":"test-token","refresh_token":"test-refresh","token_type":"bearer","expires_in":3600,"user":{"id":"m1","email":"manager@example.com","aud":"authenticated","role":"authenticated"}}"""
    private val team = """[{"id":"t1","company_id":"c1","name":"Mobile","manager_id":"m1"}]"""
    private val settingsTeam = """[{"id":"t1","company_id":"c1","name":"Mobile","manager_id":"m1","timezone":"Europe/London","check_in_cadence":"Weekly","low_wellbeing_threshold":3.0}]"""
    private val pending ="""[{"id":"r2","user_id":"u2","company_id":"c1","team_id":"t1","status":"pending","created_at":"2026-09-26T10:00:00+00:00"},{"id":"r1","user_id":"u1","company_id":"c1","team_id":"t1","status":"approved","created_at":"2026-09-25T10:00:00+00:00"}]"""

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

    @Test fun failedReloadAfterReviewKeepsPreviousTeamAndRequests() = runTest {
        var reviewed = false
        var failedReloads = 0
        val serverError = """{"message":"Server error"}"""
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/functions/v1/review-join-request" -> {
                    reviewed = true
                    json("""{"request_id":"r2","status":"approved"}""")
                }
                "/rest/v1/team", "/rest/v1/join_request" -> if (reviewed) {
                    failedReloads++
                    respond(serverError, HttpStatusCode.InternalServerError, headersOf("Content-Type", "application/json"))
                } else json(if (path == "/rest/v1/team") team else pending)
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val teamBefore = awaitValue(repo.managedTeam) { it != null }
            val requestsBefore = awaitValue(repo.joinRequests) { it.isNotEmpty() }
            val error = runCatching { repo.reviewJoinRequest("r2", approve = true) }.exceptionOrNull()
            assertNull("Expected no error but got $error", error)
            assertTrue("The reload after the review never ran", failedReloads > 0)
            assertEquals(teamBefore, repo.managedTeam.value)
            assertEquals(requestsBefore, repo.joinRequests.value)
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

    @Test fun serverErrorKeepsOriginalRestException() = runTest {
        val body = """{"error":"The request could not be saved. Please retry."}"""
        val error = reviewFailure(HttpStatusCode.InternalServerError, body)
        assertTrue("Expected RestException but got $error", error is RestException)
        assertEquals(500, (error as RestException).statusCode)
        assertEquals(
            "The request could not be completed. Check your details or try again later.",
            friendlyError(error),
        )
    }

    @Test fun unreadableConflictKeepsOriginalRestException() = runTest {
        for (body in listOf("not json", """{"message":"no error field"}""")) {
            val error = reviewFailure(HttpStatusCode.Conflict, body)
            assertTrue("Body $body: expected RestException but got $error", error is RestException)
            assertFalse(error is IllegalStateException)
            assertEquals(body, (error as RestException).error)
        }
    }

    @Test fun teamSettingsAreNotSavedYet() = runTest {
        assertTrue("The team settings migration is applied to the shared database, so saving is on", TEAM_SETTINGS_LIVE)
        val repo = SupabaseManagerRepository(client = null, scope = backgroundScope, settingsLive = false)
        val error = runCatching { repo.updateTeamSettings(Team("t1", "c1", "Mobile")) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Team settings cannot be saved to the server yet.", error?.message)
    }

    @Test fun flagOffLoadSelectsOnlyCurrentColumnsAndNeverCallsRpc() = runTest {
        var teamSelect: String? = null
        var settingsRpcCalls = 0
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> { teamSelect = request.url.parameters["select"]; json(team) }
                "/rest/v1/join_request" -> json("[]")
                else -> { if (path == "/rest/v1/rpc/update_team_settings") settingsRpcCalls++; error("Unexpected request: $path") }
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope, settingsLive = false)
            signIn(client)
            val loaded = awaitValue(repo.managedTeam) { it != null }!!
            assertEquals(setOf("id", "company_id", "name", "manager_id"), teamSelect!!.split(",").toSet())
            val error = runCatching { repo.updateTeamSettings(loaded.copy(name = "Platform")) }.exceptionOrNull()
            assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
            assertEquals("Team settings cannot be saved to the server yet.", error?.message)
            assertEquals(0, settingsRpcCalls)
            assertEquals(loaded, repo.managedTeam.value)
        } finally { client.close() }
    }

    @Test fun flagOnLoadSelectsSettingsColumns() = runTest {
        var teamSelect: String? = null
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> { teamSelect = request.url.parameters["select"]; json(settingsTeam) }
                "/rest/v1/join_request" -> json("[]")
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope, settingsLive = true)
            signIn(client)
            val loaded = awaitValue(repo.managedTeam) { it != null }!!
            assertEquals(
                setOf("id", "company_id", "name", "manager_id", "timezone", "check_in_cadence", "low_wellbeing_threshold"),
                teamSelect!!.split(",").toSet(),
            )
            assertEquals("Europe/London", loaded.timezone)
            assertEquals("Weekly", loaded.checkInCadence)
            assertEquals(3.0, loaded.lowWellbeingThreshold)
        } finally { client.close() }
    }

    @Test fun saveSendsExactlyFiveParametersAndPublishesTeam() = runTest {
        var rpcMethod: HttpMethod? = null
        var rpcBody: String? = null
        val saved = """{"id":"t1","company_id":"c1","name":"Platform","manager_id":"m1","timezone":"Africa/Johannesburg","check_in_cadence":"Daily","low_wellbeing_threshold":2.5}"""
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(settingsTeam)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/update_team_settings" -> {
                    rpcMethod = request.method
                    rpcBody = bodyText(request)
                    json(saved)
                }
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope, settingsLive = true)
            signIn(client)
            val before = awaitValue(repo.managedTeam) { it != null }!!
            repo.updateTeamSettings(
                before.copy(name = "Platform", timezone = "Africa/Johannesburg", checkInCadence = "Daily", lowWellbeingThreshold = 2.5)
            )
            assertEquals(HttpMethod.Post, rpcMethod)
            val sent = Json.parseToJsonElement(rpcBody!!).jsonObject
            assertEquals(
                setOf("p_team_id", "p_name", "p_timezone", "p_check_in_cadence", "p_low_wellbeing_threshold"),
                sent.keys,
            )
            assertEquals("\"t1\"", sent["p_team_id"].toString())
            assertEquals("\"Platform\"", sent["p_name"].toString())
            assertEquals("\"Africa/Johannesburg\"", sent["p_timezone"].toString())
            assertEquals("\"Daily\"", sent["p_check_in_cadence"].toString())
            assertEquals("2.5", sent["p_low_wellbeing_threshold"].toString())
            assertEquals(
                Team("t1", "c1", "Platform", "m1", "Africa/Johannesburg", "Daily", 2.5, memberCount = before.memberCount),
                repo.managedTeam.value,
            )
        } finally { client.close() }
    }

    @Test fun badRequestOnSaveShowsTheServerMessage() = runTest {
        assertReadableSettingsError(HttpStatusCode.BadRequest, "PT400", "Team name cannot be empty.")
    }

    @Test fun forbiddenOnSaveShowsTheServerMessage() = runTest {
        assertReadableSettingsError(HttpStatusCode.Forbidden, "PT403", "You cannot change this team's settings.")
    }

    @Test fun conflictOnSaveShowsTheServerMessage() = runTest {
        assertReadableSettingsError(HttpStatusCode.Conflict, "PT409", "Another team in your company already uses this name.")
    }

    @Test fun serverErrorOnSaveKeepsOriginalRestException() = runTest {
        val error = settingsFailure(HttpStatusCode.InternalServerError, """{"code":"XX000","message":"Server error"}""")
        assertTrue("Expected RestException but got $error", error is RestException)
        assertFalse(error is IllegalStateException)
        assertEquals(500, (error as RestException).statusCode)
    }

    @Test fun mismatchedSavedTeamIsRejected() = runTest {
        val other = """{"id":"t2","company_id":"c1","name":"Platform","manager_id":"m1","timezone":"Africa/Johannesburg","check_in_cadence":"Daily","low_wellbeing_threshold":2.0}"""
        val error = settingsFailure(HttpStatusCode.OK, other)
        assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
        assertEquals("The team settings could not be confirmed. Refresh and try again.", error?.message)
    }

    private val requesterNames = """[{"request_id":"r2","user_id":"u2","name":"Thandi Nkosi"}]"""
    private val roster = """[{"user_id":"u1","name":"Sam Peters"},{"user_id":"u3","name":"Lee Adams"}]"""

    @Test fun namesAreFilledInFromBothFunctions() = runTest {
        val rpcCalls = mutableMapOf<String, Pair<HttpMethod, String>>()
        val client = namesClient(pendingNames = { requesterNames }, roster = { roster }, rpcCalls = rpcCalls)
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val requests = awaitValue(repo.joinRequests) { it.isNotEmpty() }
            assertEquals(listOf("r2", "r1"), requests.map { it.id })
            assertEquals(listOf("Thandi Nkosi", "Sam Peters"), requests.map { it.userName })
            assertTrue(requests.all { it.userEmail.isEmpty() })
            val loaded = repo.managedTeam.value!!
            assertEquals(2, loaded.memberCount)
            assertEquals(listOf("Lee Adams", "Sam Peters"), loaded.roster?.map { it.name })
            assertEquals(listOf("u3", "u1"), loaded.roster?.map { it.userId })
            for (path in listOf("/rest/v1/rpc/pending_join_requesters", "/rest/v1/rpc/team_roster")) {
                val (method, body) = rpcCalls[path] ?: error("$path was not called")
                assertEquals(HttpMethod.Post, method)
                val sent = Json.parseToJsonElement(body).jsonObject
                assertEquals(setOf("p_team_id"), sent.keys)
                assertEquals("\"t1\"", sent["p_team_id"].toString())
            }
        } finally { client.close() }
    }

    @Test fun failingFunctionKeepsRequestsAndPlaceholders() = runTest {
        // pending_join_requesters fails: requests stay, the pending one keeps its placeholder.
        namesClient(pendingNames = { null }, roster = { roster }).let { client ->
            try {
                val repo = SupabaseManagerRepository(client, backgroundScope)
                signIn(client)
                val requests = awaitValue(repo.joinRequests) { it.isNotEmpty() }
                assertEquals(listOf("Engineer u2", "Sam Peters"), requests.map { it.userName })
                assertEquals(2, repo.managedTeam.value!!.memberCount)
            } finally { client.close() }
        }
        // team_roster fails: requests stay, the count stays unknown.
        namesClient(pendingNames = { requesterNames }, roster = { null }).let { client ->
            try {
                val repo = SupabaseManagerRepository(client, backgroundScope)
                signIn(client)
                val requests = awaitValue(repo.joinRequests) { it.isNotEmpty() }
                assertEquals(listOf("Thandi Nkosi", "Engineer u1"), requests.map { it.userName })
                val loaded = repo.managedTeam.value!!
                assertEquals(0, loaded.memberCount)
                assertNull(loaded.roster)
            } finally { client.close() }
        }
    }

    @Test fun failingRosterOnReloadKeepsOldCount() = runTest {
        var rosterUp = true
        var rosterCalls = 0
        val client = namesClient(pendingNames = { requesterNames }, roster = { rosterCalls++; if (rosterUp) roster else null })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val before = awaitValue(repo.managedTeam) { it != null }!!
            assertEquals(2, before.memberCount)
            rosterUp = false
            val callsBefore = rosterCalls
            repo.reviewJoinRequest("r2", approve = true)
            assertTrue("The reload never called team_roster", rosterCalls > callsBefore)
            assertEquals(2, repo.managedTeam.value!!.memberCount)
            assertEquals(before.roster, repo.managedTeam.value!!.roster)
            assertEquals(2, repo.joinRequests.value.size)
        } finally { client.close() }
    }

    @Test fun pendingRequesterMissingKeepsPlaceholder() = runTest {
        val client = namesClient(pendingNames = { "[]" }, roster = { "[]" })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val requests = awaitValue(repo.joinRequests) { it.isNotEmpty() }
            assertEquals("Engineer u2", requests.first { it.id == "r2" }.userName)
        } finally { client.close() }
    }

    @Test fun emptyRosterIsARealZero() = runTest {
        val client = namesClient(pendingNames = { requesterNames }, roster = { "[]" })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            val loaded = awaitValue(repo.managedTeam) { it != null }!!
            assertEquals(0, loaded.memberCount)
            assertEquals(emptyList<TeamMember>(), loaded.roster)
            assertEquals("No members yet", memberCountLabel(loaded.memberCount, known = loaded.roster != null))
        } finally { client.close() }
    }

    @Test fun removeMemberSendsTeamAndUserThenReloadsTheRoster() = runTest {
        var removeBody: String? = null
        var rosterCalls = 0
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(team)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/pending_join_requesters" -> json("[]")
                "/rest/v1/rpc/team_roster" -> { rosterCalls++; json(roster) }
                "/rest/v1/rpc/remove_team_member" -> {
                    removeBody = bodyText(request)
                    json("""{"removed":true,"user_id":"u1"}""")
                }
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.managedTeam) { it != null }
            val callsBefore = rosterCalls
            repo.removeMember("t1", "u1")
            val sent = Json.parseToJsonElement(removeBody!!).jsonObject
            assertEquals(setOf("p_team_id", "p_user_id"), sent.keys)
            assertEquals("\"t1\"", sent["p_team_id"].toString())
            assertEquals("\"u1\"", sent["p_user_id"].toString())
            assertTrue("The roster was not reloaded", rosterCalls > callsBefore)
        } finally { client.close() }
    }

    @Test fun removeMemberShowsTheServerMessage() = runTest {
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(team)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/pending_join_requesters" -> json("[]")
                "/rest/v1/rpc/team_roster" -> json(roster)
                "/rest/v1/rpc/remove_team_member" -> respond(
                    """{"code":"PT404","message":"That member is no longer on this team.","details":null,"hint":null}""",
                    HttpStatusCode.NotFound,
                    headersOf("Content-Type", "application/json"),
                )
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.managedTeam) { it != null }
            val error = runCatching { repo.removeMember("t1", "u1") }.exceptionOrNull()
            assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
            assertEquals("That member is no longer on this team.", friendlyError(error as Exception))
            assertEquals("u1", repo.managedTeam.value!!.roster?.first { it.userId == "u1" }?.userId)
        } finally { client.close() }
    }

    @Test fun deleteTeamSendsTeamIdAndSelectsTheNextTeam() = runTest {
        var deleteBody: String? = null
        var deleted = false
        val twoTeams = """[{"id":"t1","company_id":"c1","name":"Mobile","manager_id":"m1"},{"id":"t2","company_id":"c1","name":"Web","manager_id":"m1"}]"""
        val onlyWeb = """[{"id":"t2","company_id":"c1","name":"Web","manager_id":"m1"}]"""
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(if (deleted) onlyWeb else twoTeams)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/pending_join_requesters" -> json("[]")
                "/rest/v1/rpc/team_roster" -> json("[]")
                "/rest/v1/rpc/delete_team" -> {
                    deleteBody = bodyText(request)
                    deleted = true
                    json("""{"deleted":true}""")
                }
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            assertEquals("t1", awaitValue(repo.managedTeam) { it != null }?.id)
            repo.deleteTeam("t1")
            val sent = Json.parseToJsonElement(deleteBody!!).jsonObject
            assertEquals(setOf("p_team_id"), sent.keys)
            assertEquals("\"t1\"", sent["p_team_id"].toString())
            assertEquals(listOf("t2"), repo.managedTeams.value.map { it.id })
            assertEquals("t2", repo.managedTeam.value?.id)
        } finally { client.close() }
    }

    @Test fun deleteTeamDropsItEvenWhenTheReloadFails() = runTest {
        var deleted = false
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> if (deleted) respond(
                    """{"message":"Server error"}""",
                    HttpStatusCode.InternalServerError,
                    headersOf("Content-Type", "application/json"),
                ) else json(team)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/pending_join_requesters" -> json("[]")
                "/rest/v1/rpc/team_roster" -> json("[]")
                "/rest/v1/rpc/delete_team" -> { deleted = true; json("""{"deleted":true}""") }
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.managedTeam) { it != null }
            val error = runCatching { repo.deleteTeam("t1") }.exceptionOrNull()
            assertNull("Expected no error but got $error", error)
            assertTrue(repo.managedTeams.value.none { it.id == "t1" })
            assertNull(repo.managedTeam.value)
        } finally { client.close() }
    }

    @Test fun deleteTeamShowsTheServerMessage() = runTest {
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(team)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/pending_join_requesters" -> json("[]")
                "/rest/v1/rpc/team_roster" -> json("[]")
                "/rest/v1/rpc/delete_team" -> respond(
                    """{"code":"PT403","message":"You can only delete a team you created.","details":null,"hint":null}""",
                    HttpStatusCode.Forbidden,
                    headersOf("Content-Type", "application/json"),
                )
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            awaitValue(repo.managedTeam) { it != null }
            val error = runCatching { repo.deleteTeam("t1") }.exceptionOrNull()
            assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
            assertEquals("You can only delete a team you created.", friendlyError(error as Exception))
            assertEquals("t1", repo.managedTeam.value?.id)
        } finally { client.close() }
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

    /** Signs in, answers review-join-request with [status] and [body], and returns what the review threw. */
    private suspend fun kotlinx.coroutines.test.TestScope.reviewFailure(status: HttpStatusCode, body: String): Throwable? {
        val client = client(MockEngine { request ->
            if (request.url.encodedPath == "/functions/v1/review-join-request") {
                respond(body, status, headersOf("Content-Type", "application/json"))
            } else json(if (request.url.encodedPath == "/auth/v1/token") token else "[]")
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope)
            signIn(client)
            return runCatching { repo.reviewJoinRequest("r2", approve = true) }.exceptionOrNull()
        } finally { client.close() }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertReadableSettingsError(
        status: HttpStatusCode, code: String, message: String,
    ) {
        val error = settingsFailure(status, """{"code":"$code","message":"$message","details":null,"hint":null}""")
        assertTrue("Expected IllegalStateException but got $error", error is IllegalStateException)
        assertTrue(error?.cause is RestException)
        assertEquals(status.value, (error?.cause as RestException).statusCode)
        assertEquals(message, friendlyError(error as Exception))
    }

    /**
     * Loads the team with settings live, answers update_team_settings with [status] and [body],
     * checks the published team did not change, and returns what the save threw.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.settingsFailure(status: HttpStatusCode, body: String): Throwable? {
        val client = client(MockEngine { request ->
            when (val path = request.url.encodedPath) {
                "/auth/v1/token" -> json(token)
                "/rest/v1/team" -> json(settingsTeam)
                "/rest/v1/join_request" -> json("[]")
                "/rest/v1/rpc/update_team_settings" -> respond(body, status, headersOf("Content-Type", "application/json"))
                else -> error("Unexpected request: $path")
            }
        })
        try {
            val repo = SupabaseManagerRepository(client, backgroundScope, settingsLive = true)
            signIn(client)
            val before = awaitValue(repo.managedTeam) { it != null }!!
            val error = runCatching { repo.updateTeamSettings(before.copy(name = "Platform")) }.exceptionOrNull()
            assertEquals(before, repo.managedTeam.value)
            return error
        } finally { client.close() }
    }

    /**
     * Loads team t1 with [pending] requests. The two name functions answer with [pendingNames] and [roster];
     * null means a 500. Each function call's method and body is recorded in [rpcCalls].
     */
    private fun namesClient(
        pendingNames: () -> String?,
        roster: () -> String?,
        rpcCalls: MutableMap<String, Pair<HttpMethod, String>> = mutableMapOf(),
    ) = client(MockEngine { request ->
        when (val path = request.url.encodedPath) {
            "/auth/v1/token" -> json(token)
            "/rest/v1/team" -> json(team)
            "/rest/v1/join_request" -> json(pending)
            "/functions/v1/review-join-request" -> json("""{"request_id":"r2","status":"approved"}""")
            "/rest/v1/rpc/pending_join_requesters", "/rest/v1/rpc/team_roster" -> {
                rpcCalls[path] = request.method to bodyText(request)
                val body = if (path == "/rest/v1/rpc/team_roster") roster() else pendingNames()
                if (body != null) json(body) else respond(
                    """{"code":"XX000","message":"Server error"}""",
                    HttpStatusCode.InternalServerError,
                    headersOf("Content-Type", "application/json"),
                )
            }
            else -> error("Unexpected request: $path")
        }
    })

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
