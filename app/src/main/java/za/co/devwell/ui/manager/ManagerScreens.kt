package za.co.devwell.ui.manager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.JoinRequest
import za.co.devwell.model.JoinRequestStatus
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.Team
import za.co.devwell.model.TeamAggregate
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

@Composable
fun ManagerHomeScreen(
    aggregate: TeamAggregate?,
    onJoinRequests: () -> Unit,
    onTeamSettings: () -> Unit,
) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("Team insights", "Anonymous trends only") }
        item {
            PrivacyNotice("Managers can never open individual check-ins, personal notes or personal histories.")
        }
        if (aggregate == null) {
            item { DevWellCard { Text("No team data available yet.") } }
        } else if (safe == null) {
            item {
                DevWellCard {
                    Text("Not enough responses", fontWeight = FontWeight.Bold)
                    Text(
                        "Team charts stay hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                            "responses. Current: ${aggregate.responseCount}",
                    )
                }
            }
        } else {
            item {
                DevWellCard {
                    Text("${safe.responseCount} anonymous responses this period", fontWeight = FontWeight.Bold)
                }
            }
            item {
                DevWellCard {
                    Text("Team averages", style = MaterialTheme.typography.titleMedium)
                    Text("Wellbeing ${"%.1f".format(safe.averageWellbeing)}/5")
                    Text("Workload ${"%.1f".format(safe.averageWorkload)}/5")
                    Text("Energy ${"%.1f".format(safe.averageEnergy)}/10 • Focus ${"%.1f".format(safe.averageFocus)}/10")
                }
            }
        }
        item {
            Button(onClick = onJoinRequests, Modifier.fillMaxWidth()) { Text("Review join requests") }
        }
        item {
            OutlinedButton(onClick = onTeamSettings, Modifier.fillMaxWidth()) { Text("Team settings") }
        }
    }
}

@Composable
fun JoinRequestsScreen(
    requests: List<JoinRequest>,
    onReview: (requestId: String, approve: Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Join requests", "Approve or decline engineers for your company") }
        if (requests.isEmpty()) {
            item { DevWellCard { Text("No pending requests.") } }
        }
        items(requests) { request ->
            DevWellCard {
                Text(request.userName, fontWeight = FontWeight.Bold)
                Text(request.userEmail)
                Text("Status: ${request.status.name.lowercase()}")
                if (request.status == JoinRequestStatus.PENDING) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onReview(request.id, true) }) { Text("Approve") }
                        OutlinedButton(onClick = { onReview(request.id, false) }) { Text("Decline") }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamSettingsScreen(
    team: Team?,
    onSave: (Team) -> Unit,
) {
    if (team == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            PageTitle("Team settings")
            Text("No managed team loaded.")
        }
        return
    }
    var name by remember(team.id) { mutableStateOf(team.name) }
    var timezone by remember(team.id) { mutableStateOf(team.timezone.orEmpty()) }
    var cadence by remember(team.id) { mutableStateOf(team.checkInCadence.orEmpty()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Team settings", team.name) }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Team name") }) }
        item { OutlinedTextField(timezone, { timezone = it }, Modifier.fillMaxWidth(), label = { Text("Timezone") }) }
        item { OutlinedTextField(cadence, { cadence = it }, Modifier.fillMaxWidth(), label = { Text("Check-in cadence") }) }
        item {
            Button(
                onClick = {
                    onSave(team.copy(name = name, timezone = timezone, checkInCadence = cadence))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save settings") }
        }
        item {
            DevWellCard {
                Text("Member roster", fontWeight = FontWeight.Bold)
                Text("${team.memberCount} members")
            }
        }
    }
}
