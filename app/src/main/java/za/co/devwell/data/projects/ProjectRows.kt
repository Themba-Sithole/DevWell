package za.co.devwell.data.projects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import za.co.devwell.model.DevProject
import za.co.devwell.model.ProjectDeadlineParser
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.TaskPriority
import za.co.devwell.model.TaskStatus

@Serializable
data class ProjectRow(
    @SerialName("id") val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("colour") val colour: String = "#174A35",
    @SerialName("deadline") val deadline: String,
    @SerialName("progress") val progress: Int = 0,
) {
    fun toDomain(tasks: List<ProjectTask> = emptyList()) = DevProject(
        id = id,
        name = name,
        description = description,
        colour = colour,
        deadline = formatDeadline(deadline),
        progress = progress,
        tasks = tasks,
    )
}

@Serializable
data class ProjectInsertRow(
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("colour") val colour: String = "#174A35",
    @SerialName("deadline") val deadline: String,
)

@Serializable
data class ProjectPatchRow(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("colour") val colour: String,
    @SerialName("deadline") val deadline: String,
)

@Serializable
data class ProjectTaskRow(
    @SerialName("id") val id: String = "",
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("deadline") val deadline: String,
    @SerialName("priority") val priority: TaskPriority = TaskPriority.MEDIUM,
    @SerialName("note") val note: String = "",
    @SerialName("status") val status: TaskStatus = TaskStatus.OPEN,
    @SerialName("position") val position: Int = 0,
) {
    fun toDomain() = ProjectTask(
        id = id,
        title = title,
        deadline = formatDeadline(deadline),
        priority = priority,
        note = note,
        status = status,
        position = position,
    )
}

@Serializable
data class ProjectTaskInsertRow(
    @SerialName("project_id") val projectId: String,
    @SerialName("title") val title: String,
    @SerialName("deadline") val deadline: String,
    @SerialName("priority") val priority: TaskPriority,
    @SerialName("note") val note: String = "",
    @SerialName("status") val status: TaskStatus = TaskStatus.OPEN,
    @SerialName("position") val position: Int = 0,
)

@Serializable
data class ProjectTaskPatchRow(
    @SerialName("title") val title: String,
    @SerialName("deadline") val deadline: String,
    @SerialName("priority") val priority: TaskPriority,
    @SerialName("note") val note: String,
    @SerialName("status") val status: TaskStatus,
)

fun storageDeadline(label: String): String = ProjectDeadlineParser.toStorage(label)

fun isValidDeadline(label: String): Boolean = ProjectDeadlineParser.isValid(label)

fun formatDeadline(iso: String): String = try {
    ProjectDeadlineParser.toDisplay(iso)
} catch (_: Exception) {
    iso
}
