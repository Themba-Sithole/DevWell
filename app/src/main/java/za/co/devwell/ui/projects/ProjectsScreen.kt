package za.co.devwell.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import za.co.devwell.model.DevProject
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.SmartTip
import za.co.devwell.model.SmartTipsEngine
import za.co.devwell.model.TaskPriority
import za.co.devwell.model.TaskStatus
import za.co.devwell.ui.theme.Forest

private val ProjectColours = listOf(
    "#174A35", "#1F6B4C", "#2D6E9E", "#B7791F", "#C0392B", "#6B4F8F",
)

private val BlankProject = DevProject(
    id = "",
    name = "",
    description = "",
    colour = ProjectColours.first(),
    deadline = "",
    progress = 0,
    tasks = emptyList(),
)

private val BlankTask = ProjectTask(
    id = "",
    title = "",
    deadline = "",
    priority = TaskPriority.MEDIUM,
    note = "",
    status = TaskStatus.OPEN,
)

@Composable
fun ProjectsScreen(
    projects: List<DevProject>,
    onToggle: (projectId: String, taskId: String) -> Unit,
    onCreateProject: (DevProject) -> Unit = {},
    onUpdateProject: (DevProject) -> Unit = {},
    onDeleteProject: (String) -> Unit = {},
    onAddTask: (String, ProjectTask) -> Unit = { _, _ -> },
    onUpdateTask: (String, ProjectTask) -> Unit = { _, _ -> },
    onDeleteTask: (String, String) -> Unit = { _, _ -> },
) {
    var filter by remember { mutableStateOf("All") }
    val tips = remember(projects) { SmartTipsEngine.generate(projects) }
    val visibleProjects = remember(projects, filter) {
        projects
            .map { project ->
                project to project.tasks.filter { filter == "All" || (filter == "Done") == it.isDone }
            }
            .filter { filter == "All" || it.second.isNotEmpty() }
    }

    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DevProject?>(null) }
    var deleting by remember { mutableStateOf<DevProject?>(null) }
    var taskProjectId by remember { mutableStateOf<String?>(null) }
    var editingTask by remember { mutableStateOf<Pair<DevProject, ProjectTask>?>(null) }
    var deletingTask by remember { mutableStateOf<Pair<DevProject, ProjectTask>?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { ProjectsHeader() }
            item { ProjectsSummary(projects) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Open", "Done").forEach { label ->
                        FilterPill(label, selected = filter == label, onClick = { filter = label })
                    }
                }
            }

            when {
                projects.isEmpty() -> item { EmptyProjectsState() }
                visibleProjects.isEmpty() -> item { EmptyFilterState(filter) }
                else -> items(visibleProjects, key = { it.first.id }) { (project, tasks) ->
                    ProjectCard(
                        project = project,
                        tasks = tasks,
                        filter = filter,
                        onToggle = onToggle,
                        onEdit = { editing = project },
                        onDelete = { deleting = project },
                        onAddTask = { taskProjectId = project.id },
                        onEditTask = { task -> editingTask = project to task },
                        onDeleteTask = { task -> deletingTask = project to task },
                    )
                }
            }

            item { SmartTipsCard(tips) }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New project") },
        )
    }

    if (showCreate) {
        ProjectFormDialog(
            title = "New project",
            initial = BlankProject,
            confirmLabel = "Create",
            onConfirm = {
                onCreateProject(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
    editing?.let { project ->
        ProjectFormDialog(
            title = "Edit project",
            initial = project,
            confirmLabel = "Save",
            onConfirm = {
                onUpdateProject(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { project ->
        DeleteProjectDialog(
            project = project,
            onConfirm = {
                onDeleteProject(project.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    taskProjectId?.let { projectId ->
        projects.firstOrNull { it.id == projectId }?.let { project ->
            TaskFormDialog(
                title = "New task",
                project = project,
                initial = BlankTask,
                confirmLabel = "Add",
                onConfirm = {
                    onAddTask(project.id, it)
                    taskProjectId = null
                },
                onDismiss = { taskProjectId = null },
            )
        }
    }
    editingTask?.let { (project, task) ->
        TaskFormDialog(
            title = "Edit task",
            project = project,
            initial = task,
            confirmLabel = "Save",
            onConfirm = {
                onUpdateTask(project.id, it)
                editingTask = null
            },
            onDismiss = { editingTask = null },
        )
    }
    deletingTask?.let { (project, task) ->
        DeleteTaskDialog(
            task = task,
            onConfirm = {
                onDeleteTask(project.id, task.id)
                deletingTask = null
            },
            onDismiss = { deletingTask = null },
        )
    }
}

@Composable
private fun ProjectsHeader() {
    Column {
        Text(
            "Projects & tasks",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Track workload beside your wellbeing",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectsSummary(projects: List<DevProject>) {
    val openTasks = projects.sumOf { project -> project.tasks.count { !it.isDone } }
    val avgProgress = if (projects.isEmpty()) {
        0
    } else {
        projects.map { it.progress }.average().roundToInt()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Forest, Color(0xFF1F6B4C))),
                RoundedCornerShape(24.dp),
            )
            .padding(vertical = 20.dp, horizontal = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SummaryStat("${projects.size}", "Projects", Modifier.weight(1f))
            SummaryDivider()
            SummaryStat("$openTasks", "Open tasks", Modifier.weight(1f))
            SummaryDivider()
            SummaryStat("$avgProgress%", "Avg progress", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(36.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectCard(
    project: DevProject,
    tasks: List<ProjectTask>,
    filter: String,
    onToggle: (projectId: String, taskId: String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (ProjectTask) -> Unit,
    onDeleteTask: (ProjectTask) -> Unit,
) {
    val accent = projectAccent(project.colour)
    val doneCount = project.tasks.count { it.isDone }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.3f))),
                    ),
            )
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            project.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (project.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                project.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    ProgressRing(project.progress, accent)
                    ProjectMenu(
                        expanded = menuOpen,
                        onExpandedChange = { menuOpen = it },
                        onEdit = {
                            menuOpen = false
                            onEdit()
                        },
                        onDelete = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaLabel(Icons.Outlined.Schedule, "Due ${project.deadline}")
                    MetaLabel(Icons.Outlined.TaskAlt, "$doneCount of ${project.tasks.size} done")
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "TASKS",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onAddTask) {
                        Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add task")
                    }
                    Box(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${tasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                if (tasks.isEmpty()) {
                    Text(
                        if (filter == "All") "No tasks yet in this project."
                        else "No ${filter.lowercase()} tasks in this project.",
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                tasks.forEachIndexed { index, task ->
                    TaskRow(
                        task = task,
                        accent = accent,
                        onToggle = { onToggle(project.id, task.id) },
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) },
                    )
                    if (index != tasks.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(start = 46.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Project options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Edit project") },
                onClick = onEdit,
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Delete project") },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Int, accent: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxSize(),
            color = accent,
            strokeWidth = 5.dp,
            trackColor = accent.copy(alpha = 0.15f),
        )
        Text(
            "$progress%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun MetaLabel(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskRow(
    task: ProjectTask,
    accent: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = accent,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
        ) {
            Text(
                task.title,
                fontWeight = FontWeight.Medium,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityBadge(task.priority)
                Text(
                    task.deadline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TaskMenu(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            onEdit = {
                menuOpen = false
                onEdit()
            },
            onDelete = {
                menuOpen = false
                onDelete()
            },
        )
    }
}

@Composable
private fun TaskMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Task options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Edit task") },
                onClick = onEdit,
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Delete task") },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
            )
        }
    }
}

@Composable
private fun PriorityBadge(priority: TaskPriority) {
    val tint = when (priority) {
        TaskPriority.HIGH -> Color(0xFFC0392B)
        TaskPriority.MEDIUM -> Color(0xFFB7791F)
        TaskPriority.LOW -> Color(0xFF2F855A)
    }
    Text(
        priority.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = tint,
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ProjectFormDialog(
    title: String,
    initial: DevProject,
    confirmLabel: String,
    onConfirm: (DevProject) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var deadline by remember(initial) { mutableStateOf(initial.deadline) }
    var colour by remember(initial) { mutableStateOf(initial.colour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline") },
                    placeholder = { Text("e.g. 18 Sep") },
                    singleLine = true,
                )
                ColourPicker(colour, onSelect = { colour = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            description = description.trim(),
                            deadline = deadline.trim(),
                            colour = colour,
                        )
                    )
                },
                enabled = name.isNotBlank() && deadline.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ColourPicker(selected: String, onSelect: (String) -> Unit) {
    Text(
        "Colour",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectColours.forEach { hex ->
            val isSelected = hex == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(projectAccent(hex))
                    .border(
                        2.dp,
                        if (isSelected) MaterialTheme.colorScheme.onSurface
                        else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Selected",
                        Modifier.size(18.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFormDialog(
    title: String,
    project: DevProject,
    initial: ProjectTask,
    confirmLabel: String,
    onConfirm: (ProjectTask) -> Unit,
    onDismiss: () -> Unit,
) {
    var taskTitle by remember(initial) { mutableStateOf(initial.title) }
    var deadline by remember(initial) { mutableStateOf(initial.deadline) }
    var priority by remember(initial) { mutableStateOf(initial.priority) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var status by remember(initial) { mutableStateOf(initial.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title in ${project.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Task title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline") },
                    placeholder = { Text("e.g. 18 Sep") },
                    singleLine = true,
                )
                Text(
                    "Priority",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { option ->
                        FilterChip(
                            selected = priority == option,
                            onClick = { priority = option },
                            label = { Text(option.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 2,
                    maxLines = 3,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Completed", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = status == TaskStatus.DONE,
                        onCheckedChange = { status = if (it) TaskStatus.DONE else TaskStatus.OPEN },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            title = taskTitle.trim(),
                            deadline = deadline.trim(),
                            priority = priority,
                            note = note.trim(),
                            status = status,
                        )
                    )
                },
                enabled = taskTitle.isNotBlank() && deadline.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteTaskDialog(
    task: ProjectTask,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete task?") },
        text = { Text("\"${task.title}\" will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteProjectDialog(
    project: DevProject,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete project?") },
        text = {
            Text("\"${project.name}\" and its ${project.tasks.size} task(s) will be permanently removed.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SmartTipsCard(tips: List<SmartTip>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = "Smart tips",
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(
                        "Smart tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Nudges based on your projects and check-ins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (tips.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "No tips right now — you're on track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tips.forEach { tip ->
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            tip.message,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Checklist,
                contentDescription = null,
                Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Projects assigned to your team will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyFilterState(filter: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing to show", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "No ${filter.lowercase()} tasks right now. Switch back to All to see everything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun projectAccent(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Forest)