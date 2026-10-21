package za.co.devwell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.co.devwell.data.AppContainer
import za.co.devwell.data.AuthRepository
import za.co.devwell.data.OnboardingRepository
import za.co.devwell.model.*

data class AuthUiState(
    val user: DevWellUser? = null,
    val starting: Boolean = true,
    val busy: Boolean = false,
    val directoryLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val companies: List<Company> = emptyList(),
    val teams: List<Team> = emptyList(),
)

class AuthViewModel(
    private val auth: AuthRepository = AppContainer.auth,
    private val onboarding: OnboardingRepository = AppContainer.onboarding,
) : ViewModel() {
    private val state = MutableStateFlow(AuthUiState())
    val ui = state.asStateFlow()
    private var observer: Job? = null
    private var directoryJob: Job? = null

    init {
        observeRepositories()
        action {
            try { auth.restoreSession() }
            finally { state.update { it.copy(starting = false) } }
        }
    }

    private fun observeRepositories() {
        observer?.cancel()
        observer = viewModelScope.launch {
            coroutineScope {
                launch { auth.currentUser.collect { user -> state.update { it.copy(user = user) } } }
                launch { onboarding.companies.collect { values -> state.update { it.copy(companies = values) } } }
                launch { onboarding.teams.collect { values -> state.update { it.copy(teams = values) } } }
            }
        }
    }

    fun clearFeedback() { state.update { it.copy(error = null, message = null) } }

    fun signIn(email: String, password: String) {
        val error = loginValidation(email, password)
        if (error != null) { state.update { it.copy(error = error, message = null) }; return }
        action { auth.signIn(email.trim(), password).getOrThrow() }
    }

    fun signUp(name: String, email: String, password: String, confirmation: String) {
        val error = registrationValidation(name, email, password, confirmation)
        if (error != null) { state.update { it.copy(error = error, message = null) }; return }
        action {
            val message = auth.signUp(name.trim(), email.trim(), password)
            state.update { it.copy(message = message) }
        }
    }

    fun refreshProfile() = action { auth.refreshProfile() }

    /**
     * Saves the two editable profile fields in one action. The email change is
     * requested first so a rejected or duplicate address reports before the
     * display name is written.
     */
    fun updateProfileDetails(name: String, email: String) {
        val error = nameValidation(name) ?: emailValidation(email)
        if (error != null) {
            state.update { it.copy(error = error, message = null) }
            return
        }
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val nameChanged = trimmedName != state.value.user?.name
        val emailChanged = trimmedEmail != state.value.user?.email
        if (!nameChanged && !emailChanged) {
            state.update { it.copy(error = null, message = "Your details are already up to date.") }
            return
        }
        action {
            val messages = mutableListOf<String>()
            if (emailChanged) messages += auth.updateEmail(trimmedEmail)
            if (nameChanged) messages += auth.updateName(trimmedName)
            state.update { it.copy(message = messages.joinToString(" ")) }
        }
    }

    fun updatePassword(password: String, confirmation: String) {
        val error = passwordChangeValidation(password, confirmation)
        if (error != null) {
            state.update { it.copy(error = error, message = null) }
            return
        }
        action {
            val message = auth.updatePassword(password)
            state.update { it.copy(message = message) }
        }
    }

    fun refreshDirectory() {
        if (directoryJob?.isActive == true) return
        state.update { it.copy(directoryLoading = true, error = null) }
        directoryJob = viewModelScope.launch {
            try { onboarding.refreshDirectory() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { state.update { it.copy(error = friendlyError(error)) } }
            finally { state.update { it.copy(directoryLoading = false) } }
        }
    }

    fun submitJoinRequest(companyId: String, teamId: String) = action {
        onboarding.submitEngineerJoinRequest(companyId, teamId)
    }

    fun signOut() = action {
        directoryJob?.cancel()
        try { auth.signOut() }
        finally {
            state.update { AuthUiState(starting = false, busy = true) }
            observeRepositories()
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { state.update { it.copy(error = friendlyError(error)) } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}

internal fun friendlyError(error: Exception): String = when (error) {
    is RestException -> when (error.error) {
        "invalid_credentials" -> "The email or password is incorrect. Please try again."
        "email_not_confirmed" -> "Confirm your email before signing in."
        "user_already_exists", "email_exists" -> "An account may already exist. Try signing in."
        "weak_password" -> "Choose a stronger password with at least 8 characters."
        "over_email_send_rate_limit", "over_request_rate_limit" -> "Too many attempts. Wait a moment and try again."
        "PGRST205", "42P01", "PGRST204", "42703" -> "The team is still setting up the database. Please try again when it is ready."
        "42501" -> "Your account does not have permission for this action. Contact the team."
        else -> "The request could not be completed. Check your details or try again later."
    }
    is IllegalStateException -> error.message ?: "Your account is not ready. Please retry."
    else -> "Unable to connect. Check your internet connection and try again."
}
