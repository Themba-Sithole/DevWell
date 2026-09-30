package za.co.devwell.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import za.co.devwell.model.*

/* --------------------------------------------------------------------------
 * Repository contracts — UI/ViewModels depend on these, never on Supabase SDKs.
 * Each member replaces the Mock* class for their feature with a Supabase* class.
 * -------------------------------------------------------------------------- */

interface AuthRepository {
    val currentUser: Flow<DevWellUser?>
    suspend fun restoreSession() {}
    suspend fun refreshProfile() {}
    suspend fun signUp(name: String, email: String, password: String): String = error("Registration is unavailable")
    suspend fun demoLogin(role: UserRole)
    suspend fun signIn(email: String, password: String): Result<DevWellUser>
    suspend fun signOut()
}

interface OnboardingRepository {
    val companies: Flow<List<Company>>
    val teams: Flow<List<Team>>
    suspend fun refreshDirectory() {}
    suspend fun submitEngineerJoinRequest(companyId: String, teamId: String)
    suspend fun registerCompany(name: String, emailDomain: String?): Company
}
interface WellbeingRepository {
    val checkIns: Flow<List<CheckIn>>
    suspend fun saveCheckIn(draft: CheckInDraft)
}

interface TeamInsightsRepository {
    /** Same safe aggregate for manager insights and engineer privacy preview. */
    val teamAggregate: Flow<TeamAggregate>
}

interface ProjectRepository {
    val projects: Flow<List<DevProject>>
    suspend fun createProject(project: DevProject)
    suspend fun updateProject(project: DevProject)
    suspend fun deleteProject(projectId: String)
    suspend fun addTask(projectId: String, task: ProjectTask)
    suspend fun updateTask(projectId: String, task: ProjectTask)
    suspend fun deleteTask(projectId: String, taskId: String)
    suspend fun toggleTask(projectId: String, taskId: String)
}

interface ManagerRepository {
    val joinRequests: Flow<List<JoinRequest>>
    val managedTeam: Flow<Team?>
    suspend fun reviewJoinRequest(requestId: String, approve: Boolean)
    suspend fun updateTeamSettings(team: Team)
}

interface NotificationRepository {
    val preferences: Flow<NotificationPreferences>
    suspend fun updatePreferences(prefs: NotificationPreferences)
}

interface AiGuidanceRepository {
    suspend fun ask(question: String): Result<String>
}

interface AdminRepository {
    val stats: Flow<Map<String, Int>>
}

/* --------------------------------------------------------------------------
 * In-memory mocks so the skeleton runs without a backend.
 * -------------------------------------------------------------------------- */

class MockAuthRepository : AuthRepository {
    private val user = MutableStateFlow<DevWellUser?>(null)
    override val currentUser: Flow<DevWellUser?> = user

    override suspend fun demoLogin(role: UserRole) {
        user.value = when (role) {
            UserRole.ENGINEER -> DevWellUser(
                id = "u1", name = "Alex", email = "engineer@devwell.demo",
                role = role, companyId = "c1", teamId = "t1", teamName = "Mobile Team",
            )
            UserRole.MANAGER -> DevWellUser(
                id = "u2", name = "Jordan", email = "manager@devwell.demo",
                role = role, companyId = "c1", teamId = "t1", teamName = "Mobile Team",
            )
            UserRole.ADMIN -> DevWellUser(
                id = "u3", name = "Sam", email = "admin@devwell.demo", role = role,
            )
        }
    }

    override suspend fun signIn(email: String, password: String): Result<DevWellUser> {
        // Placeholder until Supabase Auth is wired.
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password required"))
        }
        demoLogin(UserRole.ENGINEER)
        return Result.success(user.value!!)
    }

    override suspend fun signOut() {
        user.value = null
    }
}

class MockOnboardingRepository : OnboardingRepository {
    private val companyState = MutableStateFlow(
        listOf(
            Company("c1", "DevWell", "devwell.io"),
            Company("c2", "Acme Corp", "acme.com"),
            Company("c3", "TechNova", "technova.dev", CompanyStatus.PENDING),
        )
    )
    private val teamState = MutableStateFlow(
        listOf(
            Team("t1", "c1", "Mobile Team", managerId = "u2", memberCount = 8),
            Team("t2", "c1", "Platform Team", memberCount = 5),
            Team("t4", "c2", "Backend Team", memberCount = 12),
        )
    )
    override val companies: Flow<List<Company>> = companyState
    override val teams: Flow<List<Team>> = teamState

    override suspend fun submitEngineerJoinRequest(companyId: String, teamId: String) {
        // Persist join_request when Supabase is connected.
    }

    override suspend fun registerCompany(name: String, emailDomain: String?): Company {
        val created = Company("c${companyState.value.size + 1}", name, emailDomain, CompanyStatus.PENDING)
        companyState.value = companyState.value + created
        return created
    }
}

class MockWellbeingRepository : WellbeingRepository {
    private val items = MutableStateFlow(
        listOf(
            CheckIn("c1", "u1", "Mon", CheckInDraft(4, 3, 7, 8, 15)),
            CheckIn("c2", "u1", "Tue", CheckInDraft(3, 4, 5, 6, 50)),
            CheckIn("c3", "u1", "Wed", CheckInDraft(4, 3, 8, 7, 0)),
        )
    )
    override val checkIns: Flow<List<CheckIn>> = items

    override suspend fun saveCheckIn(draft: CheckInDraft) {
        items.value = items.value + CheckIn("c${items.value.size + 1}", "u1", "Today", draft)
    }
}

/* --------------------------------------------------------------------------
 * Member 2 — real Supabase-backed check-in storage (FR4, FR5, Phase 3).
 * Table: public.check_in (id, user_id, responses jsonb, created_at).
 * RLS restricts rows to their owner — see create_check_in_table.sql.
 * -------------------------------------------------------------------------- */

@Serializable
private data class CheckInRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val responses: CheckInDraft,
    @SerialName("created_at") val createdAt: String,
) {
    fun toCheckIn() = CheckIn(
        id = id,
        userId = userId,
        dateLabel = createdAt.take(10), // e.g. "2026-09-29" — Trends (Member 3) can reformat if needed
        values = responses,
    )
}

@Serializable
private data class NewCheckInRow(
    @SerialName("user_id") val userId: String,
    val responses: CheckInDraft,
)

class SupabaseWellbeingRepository(
    private val client: SupabaseClient?,
    currentUser: Flow<DevWellUser?>,
    scope: CoroutineScope,
) : WellbeingRepository {
    private val items = MutableStateFlow<List<CheckIn>>(emptyList())
    override val checkIns: Flow<List<CheckIn>> = items

    init {
        // Reload whenever the signed-in user changes (including sign-out -> empty list).
        currentUser.onEach { user ->
            if (user == null) {
                items.value = emptyList()
            } else {
                refresh(user.id)
            }
        }.launchIn(scope)
    }

    private fun connection() = client ?: error(
        "Add the Supabase project URL and public key to local.properties, then rebuild the app."
    )

    private suspend fun refresh(userId: String) {
        val rows = connection().from("check_in")
            .select { filter { eq("user_id", userId) } }
            .decodeList<CheckInRow>()
        items.value = rows.sortedByDescending { it.createdAt }.map { it.toCheckIn() }
    }

    override suspend fun saveCheckIn(draft: CheckInDraft) {
        val supabase = connection()
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Please sign in again.")
        supabase.from("check_in").insert(NewCheckInRow(userId = userId, responses = draft))
        refresh(userId)
    }
}

class MockTeamInsightsRepository : TeamInsightsRepository {
    /** Demo starts BELOW threshold so privacy UI is visible; bump to 5+ to test charts. */
    private val aggregate = MutableStateFlow(
        TeamAggregate(
            teamId = "t1",
            responseCount = 4,
            averageWellbeing = 3.7,
            averageWorkload = 3.9,
            averageEnergy = 6.4,
            averageFocus = 6.8,
            averageAfterHoursMinutes = 45.0,
        )
    )
    override val teamAggregate: Flow<TeamAggregate> = aggregate

    fun setResponseCountForDemo(count: Int) {
        aggregate.value = aggregate.value.copy(responseCount = count)
    }
}

class MockProjectRepository : ProjectRepository {
    private val items = MutableStateFlow(
        listOf(
            DevProject(
                id = "p1",
                name = "DevWell Android App",
                description = "Native Kotlin client",
                deadline = "18 Sep",
                progress = 33,
                tasks = listOf(
                    ProjectTask("t1", "Complete navigation skeleton", "5 Sep", TaskPriority.HIGH, status = TaskStatus.DONE),
                    ProjectTask("t2", "Connect Supabase authentication", "10 Sep", TaskPriority.HIGH),
                    ProjectTask("t3", "Implement check-in persistence", "13 Sep", TaskPriority.MEDIUM),
                ),
            ),
            DevProject(
                id = "p2",
                name = "Research Report",
                deadline = "25 Sep",
                progress = 0,
                tasks = listOf(
                    ProjectTask("t4", "Review references", "12 Sep", TaskPriority.MEDIUM),
                ),
            ),
        )
    )
    override val projects: Flow<List<DevProject>> = items

    override suspend fun createProject(project: DevProject) {
        val saved = if (project.id.isBlank()) {
            project.copy(id = nextId("p", items.value.map { it.id }))
        } else project
        items.value = items.value + recalculateProgress(saved)
    }

    override suspend fun updateProject(project: DevProject) {
        items.value = items.value.map { current ->
            if (current.id != project.id) current
            else recalculateProgress(project)
        }
    }

    override suspend fun deleteProject(projectId: String) {
        items.value = items.value.filterNot { it.id == projectId }
    }

    override suspend fun addTask(projectId: String, task: ProjectTask) {
        items.value = items.value.map { project ->
            if (project.id != projectId) project
            else {
                val saved = if (task.id.isBlank()) {
                    task.copy(id = nextId("t", items.value.flatMap { it.tasks }.map { it.id }))
                } else task
                recalculateProgress(project.copy(tasks = project.tasks + saved))
            }
        }
    }

    override suspend fun updateTask(projectId: String, task: ProjectTask) {
        items.value = items.value.map { project ->
            if (project.id != projectId) project
            else recalculateProgress(
                project.copy(tasks = project.tasks.map { if (it.id == task.id) task else it })
            )
        }
    }

    override suspend fun deleteTask(projectId: String, taskId: String) {
        items.value = items.value.map { project ->
            if (project.id != projectId) project
            else recalculateProgress(project.copy(tasks = project.tasks.filterNot { it.id == taskId }))
        }
    }

    override suspend fun toggleTask(projectId: String, taskId: String) {
        items.value = items.value.map { project ->
            if (project.id != projectId) project
            else {
                val tasks = project.tasks.map { task ->
                    if (task.id != taskId) task
                    else task.copy(status = if (task.isDone) TaskStatus.OPEN else TaskStatus.DONE)
                }
                recalculateProgress(project.copy(tasks = tasks))
            }
        }
    }

    private fun recalculateProgress(project: DevProject): DevProject {
        val done = project.tasks.count { it.isDone }
        val progress = if (project.tasks.isEmpty()) 0 else (done * 100) / project.tasks.size
        return project.copy(progress = progress)
    }

    private fun nextId(prefix: String, existing: List<String>): String {
        val max = existing.mapNotNull { it.removePrefix(prefix).toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix${max + 1}"
    }
}

class MockManagerRepository : ManagerRepository {
    private val requests = MutableStateFlow(
        listOf(
            JoinRequest("jr1", "u_j1", "Jamie Clarke", "jamie@devwell.io", "c1", "t1"),
            JoinRequest("jr2", "u_j2", "Riya Patel", "riya@devwell.io", "c1", "t2"),
        )
    )
    private val team = MutableStateFlow(
        Team("t1", "c1", "Mobile Team", managerId = "u2", memberCount = 8)
    )
    override val joinRequests: Flow<List<JoinRequest>> = requests
    override val managedTeam: Flow<Team?> = team

    override suspend fun reviewJoinRequest(requestId: String, approve: Boolean) {
        requests.value = requests.value.map {
            if (it.id != requestId) it
            else it.copy(status = if (approve) JoinRequestStatus.APPROVED else JoinRequestStatus.REJECTED)
        }
    }

    override suspend fun updateTeamSettings(teamUpdate: Team) {
        team.value = teamUpdate
    }
}

class MockNotificationRepository : NotificationRepository {
    private val prefs = MutableStateFlow(NotificationPreferences())
    override val preferences: Flow<NotificationPreferences> = prefs
    override suspend fun updatePreferences(preferences: NotificationPreferences) {
        prefs.value = preferences
    }
}

class MockAiGuidanceRepository : AiGuidanceRepository {
    override suspend fun ask(question: String): Result<String> {
        if (question.isBlank()) return Result.failure(IllegalArgumentException("Ask a question"))
        return Result.success(
            "Guidance only — not professional advice.\n\n" +
                    "Try protecting two 25-minute focus blocks and ending work at your planned time. " +
                    "If after-hours work stays high, raise it in a team retro without sharing individual scores."
        )
    }
}

class MockAdminRepository : AdminRepository {
    private val statsState = MutableStateFlow(
        mapOf("Users" to 42, "Companies" to 4, "Teams" to 9, "Pending requests" to 6)
    )
    override val stats: Flow<Map<String, Int>> = statsState
}

/** Single composition root. Swap Mock* → Supabase* here when the backend is ready. */
object AppContainer {
    private lateinit var identity: za.co.devwell.data.auth.SupabaseIdentityRepository
    private lateinit var wellbeingRepo: WellbeingRepository

    fun initialize(context: android.content.Context) {
        if (!::identity.isInitialized) {
            val client = za.co.devwell.data.auth.SupabaseSetup.createClient(context.applicationContext)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            identity = za.co.devwell.data.auth.SupabaseIdentityRepository(client, scope)
            wellbeingRepo = SupabaseWellbeingRepository(client, identity.currentUser, scope)
        }
    }
    val auth: AuthRepository get() = identity
    val onboarding: OnboardingRepository get() = identity
    val wellbeing: WellbeingRepository get() = wellbeingRepo
    val teamInsights: TeamInsightsRepository = MockTeamInsightsRepository()
    val projects: ProjectRepository = MockProjectRepository()
    val manager: ManagerRepository = MockManagerRepository()
    val notifications: NotificationRepository = MockNotificationRepository()
    val ai: AiGuidanceRepository = MockAiGuidanceRepository()
    val admin: AdminRepository = MockAdminRepository()
}