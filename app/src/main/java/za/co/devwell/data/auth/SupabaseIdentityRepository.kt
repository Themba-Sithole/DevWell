package za.co.devwell.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.co.devwell.data.AuthRepository
import za.co.devwell.data.OnboardingRepository
import za.co.devwell.model.*

/** All real account and onboarding requests go through this repository. */
class SupabaseIdentityRepository(
    private val client: SupabaseClient?,
    scope: CoroutineScope,
) : AuthRepository, OnboardingRepository {
    override val currentUser = MutableStateFlow<DevWellUser?>(null)
    override val companies = MutableStateFlow<List<Company>>(emptyList())
    override val teams = MutableStateFlow<List<Team>>(emptyList())
    private val accountLock = Mutex()

    init {
        scope.launch {
            client?.auth?.sessionStatus?.collect { status ->
                if (status is SessionStatus.NotAuthenticated || status is SessionStatus.RefreshFailure) {
                    currentUser.value = null
                    companies.value = emptyList()
                    teams.value = emptyList()
                }
            }
        }
    }

    private fun connection() = client ?: error(
        "Add the Supabase project URL and public key to local.properties, then rebuild the app."
    )

    override suspend fun restoreSession() = accountLock.withLock {
        val supabase = client ?: return@withLock
        supabase.auth.awaitInitialization()
        if (supabase.auth.currentSessionOrNull() != null) loadProfile()
        Unit
    }

    override suspend fun signIn(email: String, password: String): Result<DevWellUser> = accountLock.withLock {
        try {
            val supabase = connection()
            supabase.auth.awaitInitialization()
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            Result.success(loadProfile())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentUser.value = null
            client?.auth?.clearSession()
            Result.failure(error)
        }
    }

    override suspend fun signUp(name: String, email: String, password: String, role: SignupRole): String = accountLock.withLock {
        val supabase = connection()
        supabase.auth.awaitInitialization()
        supabase.auth.clearSession()
        val user = supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            // The name and the requested role are metadata. The database grants the
            // final role and never accepts admin from a signup.
            data = buildJsonObject {
                put("name", name.trim())
                put("role", if (role == SignupRole.MANAGER) "manager" else "engineer")
            }
        }
        // With email confirmation on, the server answers an address that already has
        // an account with a placeholder user that has no identities and no session.
        // Never tell the person to confirm an account that already exists.
        if (user?.identities?.isEmpty() == true) error(EMAIL_ALREADY_REGISTERED)
        if (supabase.auth.currentSessionOrNull() != null) {
            loadProfile()
            if (role == SignupRole.MANAGER) {
                "Account created. Create your company and first team to finish."
            } else {
                "Account created. Choose your company and team."
            }
        } else {
            "Check your email to confirm your account, then return here and sign in."
        }
    }

    override suspend fun refreshProfile() = accountLock.withLock {
        if (connection().auth.currentSessionOrNull() == null) {
            currentUser.value = null
            error("Your session has ended. Please sign in again.")
        }
        loadProfile()
        Unit
    }

    /**
     * Display names live in public."user", which RLS only exposes for reads.
     * The guarded RPC owns the update so a member can never change their own
     * role, status or team through the same call.
     */
    override suspend fun updateName(name: String): String = accountLock.withLock {
        val trimmed = name.trim()
        require(trimmed.length >= 2) { "Enter your full name." }
        val supabase = connection()
        check(supabase.auth.currentSessionOrNull() != null) { "Please sign in again." }
        supabase.postgrest.rpc("update_own_profile", buildJsonObject { put("p_name", trimmed) })
        loadProfile()
        "Your name has been updated."
    }

    override suspend fun updateEmail(email: String): String = accountLock.withLock {
        val trimmed = email.trim()
        val supabase = connection()
        supabase.auth.awaitInitialization()
        check(supabase.auth.currentSessionOrNull() != null) { "Please sign in again." }
        // The profile row follows through the email sync trigger after confirmation.
        supabase.auth.updateUser { this.email = trimmed }
        "Check your inbox and open the confirmation link to finish changing your email."
    }

    override suspend fun updatePassword(password: String): String = accountLock.withLock {
        val supabase = connection()
        supabase.auth.awaitInitialization()
        check(supabase.auth.currentSessionOrNull() != null) { "Please sign in again." }
        supabase.auth.updateUser { this.password = password }
        "Your password has been updated."
    }

    private suspend fun loadProfile(): DevWellUser {
        val supabase = connection()
        val id = supabase.auth.currentUserOrNull()?.id ?: error("Please sign in again.")
        val profile = supabase.from("user").select { filter { eq("id", id) } }
            .decodeList<UserProfileRow>().singleOrNull()
            ?: error("Your account profile is not ready yet. Ask the team to finish the database setup, then retry.")
        val user = profile.toUser()
        check(user.id == id && supabase.auth.currentUserOrNull()?.id == id) { "The account profile did not match your sign-in." }
        currentUser.value = user
        return user
    }

    override suspend fun refreshDirectory() = accountLock.withLock {
        val supabase = connection()
        check(currentUser.value != null) { "Please sign in first." }
        val companyRows = supabase.from("company").select { filter { eq("status", "active") } }
            .decodeList<CompanyRow>().map { it.toCompany() }.sortedBy { it.name }
        val companyIds = companyRows.map { it.id }.toSet()
        // Other profiles are hidden by RLS, so the headcount comes from a guarded RPC.
        // If it is missing or fails, the teams still load and show 0.
        val counts = try {
            supabase.postgrest.rpc("team_member_counts").decodeList<TeamMemberCountRow>()
                .associate { it.teamId to it.memberCount }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
        val teamRows = supabase.from("team").select().decodeList<TeamRow>()
            .filter { it.companyId in companyIds }
            .map { it.toTeam().copy(memberCount = counts[it.id] ?: 0) }.sortedBy { it.name }
        companies.value = companyRows
        teams.value = teamRows
    }

    override suspend fun submitEngineerJoinRequest(companyId: String, teamId: String) = accountLock.withLock {
        val user = currentUser.value ?: error("Please sign in first.")
        check(user.role == UserRole.ENGINEER && entryDestination(user) == EntryDestination.ONBOARDING) {
            "This account cannot submit a join request. Refresh your account status."
        }
        check(companies.value.any { it.id == companyId && it.status == CompanyStatus.ACTIVE } &&
            teams.value.any { it.id == teamId && it.companyId == companyId }) {
            "Select an active company and a team belonging to it."
        }
        // The server verifies the JWT and saves the request + profile in one transaction.
        connection().functions.invoke("submit-join-request", body = buildJsonObject {
            put("company_id", companyId)
            put("team_id", teamId)
        })
        loadProfile()
        check(currentUser.value?.onboardingDone == true) {
            "The request could not be confirmed. Refresh your account status before trying again."
        }
    }

    /**
     * Creates the manager's own company and first team. The server activates the
     * account in the same transaction, so the profile is reloaded afterwards.
     */
    override suspend fun createManagerCompany(companyName: String, teamName: String) = accountLock.withLock {
        val user = currentUser.value ?: error("Please sign in first.")
        check(user.role == UserRole.MANAGER && !user.onboardingDone) {
            "This account cannot create a company. Refresh your account status."
        }
        connection().functions.invoke("create-company", body = buildJsonObject {
            put("company_name", companyName.trim())
            put("team_name", teamName.trim())
        })
        loadProfile()
        check(currentUser.value?.onboardingDone == true) {
            "The company could not be confirmed. Refresh your account status before trying again."
        }
    }

    override suspend fun signOut() = accountLock.withLock {
        currentUser.value = null
        companies.value = emptyList()
        teams.value = emptyList()
        val supabase = client ?: return@withLock
        try {
            supabase.auth.awaitInitialization()
            if (supabase.auth.currentSessionOrNull() != null) supabase.auth.signOut()
        } finally {
            // Always erase this phone's saved session, even without a network connection.
            supabase.auth.clearSession()
            supabase.auth.sessionManager.deleteSession()
        }
    }
}
