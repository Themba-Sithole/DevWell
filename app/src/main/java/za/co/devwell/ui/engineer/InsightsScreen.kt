package za.co.devwell.ui.engineer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckIn
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate
import za.co.devwell.ui.components.*
import za.co.devwell.ui.manager.TeamBarChart
import za.co.devwell.ui.manager.teamChartBars
import za.co.devwell.ui.theme.*

@Composable
fun EngineerInsightsScreen(checkIns: List<CheckIn>, name: String, onBack: () -> Unit) {
    val firstName = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { "there" }
    val avgFocus = checkIns.map { it.values.focus }.average()
    val avgEnergy = checkIns.map { it.values.energy }.average()
    val avgWellbeing = checkIns.map { it.values.wellbeing }.average()
    val avgWorkload = checkIns.map { it.values.workload }.average()
    val avgAfterHours = checkIns.map { it.values.afterHoursMinutes }.average()
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(
                "Insights", "Supportive patterns from your check-ins", onBack,
                trailing = {
                    Box(
                        Modifier
                            .background(SurfaceMuted, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("This week", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                    }
                },
            )
        }
        item {
            DevWellCard(padding = 18.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Text("🌱", style = MaterialTheme.typography.headlineLarge)
                    Column {
                        Text(
                            if (checkIns.isEmpty()) "Welcome to DevWell!" else "Nice work, $firstName! 👋",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                checkIns.isEmpty() ->
                                    "Complete your first check-in and DevWell will start learning your patterns."
                                avgFocus >= 7.0 ->
                                    "You've had more good focus days lately. Keep protecting your focus time."
                                avgWellbeing >= 4.0 ->
                                    "Your wellbeing scores look healthy. Keep the habits that are working for you."
                                avgEnergy >= 7.0 ->
                                    "Your energy levels are holding up well. Keep pairing them with real breaks."
                                else ->
                                    "Thanks for checking in regularly — knowing your patterns is the first step to improving them."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
        item { SectionLabel("Things to watch") }
        if (checkIns.isNotEmpty()) {
            val watches = buildList {
                if (avgAfterHours > 60.0) add(
                    WatchItem("⚠️", "After-hours work",
                        "%.1f hrs/night on average — slightly above the healthy range.".format(avgAfterHours / 60.0))
                )
                if (avgWorkload >= 4.0) add(
                    WatchItem("💼", "Workload",
                        "Heavy at the moment — %.1f/5 on average. Consider protecting recovery time.".format(avgWorkload))
                )
                if (avgEnergy <= 5.0) add(
                    WatchItem("⚡", "Energy",
                        "Energy has been low — %.1f/10 on average. Small movement breaks can help.".format(avgEnergy))
                )
            }
            if (watches.isEmpty()) {
                item {
                    WatchCard(
                        emoji = "✅",
                        title = "Nothing to watch",
                        body = "Your recent check-ins look balanced across workload, energy and focus.",
                        amber = false,
                    )
                }
            } else {
                items(watches.size) { i ->
                    val w = watches[i]
                    WatchCard(w.emoji, w.title, w.body, amber = true)
                }
            }
        } else {
            item {
                WatchCard(
                    emoji = "👀",
                    title = "No data yet",
                    body = "Watch items appear once you have a few check-ins to compare.",
                    amber = false,
                )
            }
        }
        item { SectionLabel("Recommended actions") }
        val actions = listOf(
            "🔒" to "Block 2 hours of deep work in your calendar",
            "📵" to "Enable Do Not Disturb during focus blocks",
            "☕" to "Take regular short breaks between coding sessions",
        ) + (if (expanded) listOf(
            "🌙" to "Set a hard stop time at the end of your work day",
            "🧘" to "Schedule deep work during your high-energy hours",
            "📉" to "Review and decline low-value recurring meetings",
        ) else emptyList())
        items(actions.size) { i ->
            val (emoji, text) = actions[i]
            DevWellCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
        item {
            DevWellButton(
                if (expanded) "Show fewer insights" else "See all insights",
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Outline,
            )
        }
        item { PrivacyNotice() }
    }
}

private data class WatchItem(val emoji: String, val title: String, val body: String)

@Composable
private fun WatchCard(emoji: String, title: String, body: String, amber: Boolean) {
    DevWellCard(
        containerColor = if (amber) AmberSurface else SurfaceMuted,
        borderColor = if (amber) AmberBorder else TrackGreen,
        padding = 14.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(34.dp)
                    .background(if (amber) AmberSurface else AccentSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (amber) AmberBorder else TrackGreen, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
}

@Composable
fun TeamOverviewScreen(aggregate: TeamAggregate?, onBack: () -> Unit) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader("Team Overview", "Anonymised team data", onBack)
        Row(
            Modifier.fillMaxWidth()
                .background(AccentSurface, RoundedCornerShape(12.dp))
                .border(1.dp, TrackGreen, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Groups, null,
                Modifier.size(16.dp), tint = AccentGreen)
            Text(
                "You're seeing anonymised team data.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentGreen,
            )
        }
        when {
            aggregate == null -> {
                DevWellCard { Text("No team data available yet.", color = InkMuted) }
            }
            safe == null -> {
                DevWellCard {
                    Text("Not enough responses", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Team insights become available when at least " +
                            "${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} team members have checked in. " +
                            "Current responses: ${aggregate.responseCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            else -> {
                val wellbeing = safe.averageWellbeing ?: 0.0
                DevWellCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Team well-being score",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "%.1f".format(wellbeing),
                                style = MaterialTheme.typography.headlineLarge,
                                color = DevGreen,
                            )
                            Text(
                                "/5",
                                Modifier.padding(bottom = 6.dp, start = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkMuted,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Chip(
                            when {
                                wellbeing >= 4.5 -> "Great"
                                wellbeing >= 4.0 -> "Good"
                                wellbeing >= 3.0 -> "Okay"
                                else -> "Needs attention"
                            },
                            skin = when {
                                wellbeing >= 4.0 -> ChipSkin.Green
                                wellbeing >= 3.0 -> ChipSkin.Neutral
                                else -> ChipSkin.Amber
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${safe.responseCount} anonymous responses this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TeamMiniStat(
                        value = when {
                            (safe.averageWorkload ?: 0.0) <= 1.5 -> "Very light"
                            (safe.averageWorkload ?: 0.0) <= 2.5 -> "Light"
                            (safe.averageWorkload ?: 0.0) <= 3.5 -> "Moderate"
                            (safe.averageWorkload ?: 0.0) <= 4.5 -> "Heavy"
                            else -> "Very heavy"
                        },
                        label = "Avg. workload",
                        modifier = Modifier.weight(1f),
                    )
                    TeamMiniStat(
                        value = safe.averageAfterHoursMinutes?.let { "%.1f hrs".format(it / 60.0) } ?: "-",
                        label = "After-hours",
                        modifier = Modifier.weight(1f),
                    )
                    TeamMiniStat(
                        value = safe.responseCount.toString(),
                        label = "Checked in",
                        modifier = Modifier.weight(1f),
                    )
                }
                DevWellCard {
                    Text("Team highlights", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    val highlights = listOf(
                        ((safe.averageWorkload ?: 0.0) <= 3.5) to "Workload is balanced",
                        ((safe.averageFocus ?: 0.0) >= 6.5) to "Focus time is solid",
                        ((safe.averageAfterHoursMinutes ?: 0.0) <= 60.0) to "After-hours work is low",
                    )
                    highlights.forEach { (positive, text) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp)
                                    .background(
                                        if (positive) AccentGreen else AmberDot,
                                        RoundedCornerShape(50),
                                    ),
                            )
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        }
                    }
                }
                aggregate.let { agg ->
                    teamChartBars(agg)?.let { bars -> TeamBarChart(bars) }
                }
            }
        }
        PrivacyNotice("Team insights require enough check-ins to protect individual anonymity.")
    }
}

@Composable
private fun TeamMiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    DevWellCard(modifier = modifier, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = DevGreen,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = InkMuted)
        }
    }
}
