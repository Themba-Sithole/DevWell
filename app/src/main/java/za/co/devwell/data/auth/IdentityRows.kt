package za.co.devwell.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import za.co.devwell.model.*

// These names are the contract in docs/member1-backend-contract.md.
@Serializable
data class UserProfileRow(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    @SerialName("company_id") val companyId: String? = null,
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("onboarding_done") val onboardingDone: Boolean,
) {
    fun toUser() = DevWellUser(
        id = id, name = name, email = email,
        role = UserRole.valueOf(role.uppercase()),
        status = AccountStatus.valueOf(status.uppercase()),
        companyId = companyId, teamId = teamId, onboardingDone = onboardingDone,
    )
}

@Serializable
data class CompanyRow(
    val id: String,
    val name: String,
    val status: String,
    @SerialName("email_domain") val emailDomain: String? = null,
) {
    fun toCompany() = Company(id, name, emailDomain, CompanyStatus.valueOf(status.uppercase()))
}

@Serializable
data class TeamRow(
    val id: String,
    @SerialName("company_id") val companyId: String,
    val name: String,
) {
    fun toTeam() = Team(id = id, companyId = companyId, name = name)
}

// Returned by the team_member_counts RPC: a headcount only, never names or ids of people.
@Serializable
data class TeamMemberCountRow(
    @SerialName("team_id") val teamId: String,
    @SerialName("member_count") val memberCount: Int,
)
