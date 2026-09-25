package za.co.devwell.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import za.co.devwell.data.AuthRepository
import za.co.devwell.data.OnboardingRepository
import za.co.devwell.model.*

/** In-memory only. The ViewModel allows this repository in debug builds only. */
class PreviewIdentityRepository : AuthRepository, OnboardingRepository {
    override val currentUser = MutableStateFlow<DevWellUser?>(null)
    override val companies = MutableStateFlow(listOf(
        Company("c1", "DevWell"), Company("c2", "Acme"),
        Company("c3", "Pending company", status = CompanyStatus.PENDING),
    ))
    override val teams = MutableStateFlow(listOf(
        Team("t1", "c1", "Mobile Team"), Team("t2", "c1", "Platform Team"),
        Team("t3", "c2", "Backend Team"),
    ))
    override suspend fun demoLogin(role: UserRole) {
        currentUser.value = DevWellUser("preview", "Student", "preview@devwell.demo", role,
            companyId = "c1", teamId = "t1", teamName = "Mobile Team")
    }
    fun startOnboarding() {
        currentUser.value = DevWellUser("preview", "Student", "preview@devwell.demo", UserRole.ENGINEER,
            status = AccountStatus.PENDING, onboardingDone = false)
    }
    override suspend fun signIn(email: String, password: String): Result<DevWellUser> =
        Result.failure(IllegalStateException("Leave preview mode to use real sign-in."))
    override suspend fun signOut() { currentUser.value = null }
    override suspend fun submitEngineerJoinRequest(companyId: String, teamId: String) {
        check(companies.value.any { it.id == companyId && it.status == CompanyStatus.ACTIVE })
        val team = teams.value.single { it.id == teamId && it.companyId == companyId }
        currentUser.value = currentUser.value?.copy(companyId = companyId, teamId = teamId,
            teamName = team.name, onboardingDone = true, status = AccountStatus.PENDING)
    }
    fun review(approve: Boolean) {
        currentUser.value = currentUser.value?.copy(status = if (approve) AccountStatus.ACTIVE else AccountStatus.REJECTED)
    }
    override suspend fun registerCompany(name: String, emailDomain: String?): Company =
        error("Company registration is outside the preview.")
}
