package za.co.devwell.data.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import za.co.devwell.model.*

// Column names follow supabase/migrations/20260925074724_member1_identity_onboarding.sql.
@Serializable
data class JoinRequestRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("company_id") val companyId: String,
    @SerialName("team_id") val teamId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
) {
    // RLS lets managers read only their own profile, so the requester's name and email are not available yet.
    fun toJoinRequest() = JoinRequest(
        id = id, userId = userId,
        userName = "Engineer ${userId.take(8)}",
        userEmail = "Email not available yet",
        companyId = companyId, teamId = teamId,
        status = JoinRequestStatus.valueOf(status.uppercase()),
    )
}

@Serializable
data class ManagerTeamRow(
    val id: String,
    @SerialName("company_id") val companyId: String,
    val name: String,
    @SerialName("manager_id") val managerId: String? = null,
) {
    // The team table has no settings columns or member count yet, so those keep the Team defaults.
    fun toTeam() = Team(id = id, companyId = companyId, name = name, managerId = managerId)
}

/** Success body of the review-join-request Edge Function. */
@Serializable
data class ReviewJoinRequestResult(
    @SerialName("request_id") val requestId: String,
    val status: String,
)
