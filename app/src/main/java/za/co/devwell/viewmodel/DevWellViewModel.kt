package za.co.devwell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import za.co.devwell.data.AppContainer
import za.co.devwell.model.*

/**
 * Shared shell ViewModel for the skeleton.
 * As features grow, each member can introduce a feature-specific ViewModel
 * (e.g. CheckInViewModel, ProjectsViewModel) without changing AppContainer.
 */
class DevWellViewModel : ViewModel() {
    val user: StateFlow<DevWellUser?> = AppContainer.auth.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    val companies: StateFlow<List<Company>> = AppContainer.onboarding.companies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val teams: StateFlow<List<Team>> = AppContainer.onboarding.teams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loginAs(role: UserRole) = viewModelScope.launch { AppContainer.auth.demoLogin(role) }

    fun signOut() = viewModelScope.launch { AppContainer.auth.signOut() }

    fun saveCheckIn(draft: CheckInDraft) = viewModelScope.launch {
        AppContainer.wellbeing.saveCheckIn(draft)
    }

    fun toggleTask(projectId: String, taskId: String) = viewModelScope.launch {
        AppContainer.projects.toggleTask(projectId, taskId)
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
