package za.co.devwell.data.admin

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.UserRole

class AdminUserRowTest {
    @Test
    fun mapsActiveAndSuspendedRowsWithoutExtraProfileFields() {
        val active = AdminUserRow(
            id = "engineer-1",
            name = "Alex",
            email = "alex@example.com",
            role = "engineer",
            status = "active",
            teamId = "team-1",
            teamName = "Mobile",
            onboardingDone = true,
        ).toDomain()
        val suspended = AdminUserRow(
            id = "manager-1",
            name = "Jordan",
            email = "jordan@example.com",
            role = "manager",
            status = "suspended",
            onboardingDone = true,
        ).toDomain()

        assertEquals(UserRole.ENGINEER, active.role)
        assertEquals(AccountStatus.ACTIVE, active.status)
        assertEquals("Mobile", active.teamName)
        assertEquals(UserRole.MANAGER, suspended.role)
        assertEquals(AccountStatus.SUSPENDED, suspended.status)
    }
}
