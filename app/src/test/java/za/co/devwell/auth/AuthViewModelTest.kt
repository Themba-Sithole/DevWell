package za.co.devwell.auth

import androidx.lifecycle.ViewModelStore
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
import za.co.devwell.data.OnboardingRepository
import za.co.devwell.model.*
import za.co.devwell.viewmodel.AuthViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun model(fake: FakeIdentity, preview: Boolean = false) = AuthViewModel(fake, fake, preview).also { store.put("auth", it) }

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
    @Test fun previewIsDisabledInReleaseMode() = runTest(dispatcher) {
        val vm = model(FakeIdentity(), false); runCurrent()
        vm.startPreview(UserRole.ADMIN); runCurrent()
        assertNull(vm.ui.value.user)
        assertFalse(vm.ui.value.preview)
    }
    @Test fun expiredSessionClosesProtectedScreen() = runTest(dispatcher) {
        val fake = FakeIdentity(); val vm = model(fake); runCurrent()
        vm.signIn("user@example.com", "password"); runCurrent()
        assertNotNull(vm.ui.value.user)
        fake.currentUser.value = null; runCurrent()
        assertEquals(EntryDestination.LOGIN, entryDestination(vm.ui.value.user))
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
        var waitForLogin: CompletableDeferred<Unit>? = null
        override suspend fun signIn(email: String, password: String): Result<DevWellUser> {
            signIns++
            waitForLogin?.await()
            if (fail) return Result.failure(IllegalStateException("Login failed"))
            currentUser.value = newEngineer
            return Result.success(newEngineer)
        }
        override suspend fun demoLogin(role: UserRole) {}
        override suspend fun signOut() { currentUser.value = null }
        override suspend fun refreshProfile() {
            if (approved) currentUser.value = currentUser.value?.copy(status = AccountStatus.ACTIVE)
        }
        override suspend fun submitEngineerJoinRequest(companyId: String, teamId: String) {
            if (fail) error("Request failed")
            currentUser.value = currentUser.value?.copy(companyId = companyId, teamId = teamId, onboardingDone = true)
        }
        override suspend fun registerCompany(name: String, emailDomain: String?) = Company("c1", name)
    }
}
