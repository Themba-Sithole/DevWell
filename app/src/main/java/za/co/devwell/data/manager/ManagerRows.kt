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
    // Names come from pending_join_requesters/team_roster; no email is ever exposed to managers.
    fun toJoinRequest(name: String? = null) = JoinRequest(
        id = id, userId = userId,
        userName = name?.takeIf { it.isNotBlank() } ?: placeholderName(userId),
        userEmail = "",
        companyId = companyId, teamId = teamId,
        status = JoinRequestStatus.valueOf(status.uppercase()),
    )
}

/** Shown when a name could not be loaded. */
internal fun placeholderName(userId: String) = "Engineer ${userId.take(8)}"

/** Row of public.pending_join_requesters(p_team_id): pending and declined requests. Name only, never email. */
@Serializable
data class PendingRequesterRow(
    @SerialName("request_id") val requestId: String,
    @SerialName("user_id") val userId: String,
    val name: String? = null,
)

/** Row of public.team_roster(p_team_id). Active engineers, name only. */
@Serializable
data class RosterMemberRow(
    @SerialName("user_id") val userId: String,
    val name: String? = null,
)

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
