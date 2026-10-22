package za.co.devwell.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.co.devwell.data.AppContainer
import za.co.devwell.data.AuthRepository
import za.co.devwell.data.ManagerRepository
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
    /** Increments after a rejected engineer asks to choose another team, resetting the wizard. */
    val onboardingRestart: Int = 0,
)

class AuthViewModel(
    private val auth: AuthRepository = AppContainer.auth,
    private val onboarding: OnboardingRepository = AppContainer.onboarding,
    private val manager: ManagerRepository = AppContainer.manager,
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

    fun signUp(name: String, email: String, password: String, confirmation: String, role: SignupRole) {
        val error = registrationValidation(name, email, password, confirmation)
        if (error != null) { state.update { it.copy(error = error, message = null) }; return }
        action {
            val message = auth.signUp(name.trim(), email.trim(), password, role)
            state.update { it.copy(message = message) }
        }
    }

    fun refreshProfile() = action {
        auth.refreshProfile()
        // The collector updates state.user asynchronously; read the repository's latest
        // value so the feedback matches the status that was just fetched.
        val user = auth.currentUser.first()
        // A pending/rejected engineer needs the directory to show the company and team
        // they chose. It normally loads on entering the waiting screen; retry it here.
        if (user != null && user.role == UserRole.ENGINEER &&
            user.status in setOf(AccountStatus.PENDING, AccountStatus.REJECTED)) {
            try { onboarding.refreshDirectory() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* The status check still succeeded; names can retry later. */ }
        }
        state.update {
            it.copy(message = when (user?.status) {
                AccountStatus.PENDING ->
                    "Still waiting for your manager to review your request. Check again later."
                AccountStatus.REJECTED ->
                    "Your request was not approved. You can choose a different company and team."
                AccountStatus.SUSPENDED -> "This account is still suspended."
                else -> null
            })
        }
    }

    /** A rejected engineer opts in to choosing another company and team. */
    fun restartOnboarding() = action {
        onboarding.restartTeamOnboarding()
        state.update { it.copy(onboardingRestart = it.onboardingRestart + 1) }
        try { onboarding.refreshDirectory() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* The wizard has its own refresh action. */ }
    }

    /** Saves the display name, the only editable profile field. */
    fun updateProfileName(name: String) {
        val error = nameValidation(name)
        if (error != null) {
            state.update { it.copy(error = error, message = null) }
            return
        }
        val trimmedName = name.trim()
        if (trimmedName == state.value.user?.name) {
            state.update { it.copy(error = null, message = "Your details are already up to date.") }
            return
        }
        action {
            state.update { it.copy(message = auth.updateName(trimmedName)) }
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

    /** Creates the manager's company and first team, then routes into the manager dashboard. */
    fun createManagerCompany(companyName: String, teamName: String) {
        val error = companyNameValidation(companyName) ?: teamNameValidation(teamName)
        if (error != null) { state.update { it.copy(error = error, message = null) }; return }
        action {
            onboarding.createManagerCompany(companyName.trim(), teamName.trim())
            // The manager now owns a team, so the manager screens must load it.
            try { manager.refresh() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                // The company was created; a failed reload only leaves the team list stale.
            }
        }
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
        "user_already_exists", "email_exists" -> EMAIL_ALREADY_REGISTERED
        "weak_password" -> "Choose a stronger password with at least 8 characters."
        "over_email_send_rate_limit" -> "The email service has hit its hourly limit. Wait about an hour and try again."
        "over_request_rate_limit" -> "Too many attempts. Wait a moment and try again."
        "email_address_not_authorized" -> "This project cannot deliver confirmation emails to that address yet. Ask the team to finish the email setup."
        "email_address_invalid" -> "That email address cannot receive mail. Check the spelling or use a different address."
        "PGRST205", "42P01", "PGRST204", "42703" -> "The team is still setting up the database. Please try again when it is ready."
        "42501" -> "Your account does not have permission for this action. Contact the team."
        else -> "The request could not be completed. Check your details or try again later."
    }
    is IllegalStateException -> error.message ?: "Your account is not ready. Please retry."
    else -> "Unable to connect. Check your internet connection and try again."
}
