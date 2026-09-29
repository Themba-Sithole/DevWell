package za.co.devwell.data.projects

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import za.co.devwell.data.MockProjectRepository
import za.co.devwell.data.ProjectRepository
import za.co.devwell.model.DevProject
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.TaskStatus

class SupabaseProjectRepository(
    private val client: SupabaseClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val fallback: ProjectRepository = MockProjectRepository(),
) : ProjectRepository {

    private val _projects = MutableStateFlow<List<DevProject>>(emptyList())
    override val projects: Flow<List<DevProject>> = _projects.asStateFlow()
    private val writeLock = Mutex()
    private var fallbackProjects: List<DevProject> = emptyList()

    init {
        scope.launch {
            fallback.projects.collect { values ->
                fallbackProjects = values
                if (client.auth.currentUserOrNull() == null) _projects.value = values
            }
        }
        scope.launch {
            try {
                client.auth.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) loadProjects() else _projects.value = fallbackProjects
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _projects.value = fallbackProjects
            }
        }
    }

    private suspend fun loadProjects() = withContext(Dispatchers.IO) {
        val uid = client.auth.currentUserOrNull()?.id ?: run {
            _projects.value = fallbackProjects
            return@withContext
        }
        val projectRows = client.from("project").select { filter { eq("user_id", uid) } }
            .decodeList<ProjectRow>()
        val projectIds = projectRows.map { it.id }
        val taskRows = if (projectIds.isEmpty()) {
            emptyList()
        } else {
            client.from("task").select().decodeList<ProjectTaskRow>()
                .filter { it.projectId in projectIds }
                .sortedBy { it.position }
        }
        val tasksByProject = taskRows.groupBy { it.projectId ?: "" }
        _projects.value = projectRows.map { row ->
            row.toDomain(tasksByProject[row.id]?.map { it.toDomain() }.orEmpty())
        }.sortedBy { it.name }
    }

    override suspend fun createProject(project: DevProject) = writeLock.withLock {
        val userId = client.auth.currentUserOrNull()?.id
        if (userId == null) return@withLock fallback.createProject(project)
        val row = ProjectInsertRow(
            userId = userId,
            name = project.name,
            description = project.description,
            colour = project.colour,
            deadline = storageDeadline(project.deadline),
        )
        withContext(Dispatchers.IO) { client.from("project").insert(row) }
        loadProjects()
    }

    override suspend fun updateProject(project: DevProject) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.updateProject(project)
        if (project.id.isBlank()) return@withLock
        val patch = ProjectPatchRow(
            name = project.name,
            description = project.description,
            colour = project.colour,
            deadline = storageDeadline(project.deadline),
        )
        withContext(Dispatchers.IO) {
            client.from("project").update(patch) { filter { eq("id", project.id) } }
        }
        loadProjects()
    }

    override suspend fun deleteProject(projectId: String) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.deleteProject(projectId)
        if (projectId.isBlank()) return@withLock
        withContext(Dispatchers.IO) {
            client.from("project").delete { filter { eq("id", projectId) } }
        }
        loadProjects()
    }

    override suspend fun addTask(projectId: String, task: ProjectTask) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.addTask(projectId, task)
        if (projectId.isBlank()) return@withLock
        val taskRows = withContext(Dispatchers.IO) {
            client.from("task")
                .select { filter { eq("project_id", projectId) } }
                .decodeList<ProjectTaskRow>()
        }
        val row = ProjectTaskInsertRow(
            projectId = projectId,
            title = task.title,
            deadline = storageDeadline(task.deadline),
            priority = task.priority,
            note = task.note,
            status = task.status,
            position = (taskRows.maxOfOrNull { it.position } ?: -1) + 1,
        )
        withContext(Dispatchers.IO) { client.from("task").insert(row) }
        loadProjects()
    }

    override suspend fun updateTask(projectId: String, task: ProjectTask) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.updateTask(projectId, task)
        if (task.id.isBlank()) return@withLock
        val patch = ProjectTaskPatchRow(
            title = task.title,
            deadline = storageDeadline(task.deadline),
            priority = task.priority,
            note = task.note,
            status = task.status,
        )
        withContext(Dispatchers.IO) {
            client.from("task").update(patch) {
                filter { eq("id", task.id); eq("project_id", projectId) }
            }
        }
        loadProjects()
    }

    override suspend fun deleteTask(projectId: String, taskId: String) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.deleteTask(projectId, taskId)
        if (taskId.isBlank()) return@withLock
        withContext(Dispatchers.IO) {
            client.from("task").delete {
                filter { eq("id", taskId); eq("project_id", projectId) }
            }
        }
        loadProjects()
    }

    override suspend fun toggleTask(projectId: String, taskId: String) = writeLock.withLock {
        if (client.auth.currentUserOrNull() == null) return@withLock fallback.toggleTask(projectId, taskId)
        val project = _projects.value.firstOrNull { it.id == projectId }
            ?: error("Project not found")
        val task = project.tasks.firstOrNull { it.id == taskId } ?: error("Task not found")
        val newStatus = if (task.isDone) TaskStatus.OPEN else TaskStatus.DONE
        val patch = ProjectTaskPatchRow(
            title = task.title,
            deadline = storageDeadline(task.deadline),
            priority = task.priority,
            note = task.note,
            status = newStatus,
        )
        withContext(Dispatchers.IO) {
            client.from("task").update(patch) {
                filter { eq("id", taskId); eq("project_id", projectId) }
            }
        }
        loadProjects()
    }
}
