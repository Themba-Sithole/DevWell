package za.co.devwell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.co.devwell.data.ManagerRepository
import za.co.devwell.model.JoinRequest
import za.co.devwell.model.Team

data class ManagerUiState(
    val joinRequests: List<JoinRequest> = emptyList(),
    val team: Team? = null,
    val reviewingId: String? = null,      // request currently being approved/declined
    val savingSettings: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Client-side rules for team settings. The update_team_settings RPC enforces the same rules;
 * checking here first gives the manager a clear message without a server round trip.
 */
object TeamSettingsRules {
    const val NAME_MAX_LENGTH = 60
    val CADENCES = listOf("Daily", "Weekly")
    val THRESHOLD_RANGE = 1.0..4.0   // wellbeing is scored 1..5

    /** Returns the first problem as a user-facing message, or null when the team is valid. */
    fun validate(team: Team, existingName: String? = null): String? {
        val name = team.name.trim()
        val threshold = team.lowWellbeingThreshold
        return when {
            name.isEmpty() -> "Team name cannot be empty."
            name.length > NAME_MAX_LENGTH && name != existingName?.trim() -> "Team name must be $NAME_MAX_LENGTH characters or fewer."
            team.checkInCadence !in CADENCES -> "Choose a check-in cadence: ${CADENCES.joinToString(" or ")}."
            threshold == null || threshold !in THRESHOLD_RANGE ->
                "Low-wellbeing threshold must be between 1.0 and 4.0."
            else -> null
        }
    }
}

/** Join-request review and team settings for managers. Aggregates stay in DevWellViewModel for now. */
class ManagerViewModel(private val repository: ManagerRepository) : ViewModel() {
    private val state = MutableStateFlow(ManagerUiState())
    val ui: StateFlow<ManagerUiState> = state.asStateFlow()

    init {
        observe(repository.joinRequests) { current, requests -> current.copy(joinRequests = requests) }
        observe(repository.managedTeam) { current, team -> current.copy(team = team) }
    }

    private fun <T> observe(flow: Flow<T>, apply: (ManagerUiState, T) -> ManagerUiState) {
        viewModelScope.launch {
            try {
                flow.collect { value -> state.update { apply(it, value) } }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(error = friendlyError(error)) }
            }
        }
    }

    fun clearFeedback() = state.update { it.copy(error = null, message = null) }


    fun reviewJoinRequest(requestId: String, approve: Boolean) {
        if (state.value.reviewingId != null) return   // ignore repeated taps
        state.update { it.copy(reviewingId = requestId, error = null, message = null) }
        viewModelScope.launch {
            try {
                repository.reviewJoinRequest(requestId, approve)
                state.update { it.copy(message = if (approve) "Request approved." else "Request declined.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(error = friendlyError(error)) }
            } finally {
                state.update { it.copy(reviewingId = null) }
            }
        }
    }

    fun updateTeamSettings(team: Team) {
        if (state.value.savingSettings) return
        val trimmed = team.copy(name = team.name.trim(), timezone = team.timezone?.trim())
        TeamSettingsRules.validate(trimmed, state.value.team?.name)?.let { problem ->
            state.update { it.copy(error = problem, message = null) }
            return
        }
        state.update { it.copy(savingSettings = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                repository.updateTeamSettings(trimmed)
                state.update { it.copy(message = "Team settings saved.") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(error = friendlyError(error)) }
            } finally {
                state.update { it.copy(savingSettings = false) }
            }
        }
    }
}

class ManagerViewModelFactory(private val repository: ManagerRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ManagerViewModel::class.java))
        return ManagerViewModel(repository) as T
    }
}