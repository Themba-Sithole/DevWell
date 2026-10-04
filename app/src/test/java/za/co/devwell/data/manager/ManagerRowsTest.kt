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
        assertEquals("Email not available yet", request.userEmail)
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
        val managed = ManagerTeamRow("t1", "c1", "Mobile", managerId = "m1").toTeam()
        assertEquals("m1", managed.managerId)
    }
}
