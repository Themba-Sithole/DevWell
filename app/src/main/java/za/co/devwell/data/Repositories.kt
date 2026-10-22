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
import kotlinx.coroutines.flow.flowOf
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
import kotlinx.coroutines.CancellationException
import za.co.devwell.data.offline.CheckInQueuedException
import za.co.devwell.data.offline.PendingCheckInDao
import za.co.devwell.data.offline.isNetworkFailure
import za.co.devwell.data.offline.toPending
import za.co.devwell.data.offline.PendingCheckIn
import kotlinx.coroutines.flow.combine

/* --------------------------------------------------------------------------
 * Repository contracts — UI/ViewModels depend on these, never on Supabase SDKs.
 * Mock* classes at the bottom are test fixtures; the app always uses Supabase*.
 * -------------------------------------------------------------------------- */

interface AuthRepository {
    val currentUser: Flow<DevWellUser?>
    suspend fun restoreSession() {}
    suspend fun refreshProfile() {}
    /**
     * Registers an account that asks for [role]. The server grants the final role:
     * only engineer and manager can be requested, never admin.
     */
    suspend fun signUp(name: String, email: String, password: String, role: SignupRole): String =
        error("Registration is unavailable")
    /** Updates the signed-in user's display name. Returns a confirmation message. */
    suspend fun updateName(name: String): String = error("Editing your profile is unavailable")
    /** Replaces the signed-in account's password. Returns a confirmation message. */
    suspend fun updatePassword(password: String): String = error("Changing your password is unavailable")
    suspend fun signIn(email: String, password: String): Result<DevWellUser>
    suspend fun signOut()
}

interface OnboardingRepository {
    val companies: Flow<List<Company>>
    val teams: Flow<List<Team>>
    suspend fun refreshDirectory() {}
    suspend fun submitEngineerJoinRequest(companyId: String, teamId: String)
    /** Creates the manager's own company and first team, and activates the account. */
    suspend fun createManagerCompany(companyName: String, teamName: String)
}
interface WellbeingRepository {
    val checkIns: Flow<List<CheckIn>>
    suspend fun saveCheckIn(draft: CheckInDraft)
    suspend fun syncPending(): Boolean = true
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
    /** Every team the manager owns. */
    val managedTeams: Flow<List<Team>>
    /** The team currently being managed. */
    val managedTeam: Flow<Team?>
    suspend fun reviewJoinRequest(requestId: String, approve: Boolean)
    suspend fun updateTeamSettings(team: Team)
    /** Switches the managed team so its requests and roster are shown. */
    suspend fun selectTeam(teamId: String)
    /** Creates another team in the manager's company and selects it. */
    suspend fun createTeam(name: String)
    /** Reloads the managed teams, for example after the manager creates their company. */
    suspend fun refresh()
}

interface NotificationRepository {
    val preferences: Flow<NotificationPreferences>
    val syncStatus: Flow<String?> get() = flowOf(null)
    suspend fun updatePreferences(prefs: NotificationPreferences)
}

interface AiGuidanceRepository {
    /**
     * Sends the running conversation to the assistant. [messages] is the full
     * in-session transcript, oldest first; the last entry must be the user's
     * newest question. Recent turns are forwarded so replies stay in context.
     */
    suspend fun ask(messages: List<AiMessage>): Result<String>
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
 * In-memory fakes used by unit tests.
 * -------------------------------------------------------------------------- */

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

// No default values on purpose: the serializer skips properties that equal
// their default, so every field must be written explicitly.
@Serializable
private data class CheckInResponsesOut(
    val wellbeing: Int,
    val workload: Int,
    val energy: Int,
    val focus: Int,
    val afterHoursMinutes: Int,
    val note: String,
)

private fun CheckInDraft.toOut() = CheckInResponsesOut(
    wellbeing = wellbeing,
    workload = workload,
    energy = energy,
    focus = focus,
    afterHoursMinutes = afterHoursMinutes,
    note = note,
)

@Serializable
private data class NewCheckInRow(
    @SerialName("user_id") val userId: String,
    val responses: CheckInResponsesOut,
)

@Serializable
private data class CheckInPatch(val responses: CheckInResponsesOut)
@Serializable
private data class QueuedCheckInRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val responses: CheckInResponsesOut,
    @SerialName("created_at") val createdAt: String,
)

private fun PendingCheckIn.toOut() = CheckInResponsesOut(
    wellbeing = wellbeing,
    workload = workload,
    energy = energy,
    focus = focus,
    afterHoursMinutes = afterHoursMinutes,
    note = note,
)
private fun PendingCheckIn.asCheckIn() = CheckIn(
    id = "pending-$id",
    userId = userId,
    dateLabel = localDate,
    values = CheckInDraft(
        wellbeing = wellbeing,
        workload = workload,
        energy = energy,
        focus = focus,
        afterHoursMinutes = afterHoursMinutes,
        note = note,
    ),
)

class SupabaseWellbeingRepository(
    private val client: SupabaseClient?,
    currentUser: Flow<DevWellUser?>,
    scope: CoroutineScope,
    private val queue: PendingCheckInDao,
    private val scheduleSync: () -> Unit,
) : WellbeingRepository {
    // Check-ins that exist on the server, and check-ins still waiting on the phone.
    private val serverItems = MutableStateFlow<List<CheckIn>>(emptyList())
    private val pendingItems = MutableStateFlow<List<CheckIn>>(emptyList())

    /** Server rows plus queued ones. A queued check-in wins over the server row for the same day. */
    override val checkIns: Flow<List<CheckIn>> = combine(serverItems, pendingItems) { server, pending ->
        val pendingDates = pending.map { it.dateLabel }.toSet()
        (server.filter { it.dateLabel !in pendingDates } + pending).sortedByDescending { it.dateLabel }
    }

    init {
        // Reload whenever the signed-in user changes (including sign-out -> empty lists).
        currentUser.onEach { user ->
            if (user == null) {
                serverItems.value = emptyList()
                pendingItems.value = emptyList()
            } else {
                reloadPending(user.id)
                // Opening the app offline must not crash: keep what we have and carry on.
                try {
                    refresh(user.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                // Anything saved offline in an earlier session is uploaded now.
                if (pendingItems.value.isNotEmpty()) scheduleSync()
            }
        }.launchIn(scope)
    }

    private fun connection() = client ?: error(
        "Add the Supabase project URL and public key to local.properties, then rebuild the app."
    )

    private suspend fun reloadPending(userId: String) {
        pendingItems.value = queue.forUser(userId).map { it.asCheckIn() }
    }

    private suspend fun refresh(userId: String) {
        val rows = connection().from("check_in")
            .select { filter { eq("user_id", userId) } }
            .decodeList<CheckInRow>()
        // Enforce one check-in per day: keep only the most recent row for each date.
        serverItems.value = rows.sortedByDescending { it.createdAt }
            .map { it.toCheckIn() }
            .distinctBy { it.dateLabel }
    }

    /**
     * One check-in per day (FR4): update today's row if it exists, otherwise insert.
     * If the phone can't reach the server, the check-in is kept in the local queue
     * and CheckInQueuedException tells the ViewModel it was saved offline.
     */
    override suspend fun saveCheckIn(draft: CheckInDraft) {
        val supabase = connection()
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Please sign in again.")
        val today = TrendsAnalysis.today().toString()
        try {
            // Look at server rows only, so we never try to update a check-in that isn't uploaded yet.
            val existing = TrendsAnalysis.todayCheckIn(serverItems.value)
            if (existing == null) {
                supabase.from("check_in").insert(NewCheckInRow(userId = userId, responses = draft.toOut()))
            } else {
                supabase.from("check_in").update(CheckInPatch(responses = draft.toOut())) {
                    filter { eq("id", existing.id) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.isNetworkFailure()) throw e
            queue.insert(draft.toPending(userId, today))
            reloadPending(userId)
            scheduleSync()
            throw CheckInQueuedException()
        }
        // Saved online, so any older offline copy for today is now out of date.
        queue.deleteForDay(userId, today)
        reloadPending(userId)
        refresh(userId)
    }

    /**
     * Uploads queued check-ins. Returns false when the connection failed and WorkManager
     * should retry. An item is deleted from the queue only after the server accepted it.
     * Items use their own id with upsert, so a retry can never create a duplicate row.
     */
    override suspend fun syncPending(): Boolean {
        val supabase = client ?: return true
        supabase.auth.awaitInitialization()
        val userId = supabase.auth.currentUserOrNull()?.id ?: return true
        val pending = queue.forUser(userId)
        if (pending.isEmpty()) return true
        try {
            refresh(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        var retry = false
        for (item in pending) {
            try {
                val existing = serverItems.value.firstOrNull { it.dateLabel == item.localDate }
                if (existing == null) {
                    supabase.from("check_in").upsert(
                        QueuedCheckInRow(
                            id = item.id,
                            userId = userId,
                            responses = item.toOut(),
                            createdAt = Instant.ofEpochMilli(item.createdAt).toString(),
                        )
                    )
                } else {
                    // One check-in per day: update that day's row instead of adding a second.
                    supabase.from("check_in").update(CheckInPatch(responses = item.toOut())) {
                        filter { eq("id", existing.id) }
                    }
                }
                queue.delete(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connection problems are retried; other failures stay queued until the next app start.
                if (e.isNetworkFailure()) retry = true
            }
        }
        reloadPending(userId)
        try {
            refresh(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return !retry
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

class MockNotificationRepository : NotificationRepository {
    private val prefs = MutableStateFlow(NotificationPreferences())
    override val preferences: Flow<NotificationPreferences> = prefs
    override suspend fun updatePreferences(preferences: NotificationPreferences) {
        prefs.value = preferences
    }
}

class MockAiGuidanceRepository : AiGuidanceRepository {
    override suspend fun ask(messages: List<AiMessage>): Result<String> {
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
    override suspend fun ask(messages: List<AiMessage>): Result<String> = runCatching {
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

/** Empty sources used only while the Supabase client is not configured. */
private class EmptyTeamInsightsRepository : TeamInsightsRepository {
    override val teamAggregate: Flow<TeamAggregate?> = flowOf(null)
}

private class EmptyProjectRepository : ProjectRepository {
    override val projects: Flow<List<DevProject>> = flowOf(emptyList())
    override suspend fun createProject(project: DevProject) = Unit
    override suspend fun updateProject(project: DevProject) = Unit
    override suspend fun deleteProject(projectId: String) = Unit
    override suspend fun addTask(projectId: String, task: ProjectTask) = Unit
    override suspend fun updateTask(projectId: String, task: ProjectTask) = Unit
    override suspend fun deleteTask(projectId: String, taskId: String) = Unit
    override suspend fun toggleTask(projectId: String, taskId: String) = Unit
}

private class EmptyAdminRepository : AdminRepository {
    override val stats: Flow<Map<String, Int>> = flowOf(emptyMap())
    override val users: Flow<List<DevWellUser>> = flowOf(emptyList())
    override val isLoading: Flow<Boolean> = flowOf(false)
    override val busyUserId: Flow<String?> = flowOf(null)
    override val errorMessage: Flow<String?> = flowOf(null)
    override suspend fun refresh() = Unit
    override fun clearError() = Unit
    override suspend fun setUserStatus(userId: String, active: Boolean) = Unit
}

/** Single composition root. Runtime data always comes from Supabase. */
object AppContainer {
    private lateinit var identity: za.co.devwell.data.auth.SupabaseIdentityRepository
    private lateinit var wellbeingRepo: WellbeingRepository
    private lateinit var managerRepo: ManagerRepository
    private lateinit var teamInsightsRepo: TeamInsightsRepository
    private lateinit var avatarRepo: za.co.devwell.data.avatar.AvatarRepository
    private var adminRepository: AdminRepository = EmptyAdminRepository()
    private var projectRepository: ProjectRepository = EmptyProjectRepository()
    private var aiRepository: AiGuidanceRepository = SupabaseAiGuidanceRepository(null)
    private var notificationRepository: NotificationRepository = MockNotificationRepository()

    fun initialize(context: android.content.Context) {
        if (!::identity.isInitialized) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val client = za.co.devwell.data.auth.SupabaseSetup.createClient(context.applicationContext)
            aiRepository = SupabaseAiGuidanceRepository(client)
            identity = za.co.devwell.data.auth.SupabaseIdentityRepository(client, scope)
            notificationRepository = if (client == null) {
                PersistentNotificationRepository(context.applicationContext)
            } else {
                SupabaseNotificationRepository(client, identity.currentUser, context.applicationContext, scope)
            }
            wellbeingRepo = SupabaseWellbeingRepository(
                client = client,
                currentUser = identity.currentUser,
                scope = scope,
                queue = za.co.devwell.data.offline.OfflineDatabase.get(context).pendingCheckIns(),
                scheduleSync = { za.co.devwell.data.offline.CheckInSyncScheduler.schedule(context) },
            )
            avatarRepo = za.co.devwell.data.avatar.SupabaseAvatarRepository(client, identity.currentUser, scope)
            managerRepo = za.co.devwell.data.manager.SupabaseManagerRepository(client, scope)
            teamInsightsRepo = if (client == null) {
                EmptyTeamInsightsRepository()
            } else {
                SupabaseTeamInsightsRepository(client, identity.currentUser, managerRepo.managedTeam, scope)
            }
            projectRepository = if (client == null) {
                EmptyProjectRepository()
            } else {
                za.co.devwell.data.projects.SupabaseProjectRepository(client, scope)
            }
            adminRepository = if (client == null) {
                EmptyAdminRepository()
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
    val notifications: NotificationRepository get() = notificationRepository
    val ai: AiGuidanceRepository get() = aiRepository
    val admin: AdminRepository get() = adminRepository
    val avatar: za.co.devwell.data.avatar.AvatarRepository get() = avatarRepo
}
