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
    // Added by 20261003120000_member5_team_settings.sql; absent until that migration is live.
    val timezone: String? = null,
    @SerialName("check_in_cadence") val checkInCadence: String? = null,
    @SerialName("low_wellbeing_threshold") val lowWellbeingThreshold: Double? = null,
) {
    // Missing settings keep the Team defaults; the team table has no member count, so that stays 0.
    fun toTeam(): Team {
        val defaults = Team(id = id, companyId = companyId, name = name, managerId = managerId)
        return defaults.copy(
            timezone = timezone ?: defaults.timezone,
            checkInCadence = checkInCadence ?: defaults.checkInCadence,
            lowWellbeingThreshold = lowWellbeingThreshold ?: defaults.lowWellbeingThreshold,
        )
    }
}

/** Success body of the review-join-request Edge Function. */
@Serializable
data class ReviewJoinRequestResult(
    @SerialName("request_id") val requestId: String,
    val status: String,
)
