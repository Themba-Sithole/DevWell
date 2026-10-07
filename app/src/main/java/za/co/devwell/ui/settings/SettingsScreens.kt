package za.co.devwell.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.NotificationPreferences
import za.co.devwell.model.UserRole
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle

@Composable
fun ProfileScreen(
    role: UserRole,
    onNotifications: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        PageTitle("Profile & settings", role.name.lowercase().replaceFirstChar { it.uppercase() })
        DevWellCard {
            Text("Notifications", fontWeight = FontWeight.Bold)
            Text("Daily reminder, quiet hours and category controls")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNotifications) { Text("Open preferences") }
        }
        Spacer(Modifier.height(12.dp))
        DevWellCard {
            Text("Privacy", fontWeight = FontWeight.Bold)
            Text("Managers never see individual check-ins. Team data requires at least 5 responses.")
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
fun NotificationsScreen(
    prefs: NotificationPreferences,
    onSave: (NotificationPreferences) -> Unit,
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
        item { PageTitle("Notification preferences") }
        item { PrefToggle("Daily check-in reminder", daily) { daily = it } }
        item { PrefToggle("Check-in reminders", checkIn) { checkIn = it } }
        item { PrefToggle("Team alerts", teamAlerts) { teamAlerts = it } }
        item { PrefToggle("Tip nudges", tipNudges) { tipNudges = it } }
        item {
            OutlinedTextField(quietStart, { quietStart = it }, Modifier.fillMaxWidth(), label = { Text("Quiet start (HH:mm)") })
        }
        item {
            OutlinedTextField(quietEnd, { quietEnd = it }, Modifier.fillMaxWidth(), label = { Text("Quiet end (HH:mm)") })
        }
        item {
            Button(
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
            ) { Text("Save preferences") }
        }
    }
}

@Composable
private fun PrefToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
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

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("DevWell assistant", "Guidance only — not professional advice")
        if (liveAssistantAvailable || previewAssistantAvailable) Text(
            "This free assistant sends questions to Google Gemini. Google may use questions and replies to improve its products. Avoid names or sensitive details.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!liveAssistantAvailable && !previewAssistantAvailable) {
            Text("Sign in with an active account to use the live AI assistant.")
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask about workload, focus or wellbeing") },
            minLines = 3,
        )
        Button(
            onClick = {
                loading = true
                error = null
                answer = null
                onAsk(question, previewAssistantAvailable) { result ->
                    loading = false
                    result.onSuccess { answer = it }.onFailure { error = it.message }
                }
            },
            enabled = (liveAssistantAvailable || previewAssistantAvailable) && question.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "Thinking…" else "Ask") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        answer?.let {
            DevWellCard {
                Text("Assistant", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
        }
    }
}
