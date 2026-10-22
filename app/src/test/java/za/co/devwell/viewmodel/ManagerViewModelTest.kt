package za.co.devwell.viewmodel

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
import za.co.devwell.data.ManagerRepository
import za.co.devwell.model.JoinRequest
import za.co.devwell.model.JoinRequestStatus
import za.co.devwell.model.Team

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    private fun model(fake: FakeManager) = ManagerViewModel(fake).also { store.put("manager", it) }

    @Test fun approvingUpdatesStatusAndShowsMessage() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.reviewJoinRequest("jr1", true); runCurrent()

        assertEquals(1, fake.reviewCalls)
        assertEquals(JoinRequestStatus.APPROVED, vm.ui.value.joinRequests.first { it.id == "jr1" }.status)
        assertEquals("Request approved.", vm.ui.value.message)
        assertNull(vm.ui.value.reviewingId)
    }

    @Test fun decliningShowsDeclineMessage() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.reviewJoinRequest("jr1", false); runCurrent()

        assertEquals(JoinRequestStatus.REJECTED, vm.ui.value.joinRequests.first { it.id == "jr1" }.status)
        assertEquals("Request declined.", vm.ui.value.message)
    }

    @Test fun failedReviewShowsErrorAndClearsBusy() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()

        vm.reviewJoinRequest("jr1", true); runCurrent()

        assertNotNull(vm.ui.value.error)
        assertNull(vm.ui.value.message)
        assertNull(vm.ui.value.reviewingId)
        // status is unchanged because the repository call failed
        assertEquals(JoinRequestStatus.PENDING, vm.ui.value.joinRequests.first { it.id == "jr1" }.status)
    }

    @Test fun repeatedTapMakesOnlyOneReviewCall() = runTest(dispatcher) {
        val fake = FakeManager(); fake.waitForReview = CompletableDeferred()
        val vm = model(fake); runCurrent()

        vm.reviewJoinRequest("jr1", true)
        vm.reviewJoinRequest("jr1", true)
        runCurrent()

        assertEquals(1, fake.reviewCalls)
        assertEquals("jr1", vm.ui.value.reviewingId)
        fake.waitForReview!!.complete(Unit); runCurrent()
        assertNull(vm.ui.value.reviewingId)
    }

    @Test fun blankTeamNameIsRejectedWithoutCallingRepository() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(fake.team.value!!.copy(name = "   ")); runCurrent()

        assertEquals(0, fake.updateCalls)
        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.savingSettings)
    }

    @Test fun savingTeamSettingsTrimsNameAndShowsMessage() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(fake.team.value!!.copy(name = "  Renamed  ")); runCurrent()

        assertEquals(1, fake.updateCalls)
        assertEquals("Renamed", fake.team.value?.name)
        assertEquals("Team settings saved.", vm.ui.value.message)
    }

    @Test fun savingTeamSettingsTrimsTimezone() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(fake.team.value!!.copy(timezone = "  Europe/London  ")); runCurrent()

        assertEquals(1, fake.updateCalls)
        assertEquals("Europe/London", fake.team.value?.timezone)
    }

    @Test fun failedSaveShowsErrorAndClearsSaving() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(fake.team.value!!.copy(name = "Renamed")); runCurrent()

        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.savingSettings)
    }

    // --- TeamSettingsRules: pure validation, no coroutines needed ---

    private val validTeam = Team("t1", "c1", "Mobile Team", checkInCadence = "Daily", lowWellbeingThreshold = 2.0)
    private val tooLong = "Team name must be 60 characters or fewer."
    private val badCadence = "Choose a check-in cadence: Daily or Weekly."
    private val badThreshold = "Low-wellbeing threshold must be between 1.0 and 4.0."

    @Test fun validTeamPassesRules() {
        assertNull(TeamSettingsRules.validate(validTeam))
        assertNull(TeamSettingsRules.validate(validTeam.copy(checkInCadence = "Weekly")))
    }

    @Test fun blankNameFailsRules() {
        assertEquals("Team name cannot be empty.", TeamSettingsRules.validate(validTeam.copy(name = "   ")))
    }

    @Test fun nameLengthLimitIsSixtyAfterTrimming() {
        assertNull(TeamSettingsRules.validate(validTeam.copy(name = "A".repeat(60))))
        assertNull(TeamSettingsRules.validate(validTeam.copy(name = "  " + "A".repeat(60) + "  ")))
        assertEquals(tooLong, TeamSettingsRules.validate(validTeam.copy(name = "A".repeat(61))))
    }

    @Test fun cadenceMustBeAnAllowedOption() {
        assertEquals(badCadence, TeamSettingsRules.validate(validTeam.copy(checkInCadence = "Hourly")))
        assertEquals(badCadence, TeamSettingsRules.validate(validTeam.copy(checkInCadence = null)))
        assertEquals(badCadence, TeamSettingsRules.validate(validTeam.copy(checkInCadence = "daily")))
    }

    @Test fun thresholdBoundsAreInclusive() {
        assertNull(TeamSettingsRules.validate(validTeam.copy(lowWellbeingThreshold = 1.0)))
        assertNull(TeamSettingsRules.validate(validTeam.copy(lowWellbeingThreshold = 4.0)))
    }

    @Test fun thresholdOutsideRangeOrMissingFails() {
        listOf(0.5, 4.5, null, Double.NaN).forEach { value ->
            assertEquals("threshold=$value", badThreshold, TeamSettingsRules.validate(validTeam.copy(lowWellbeingThreshold = value)))
        }
    }

    // --- ManagerViewModel: invalid settings never reach the repository ---

    private fun TestScope.assertRejected(update: (Team) -> Team, expected: String) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(update(fake.team.value!!)); runCurrent()

        assertEquals(expected, vm.ui.value.error)
        assertEquals(0, fake.updateCalls)
        assertFalse(vm.ui.value.savingSettings)
    }

    @Test fun tooLongNameIsRejected() = runTest(dispatcher) {
        assertRejected({ it.copy(name = "A".repeat(61)) }, tooLong)
    }

    @Test fun unknownCadenceIsRejected() = runTest(dispatcher) {
        assertRejected({ it.copy(checkInCadence = "Hourly") }, badCadence)
    }

    @Test fun outOfRangeThresholdIsRejected() = runTest(dispatcher) {
        assertRejected({ it.copy(lowWellbeingThreshold = 5.0) }, badThreshold)
    }

    @Test fun validSaveStoresCadenceAndThreshold() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.updateTeamSettings(fake.team.value!!.copy(checkInCadence = "Weekly", lowWellbeingThreshold = 3.5)); runCurrent()

        assertEquals(1, fake.updateCalls)
        assertEquals("Weekly", fake.team.value?.checkInCadence)
        assertEquals(3.5, fake.team.value?.lowWellbeingThreshold!!, 0.0)
        assertEquals("Team settings saved.", vm.ui.value.message)
    }

    @Test fun clearFeedbackRemovesErrorAndMessage() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()
        vm.reviewJoinRequest("jr1", true); runCurrent()
        assertNotNull(vm.ui.value.error)

        vm.clearFeedback()

        assertNull(vm.ui.value.error)
        assertNull(vm.ui.value.message)
    }

    @Test fun managerCanSwitchBetweenTheTeamsTheyOwn() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()
        assertEquals(2, vm.ui.value.teams.size)

        vm.selectTeam("t2"); runCurrent()

        assertEquals("t2", fake.selectedTeamId)
    }

    @Test fun creatingATeamPassesTheTrimmedNameAndConfirms() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.createTeam("  Web Team  "); runCurrent()

        assertEquals(1, fake.createCalls)
        assertEquals("Web Team", fake.createdTeamName)
        assertEquals("Team created.", vm.ui.value.message)
        assertFalse(vm.ui.value.creatingTeam)
    }

    @Test fun anEmptyTeamNameIsRejectedBeforeTheBackend() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.createTeam("   "); runCurrent()

        assertEquals(0, fake.createCalls)
        assertNotNull(vm.ui.value.error)
    }

    @Test fun aFailedTeamCreationShowsTheError() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()

        vm.createTeam("Web Team"); runCurrent()

        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.creatingTeam)
    }

    @Test fun removingAMemberConfirmsAndClearsTheRoster() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.removeMember("u1"); runCurrent()

        assertEquals("t1", fake.removeTeamId)
        assertEquals("u1", fake.removedUserId)
        assertEquals("Member removed. They must choose a company and team again.", vm.ui.value.message)
        assertNull(vm.ui.value.removingMemberId)
    }

    @Test fun aFailedRemovalShowsTheError() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()

        vm.removeMember("u1"); runCurrent()

        assertNotNull(vm.ui.value.error)
        assertNull(vm.ui.value.removingMemberId)
    }

    @Test fun deletingATeamConfirmsAndMovesToTheRemainingTeam() = runTest(dispatcher) {
        val fake = FakeManager()
        val vm = model(fake); runCurrent()

        vm.deleteTeam(); runCurrent()

        assertEquals("t1", fake.deletedTeamId)
        assertEquals("t2", vm.ui.value.team?.id)
        assertEquals("Team deleted. Its members must choose a company and team again.", vm.ui.value.message)
        assertFalse(vm.ui.value.deletingTeam)
    }

    @Test fun aFailedDeletionShowsTheError() = runTest(dispatcher) {
        val fake = FakeManager(); fake.fail = true
        val vm = model(fake); runCurrent()

        vm.deleteTeam(); runCurrent()

        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.deletingTeam)
        assertEquals("t1", vm.ui.value.team?.id)
    }

    private class FakeManager : ManagerRepository {
        override val joinRequests = MutableStateFlow(
            listOf(
                JoinRequest("jr1", "u_j1", "Jamie Clarke", "jamie@devwell.io", "c1", "t1"),
                JoinRequest("jr2", "u_j2", "Riya Patel", "riya@devwell.io", "c1", "t2"),
            )
        )
        val team = MutableStateFlow<Team?>(Team("t1", "c1", "Mobile Team", managerId = "u2", memberCount = 8))
        override val managedTeam = team
        override val managedTeams = MutableStateFlow(
            listOf(
                Team("t1", "c1", "Mobile Team", managerId = "u2", memberCount = 8),
                Team("t2", "c1", "Web Team", managerId = "u2"),
            )
        )

        var fail = false
        var reviewCalls = 0
        var updateCalls = 0
        var createCalls = 0
        var selectedTeamId: String? = null
        var createdTeamName: String? = null
        var removeTeamId: String? = null
        var removedUserId: String? = null
        var deletedTeamId: String? = null
        var waitForReview: CompletableDeferred<Unit>? = null

        override suspend fun reviewJoinRequest(requestId: String, approve: Boolean) {
            reviewCalls++
            waitForReview?.await()
            if (fail) error("Review failed")
            joinRequests.value = joinRequests.value.map {
                if (it.id != requestId) it
                else it.copy(status = if (approve) JoinRequestStatus.APPROVED else JoinRequestStatus.REJECTED)
            }
        }

        override suspend fun updateTeamSettings(team: Team) {
            updateCalls++
            if (fail) error("Save failed")
            this.team.value = team
        }

        override suspend fun selectTeam(teamId: String) {
            selectedTeamId = teamId
        }

        override suspend fun createTeam(name: String) {
            createCalls++
            if (fail) error("Create failed")
            createdTeamName = name
        }

        override suspend fun removeMember(teamId: String, userId: String) {
            removeTeamId = teamId
            if (fail) error("Remove failed")
            removedUserId = userId
            team.value = team.value?.copy(roster = team.value?.roster?.filterNot { it.userId == userId })
        }

        override suspend fun deleteTeam(teamId: String) {
            if (fail) error("Delete failed")
            deletedTeamId = teamId
            val remaining = managedTeams.value.filterNot { it.id == teamId }
            managedTeams.value = remaining
            team.value = remaining.firstOrNull()
        }

        override suspend fun refresh() = Unit
    }
}