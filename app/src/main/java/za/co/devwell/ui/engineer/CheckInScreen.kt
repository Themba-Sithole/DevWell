package za.co.devwell.ui.engineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckInDraft
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

/**
 * Member 2 — Five-step wellbeing check-in (FR4, Story 1).
 * Persist via WellbeingRepository; add offline queue later (NFR7).
 *
 * isSaving / errorMessage have defaults so existing callers keep compiling.
 * The ViewModel will drive them once saving is wired up.
 */

private val WELLBEING_CHOICES = listOf("Struggling", "Not great", "Okay", "Good", "Great")
private val WORKLOAD_CHOICES = listOf("Very light", "Light", "Moderate", "Heavy", "Very heavy")
private val AFTER_HOURS_CHOICES = listOf("None", "Up to 30 min", "30–60 min", "1–2 hours", "More than 2 hours")
private val AFTER_HOURS_MINUTES = listOf(0, 15, 45, 90, 150)
private const val NOTE_MAX_LENGTH = 500
private const val NOT_SELECTED = -1

/** Pure mapping functions so they can be unit tested without Compose. */
internal fun afterHoursMinutesFor(choiceIndex: Int): Int =
    AFTER_HOURS_MINUTES[choiceIndex.coerceIn(0, AFTER_HOURS_MINUTES.lastIndex)]

internal fun buildCheckInDraft(
    wellbeingIndex: Int,
    workloadIndex: Int,
    energy: Float,
    focus: Float,
    afterHoursIndex: Int,
    note: String,
): CheckInDraft = CheckInDraft(
    wellbeing = wellbeingIndex + 1,
    workload = workloadIndex + 1,
    energy = energy.toInt(),
    focus = focus.toInt(),
    afterHoursMinutes = afterHoursMinutesFor(afterHoursIndex),
    note = note.trim(),
)

@Composable
fun CheckInScreen(
    onCancel: () -> Unit,
    onSubmit: (CheckInDraft) -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    // rememberSaveable keeps answers if the phone rotates.
    var step by rememberSaveable { mutableIntStateOf(1) }
    var wellbeing by rememberSaveable { mutableIntStateOf(NOT_SELECTED) }
    var workload by rememberSaveable { mutableIntStateOf(NOT_SELECTED) }
    var energy by rememberSaveable { mutableFloatStateOf(5f) }
    var focus by rememberSaveable { mutableFloatStateOf(5f) }
    var afterHours by rememberSaveable { mutableIntStateOf(NOT_SELECTED) }
    var note by rememberSaveable { mutableStateOf("") }
    val titles = listOf("Today's wellbeing", "Workload", "Energy and focus", "After-hours work", "Review")

    // Steps 1, 2 and 4 need an answer before "Next" is allowed.
    val canProceed = when (step) {
        1 -> wellbeing != NOT_SELECTED
        2 -> workload != NOT_SELECTED
        4 -> afterHours != NOT_SELECTED
        else -> true
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onCancel, enabled = !isSaving) { Text("Close") }
        LinearProgressIndicator(progress = { step / 5f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Step $step of 5", color = MaterialTheme.colorScheme.primary)
        PageTitle(titles[step - 1])
        Box(Modifier.weight(1f)) {
            when (step) {
                1 -> ChoiceStep("How are you feeling today?", WELLBEING_CHOICES, wellbeing) { wellbeing = it }
                2 -> ChoiceStep("How was your workload yesterday?", WORKLOAD_CHOICES, workload) { workload = it }
                3 -> Column {
                    Text("Energy: ${energy.toInt()}/10")
                    Slider(value = energy, onValueChange = { energy = it }, valueRange = 1f..10f, steps = 8)
                    Spacer(Modifier.height(24.dp))
                    Text("Focus: ${focus.toInt()}/10")
                    Slider(value = focus, onValueChange = { focus = it }, valueRange = 1f..10f, steps = 8)
                }
                4 -> ChoiceStep("How much did you work after hours?", AFTER_HOURS_CHOICES, afterHours) { afterHours = it }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyNotice("Only you can view this individual response.")
                    Text("Feeling: ${WELLBEING_CHOICES.getOrElse(wellbeing) { "-" }}")
                    Text("Workload yesterday: ${WORKLOAD_CHOICES.getOrElse(workload) { "-" }}")
                    Text("Energy: ${energy.toInt()}/10 • Focus: ${focus.toInt()}/10")
                    Text("After-hours work: ${AFTER_HOURS_CHOICES.getOrElse(afterHours) { "-" }}")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= NOTE_MAX_LENGTH) note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional private note") },
                        supportingText = { Text("${note.length}/$NOTE_MAX_LENGTH") },
                        minLines = 3,
                        enabled = !isSaving,
                    )
                    if (errorMessage != null) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (step == 1) onCancel() else step-- },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Back") }
            Button(
                onClick = {
                    if (step < 5) {
                        step++
                    } else {
                        onSubmit(buildCheckInDraft(wellbeing, workload, energy, focus, afterHours, note))
                    }
                },
                enabled = canProceed && !isSaving,
                modifier = Modifier.weight(1f),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (step < 5) "Next" else "Submit")
                }
            }
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