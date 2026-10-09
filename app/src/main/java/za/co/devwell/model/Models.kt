package za.co.devwell.model

import kotlinx.serialization.Serializable

/**
 * Domain models aligned with Deliverable 3 data design.
 * Keep these shared; feature teams should not invent parallel types.
 */

enum class UserRole { ENGINEER, MANAGER, ADMIN }

enum class AccountStatus { PENDING, ACTIVE, REJECTED, SUSPENDED }

enum class CompanyStatus { PENDING, ACTIVE }

enum class JoinRequestStatus { PENDING, APPROVED, REJECTED }

enum class TaskPriority { LOW, MEDIUM, HIGH }

enum class TaskStatus { OPEN, DONE }

data class DevWellUser(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val companyId: String? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val onboardingDone: Boolean = true,
)

data class Company(
    val id: String,
    val name: String,
    val emailDomain: String? = null,
    val status: CompanyStatus = CompanyStatus.ACTIVE,
)

data class Team(
    val id: String,
    val companyId: String,
    val name: String,
    val managerId: String? = null,
    val timezone: String? = "Africa/Johannesburg",
    val checkInCadence: String? = "Daily",
    val lowWellbeingThreshold: Double? = 2.0,
    val memberCount: Int = 0,
)

data class JoinRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val companyId: String,
    val teamId: String?,
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,
)

/**
 * Draft values collected during the 5-step check-in flow.
 * @Serializable so it can be stored directly as the `responses` JSONB column
 * in Supabase's check_in table (see SupabaseWellbeingRepository).
 */
@Serializable
data class CheckInDraft(
    val wellbeing: Int = 3,          // 1..5 (Struggling..Great)
    val workload: Int = 3,           // 1..5 (Very light..Very heavy)
    val energy: Int = 5,             // 1..10
    val focus: Int = 5,              // 1..10
    val afterHoursMinutes: Int = 0,
    val note: String = "",
)

data class CheckIn(
    val id: String,
    val userId: String,
    val dateLabel: String,
    val values: CheckInDraft,
)

/** Anonymised team aggregate — NEVER includes names, notes, or individual scores. */
data class TeamAggregate(
    val teamId: String,
    val responseCount: Int,
    val averageWellbeing: Double? = null,
    val averageWorkload: Double? = null,
    val averageEnergy: Double? = null,
    val averageFocus: Double? = null,
    val averageAfterHoursMinutes: Double? = null,
)

data class ProjectTask(
    val id: String,
    val title: String,
    val deadline: String,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val note: String = "",
    val status: TaskStatus = TaskStatus.OPEN,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
}

data class DevProject(
    val id: String,
    val name: String,
    val description: String = "",
    val colour: String = "#174A35",
    val deadline: String,
    val progress: Int,
    val tasks: List<ProjectTask>,
)

data class NotificationPreferences(
    val dailyEnabled: Boolean = true,
    val reminderTime: String = "09:00",
    val quietStart: String? = "21:00",
    val quietEnd: String? = "07:00",
    val checkInReminders: Boolean = true,
    val teamAlerts: Boolean = true,
    val tipNudges: Boolean = false,
)

data class SmartTip(
    val id: String,
    val message: String,
)

data class AiMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
)