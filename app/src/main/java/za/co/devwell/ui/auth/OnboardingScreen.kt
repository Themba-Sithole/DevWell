package za.co.devwell.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.CompanyStatus
import za.co.devwell.viewmodel.AuthUiState

@Composable
fun OnboardingScreen(
    state: AuthUiState,
    onSubmit: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val companies = state.companies.filter { it.status == CompanyStatus.ACTIVE }
    var companyId by rememberSaveable { mutableStateOf<String?>(null) }
    var teamId by rememberSaveable { mutableStateOf<String?>(null) }
    val companyTeams = state.teams.filter { it.companyId == companyId }
    val validSelection = companies.any { it.id == companyId } && companyTeams.any { it.id == teamId }

    // Refreshes may remove a company or team. Never submit a stale selection.
    LaunchedEffect(state.companies, state.teams) {
        if (companies.none { it.id == companyId }) { companyId = null; teamId = null }
        if (companyTeams.none { it.id == teamId }) teamId = null
    }
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Join your team", style = MaterialTheme.typography.headlineMedium)
            Text("Select a company and team, then request access.")
            if (state.preview) Text("Student preview - sample data", color = MaterialTheme.colorScheme.primary)
        }
        item { AuthFeedback(state.error, state.message) }
        if (state.directoryLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("Company", style = MaterialTheme.typography.titleMedium) }
        if (companies.isEmpty() && !state.directoryLoading) item {
            Text("No active companies are available yet. Ask your administrator or refresh later.")
        }
        items(companies, key = { it.id }) { company ->
            FilterChip(selected = companyId == company.id, onClick = { companyId = company.id; teamId = null },
                enabled = !state.busy && !state.directoryLoading, label = { Text(company.name) })
        }
        item { Text("Team", style = MaterialTheme.typography.titleMedium) }
        if (companyId == null) item { Text("Choose a company first.") }
        else if (companyTeams.isEmpty()) item { Text("This company has no available teams yet.") }
        items(companyTeams, key = { it.id }) { team ->
            FilterChip(selected = teamId == team.id, onClick = { teamId = team.id },
                enabled = !state.busy && !state.directoryLoading, label = { Text(team.name) })
        }
        item {
            Button(onClick = { if (validSelection) onSubmit(companyId!!, teamId!!) },
                enabled = validSelection && !state.busy && !state.directoryLoading,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Submitting..." else "Submit join request") }
            OutlinedButton(onClick = onRefresh, enabled = !state.busy && !state.directoryLoading,
                modifier = Modifier.fillMaxWidth()) { Text("Refresh companies and teams") }
            TextButton(onClick = onRefreshProfile, enabled = !state.busy) { Text("Refresh account status") }
            TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Sign out") }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onPreviewReview: (Boolean) -> Unit,
) {
    val rejected = state.user?.status == AccountStatus.REJECTED
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(32.dp)) }
        item { Text(if (rejected) "Request not approved" else "Waiting for approval", style = MaterialTheme.typography.headlineMedium) }
        item { Text(if (rejected) "Contact your manager to discuss your request. Your account cannot access the dashboard yet."
            else "Your account is waiting for approval. Refresh your status after your manager has reviewed it.") }
        item { AuthFeedback(state.error) }
        item {
            Button(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Checking..." else "Refresh status")
            }
            OutlinedButton(onClick = onSignOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
        if (state.preview) item {
            Text("Student preview - these buttons do not change the database.")
            TextButton(onClick = { onPreviewReview(true) }) { Text("Simulate approval") }
            TextButton(onClick = { onPreviewReview(false) }) { Text("Simulate rejection") }
        }
    }
}
