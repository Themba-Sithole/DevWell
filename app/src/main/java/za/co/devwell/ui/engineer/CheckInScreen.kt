package za.co.devwell.ui.engineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckInDraft
import za.co.devwell.ui.auth.AuthFeedback
import za.co.devwell.ui.components.ButtonVariant
import za.co.devwell.ui.components.DevWellButton
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.DevWellDivider
import za.co.devwell.ui.components.DevWellTextField
import za.co.devwell.ui.components.PrivacyNotice
import za.co.devwell.ui.components.SegmentedProgress
import za.co.devwell.ui.theme.*

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

    Column(Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !isSaving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close check-in")
            }
            Text("Check-in", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("Step $step of 5", style = MaterialTheme.typography.labelMedium, color = InkMuted)
        }
        Spacer(Modifier.height(8.dp))
        SegmentedProgress(total = 5, current = step)
        Spacer(Modifier.height(20.dp))
        Text(titles[step - 1], style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                1 -> ChoiceStep("How are you feeling today?", WELLBEING_CHOICES, wellbeing) { wellbeing = it }
                2 -> ChoiceStep("How was your workload yesterday?", WORKLOAD_CHOICES, workload) { workload = it }
                3 -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SliderGroup("⚡ Energy", energy) { energy = it }
                    SliderGroup("🎯 Focus", focus) { focus = it }
                }
                4 -> Column {
                    ChoiceStep("How much did you work after hours?", AFTER_HOURS_CHOICES, afterHours) { afterHours = it }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This stays private and helps your team spot patterns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DevWellCard(padding = 16.dp) {
                        ReviewRow("Feeling", WELLBEING_CHOICES.getOrElse(wellbeing) { "-" })
                        DevWellDivider()
                        ReviewRow("Workload yesterday", WORKLOAD_CHOICES.getOrElse(workload) { "-" })
                        DevWellDivider()
                        ReviewRow("Energy and focus", "${energy.toInt()}/10 · ${focus.toInt()}/10")
                        DevWellDivider()
                        ReviewRow("After-hours work", AFTER_HOURS_CHOICES.getOrElse(afterHours) { "-" })
                    }
                    DevWellTextField(
                        note,
                        { if (it.length <= NOTE_MAX_LENGTH) note = it },
                        label = "Optional private note",
                        singleLine = false,
                        maxLines = 4,
                        enabled = !isSaving,
                    )
                    Text(
                        "${note.length}/$NOTE_MAX_LENGTH",
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        textAlign = TextAlign.End,
                    )
                    PrivacyNotice("Only you can view this individual response.")
                    AuthFeedback(error = errorMessage)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DevWellButton(
                "Back",
                onClick = { if (step == 1) onCancel() else step-- },
                modifier = Modifier.weight(1f),
                enabled = !isSaving,
                variant = ButtonVariant.Outline,
            )
            DevWellButton(
                if (isSaving) "Saving..." else if (step < 5) "Next" else "View my trends",
                onClick = {
                    if (step < 5) {
                        step++
                    } else {
                        onSubmit(buildCheckInDraft(wellbeing, workload, energy, focus, afterHours, note))
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = canProceed && !isSaving,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(question, style = MaterialTheme.typography.titleLarge, color = Ink)
        choices.forEachIndexed { index, label ->
            val isSelected = selected == index
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, onClick = { onSelect(index) }),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) AccentSurface else CardWhite,
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) DevGreen else BorderGreen,
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = DevGreen,
                            unselectedColor = BorderGreen,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) DevGreen else Ink,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderGroup(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Ink)
        Text("${value.toInt()}/10", style = MaterialTheme.typography.headlineSmall, color = DevGreen)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 1f..10f,
        steps = 8,
        colors = SliderDefaults.colors(
            thumbColor = DevGreen,
            activeTrackColor = DevGreen,
            inactiveTrackColor = TrackGreen,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
    Row {
        Text("Low", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Text("High", style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Text(
            value,
            Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
