package za.co.devwell.auth

import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.model.*

class EntryDestinationTest {
    private fun user(role: UserRole = UserRole.ENGINEER) =
        DevWellUser("u1", "Student", "student@example.com", role, companyId = "c1", teamId = "t1")

    @Test fun signedOutShowsLogin() { assertEquals(EntryDestination.LOGIN, entryDestination(null)) }
    @Test fun activeUsersGoToTheirOwnRoleHome() {
        assertEquals(EntryDestination.ENGINEER, entryDestination(user()))
        assertEquals(EntryDestination.MANAGER, entryDestination(user(UserRole.MANAGER)))
        assertEquals(EntryDestination.ADMIN, entryDestination(user(UserRole.ADMIN)))
    }
    @Test fun pendingAndRejectedUsersNeverReachAnyDashboard() {
        UserRole.entries.forEach { role ->
            listOf(AccountStatus.PENDING, AccountStatus.REJECTED).forEach { status ->
                assertEquals(EntryDestination.PENDING, entryDestination(user(role).copy(status = status)))
            }
        }
    }
    @Test fun newEngineerCanChooseTeamBeforeApproval() {
        assertEquals(EntryDestination.ONBOARDING, entryDestination(user().copy(
            onboardingDone = false, status = AccountStatus.PENDING, companyId = null, teamId = null)))
    }
    @Test fun rejectedEngineerCannotResubmitByIncompleteOnboarding() {
        assertEquals(EntryDestination.PENDING, entryDestination(user().copy(
            onboardingDone = false, status = AccountStatus.REJECTED)))
    }
    @Test fun incompletePrivilegedAccountWaitsForProvisioning() {
        assertEquals(EntryDestination.PENDING, entryDestination(user(UserRole.MANAGER).copy(onboardingDone = false)))
        assertEquals(EntryDestination.PENDING, entryDestination(user(UserRole.ADMIN).copy(onboardingDone = false)))
    }
    @Test fun engineerWithoutMembershipCannotReachDashboard() {
        assertEquals(EntryDestination.ONBOARDING, entryDestination(user().copy(teamId = null)))
        assertEquals(EntryDestination.ONBOARDING, entryDestination(user().copy(companyId = "")))
    }
    @Test fun credentialsAreValidatedWithoutTrimmingPassword() {
        assertNotNull(loginValidation("", "abc"))
        assertNotNull(loginValidation("wrong", "abc"))
        assertNotNull(loginValidation("user@example.com", " "))
        assertNull(loginValidation(" user@example.com ", " a valid password "))
    }
    @Test fun registrationChecksNameLengthAndConfirmation() {
        assertNotNull(registrationValidation("A", "a@b.com", "password123", "password123"))
        assertNotNull(registrationValidation("Alex", "a@b.com", "short", "short"))
        assertNotNull(registrationValidation("Alex", "a@b.com", "password123", "different"))
        assertNull(registrationValidation("Alex", "a@b.com", "password123", "password123"))
    }
}
