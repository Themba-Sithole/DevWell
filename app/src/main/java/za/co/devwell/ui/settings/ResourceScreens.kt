package za.co.devwell.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.co.devwell.BuildConfig
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
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
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
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
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
fun WhatMyManagerSeesScreen(onBack: () -> Unit, onManagerPreview: () -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("What my manager sees", "How DevWell protects your data", onBack) }
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
                    "Preview the exact manager view", onManagerPreview,
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

private data class Faq(val question: String, val answer: String)

private val FAQ = listOf(
    Faq(
        "What is DevWell?",
        "DevWell is a daily well-being check-in for software teams. A check-in takes about " +
            "30 seconds and records how you feel about your wellbeing, workload, energy, focus " +
            "and after-hours time, so patterns become visible before they become burnout.",
    ),
    Faq(
        "Who can see my check-ins?",
        "Only you. Your individual responses and private notes are never shown to managers, " +
            "administrators or teammates. Team insights are published only after at least " +
            "${za.co.devwell.model.PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} distinct members " +
            "have checked in, and even then only as anonymised averages.",
    ),
    Faq(
        "Can my manager see my notes or exact scores?",
        "No. Managers see team averages and response counts only. They can never open an " +
            "individual check-in, a private note, or a personal trend.",
    ),
    Faq(
        "How often should I check in?",
        "Once a day is ideal, at roughly the same time. One check-in per day is saved — " +
            "checking in again later simply updates today's entry.",
    ),
    Faq(
        "Is the AI assistant medical advice?",
        "No. It offers general, practical guidance only. If you are struggling, please speak to " +
            "a qualified professional or your GP.",
    ),
    Faq(
        "I want to change my team or delete my account",
        "Ask your team manager or company administrator. They can review join requests and " +
            "suspend accounts, and can remove your data on request.",
    ),
)

private const val SUPPORT_ADDRESS = "support@devwell.io"

/** Compose a plain-text mailto: message with the app and device details attached. */
private fun emailBody(prefix: String): String = buildString {
    appendLine(prefix)
    appendLine()
    appendLine("App version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
    append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
        "Android ${android.os.Build.VERSION.RELEASE}")
}

private fun openEmail(context: Context, address: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (error: Exception) {
        Toast.makeText(context, "No email app found — write to $address instead.", Toast.LENGTH_LONG).show()
    }
}

/**
 * Expandable row used by the FAQ and acknowledgements sections.
 * The horizontal padding keeps the label and its expanded body inset from the
 * card border so the text lines up with the section header above it.
 */
@Composable
private fun ExpandableRow(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    horizontalPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                Modifier.size(18.dp).rotate(rotation), tint = PaleGreen,
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
            Modifier.size(18.dp), tint = PaleGreen,
        )
    }
}

@Composable
private fun PolicyText(body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        body.split("\n\n").forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

/** A titled card holding one or more policy paragraphs. */
@Composable
private fun PolicySection(title: String, vararg paragraphs: String) {
    DevWellCard {
        SectionLabel(title)
        Spacer(Modifier.height(10.dp))
        PolicyText(paragraphs.joinToString("\n\n"))
    }
}

/** Header card shared by the privacy policy and terms of service pages. */
@Composable
private fun PolicyHeader(icon: ImageVector, subtitle: String, body: String) {
    DevWellCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(subtitle, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Last updated 20 October 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PolicyText(body)
    }
}

/** Dedicated privacy policy page, opened from Help & about. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit = {}) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Privacy policy", "What DevWell stores and who can see it", onBack) }
        item {
            PolicyHeader(
                icon = Icons.Outlined.PrivacyTip,
                subtitle = "Your check-ins stay yours",
                body = "This policy explains what DevWell stores, who can see it and how you " +
                    "can have it removed. It applies to every account, whatever your role.",
            )
        }
        item {
            PolicySection(
                "What DevWell stores",
                "Your check-in responses — wellbeing, workload, energy, focus, after-hours " +
                    "minutes and any note you write — plus your account details (name, email, " +
                    "company and team) and your notification preferences on this device.",
                "Your sign-in session is kept in encrypted storage on this phone, so you are " +
                    "not asked to log in every time you open the app.",
            )
        }
        item {
            PolicySection(
                "Who can see it",
                "Your individual responses and private notes are visible only to you. " +
                    "Managers, administrators and teammates can never open a single check-in " +
                    "or a personal trend.",
                "Managers only see anonymised team summaries, and only once at least " +
                    "${za.co.devwell.model.PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} distinct " +
                    "members have checked in. Even then the numbers are averages — never names.",
            )
        }
        item {
            PolicySection(
                "How it is protected",
                "Data is stored in a Supabase project protected by row-level security, so " +
                    "each account can only read what belongs to it, and traffic is encrypted " +
                    "in transit.",
                "DevWell never stores your password. Sign-in is handled directly by Supabase " +
                    "Auth, and your credentials stay on their secure servers.",
            )
        }
        item {
            PolicySection(
                "The AI assistant",
                "Assistant conversations are sent to the AI provider only after you consent, " +
                    "and they are not stored by DevWell. Avoid including names or sensitive " +
                    "details in your messages.",
            )
        }
        item {
            PolicySection(
                "Your choices",
                "Ask your team manager or company administrator to delete your account and " +
                    "its data at any time. The deletion covers your check-ins, profile and " +
                    "notification preferences.",
                "Questions about this policy? Write to $SUPPORT_ADDRESS and we will clarify " +
                    "what is held for your account.",
            )
        }
        item { PrivacyNotice("DevWell is a wellbeing tool — not a surveillance or performance tracking system.") }
    }
}

/** Dedicated terms of service page, opened from Help & about. */
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Terms of service", "Using DevWell responsibly", onBack) }
        item {
            PolicyHeader(
                icon = Icons.Outlined.Gavel,
                subtitle = "Using DevWell responsibly",
                body = "These terms describe what you can expect from DevWell and what we " +
                    "ask in return. By signing in you agree to them.",
            )
        }
        item {
            PolicySection(
                "Not medical advice",
                "DevWell is a student-built well-being companion. It offers general, " +
                    "practical guidance only and is not medical, psychological or professional " +
                    "advice.",
                "If you are in crisis or struggling, please contact a qualified professional " +
                    "or your local emergency services straight away.",
            )
        }
        item {
            PolicySection(
                "Using the app",
                "Use DevWell for its intended purpose, keep your sign-in details secure, and " +
                    "do not attempt to access another member's data or reports.",
                "Your account belongs to you. If you share a device, sign out when you are " +
                    "finished so nobody else can open your check-ins.",
            )
        }
        item {
            PolicySection(
                "Anonymity in team insights",
                "Team insights stay hidden until at least " +
                    "${za.co.devwell.model.PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} distinct " +
                    "members have checked in. Below that threshold no averages are shown, which " +
                    "keeps individual responses unidentifiable.",
                "Managers must not ask members to reveal their check-ins, and aggregate results " +
                    "are for wellbeing support — never for performance reviews.",
            )
        }
        item {
            PolicySection(
                "Accounts and suspension",
                "Accounts that misuse the service — for example by probing another member's " +
                    "data or by using DevWell outside its purpose — may be suspended by an " +
                    "administrator.",
                "Your manager or company administrator can review join requests, suspend " +
                    "accounts and remove your data on request.",
            )
        }
        item {
            PolicySection(
                "Availability and changes",
                "DevWell is provided as-is for the duration of the project. Features may " +
                    "change or the service may stop without notice, and no uptime guarantee is " +
                    "given.",
                "These terms are governed by the rules of the academic project they were " +
                    "built for.",
            )
        }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)) {
                    SectionLabel("Questions about these terms")
                }
                DevWellDivider()
                ContactRow(
                    icon = Icons.Outlined.Email,
                    title = "Email support",
                    subtitle = SUPPORT_ADDRESS,
                    onClick = {
                        openEmail(context, SUPPORT_ADDRESS, "DevWell terms of service question",
                            emailBody("Hi DevWell team,\n\nAbout the terms of service:"))
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        item { PrivacyNotice("Nothing on this page is tracked or reported — it is here for you.") }
    }
}

/** Help & About: FAQ, support contacts, policies and app version. */
@Composable
fun HelpAboutScreen(
    onBack: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    onTermsOfService: () -> Unit = {},
) {
    val context = LocalContext.current
    var openFaq by rememberSaveable { mutableIntStateOf(-1) }
    var acknowledgementsOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Help & about", "Support, policies and app details", onBack) }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)) {
                    SectionLabel("Frequently asked questions")
                }
                FAQ.forEachIndexed { index, faq ->
                    DevWellDivider()
                    ExpandableRow(
                        title = faq.question,
                        expanded = openFaq == index,
                        onClick = { openFaq = if (openFaq == index) -1 else index },
                    ) {
                        Text(faq.answer, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)) {
                    SectionLabel("Contact support")
                }
                DevWellDivider()
                ContactRow(
                    icon = Icons.Outlined.Email,
                    title = "Email support",
                    subtitle = SUPPORT_ADDRESS,
                    onClick = {
                        openEmail(context, SUPPORT_ADDRESS, "DevWell support request", emailBody("Hi DevWell team,"))
                    },
                )
                DevWellDivider()
                ContactRow(
                    icon = Icons.Outlined.BugReport,
                    title = "Report a problem",
                    subtitle = "Tell us what went wrong — version and device are attached",
                    onClick = {
                        openEmail(
                            context, SUPPORT_ADDRESS,
                            "DevWell problem report",
                            emailBody("What happened:\n\nWhat I expected:\n\nSteps to reproduce:"),
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)) {
                    SectionLabel("Policies")
                }
                DevWellDivider()
                ContactRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy policy",
                    subtitle = "What DevWell stores and who can see it",
                    onClick = onPrivacyPolicy,
                )
                DevWellDivider()
                ContactRow(
                    icon = Icons.Outlined.Gavel,
                    title = "Terms of service",
                    subtitle = "Using DevWell responsibly",
                    onClick = onTermsOfService,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp, start = 14.dp, end = 14.dp)) {
                    SectionLabel("About")
                }
                DevWellDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(Icons.Outlined.Info)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "DevWell for Android",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
                DevWellDivider()
                ExpandableRow(
                    title = "Acknowledgements",
                    subtitle = "Open-source software and credits",
                    expanded = acknowledgementsOpen,
                    onClick = { acknowledgementsOpen = !acknowledgementsOpen },
                ) {
                    listOf(
                        "Jetpack Compose, Material 3, Navigation Compose and WorkManager — Apache-2.0",
                        "Supabase Kotlin (Auth, PostgREST, Functions) — Apache-2.0",
                        "Ktor and kotlinx.serialization — Apache-2.0",
                        "Outfit and Inter typefaces — SIL Open Font License",
                    ).forEach { credit -> BulletRow(credit, DevGreen) }
                    Text(
                        "Thanks to the DevWell team, our mentors and every engineer who tested " +
                            "the check-in flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        item {
            PrivacyNotice("Nothing on this page is tracked or reported — it is here for you.")
        }
    }
}
