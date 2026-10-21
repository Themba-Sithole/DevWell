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
import za.co.devwell.data.offline.CheckInQueuedException

/** Member 2 — tracks the outcome of saving a check-in (Phase 4). */
sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    object Success : SaveState()

    object Queued : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * In-session AI conversation. Not persisted yet: it lives only for as long as
 * the process/session holds this ViewModel, and is cleared by "New chat".
 */
data class AiChatState(
    val messages: List<AiMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

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
    val notificationSyncStatus: StateFlow<String?> = AppContainer.notifications.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val adminStats: StateFlow<Map<String, Int>> = AppContainer.admin.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val adminUsers: StateFlow<List<DevWellUser>> = AppContainer.admin.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val adminLoading: StateFlow<Boolean> = AppContainer.admin.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val adminBusyUserId: StateFlow<String?> = AppContainer.admin.busyUserId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val adminErrorMessage: StateFlow<String?> = AppContainer.admin.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshAdminDashboard() = viewModelScope.launch {
        AppContainer.admin.refresh()
    }

    fun clearAdminError() = AppContainer.admin.clearError()

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
        } catch (e: CheckInQueuedException) {
            _checkInSaveState.value = SaveState.Queued
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

    private val _aiChat = MutableStateFlow(AiChatState())
    /** Chat transcript for AiAssistantScreen. In-memory for the current session only. */
    val aiChat: StateFlow<AiChatState> = _aiChat.asStateFlow()

    /** Appends the user's question, asks the assistant with the full context, then appends the reply. */
    fun sendAiMessage(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _aiChat.value.sending) return
        val history = _aiChat.value.messages +
            AiMessage(id = java.util.UUID.randomUUID().toString(), fromUser = true, text = question)
        _aiChat.value = _aiChat.value.copy(messages = history, sending = true, error = null)
        askAiInternal(history)
    }

    /** Re-sends the current transcript after a failure (the last turn is the user's question). */
    fun retryAi() {
        val state = _aiChat.value
        if (state.sending || state.messages.lastOrNull()?.fromUser != true) return
        _aiChat.value = state.copy(sending = true, error = null)
        askAiInternal(state.messages)
    }

    private fun askAiInternal(history: List<AiMessage>) = viewModelScope.launch {
        val result = AppContainer.ai.ask(history)
        _aiChat.value = result.fold(
            onSuccess = { answer ->
                _aiChat.value.copy(
                    messages = history +
                        AiMessage(id = java.util.UUID.randomUUID().toString(), fromUser = false, text = answer),
                    sending = false,
                    error = null,
                )
            },
            onFailure = { failure ->
                _aiChat.value.copy(
                    sending = false,
                    error = failure.message ?: "The assistant is unavailable. Please try again.",
                )
            },
        )
    }

    /** Starts a fresh conversation. Previous messages are discarded (not persisted). */
    fun clearAiChat() {
        _aiChat.value = AiChatState()
    }

    fun updateTeamSettings(team: Team) = viewModelScope.launch {
        AppContainer.manager.updateTeamSettings(team)
    }

    fun submitJoinRequest(companyId: String, teamId: String) = viewModelScope.launch {
        AppContainer.onboarding.submitEngineerJoinRequest(companyId, teamId)
    }
}
