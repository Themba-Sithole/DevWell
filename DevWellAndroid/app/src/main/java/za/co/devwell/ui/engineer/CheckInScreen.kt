package za.co.devwell.ui.engineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckInDraft
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

/**
 * Member 2 — Five-step wellbeing check-in (FR4, Story 1).
 * Persist via WellbeingRepository; add offline queue later (NFR7).
 */
@Composable
fun CheckInScreen(onCancel: () -> Unit, onSubmit: (CheckInDraft) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var wellbeing by remember { mutableIntStateOf(3) }
    var workload by remember { mutableIntStateOf(3) }
    var energy by remember { mutableFloatStateOf(5f) }
    var focus by remember { mutableFloatStateOf(5f) }
    var afterHours by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    val titles = listOf("Today's wellbeing", "Workload", "Energy and focus", "After-hours work", "Review")

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onCancel) { Text("Close") }
        LinearProgressIndicator(progress = { step / 5f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Step $step of 5", color = MaterialTheme.colorScheme.primary)
        PageTitle(titles[step - 1])
        Box(Modifier.weight(1f)) {
            when (step) {
                1 -> ChoiceStep(
                    "How are you feeling today?",
                    listOf("Struggling", "Not great", "Okay", "Good", "Great"),
                    wellbeing,
                ) { wellbeing = it }
                2 -> ChoiceStep(
                    "How was your workload yesterday?",
                    listOf("Very light", "Light", "Moderate", "Heavy", "Very heavy"),
                    workload,
                ) { workload = it }
                3 -> Column {
                    Text("Energy: ${energy.toInt()}/10")
                    Slider(value = energy, onValueChange = { energy = it }, valueRange = 1f..10f, steps = 8)
                    Spacer(Modifier.height(24.dp))
                    Text("Focus: ${focus.toInt()}/10")
                    Slider(value = focus, onValueChange = { focus = it }, valueRange = 1f..10f, steps = 8)
                }
                4 -> ChoiceStep(
                    "How much did you work after hours?",
                    listOf("None", "Up to 30 min", "30–60 min", "1–2 hours", "More than 2 hours"),
                    afterHours,
                ) { afterHours = it }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyNotice("Only you can view this individual response.")
                    Text("Wellbeing: ${wellbeing + 1}/5 • Workload: ${workload + 1}/5")
                    Text("Energy: ${energy.toInt()}/10 • Focus: ${focus.toInt()}/10")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional private note") },
                        minLines = 3,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (step == 1) onCancel() else step-- },
                modifier = Modifier.weight(1f),
            ) { Text("Back") }
            Button(
                onClick = {
                    if (step < 5) {
                        step++
                    } else {
                        onSubmit(
                            CheckInDraft(
                                wellbeing = wellbeing + 1,
                                workload = workload + 1,
                                energy = energy.toInt(),
                                focus = focus.toInt(),
                                afterHoursMinutes = listOf(0, 15, 45, 90, 150)[afterHours],
                                note = note,
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (step < 5) "Next" else "Submit") }
        }
    }
}

@Composable
private fun ChoiceStep(
    question: String,
    choices: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question, style = MaterialTheme.typography.titleLarge)
        choices.forEachIndexed { index, label ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == index, onClick = { onSelect(index) }),
                shape = RoundedCornerShape(16.dp),
                color = if (selected == index) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == index, onClick = { onSelect(index) })
                    Text(label)
                }
            }
        }
    }
}
