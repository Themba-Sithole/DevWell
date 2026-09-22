package za.co.devwell.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.DevProject
import za.co.devwell.model.SmartTipsEngine
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle

@Composable
fun ProjectsScreen(
    projects: List<DevProject>,
    onToggle: (projectId: String, taskId: String) -> Unit,
) {
    var filter by remember { mutableStateOf("All") }
    val tips = remember(projects) { SmartTipsEngine.generate(projects) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Projects & tasks", "Track workload beside your wellbeing") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Open", "Done").forEach { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = label },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (projects.isEmpty()) {
            item { DevWellCard { Text("No projects yet.") } }
        }
        items(projects) { project ->
            DevWellCard {
                Text(project.name, style = MaterialTheme.typography.titleLarge)
                Text("Due ${project.deadline} • ${project.progress}% complete")
                LinearProgressIndicator(
                    progress = { project.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                project.tasks
                    .filter { filter == "All" || (filter == "Done") == it.isDone }
                    .forEach { task ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(project.id, task.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = task.isDone, onCheckedChange = { onToggle(project.id, task.id) })
                            Column {
                                Text(task.title)
                                Text(
                                    "${task.priority.name.lowercase()} • ${task.deadline}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
            }
        }
        item {
            DevWellCard {
                Text("Smart tips", fontWeight = FontWeight.Bold)
                if (tips.isEmpty()) {
                    Text("No tips right now.")
                } else {
                    tips.forEach { tip ->
                        Spacer(Modifier.height(8.dp))
                        Text("• ${tip.message}")
                    }
                }
            }
        }
    }
}
