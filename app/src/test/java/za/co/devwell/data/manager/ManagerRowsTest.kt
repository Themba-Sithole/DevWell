package za.co.devwell.data.manager

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.model.JoinRequestStatus

class ManagerRowsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun joinRequestRowMapsWithPlaceholderIdentity() {
        val row = json.decodeFromString<JoinRequestRow>(
            """{"id":"r1","user_id":"a1b2c3d4-0000-0000-0000-000000000000","company_id":"c1","team_id":"t1","status":"approved","created_at":"2026-09-25T10:00:00+00:00"}"""
        )
        val request = row.toJoinRequest()
        assertEquals("r1", request.id)
        assertEquals("t1", request.teamId)
        assertEquals(JoinRequestStatus.APPROVED, request.status)
        assertEquals("Engineer a1b2c3d4", request.userName)
        assertEquals("", request.userEmail)
    }

    @Test fun joinRequestRowUsesNameWhenGiven() {
        val row = JoinRequestRow("r1", "a1b2c3d4-0000", "c1", "t1", status = "pending")
        assertEquals("Thandi Nkosi", row.toJoinRequest("Thandi Nkosi").userName)
        assertEquals("Engineer a1b2c3d4", row.toJoinRequest("  ").userName)
        assertEquals("Engineer a1b2c3d4", row.toJoinRequest(null).userName)
    }

    @Test fun pendingRequesterRowDecodesNameOnly() {
        val row = json.decodeFromString<PendingRequesterRow>(
            """{"request_id":"r1","user_id":"u1","name":"Thandi Nkosi","email":"ignored@example.com"}"""
        )
        assertEquals(PendingRequesterRow("r1", "u1", "Thandi Nkosi"), row)
    }

    @Test fun rosterMemberRowDecodesNameOnly() {
        val row = json.decodeFromString<RosterMemberRow>("""{"user_id":"u1","name":"Sam Peters"}""")
        assertEquals(RosterMemberRow("u1", "Sam Peters"), row)
        assertNull(json.decodeFromString<RosterMemberRow>("""{"user_id":"u2"}""").name)
    }

    @Test fun createdAtIsOptional() {
        val row = json.decodeFromString<JoinRequestRow>(
            """{"id":"r1","user_id":"u1","company_id":"c1","team_id":"t1","status":"pending"}"""
        )
        assertNull(row.createdAt)
        assertEquals(JoinRequestStatus.PENDING, row.toJoinRequest().status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownStatusFailsClosed() {
        JoinRequestRow("r1", "u1", "c1", "t1", status = "maybe").toJoinRequest()
    }

    @Test fun teamRowKeepsDefaultsForMissingColumns() {
        val team = json.decodeFromString<ManagerTeamRow>(
            """{"id":"t1","company_id":"c1","name":"Mobile"}"""
        ).toTeam()
        assertNull(team.managerId)
        assertEquals(0, team.memberCount)
        assertEquals("Mobile", team.name)
        assertEquals("Africa/Johannesburg", team.timezone)
        assertEquals("Daily", team.checkInCadence)
        assertEquals(2.0, team.lowWellbeingThreshold)
        val managed = ManagerTeamRow("t1", "c1", "Mobile", managerId = "m1").toTeam()
        assertEquals("m1", managed.managerId)
    }

    @Test fun teamRowUsesSettingsColumnsWhenPresent() {
        val team = json.decodeFromString<ManagerTeamRow>(
            """{"id":"t1","company_id":"c1","name":"Mobile","manager_id":"m1","timezone":"Europe/London","check_in_cadence":"Weekly","low_wellbeing_threshold":3.5}"""
        ).toTeam()
        assertEquals("Europe/London", team.timezone)
        assertEquals("Weekly", team.checkInCadence)
        assertEquals(3.5, team.lowWellbeingThreshold)
        assertEquals(0, team.memberCount)
    }
}
