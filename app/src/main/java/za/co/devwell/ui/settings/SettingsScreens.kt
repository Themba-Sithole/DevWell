package za.co.devwell.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.NotificationPreferences
import za.co.devwell.model.UserRole
import za.co.devwell.ui.auth.AuthFeedback
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*

@Composable
fun ProfileScreen(
    role: UserRole,
    onNotifications: () -> Unit,
    onSignOut: () -> Unit,
    name: String = "",
    email: String = "",
    prefs: NotificationPreferences? = null,
    onToggleDaily: (Boolean) -> Unit = {},
    onTips: () -> Unit = {},
    onExercises: () -> Unit = {},
    onPrivacySettings: () -> Unit = {},
    onTeamSettings: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Ink)
        DevWellCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(name.ifBlank { "?" }.initials(), size = 56.dp)
                Column {
                    Text(
                        name.ifBlank { "DevWell user" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                    )
                    if (email.isNotBlank()) {
                        Text(email, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    } else {
                        Text(
                            role.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Chip(
                        when (role) {
                            UserRole.ENGINEER -> "✓ Member"
                            UserRole.MANAGER -> "Manager"
                            UserRole.ADMIN -> "Admin"
                        },
                        skin = ChipSkin.Green,
                    )
                }
            }
        }
        DevWellCard {
            SectionLabel("Reminders")
            Spacer(Modifier.height(8.dp))
            PrefRow(
                "Daily check-in reminder",
                checked = prefs?.dailyEnabled ?: true,
                onCheckedChange = onToggleDaily,
                subtitle = "9:00 AM",
            )
        }
        if (role == UserRole.ENGINEER) {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp)) { SectionLabel("Resources", Modifier.padding(horizontal = 14.dp)) }
                Spacer(Modifier.height(4.dp))
                SettingsRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "Well-being tips for developers",
                    subtitle = "Evidence-backed practices for developer health",
                    onClick = onTips,
                )
                DevWellDivider()
                SettingsRow(
                    icon = Icons.Outlined.FitnessCenter,
                    title = "Quick exercises & guides",
                    subtitle = "Desk-friendly routines under 5 minutes",
                    onClick = onExercises,
                )
            }
        }
        DevWellCard(padding = 0.dp) {
            Column(Modifier.padding(top = 14.dp)) { SectionLabel("Account", Modifier.padding(horizontal = 14.dp)) }
            Spacer(Modifier.height(4.dp))
            if (role == UserRole.MANAGER) {
                SettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "Team settings",
                    subtitle = "Name, timezone and check-in schedule",
                    onClick = onTeamSettings,
                )
                DevWellDivider()
            }
            SettingsRow(
                icon = Icons.Outlined.Shield,
                title = "Privacy settings",
                subtitle = "What DevWell stores and shares",
                onClick = onPrivacySettings,
            )
            DevWellDivider()
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Notification preferences",
                subtitle = "Daily reminder, quiet hours and category controls",
                onClick = onNotifications,
            )
            DevWellDivider()
            Row(
                Modifier.fillMaxWidth()
                    .clickableRow(onSignOut)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout, null,
                    Modifier.size(36.dp).padding(8.dp), tint = RedText,
                )
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RedText,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        PrivacyNotice(
            when (role) {
                UserRole.ENGINEER ->
                    "Your data is private and secure. Managers only see anonymised team insights."
                UserRole.MANAGER ->
                    "You only see anonymised, aggregated team data. No individual responses are accessible."
                UserRole.ADMIN ->
                    "The admin role manages access but does not expose individual wellbeing responses."
            },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickableRow(onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
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
            Modifier.size(20.dp), tint = PaleGreen,
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
fun NotificationsScreen(
    prefs: NotificationPreferences,
    onSave: (NotificationPreferences) -> Unit,
    onBack: () -> Unit = {},
) {
    var daily by remember(prefs) { mutableStateOf(prefs.dailyEnabled) }
    var checkIn by remember(prefs) { mutableStateOf(prefs.checkInReminders) }
    var teamAlerts by remember(prefs) { mutableStateOf(prefs.teamAlerts) }
    var tipNudges by remember(prefs) { mutableStateOf(prefs.tipNudges) }
    var quietStart by remember(prefs) { mutableStateOf(prefs.quietStart.orEmpty()) }
    var quietEnd by remember(prefs) { mutableStateOf(prefs.quietEnd.orEmpty()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Notification preferences", "Daily reminder, quiet hours and category controls", onBack) }
        item {
            DevWellCard(padding = 4.dp) {
                PrefRow("Daily check-in reminder", daily, { daily = it }, subtitle = "Nudge me at my usual check-in time")
                PrefRow("Check-in reminders", checkIn, { checkIn = it }, subtitle = "Remind me when I miss a day")
                PrefRow("Team alerts", teamAlerts, { teamAlerts = it }, subtitle = "Anonymous team milestone notices")
                PrefRow("Tip nudges", tipNudges, { tipNudges = it }, subtitle = "Weekly well-being tips")
            }
        }
        item {
            DevWellCard {
                SectionLabel("Quiet hours")
                Spacer(Modifier.height(4.dp))
                Text(
                    "No notifications are sent during quiet hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(12.dp))
                DevWellTextField(quietStart, { quietStart = it }, label = "Quiet start (HH:mm)")
                Spacer(Modifier.height(10.dp))
                DevWellTextField(quietEnd, { quietEnd = it }, label = "Quiet end (HH:mm)")
            }
        }
        item {
            DevWellButton(
                "Save preferences",
                onClick = {
                    onSave(
                        prefs.copy(
                            dailyEnabled = daily,
                            checkInReminders = checkIn,
                            teamAlerts = teamAlerts,
                            tipNudges = tipNudges,
                            quietStart = quietStart.ifBlank { null },
                            quietEnd = quietEnd.ifBlank { null },
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AiAssistantScreen(
    onAsk: (String, Boolean, (Result<String>) -> Unit) -> Unit,
    liveAssistantAvailable: Boolean = true,
    previewAssistantAvailable: Boolean = false,
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("DevWell assistant", "Guidance only — not professional advice")
        if (liveAssistantAvailable || previewAssistantAvailable) {
            Row(
                Modifier.fillMaxWidth()
                    .background(AmberSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, AmberBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = AmberText)
                Text(
                    "This free assistant sends questions to Google Gemini. Google may use questions and " +
                        "replies to improve its products. Avoid names or sensitive details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberText,
                )
            }
        }
        if (!liveAssistantAvailable && !previewAssistantAvailable) {
            AuthFeedback(error = "Sign in with an active account to use the live AI assistant.")
        }
        DevWellTextField(
            question,
            { question = it },
            label = "Ask about workload, focus or wellbeing",
            placeholder = "How can I protect my focus time this week?",
            singleLine = false,
            maxLines = 6,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = consent,
                onCheckedChange = { consent = it },
                enabled = !loading,
                colors = CheckboxDefaults.colors(
                    checkedColor = DevGreen,
                    uncheckedColor = BorderGreen,
                ),
            )
            Text(
                "I agree to send this question to Google Gemini, which may use it to improve its products.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
        DevWellButton(
            if (loading) "Thinking…" else "Ask",
            onClick = {
                loading = true
                error = null
                answer = null
                consent = false
                onAsk(question, previewAssistantAvailable) { result ->
                    loading = false
                    result.onSuccess { answer = it }.onFailure { error = it.message }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (liveAssistantAvailable || previewAssistantAvailable) && question.isNotBlank() && consent && !loading,
        )
        error?.let { AuthFeedback(error = it) }
        answer?.let {
            DevWellCard(containerColor = AccentSurface, borderColor = TrackGreen) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = DevGreen)
                    Text(
                        "Assistant",
                        style = MaterialTheme.typography.titleSmall,
                        color = DevGreen,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}
