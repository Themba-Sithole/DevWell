package za.co.devwell.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.co.devwell.model.JoinRequest
import za.co.devwell.model.JoinRequestStatus
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.Team
import za.co.devwell.model.TeamAggregate
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice
import za.co.devwell.ui.theme.DevWellTheme
import za.co.devwell.viewmodel.ManagerViewModel

private fun Double?.oneDecimal(): String = this?.let { "%.1f".format(it) } ?: "-"

/** One bar on the manager chart. Holds a team average only, never an individual score. */
internal data class TeamChartBar(val label: String, val value: Double, val scaleMax: Int) {
    /** Share of the track to fill, always within 0..1. */
    val fraction: Float get() = (value / scaleMax).toFloat().coerceIn(0f, 1f)
}

/**
 * Workload, energy and focus bars. Returns null below the anonymity threshold, so a chart
 * can never be drawn from an unsafe aggregate. Missing or non-finite averages are skipped.
 */
internal fun teamChartBars(aggregate: TeamAggregate): List<TeamChartBar>? {
    val safe = PrivacyPolicy.safeAggregateOrNull(aggregate) ?: return null
    fun bar(label: String, value: Double?, max: Int) =
        value?.takeIf { it.isFinite() }?.let { TeamChartBar(label, it, max) }
    return listOfNotNull(
        bar("Workload", safe.averageWorkload, 5),
        bar("Energy", safe.averageEnergy, 10),
        bar("Focus", safe.averageFocus, 10),
    )
}

@Composable
private fun TeamBarChart(bars: List<TeamChartBar>) {
    DevWellCard {
        Text("Workload, energy and focus", style = MaterialTheme.typography.titleMedium)
        bars.forEach { bar ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bar.label)
                Text("${bar.value.oneDecimal()}/${bar.scaleMax}", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxWidth(bar.fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Anonymous team averages. Workload: higher means heavier.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FeedbackCard(error: String?, message: String?, onDismiss: () -> Unit) {
    DevWellCard {
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        } else if (message != null) {
            Text(message)
        }
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Composable
fun ManagerHomeScreen(
    aggregate: TeamAggregate?,
    onJoinRequests: () -> Unit,
    onTeamSettings: () -> Unit,
) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    val bars = aggregate?.let { teamChartBars(it) }

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
                    Text("Not enough participants", fontWeight = FontWeight.Bold)
                    Text(
                        "Team charts stay hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                                "participants. Current: ${aggregate.responseCount}",
                    )
                }
            }
        } else {
            item {
                DevWellCard {
                    Text("${safe.responseCount} anonymous participants this period", fontWeight = FontWeight.Bold)
                }
            }
            item {
                DevWellCard {
                    Text("Team averages", style = MaterialTheme.typography.titleMedium)
                    Text("Wellbeing ${safe.averageWellbeing.oneDecimal()}/5")
                }
            }
            if (!bars.isNullOrEmpty()) {
                item { TeamBarChart(bars) }
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
    reviewingId: String? = null,
    error: String? = null,
    message: String? = null,
    onDismissFeedback: () -> Unit = {},
) {
    val busy = reviewingId != null
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Join requests", "Approve or decline engineers for your company") }
        if (error != null || message != null) {
            item { FeedbackCard(error, message, onDismissFeedback) }
        }
        if (requests.isEmpty()) {
            item { DevWellCard { Text("No pending requests.") } }
        }
        items(requests, key = { it.id }) { request ->
            DevWellCard {
                Text(request.userName, fontWeight = FontWeight.Bold)
                Text(request.userEmail)
                Text("Status: ${request.status.name.lowercase()}")
                if (request.status == JoinRequestStatus.PENDING) {
                    Spacer(Modifier.height(12.dp))
                    if (reviewingId == request.id) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onReview(request.id, true) }, enabled = !busy) { Text("Approve") }
                        OutlinedButton(onClick = { onReview(request.id, false) }, enabled = !busy) { Text("Decline") }
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
    saving: Boolean = false,
    error: String? = null,
    message: String? = null,
    onDismissFeedback: () -> Unit = {},
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
        if (error != null || message != null) {
            item { FeedbackCard(error, message, onDismissFeedback) }
        }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Team name") }) }
        item { OutlinedTextField(timezone, { timezone = it }, Modifier.fillMaxWidth(), label = { Text("Timezone") }) }
        item { OutlinedTextField(cadence, { cadence = it }, Modifier.fillMaxWidth(), label = { Text("Check-in cadence") }) }
        item {
            Button(
                onClick = {
                    onSave(team.copy(name = name, timezone = timezone, checkInCadence = cadence))
                },
                enabled = !saving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving..." else "Save settings") }
        }
        item {
            DevWellCard {
                Text("Member roster", fontWeight = FontWeight.Bold)
                Text("${team.memberCount} members")
            }
        }
    }
}

/** Connects the join-requests screen to its ViewModel. Clears old feedback on entry. */
@Composable
fun JoinRequestsRoute(viewModel: ManagerViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearFeedback() }
    JoinRequestsScreen(
        requests = state.joinRequests,
        onReview = viewModel::reviewJoinRequest,
        reviewingId = state.reviewingId,
        error = state.error,
        message = state.message,
        onDismissFeedback = viewModel::clearFeedback,
    )
}

@Composable
fun TeamSettingsRoute(viewModel: ManagerViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearFeedback() }
    TeamSettingsScreen(
        team = state.team,
        onSave = viewModel::updateTeamSettings,
        saving = state.savingSettings,
        error = state.error,
        message = state.message,
        onDismissFeedback = viewModel::clearFeedback,
    )
}

private val previewAggregate = TeamAggregate(
    teamId = "preview",
    responseCount = 5,
    averageWellbeing = 3.7,
    averageWorkload = 3.9,
    averageEnergy = 6.4,
    averageFocus = 6.8,
)

@Preview(showBackground = true, name = "Hidden: 4 participants")
@Composable
private fun ManagerHomeHiddenPreview() {
    DevWellTheme { ManagerHomeScreen(previewAggregate.copy(responseCount = 4), {}, {}) }
}

@Preview(showBackground = true, name = "Visible: 5 participants")
@Composable
private fun ManagerHomeVisiblePreview() {
    DevWellTheme { ManagerHomeScreen(previewAggregate, {}, {}) }
}