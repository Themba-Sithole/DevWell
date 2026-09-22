package za.co.devwell.ui.engineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckIn
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

@Composable
fun TrendsScreen(checkIns: List<CheckIn>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Personal trends", "Your private check-in history") }
        if (checkIns.isEmpty()) {
            item { DevWellCard { Text("No check-ins yet. Complete your first check-in from Home.") } }
        }
        items(checkIns.reversed()) { item ->
            DevWellCard {
                Text(item.dateLabel, fontWeight = FontWeight.Bold)
                Text("Wellbeing ${item.values.wellbeing}/5 • Workload ${item.values.workload}/5")
                Text("Energy ${item.values.energy}/10 • Focus ${item.values.focus}/10")
            }
        }
    }
}

@Composable
fun PrivacyPreviewScreen(aggregate: TeamAggregate?) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle(
            "What my manager sees",
            "Exactly the same safe team output available to management",
        )
        PrivacyNotice()
        if (aggregate == null) {
            DevWellCard { Text("No team data available yet.") }
        } else if (safe == null) {
            DevWellCard {
                Text("Not enough responses", fontWeight = FontWeight.Bold)
                Text(
                    "Team charts are hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                        "responses are available. Current responses: ${aggregate.responseCount}",
                )
            }
        } else {
            DevWellCard {
                Text("Team overview (anonymous)", style = MaterialTheme.typography.titleMedium)
                Text("Wellbeing ${"%.1f".format(safe.averageWellbeing)}/5")
                Text("Workload ${"%.1f".format(safe.averageWorkload)}/5")
                Text("Energy ${"%.1f".format(safe.averageEnergy)}/10 • Focus ${"%.1f".format(safe.averageFocus)}/10")
                Text("Based on ${safe.responseCount} responses — no names or notes.")
            }
        }
    }
}
