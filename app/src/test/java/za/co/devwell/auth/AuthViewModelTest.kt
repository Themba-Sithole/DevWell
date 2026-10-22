package za.co.devwell.auth

import androidx.lifecycle.ViewModelStore
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import za.co.devwell.data.AuthRepository
import za.co.devwell.data.ManagerRepository
import za.co.devwell.data.OnboardingRepository
import za.co.devwell.model.*
import za.co.devwell.viewmodel.AuthViewModel
import za.co.devwell.viewmodel.friendlyError

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun model(fake: FakeIdentity, manager: FakeManager = FakeManager()) =
        AuthViewModel(fake, fake, manager).also { store.put("auth", it) }

    @Test fun invalidFormDoesNotContactBackend() = runTest(dispatcher) {
        val fake = FakeIdentity(); val vm = model(fake); runCurrent()
        vm.signIn("invalid", "password"); runCurrent()
        assertEquals(0, fake.signIns)
        assertNotNull(vm.ui.value.error)
    }
    @Test fun failedLoginShowsErrorAndStopsLoading() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.fail = true
        val vm = model(fake); runCurrent()
        vm.signIn("user@example.com", "password"); runCurrent()
        assertFalse(vm.ui.value.busy)
        assertNull(vm.ui.value.user)
        assertNotNull(vm.ui.value.error)
    }
    @Test fun repeatedTapMakesOnlyOneRequest() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.waitForLogin = CompletableDeferred()
        val vm = model(fake); runCurrent()
        vm.signIn("user@example.com", "password")
        vm.signIn("user@example.com", "password")
        runCurrent()
        assertEquals(1, fake.signIns)
        fake.waitForLogin!!.complete(Unit); runCurrent()
        assertFalse(vm.ui.value.busy)
    }
    @Test fun failedJoinRequestDoesNotAdvanceToPending() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.currentUser.value = fake.newEngineer
        val vm = model(fake); runCurrent(); fake.fail = true
        vm.submitJoinRequest("c1", "t1"); runCurrent()
        assertEquals(EntryDestination.ONBOARDING, entryDestination(vm.ui.value.user))
        assertNotNull(vm.ui.value.error)
    }
    @Test fun successfulJoinAndApprovalRefreshUpdateRouting() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.currentUser.value = fake.newEngineer
        val vm = model(fake); runCurrent()
        vm.submitJoinRequest("c1", "t1"); runCurrent()
        assertEquals(EntryDestination.PENDING, entryDestination(vm.ui.value.user))
        fake.approved = true; vm.refreshProfile(); runCurrent()
        assertEquals(EntryDestination.ENGINEER, entryDestination(vm.ui.value.user))
        vm.signOut(); runCurrent()
        assertEquals(EntryDestination.LOGIN, entryDestination(vm.ui.value.user))
    }
    @Test fun expiredSessionClosesProtectedScreen() = runTest(dispatcher) {
        val fake = FakeIdentity(); val vm = model(fake); runCurrent()
        vm.signIn("user@example.com", "password"); runCurrent()
        assertNotNull(vm.ui.value.user)
        fake.currentUser.value = null; runCurrent()
        assertEquals(EntryDestination.LOGIN, entryDestination(vm.ui.value.user))
    }
    @Test fun registrationSendsTheRequestedRole() = runTest(dispatcher) {
        val fake = FakeIdentity(); val vm = model(fake); runCurrent()
        vm.signUp("Student", "user@example.com", "password123", "password123", SignupRole.MANAGER); runCurrent()
        assertEquals(SignupRole.MANAGER, fake.requestedRole)

        vm.signUp("Student", "user@example.com", "password123", "password123", SignupRole.ENGINEER); runCurrent()
        assertEquals(SignupRole.ENGINEER, fake.requestedRole)
    }
    @Test fun invalidRegistrationNeverReachesTheBackend() = runTest(dispatcher) {
        val fake = FakeIdentity(); val vm = model(fake); runCurrent()
        vm.signUp("Student", "user@example.com", "short", "short", SignupRole.MANAGER); runCurrent()
        assertNull(fake.requestedRole)
        assertNotNull(vm.ui.value.error)
    }
    @Test fun emailFailuresExplainWhatTheEmailServiceRejected() {
        assertEquals(
            "The email service has hit its hourly limit. Wait about an hour and try again.",
            friendlyError(AuthRestException("over_email_send_rate_limit", "email rate limit exceeded", 429)),
        )
        assertTrue(
            "email setup" in friendlyError(
                AuthRestException("email_address_not_authorized", "Email address not authorized.", 400),
            ),
        )
        assertTrue(
            "cannot receive mail" in friendlyError(
                AuthRestException("email_address_invalid", "Email address is invalid", 400),
            ),
        )
    }
    @Test fun registeringAnExistingEmailAsksThePersonToSignIn() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.duplicateEmail = true
        val vm = model(fake); runCurrent()
        vm.signUp("Student", "user@example.com", "password123", "password123", SignupRole.ENGINEER); runCurrent()
        assertEquals(EMAIL_ALREADY_REGISTERED, vm.ui.value.error)
        assertNull(vm.ui.value.message)
    }
    @Test fun managerCreatesCompanyAndReachesTheManagerDashboard() = runTest(dispatcher) {
        val fake = FakeIdentity()
        fake.currentUser.value = DevWellUser("u2", "Boss", "boss@example.com", UserRole.MANAGER,
            status = AccountStatus.PENDING, onboardingDone = false)
        val manager = FakeManager()
        val vm = model(fake, manager); runCurrent()
        assertEquals(EntryDestination.MANAGER_ONBOARDING, entryDestination(vm.ui.value.user))

        vm.createManagerCompany("Acme Software", "Platform"); runCurrent()

        assertEquals("Acme Software" to "Platform", fake.createdCompany)
        assertEquals(EntryDestination.MANAGER, entryDestination(vm.ui.value.user))
        // The manager screens must pick up the team that was just created.
        assertEquals(1, manager.refreshes)
    }
    @Test fun invalidCompanyNamesAreRejectedBeforeTheBackend() = runTest(dispatcher) {
        val fake = FakeIdentity()
        fake.currentUser.value = DevWellUser("u2", "Boss", "boss@example.com", UserRole.MANAGER,
            status = AccountStatus.PENDING, onboardingDone = false)
        val vm = model(fake); runCurrent()

        vm.createManagerCompany("  ", "Platform"); runCurrent()

        assertNull(fake.createdCompany)
        assertNotNull(vm.ui.value.error)
    }
    @Test fun failedCompanyCreationKeepsTheManagerOnTheOnboardingScreen() = runTest(dispatcher) {
        val fake = FakeIdentity(); fake.fail = true
        fake.currentUser.value = DevWellUser("u2", "Boss", "boss@example.com", UserRole.MANAGER,
            status = AccountStatus.PENDING, onboardingDone = false)
        val manager = FakeManager()
        val vm = model(fake, manager); runCurrent()

        vm.createManagerCompany("Acme Software", "Platform"); runCurrent()

        assertEquals(EntryDestination.MANAGER_ONBOARDING, entryDestination(vm.ui.value.user))
        assertNotNull(vm.ui.value.error)
        assertEquals(0, manager.refreshes)
    }

    private class FakeIdentity : AuthRepository, OnboardingRepository {
        val newEngineer = DevWellUser("u1", "Student", "user@example.com", UserRole.ENGINEER,
            status = AccountStatus.PENDING, onboardingDone = false)
        override val currentUser = MutableStateFlow<DevWellUser?>(null)
        override val companies = MutableStateFlow<List<Company>>(emptyList())
        override val teams = MutableStateFlow<List<Team>>(emptyList())
        var signIns = 0
        var fail = false
        var approved = false
        var duplicateEmail = false
        var requestedRole: SignupRole? = null
        var createdCompany: Pair<String, String>? = null
        var waitForLogin: CompletableDeferred<Unit>? = null
        override suspend fun signIn(email: String, password: String): Result<DevWellUser> {
            signIns++
            waitForLogin?.await()
            if (fail) return Result.failure(IllegalStateException("Login failed"))
            currentUser.value = newEngineer
            return Result.success(newEngineer)
        }
        override suspend fun signUp(name: String, email: String, password: String, role: SignupRole): String {
            requestedRole = role
            if (duplicateEmail) {
                // AuthRestException takes the error code first and the server message second.
                throw AuthRestException("user_already_exists", "User already registered", 422)
            }
            return "Account created."
        }
        override suspend fun signOut() { currentUser.value = null }
        override suspend fun refreshProfile() {
            if (approved) currentUser.value = currentUser.value?.copy(status = AccountStatus.ACTIVE)
        }
        override suspend fun submitEngineerJoinRequest(companyId: String, teamId: String) {
            if (fail) error("Request failed")
            currentUser.value = currentUser.value?.copy(companyId = companyId, teamId = teamId, onboardingDone = true)
        }
        override suspend fun createManagerCompany(companyName: String, teamName: String) {
            if (fail) error("Company failed")
            createdCompany = companyName to teamName
            currentUser.value = currentUser.value?.copy(
                role = UserRole.MANAGER, companyId = "c1", teamId = "t1",
                status = AccountStatus.ACTIVE, onboardingDone = true,
            )
        }
    }

    /** Counts the reloads the view model asks for after the company is created. */
    private class FakeManager : ManagerRepository {
        override val joinRequests = MutableStateFlow<List<JoinRequest>>(emptyList())
        override val managedTeams = MutableStateFlow<List<Team>>(emptyList())
        override val managedTeam = MutableStateFlow<Team?>(null)
        var refreshes = 0
        override suspend fun reviewJoinRequest(requestId: String, approve: Boolean) = Unit
        override suspend fun updateTeamSettings(team: Team) = Unit
        override suspend fun selectTeam(teamId: String) = Unit
        override suspend fun createTeam(name: String) = Unit
        override suspend fun refresh() { refreshes++ }
    }
}
