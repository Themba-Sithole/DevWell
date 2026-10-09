package za.co.devwell.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*

private data class Tip(
    val emoji: String,
    val category: String,
    val title: String,
    val body: String,
    val tag: String,
)

private val TIPS = listOf(
    Tip("⏱️", "Focus", "The Pomodoro Technique",
        "Work in focused 25-minute sessions with 5-minute breaks. After 4 sessions, take a longer " +
            "15–30 minute break. This rhythm preserves deep focus and prevents mental fatigue from accumulating.",
        "25 min work · 5 min rest"),
    Tip("😴", "Sleep", "Protect Your Sleep",
        "Engineers who sleep 7–9 hours make fewer bugs and write cleaner code. Avoid screens for 1 hour " +
            "before bed. Keep a consistent sleep and wake time — even on weekends.",
        "7–9 hrs recommended"),
    Tip("🚶", "Movement", "Move Every Hour",
        "Sitting for long periods increases fatigue and reduces cognitive performance. Set a timer to stand " +
            "and move for 2–3 minutes every hour. Even a short walk to make a drink can reset your focus.",
        "2 min every hour"),
    Tip("🔕", "Boundaries", "Protect After-Hours Time",
        "Being constantly available outside work hours leads to burnout faster than almost anything else. " +
            "Set a clear sign-off time and use DevWell to track your after-hours patterns over time.",
        "Log it in your check-in"),
    Tip("🔄", "Context", "Reduce Context Switching",
        "Every interruption costs 15–20 minutes of deep focus recovery. Time-box your tasks, batch your Slack " +
            "and email checks, and protect at least 2 hours of uninterrupted flow time each day.",
        "Batch notifications"),
    Tip("👁️", "Eye Care", "The 20-20-20 Rule",
        "Every 20 minutes, look at something 20 feet away for 20 seconds. This relaxes the eye muscles strained " +
            "by screen work and significantly reduces eye fatigue over a full working day.",
        "Every 20 minutes"),
    Tip("🌱", "Wellbeing", "Spot Burnout Early",
        "Watch for: feeling disconnected from your work, chronic exhaustion even after rest, reduced satisfaction " +
            "in tasks you once enjoyed, and increased cynicism. These are signals to act — not to push through.",
        "Check your trends"),
)

@Composable
fun WellbeingTipsScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Well-being Tips", "Evidence-backed practices for developer health", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(AccentSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, TrackGreen, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🌿", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Small, consistent habits make the biggest difference to your long-term health " +
                        "and productivity as an engineer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DevGreen,
                )
            }
        }
        items(TIPS) { tip ->
            DevWellCard(padding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(42.dp)
                            .background(SurfaceMuted, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tip.emoji, style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        SectionLabel(tip.category)
                        Spacer(Modifier.height(2.dp))
                        Text(tip.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(tip.body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        Spacer(Modifier.height(10.dp))
                        Chip(tip.tag, skin = ChipSkin.Green)
                    }
                }
            }
        }
        item {
            PrivacyNotice("These tips are for your benefit — nothing here is tracked or reported.")
        }
    }
}

private data class Exercise(
    val emoji: String,
    val title: String,
    val duration: String,
    val description: String,
    val steps: List<String>,
)

private val EXERCISES = listOf(
    Exercise("👁️", "Eye Strain Relief", "2 min", "Relieves tension from prolonged screen use.", listOf(
        "Look away from your screen at an object 20 feet (6 m) away",
        "Hold your gaze for 20 full seconds — blink naturally",
        "Gently close your eyes and cup them with your palms for 10 seconds",
        "Repeat 3 times — do this every 20–30 minutes of screen work",
    )),
    Exercise("🧘", "Box Breathing", "4 min", "Resets your nervous system and clears mental fog.", listOf(
        "Sit upright with feet flat on the floor",
        "Exhale fully to empty your lungs",
        "Inhale slowly for 4 seconds through your nose",
        "Hold your breath for 4 seconds",
        "Exhale slowly for 4 seconds through your mouth",
        "Hold empty for 4 seconds — repeat 4–6 cycles",
    )),
    Exercise("🤸", "Neck & Shoulder Release", "3 min", "Dissolves tension built up from hunching over a keyboard.", listOf(
        "Slowly tilt your right ear toward your right shoulder — hold 15 sec",
        "Return to centre, then tilt left — hold 15 sec",
        "Roll shoulders backward 5 times, then forward 5 times",
        "Interlock fingers behind your head, gently pull chin to chest — hold 20 sec",
        "Repeat the sequence twice",
    )),
    Exercise("🤲", "Wrist & Hand Stretch", "2 min", "Prevents repetitive strain injury from constant typing.", listOf(
        "Extend your right arm, palm facing out (like a stop sign)",
        "Use your left hand to gently pull the fingers back — hold 15 sec",
        "Flip your palm down and pull fingers gently downward — hold 15 sec",
        "Make a tight fist, then spread fingers wide — repeat 10 times",
        "Repeat on the left hand",
    )),
    Exercise("🪑", "Desk Posture Reset", "1 min", "A quick checklist to realign your body at the desk.", listOf(
        "Sit back fully in your chair — don't perch on the edge",
        "Feet flat on the floor, hips and knees at ~90°",
        "Monitor top aligned with eye level, arm's length away",
        "Elbows close to your body, wrists neutral (not bent up or down)",
        "Relax your shoulders — they should not be raised or hunched",
    )),
    Exercise("🧠", "Mindful Reset", "5 min", "A short mental break to clear cognitive overload.", listOf(
        "Step away from your screen and find a comfortable seat",
        "Set a 5-minute timer, then put your phone face-down",
        "Close your eyes and focus only on the physical sensation of breathing",
        "When a thought enters, acknowledge it without judgment and return to breath",
        "After the timer, take one slow deep breath before returning to work",
    )),
)

@Composable
fun ExercisesScreen(onBack: () -> Unit) {
    var openIndex by rememberSaveable { mutableIntStateOf(-1) }
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Exercises & Guides", "Quick routines designed for desk-based engineers", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(AccentSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, TrackGreen, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💪", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap any exercise to expand the full step-by-step guide. These take under 5 minutes " +
                        "and can be done at your desk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DevGreen,
                )
            }
        }
        items(EXERCISES.size) { index ->
            ExerciseCard(
                exercise = EXERCISES[index],
                expanded = openIndex == index,
                onClick = { openIndex = if (openIndex == index) -1 else index },
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    DevWellCard(padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp)
                    .background(SurfaceMuted, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(exercise.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(exercise.description, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Chip(exercise.duration, skin = ChipSkin.Green)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                Modifier.size(18.dp).rotate(rotation), tint = PaleGreen,
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(color = SurfaceMuted)
                Spacer(Modifier.height(2.dp))
                exercise.steps.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(20.dp).background(DevGreen, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                        Text(
                            step,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacySettingsScreen(onBack: () -> Unit, onManagerPreview: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Privacy Settings", "How DevWell protects your data", onBack) }
        item {
            DevWellCard {
                SectionLabel("What DevWell stores")
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Your check-in responses — private to you",
                    "Your account details (name, email, company)",
                    "Your notification preferences on this device",
                ).forEach { item ->
                    BulletRow(item, DevGreen)
                }
            }
        }
        item {
            DevWellCard {
                SectionLabel("What managers see")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Managers only see anonymised team summaries, shown after at least " +
                        "${za.co.devwell.model.PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} team responses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Your name or individual answers",
                    "Your exact scores or personal trends",
                    "Your private check-in notes",
                ).forEach { item ->
                    BulletRow(item, RedText, icon = "✕")
                }
                Spacer(Modifier.height(12.dp))
                DevWellButton(
                    "Preview what my manager sees", onManagerPreview,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Outline,
                )
            }
        }
        item {
            DevWellCard {
                SectionLabel("On this device")
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your sign-in is stored securely on this phone. If you share this device, " +
                        "sign out from your profile when you are finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        item {
            PrivacyNotice("DevWell is a wellbeing tool — not a surveillance or performance tracking system.")
        }
    }
}

@Composable
private fun BulletRow(text: String, dotColor: androidx.compose.ui.graphics.Color, icon: String = "•") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.bodyMedium, color = dotColor, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}
