package za.co.devwell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.co.devwell.data.AppContainer
import za.co.devwell.model.*

/** Member 2 — tracks the outcome of saving a check-in (Phase 4). */
sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Shared shell ViewModel for the skeleton.
 * As features grow, each member can introduce a feature-specific ViewModel
 * (e.g. CheckInViewModel, ProjectsViewModel) without changing AppContainer.
 */
class DevWellViewModel : ViewModel() {
    val checkIns: StateFlow<List<CheckIn>> = AppContainer.wellbeing.checkIns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<DevProject>> = AppContainer.projects.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val teamAggregate: StateFlow<TeamAggregate?> = AppContainer.teamInsights.teamAggregate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val joinRequests: StateFlow<List<JoinRequest>> = AppContainer.manager.joinRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val managedTeam: StateFlow<Team?> = AppContainer.manager.managedTeam
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notificationPrefs: StateFlow<NotificationPreferences> = AppContainer.notifications.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPreferences())

    val adminStats: StateFlow<Map<String, Int>> = AppContainer.admin.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val adminUsers: StateFlow<List<DevWellUser>> = AppContainer.admin.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setUserStatus(userId: String, active: Boolean) = viewModelScope.launch {
        AppContainer.admin.setUserStatus(userId, active)
    }

    val companies: StateFlow<List<Company>> = AppContainer.onboarding.companies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val teams: StateFlow<List<Team>> = AppContainer.onboarding.teams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _checkInSaveState = MutableStateFlow<SaveState>(SaveState.Idle)
    /** Drive CheckInScreen's isSaving / errorMessage params from this. */
    val checkInSaveState: StateFlow<SaveState> = _checkInSaveState.asStateFlow()

    fun saveCheckIn(draft: CheckInDraft) = viewModelScope.launch {
        _checkInSaveState.value = SaveState.Loading
        try {
            AppContainer.wellbeing.saveCheckIn(draft)
            _checkInSaveState.value = SaveState.Success
        } catch (e: Exception) {
            _checkInSaveState.value = SaveState.Error(
                e.message ?: "Couldn't save your check-in. Check your connection and try again."
            )
        }
    }

    /** Call after navigating away on Success, or before retrying after an Error. */
    fun resetCheckInSaveState() {
        _checkInSaveState.value = SaveState.Idle
    }

    fun toggleTask(projectId: String, taskId: String) = viewModelScope.launch {
        AppContainer.projects.toggleTask(projectId, taskId)
    }

    fun createProject(project: DevProject) = viewModelScope.launch {
        AppContainer.projects.createProject(project)
    }

    fun updateProject(project: DevProject) = viewModelScope.launch {
        AppContainer.projects.updateProject(project)
    }

    fun deleteProject(projectId: String) = viewModelScope.launch {
        AppContainer.projects.deleteProject(projectId)
    }

    fun addTask(projectId: String, task: ProjectTask) = viewModelScope.launch {
        AppContainer.projects.addTask(projectId, task)
    }

    fun updateTask(projectId: String, task: ProjectTask) = viewModelScope.launch {
        AppContainer.projects.updateTask(projectId, task)
    }

    fun deleteTask(projectId: String, taskId: String) = viewModelScope.launch {
        AppContainer.projects.deleteTask(projectId, taskId)
    }

    fun reviewJoinRequest(requestId: String, approve: Boolean) = viewModelScope.launch {
        AppContainer.manager.reviewJoinRequest(requestId, approve)
    }

    fun updateNotificationPrefs(prefs: NotificationPreferences) = viewModelScope.launch {
        AppContainer.notifications.updatePreferences(prefs)
    }

    fun askAi(question: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        onResult(AppContainer.ai.ask(question))
    }

    fun updateTeamSettings(team: Team) = viewModelScope.launch {
        AppContainer.manager.updateTeamSettings(team)
    }

    fun submitJoinRequest(companyId: String, teamId: String) = viewModelScope.launch {
        AppContainer.onboarding.submitEngineerJoinRequest(companyId, teamId)
    }
}