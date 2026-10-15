package za.co.devwell.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import za.co.devwell.model.*
import java.time.Instant
import java.time.ZoneId

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
    val teamAggregate: Flow<TeamAggregate?>
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
    /**
     * Sends the running conversation to the assistant. [messages] is the full
     * in-session transcript, oldest first; the last entry must be the user's
     * newest question. Recent turns are forwarded so replies stay in context.
     */
    suspend fun ask(messages: List<AiMessage>, preview: Boolean = false): Result<String>
}

interface AdminRepository {
    val stats: Flow<Map<String, Int>>
    val users: Flow<List<DevWellUser>>
    val isLoading: Flow<Boolean>
    val busyUserId: Flow<String?>
    val errorMessage: Flow<String?>
    suspend fun refresh()
    fun clearError()
    suspend fun setUserStatus(userId: String, active: Boolean)
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
    private val today: String get() = TrendsAnalysis.today().toString()
    private val items = MutableStateFlow(
        listOf(
            CheckIn("c1", "u1", TrendsAnalysis.today().minusDays(2).toString(), CheckInDraft(4, 3, 7, 8, 15)),
            CheckIn("c2", "u1", TrendsAnalysis.today().minusDays(1).toString(), CheckInDraft(3, 4, 5, 6, 50)),
            CheckIn("c3", "u1", TrendsAnalysis.today().toString(), CheckInDraft(4, 3, 8, 7, 0)),
        )
    )
    override val checkIns: Flow<List<CheckIn>> = items

    /** One check-in per day: editing today replaces the existing entry. */
    override suspend fun saveCheckIn(draft: CheckInDraft) {
        val date = today
        val existing = items.value.firstOrNull { it.dateLabel == date }
        items.value = if (existing == null) {
            items.value + CheckIn("c${items.value.size + 1}", "u1", date, draft)
        } else {
            items.value.map { if (it.id == existing.id) it.copy(values = draft) else it }
        }
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
        dateLabel = runCatching {
            Instant.parse(createdAt).atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate().toString()
        }.getOrElse { createdAt.take(10) },
        values = responses,
    )
}

@Serializable
private data class NewCheckInRow(
    @SerialName("user_id") val userId: String,
    val responses: CheckInDraft,
)

@Serializable
private data class CheckInPatch(val responses: CheckInDraft)

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
        // Enforce one check-in per day: keep only the most recent row for each date.
        items.value = rows.sortedByDescending { it.createdAt }
            .map { it.toCheckIn() }
            .distinctBy { it.dateLabel }
    }

    /**
     * One check-in per day (FR4). If today's check-in already exists it is
     * updated in place so the engineer can correct a mistake; otherwise a new
     * row is inserted.
     */
    override suspend fun saveCheckIn(draft: CheckInDraft) {
        val supabase = connection()
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Please sign in again.")
        val existing = TrendsAnalysis.todayCheckIn(items.value)
        if (existing == null) {
            supabase.from("check_in").insert(NewCheckInRow(userId = userId, responses = draft))
        } else {
            supabase.from("check_in").update(CheckInPatch(responses = draft)) {
                filter { eq("id", existing.id) }
            }
        }
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
    override val teamAggregate: Flow<TeamAggregate?> = aggregate

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
                    ProjectTask("t1", "Complete navigation skeleton", "5 Sep", TaskPriority.HIGH, status = TaskStatus.DONE, position = 0),
                    ProjectTask("t2", "Connect Supabase authentication", "10 Sep", TaskPriority.HIGH, position = 1),
                    ProjectTask("t3", "Implement check-in persistence", "13 Sep", TaskPriority.MEDIUM, position = 2),
                ),
            ),
            DevProject(
                id = "p2",
                name = "Research Report",
                deadline = "25 Sep",
                progress = 0,
                tasks = listOf(
                    ProjectTask("t4", "Review references", "12 Sep", TaskPriority.MEDIUM, position = 0),
                ),
            ),
        )
    )
    override val projects: Flow<List<DevProject>> = items.map { projects -> projects.sortedBy { it.name } }

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
                    task.copy(
                        id = nextId("t", items.value.flatMap { it.tasks }.map { it.id }),
                        position = (project.tasks.maxOfOrNull { it.position } ?: -1) + 1,
                    )
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
        val tasks = project.tasks.sortedBy { it.position }
        val done = tasks.count { it.isDone }
        val progress = if (tasks.isEmpty()) 0 else (done * 100) / tasks.size
        return project.copy(progress = progress, tasks = tasks)
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
    override suspend fun ask(messages: List<AiMessage>, preview: Boolean): Result<String> {
        val last = messages.lastOrNull()
        if (last == null || !last.fromUser || last.text.isBlank()) {
            return Result.failure(IllegalArgumentException("Ask a question"))
        }
        return Result.success(
            "Guidance only — not professional advice.\n\n" +
                    "Try protecting two 25-minute focus blocks and ending work at your planned time. " +
                    "If after-hours work stays high, raise it in a team retro without sharing individual scores."
        )
    }
}

@Serializable
private data class AiAssistantResponse(val answer: String)

class SupabaseAiGuidanceRepository(private val client: SupabaseClient?) : AiGuidanceRepository {
    override suspend fun ask(messages: List<AiMessage>, preview: Boolean): Result<String> = runCatching {
        val last = messages.lastOrNull()
        require(last != null && last.fromUser) { "Ask a question first." }
        val question = last.text.trim()
        require(question.isNotEmpty()) { "Ask a question first." }
        require(question.length <= MAX_QUESTION_CHARS) { "Keep your question under 1,000 characters." }
        // Only forward the tail of the conversation so the request stays small.
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
            .map { it.copy(text = it.text.take(MAX_MESSAGE_CHARS)) }
        val supabase = client ?: error("Supabase is not configured for this app.")
        val response = try {
            supabase.functions.invoke("ai-assistant", body = buildJsonObject {
                put("consent", true)
                put("preview", preview)
                putJsonArray("messages") {
                    recent.forEach { message ->
                        addJsonObject {
                            put("role", if (message.fromUser) "user" else "model")
                            put("text", message.text)
                        }
                    }
                }
            })
        } catch (_: Exception) {
            throw IllegalStateException("The assistant is temporarily unavailable. Please retry.")
        }
        val responseBody = try {
            response.body<AiAssistantResponse>()
        } catch (_: Exception) {
            throw IllegalStateException("The assistant is temporarily unavailable. Please retry.")
        }
        responseBody.answer.trim().also {
            require(it.isNotEmpty()) { "The assistant returned an empty response. Please retry." }
        }
    }

    companion object {
        private const val MAX_QUESTION_CHARS = 1000
        private const val MAX_MESSAGE_CHARS = 4000
        private const val MAX_CONTEXT_MESSAGES = 20
    }
}

class MockAdminRepository : AdminRepository {
    private val statsState = MutableStateFlow(
        mapOf(
            "Users" to 5,
            "Active accounts" to 3,
            "Suspended accounts" to 1,
            "Companies" to 4,
            "Teams" to 9,
            "Pending requests" to 6,
        )
    )
    override val stats: Flow<Map<String, Int>> = statsState
    override val isLoading: Flow<Boolean> = MutableStateFlow(false)
    override val busyUserId: Flow<String?> = MutableStateFlow(null)
    override val errorMessage: Flow<String?> = MutableStateFlow(null)

    private val usersState = MutableStateFlow(
        listOf(
            DevWellUser("u1", "zuh", "zuh@devwell.io", UserRole.ENGINEER, companyId = "c1", teamId = "t1", teamName = "Mobile Team"),
            DevWellUser("u2", "themb", "themb@devwell.io", UserRole.MANAGER, companyId = "c1", teamId = "t1", teamName = "Mobile Team"),
            DevWellUser("u3", "taash", "taash@devwell.io", UserRole.ADMIN),
            DevWellUser("u4", "dan", "dan@devwell.io", UserRole.ENGINEER, companyId = "c1", status = AccountStatus.PENDING),
            DevWellUser("u5", "lee", "lee@devwell.io", UserRole.ENGINEER, companyId = "c1", teamId = "t1", status = AccountStatus.SUSPENDED),
        )
    )
    override val users: Flow<List<DevWellUser>> = usersState

    override suspend fun refresh() = Unit

    override fun clearError() = Unit

    override suspend fun setUserStatus(userId: String, active: Boolean) {
        usersState.value = usersState.value.map {
            if (it.id == userId) it.copy(status = if (active) AccountStatus.ACTIVE else AccountStatus.SUSPENDED)
            else it
        }
    }
}

/** Single composition root. Swap Mock* → Supabase* here when the backend is ready. */
object AppContainer {
    private lateinit var identity: za.co.devwell.data.auth.SupabaseIdentityRepository
    private lateinit var wellbeingRepo: WellbeingRepository
    private lateinit var managerRepo: ManagerRepository
    private lateinit var teamInsightsRepo: TeamInsightsRepository
    private var adminRepository: AdminRepository = MockAdminRepository()
    private var projectRepository: ProjectRepository = MockProjectRepository()
    private var aiRepository: AiGuidanceRepository = MockAiGuidanceRepository()

    fun initialize(context: android.content.Context) {
        if (!::identity.isInitialized) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val client = za.co.devwell.data.auth.SupabaseSetup.createClient(context.applicationContext)
            aiRepository = if (client == null) MockAiGuidanceRepository() else SupabaseAiGuidanceRepository(client)
            identity = za.co.devwell.data.auth.SupabaseIdentityRepository(client, scope)
            wellbeingRepo = SupabaseWellbeingRepository(client, identity.currentUser, scope)
            managerRepo = za.co.devwell.data.manager.SupabaseManagerRepository(client, scope)
            teamInsightsRepo = if (client == null) {
                MockTeamInsightsRepository()
            } else {
                SupabaseTeamInsightsRepository(client, identity.currentUser, managerRepo.managedTeam, scope)
            }
            projectRepository = if (client == null) {
                MockProjectRepository()
            } else {
                za.co.devwell.data.projects.SupabaseProjectRepository(client, scope)
            }
            adminRepository = if (client == null) {
                MockAdminRepository()
            } else {
                za.co.devwell.data.admin.SupabaseAdminRepository(client, identity.currentUser, scope)
            }
        }
    }

    val auth: AuthRepository get() = identity
    val onboarding: OnboardingRepository get() = identity
    val wellbeing: WellbeingRepository get() = wellbeingRepo
    val teamInsights: TeamInsightsRepository get() = teamInsightsRepo
    val projects: ProjectRepository get() = projectRepository
    val manager: ManagerRepository get() = managerRepo
    val notifications: NotificationRepository = MockNotificationRepository()
    val ai: AiGuidanceRepository get() = aiRepository
    val admin: AdminRepository get() = adminRepository
}
