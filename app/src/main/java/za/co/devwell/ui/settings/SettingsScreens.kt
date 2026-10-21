package za.co.devwell.ui.settings

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import za.co.devwell.model.AiMessage
import za.co.devwell.model.NotificationPreferences
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.UserRole
import za.co.devwell.ui.auth.AuthFeedback
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*

@Composable
fun ProfileScreen(
    role: UserRole,
    onSignOut: () -> Unit,
    name: String = "",
    email: String = "",
    prefs: NotificationPreferences? = null,
    onToggleDaily: (Boolean) -> Unit = {},
    onReminderTimeChange: (String) -> Unit = {},
    onTips: () -> Unit = {},
    onExercises: () -> Unit = {},
    onSettings: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val dailyReminderEnabled = prefs?.dailyEnabled ?: true
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            BackHeader("Profile", onBack = onBack)
        } else {
            Text("Profile", style = MaterialTheme.typography.headlineMedium, color = Ink)
        }
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
                checked = dailyReminderEnabled,
                onCheckedChange = onToggleDaily,
                subtitle = "Nudge me at my usual check-in time",
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                enabled = dailyReminderEnabled,
                onClick = {
                    val time = prefs?.reminderTime ?: "09:00"
                    val parts = time.split(":")
                    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                        onReminderTimeChange("%02d:%02d".format(selectedHour, selectedMinute))
                    }, hour, minute, false).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Daily reminder: ${prefs?.reminderTime ?: "09:00"}")
            }
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
            SettingsRow(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                subtitle = "Account, privacy, notifications and help",
                onClick = onSettings,
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
        PrivacyNotice(privacyNoticeFor(role))
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

private fun privacyNoticeFor(role: UserRole): String = when (role) {
    UserRole.ENGINEER ->
        "Your data is private and secure. Managers only see anonymised team insights."
    UserRole.MANAGER ->
        "You only see anonymised, aggregated team data. No individual responses are accessible."
    UserRole.ADMIN ->
        "The admin role manages access but does not expose individual wellbeing responses."
}

/**
 * General settings hub. Replaces the old profile entry for privacy settings and
 * groups account details, privacy, notification and help destinations.
 */
@Composable
fun SettingsScreen(
    role: UserRole,
    name: String,
    email: String,
    onAccountProfile: () -> Unit,
    onManagerVisibility: () -> Unit,
    onNotifications: () -> Unit,
    onHelpAbout: () -> Unit,
    onTeamSettings: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Settings", "Account, privacy, notifications and help", onBack) }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp)) {
                    SectionLabel("Account & profile", Modifier.padding(horizontal = 14.dp))
                }
                Spacer(Modifier.height(4.dp))
                SettingsRow(
                    icon = Icons.Outlined.Person,
                    title = "Account & profile",
                    subtitle = (if (name.isNotBlank()) name else "Your details") +
                        if (email.isNotBlank()) " · $email" else "",
                    onClick = onAccountProfile,
                )
                if (role == UserRole.MANAGER) {
                    DevWellDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Settings,
                        title = "Team settings",
                        subtitle = "Team name and wellbeing alerts",
                        onClick = onTeamSettings,
                    )
                }
                DevWellDivider()
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notification preferences",
                    subtitle = "Daily reminder, quiet hours and category controls",
                    onClick = onNotifications,
                )
            }
        }
        // A manager reviews their team, so "what my manager sees" does not apply to their account.
        if (role != UserRole.MANAGER) {
            item {
                DevWellCard(padding = 0.dp) {
                    Column(Modifier.padding(top = 14.dp)) {
                        SectionLabel("Privacy", Modifier.padding(horizontal = 14.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Shield,
                        title = "What my manager sees",
                        subtitle = "What DevWell stores and what it never shares",
                        onClick = onManagerVisibility,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Managers never see individual check-ins. Team insights require at least " +
                            "${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} distinct participants.",
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item {
            DevWellCard(padding = 0.dp) {
                Column(Modifier.padding(top = 14.dp)) {
                    SectionLabel("Help & about", Modifier.padding(horizontal = 14.dp))
                }
                Spacer(Modifier.height(4.dp))
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "Help & about",
                    subtitle = "FAQ, support, policies and app version",
                    onClick = onHelpAbout,
                )
            }
        }
        item { PrivacyNotice(privacyNoticeFor(role)) }
    }
}

/** Editable account details: display name, email address and password. */
@Composable
fun AccountSettingsScreen(
    name: String,
    email: String,
    busy: Boolean,
    error: String?,
    message: String?,
    onSaveProfile: (name: String, email: String) -> Unit,
    onChangePassword: (password: String, confirmation: String) -> Unit,
    onDismissFeedback: () -> Unit,
    onBack: () -> Unit = {},
) {
    var nameField by rememberSaveable(name) { mutableStateOf(name) }
    var emailField by rememberSaveable(email) { mutableStateOf(email) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    // Stale feedback from a previous screen visit should not follow the user in.
    LaunchedEffect(Unit) { onDismissFeedback() }

    val profileChanged = nameField.trim() != name || emailField.trim() != email

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Account & profile", "Edit your name, email and password", onBack) }
        item {
            DevWellCard {
                SectionLabel("Your details")
                Spacer(Modifier.height(12.dp))
                DevWellTextField(nameField, { nameField = it }, label = "Full name")
                Spacer(Modifier.height(10.dp))
                DevWellTextField(
                    emailField,
                    { emailField = it },
                    label = "Email address",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your name is shared with your team. Changing your email sends a " +
                        "confirmation link to the new address before it takes effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                DevWellButton(
                    "Save changes",
                    onClick = { onSaveProfile(nameField.trim(), emailField.trim()) },
                    enabled = !busy && profileChanged && nameField.isNotBlank() && emailField.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            DevWellCard {
                SectionLabel("Change password")
                Spacer(Modifier.height(12.dp))
                DevWellTextField(
                    password,
                    { password = it },
                    label = "New password",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(10.dp))
                DevWellTextField(
                    confirmation,
                    { confirmation = it },
                    label = "Confirm new password",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Use at least 8 characters. You stay signed in on this device after changing it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                DevWellButton(
                    "Update password",
                    onClick = {
                        onChangePassword(password, confirmation)
                        password = ""
                        confirmation = ""
                    },
                    enabled = !busy && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Outline,
                )
            }
        }
        if (error != null || message != null) {
            item { AuthFeedback(error = error, message = message) }
        }
        item {
            PrivacyNotice(
                "DevWell never stores your password. Changes are applied to your secure account session.",
            )
        }
    }
}

@Composable
fun NotificationsScreen(
    prefs: NotificationPreferences,
    onSave: (NotificationPreferences) -> Unit,
    syncStatus: String? = null,
    onBack: () -> Unit = {},
) {
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
        syncStatus?.let { status ->
            item {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
    }
}

@Composable
fun AiAssistantScreen(
    messages: List<AiMessage>,
    sending: Boolean,
    error: String?,
    liveAssistantAvailable: Boolean = true,
    previewAssistantAvailable: Boolean = false,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var consentConfirmed by remember { mutableStateOf(false) }
    val available = liveAssistantAvailable || previewAssistantAvailable
    val canSend = available && consent && input.isNotBlank() && !sending
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(consent) {
        if (consent) {
            consentConfirmed = true
            delay(2600)
            consentConfirmed = false
        } else {
            consentConfirmed = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PageTitle("DevWell assistant", "Guidance only — not professional advice")
            }
            if (messages.isNotEmpty()) {
                TextButton(onClick = onNewChat, enabled = !sending) {
                    Icon(Icons.Outlined.AddComment, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New chat")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (available) {
            GeminiConsentBanner()
        } else {
            AuthFeedback(error = "Sign in with an active account to use the live AI assistant.")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item { ChatEmptyState() }
            }
            items(messages, key = { it.id }) { message -> ChatBubble(message) }
            if (sending) {
                item { TypingIndicator() }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            AuthFeedback(error = error)
            if (messages.lastOrNull()?.fromUser == true) {
                Spacer(Modifier.height(8.dp))
                DevWellButton(
                    "Try again",
                    onClick = onRetry,
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(
            visible = available && !consent,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = consent,
                    onCheckedChange = { consent = it },
                    enabled = !sending,
                    colors = CheckboxDefaults.colors(
                        checkedColor = DevGreen,
                        uncheckedColor = BorderGreen,
                    ),
                )
                Text(
                    "I agree to send this conversation to Google Gemini, which may use it to improve its products.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        AnimatedVisibility(
            visible = available && consent && consentConfirmed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ConsentConfirmedBanner()
        }
        if (available) {
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevWellTextField(
                input,
                { input = it },
                modifier = Modifier.weight(1f),
                placeholder = "Ask about workload, focus or wellbeing",
                enabled = available && !sending,
                singleLine = false,
                maxLines = 4,
            )
            SendButton(
                enabled = canSend,
                onClick = {
                    val text = input
                    input = ""
                    onSend(text)
                },
            )
        }
    }
}

@Composable
private fun GeminiConsentBanner() {
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
            "This free assistant sends your conversation to Google Gemini. Google may use it to " +
                "improve its products. Avoid names or sensitive details.",
            style = MaterialTheme.typography.bodySmall,
            color = AmberText,
        )
    }
}

@Composable
private fun ConsentConfirmedBanner() {
    Row(
        Modifier.fillMaxWidth()
            .background(AccentSurface, RoundedCornerShape(12.dp))
            .border(1.dp, TrackGreen, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = DevGreen)
        Text(
            "Consent recorded for this session — you can now send messages.",
            style = MaterialTheme.typography.bodySmall,
            color = DevGreen,
        )
    }
}

@Composable
private fun ChatEmptyState() {
    DevWellCard(containerColor = AccentSurface, borderColor = TrackGreen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = DevGreen)
            Text("Start a conversation", style = MaterialTheme.typography.titleSmall, color = DevGreen)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask about workload, focus, communication or general wellbeing. This conversation isn't " +
                "saved, and nothing is sent until you tap send.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
    }
}

@Composable
private fun ChatBubble(message: AiMessage) {
    val fromUser = message.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start) {
            if (!fromUser) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = DevGreen)
                    Text("Assistant", style = MaterialTheme.typography.labelSmall, color = DevGreen)
                }
                Spacer(Modifier.height(4.dp))
            }
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(if (fromUser) DevGreen else AccentSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, if (fromUser) DevGreen else TrackGreen, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fromUser) Color.White else Ink,
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .background(AccentSurface, RoundedCornerShape(16.dp))
                .border(1.dp, TrackGreen, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = DevGreen)
            Text("Assistant is thinking…", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .background(if (enabled) DevGreen else TrackGreen, CircleShape),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = if (enabled) Color.White else InkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
