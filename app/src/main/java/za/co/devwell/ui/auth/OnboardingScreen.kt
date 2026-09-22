package za.co.devwell.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.co.devwell.model.Company
import za.co.devwell.model.CompanyStatus
import za.co.devwell.model.Team
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle

@Composable
fun OnboardingScreen(
    companies: List<Company>,
    teams: List<Team>,
    onSubmit: (companyId: String, teamId: String) -> Unit,
) {
    val activeCompanies = companies.filter { it.status == CompanyStatus.ACTIVE }
    var selectedCompanyId by remember { mutableStateOf(activeCompanies.firstOrNull()?.id) }
    val teamsForCompany = teams.filter { it.companyId == selectedCompanyId }
    var selectedTeamId by remember { mutableStateOf(teamsForCompany.firstOrNull()?.id) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Join your team", "Select an active company and team, then request access.") }
        item { Text("Company", style = MaterialTheme.typography.titleMedium) }
        items(activeCompanies) { company ->
            FilterChip(
                selected = selectedCompanyId == company.id,
                onClick = {
                    selectedCompanyId = company.id
                    selectedTeamId = teams.firstOrNull { it.companyId == company.id }?.id
                },
                label = { Text(company.name) },
            )
        }
        item { Text("Team", style = MaterialTheme.typography.titleMedium) }
        items(teamsForCompany) { team ->
            FilterChip(
                selected = selectedTeamId == team.id,
                onClick = { selectedTeamId = team.id },
                label = { Text("${team.name} (${team.memberCount})") },
            )
        }
        item {
            val companyId = selectedCompanyId
            val teamId = selectedTeamId
            Button(
                onClick = { if (companyId != null && teamId != null) onSubmit(companyId, teamId) },
                enabled = companyId != null && teamId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Submit join request") }
        }
    }
}

@Composable
fun PendingApprovalScreen(onBackToLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        PageTitle("Waiting for approval", "A manager will review your join request.")
        DevWellCard {
            Text("You will unlock the engineer dashboard once your request is approved.")
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBackToLogin, Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}
