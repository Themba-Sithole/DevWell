package za.co.devwell.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.CompanyStatus
import za.co.devwell.viewmodel.AuthUiState

@Composable
fun OnboardingScreen(state: AuthUiState, onSubmit: (String, String) -> Unit, onRefresh: () -> Unit,
    onRefreshProfile: () -> Unit, onSignOut: () -> Unit) {
    val companies = state.companies.filter { it.status == CompanyStatus.ACTIVE }
    var companyId by rememberSaveable(state.user?.id) { mutableStateOf<String?>(null) }
    var teamId by rememberSaveable(state.user?.id) { mutableStateOf<String?>(null) }
    var step by rememberSaveable(state.user?.id) { mutableIntStateOf(1) }
    val company = companies.find { it.id == companyId }
    val teams = state.teams.filter { it.companyId == companyId }
    val team = teams.find { it.id == teamId }
    LaunchedEffect(state.companies, state.teams) {
        if (company == null) { companyId = null; teamId = null; step = 1 }
        else if (team == null) { teamId = null; if (step == 3) step = 2 }
    }
    BackHandler(step > 1 && !state.busy) { step-- }
    Column(Modifier.fillMaxSize().background(AuthBackground).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(AuthGreen).statusBarsPadding().padding(24.dp)) {
            Brand(Modifier.background(AuthBackground, AuthShape).padding(horizontal = 8.dp))
            Spacer(Modifier.height(28.dp))
            Text("Welcome, ${state.user?.name?.substringBefore(' ') ?: "there"}!", color = Color.White,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Let's connect you to your team.", color = Color(0xFFC1D7BD), modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index -> Box(Modifier.weight(1f).height(6.dp).background(if (index < step) Color.White else Color(0xFF709174), CircleShape)) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("STEP $step OF 3", style = MaterialTheme.typography.labelMedium, color = AuthMuted)
            Text(when(step) { 1 -> "Which company do you work for?"; 2 -> "Which team are you on?"; else -> "Confirm your details" },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(when(step) { 1 -> "Select your employer. Your manager will verify your access."; 2 -> "Teams within ${company?.name.orEmpty()}"; else -> "Your manager will receive a request to approve your access." },
                style = MaterialTheme.typography.bodySmall, color = AuthMuted)
            AuthFeedback(state.error, state.message)
            if (state.directoryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (step) {
                1 -> {
                    companies.forEach { item -> SelectionCard(item.name, item.emailDomain.orEmpty(), companyId == item.id,
                        !state.busy && !state.directoryLoading, item.name.take(2).uppercase()) {
                        if (companyId != item.id) { companyId = item.id; teamId = null }
                    } }
                    if (companies.isEmpty() && !state.directoryLoading) Text("No active companies are available yet. Ask your administrator or refresh later.", color = AuthMuted)
                }
                2 -> {
                    teams.forEach { item -> SelectionCard(item.name, "${item.memberCount} members", teamId == item.id,
                        !state.busy && !state.directoryLoading, null) { teamId = item.id } }
                    if (teams.isEmpty() && !state.directoryLoading) Text("This company has no available teams yet.", color = AuthMuted)
                }
                3 -> {
                    Surface(shape = AuthShape, color = Color.White, border = BorderStroke(1.dp, AuthBorder), shadowElevation = 1.dp) {
                        Column(Modifier.padding(16.dp)) {
                            DetailRow("Your name", state.user?.name.orEmpty())
                            DetailRow("Company", company?.name.orEmpty())
                            DetailRow("Team", team?.name.orEmpty())
                        }
                    }
                    PrivacyNote("Your request is sent only to your team manager — no data is shared publicly.")
                }
            }
            if (step < 3) TextButton(onClick = onRefresh, enabled = !state.busy && !state.directoryLoading) { Text("Refresh companies and teams") }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 1) OutlinedButton(onClick = { step-- }, enabled = !state.busy, shape = AuthShape,
                    border = BorderStroke(1.dp, AuthGreen), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Back", color = AuthGreen) }
                PrimaryAction(if (state.busy) "Sending..." else if (step == 3) "Send Request" else "Continue",
                    modifier = Modifier.weight(1f), enabled = !state.busy && !state.directoryLoading && company != null && (step == 1 || team != null)) {
                    if (step < 3) step++ else if (company != null && team != null) onSubmit(company.id, team.id)
                }
            }
            TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Sign out", color = AuthMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SelectionCard(title: String, subtitle: String, selected: Boolean, enabled: Boolean, initials: String?, onClick: () -> Unit) {
    Surface(shape = AuthShape, color = if (selected) za.co.devwell.ui.theme.AccentSurface else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AuthGreen else AuthBorder),
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (selected) AuthGreen else za.co.devwell.ui.theme.AccentSurface) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    if (initials != null) Text(initials, color = if (selected) Color.White else AuthGreen, fontWeight = FontWeight.Bold)
                    else Icon(Icons.Outlined.Groups, null, tint = if (selected) Color.White else AuthGreen)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        color = AuthMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) Icon(Icons.Outlined.Check, "Selected", Modifier.size(20.dp), tint = AuthGreen)
        }
    }
}

@Composable
fun PendingApprovalScreen(state: AuthUiState, onRefresh: () -> Unit, onSignOut: () -> Unit, onPreviewReview: (Boolean) -> Unit) {
    val rejected = state.user?.status == AccountStatus.REJECTED
    val suspended = state.user?.status == AccountStatus.SUSPENDED
    val company = state.companies.find { it.id == state.user?.companyId }?.name
    val team = state.teams.find { it.id == state.user?.teamId }?.name ?: state.user?.teamName
    Column(Modifier.fillMaxSize().background(AuthBackground).systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(64.dp))
        Surface(shape = CircleShape, color = za.co.devwell.ui.theme.AccentSurface) {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.HourglassEmpty, null, Modifier.size(34.dp), tint = AuthGreen) }
        }
        Text(
            when {
                suspended -> "Account suspended"
                rejected -> "Request not approved"
                else -> "Awaiting Approval"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                suspended -> "An administrator suspended this account. Contact your administrator if you need help restoring access."
                rejected -> "Contact your manager to discuss your request. Your account cannot access the dashboard yet."
                else -> "Your request to join ${company ?: "your company"} has been sent to your manager. You'll have full access once they approve it."
            },
            color = AuthMuted,
            textAlign = TextAlign.Center,
        )
        if (!suspended) Surface(shape = AuthShape, color = Color.White, border = BorderStroke(1.dp, AuthBorder), shadowElevation = 1.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("YOUR REQUEST", color = AuthMuted, style = MaterialTheme.typography.labelSmall)
                DetailRow("Company", company ?: "Your company")
                HorizontalDivider(color = AuthBackground)
                DetailRow("Team", team ?: "Your selected team")
                DetailRow("Status", if (rejected) "Not approved" else "Pending review")
            }
        }
        AuthFeedback(state.error, state.message)
        PrimaryAction(
            when {
                state.busy -> "Checking..."
                suspended -> "Refresh account status"
                else -> "Check approval status"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            onClick = onRefresh,
        )
        if (!suspended) PrivacyNote("Your manager can approve your access from their DevWell dashboard.")
        TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Sign out", color = AuthMuted) }
        if (state.preview) {
            Text("Student preview — sample data", color = AuthMuted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onPreviewReview(true) }, enabled = !state.busy) { Text("Simulate approval") }
            TextButton(onClick = { onPreviewReview(false) }, enabled = !state.busy) { Text("Simulate rejection") }
        }
    }
}
