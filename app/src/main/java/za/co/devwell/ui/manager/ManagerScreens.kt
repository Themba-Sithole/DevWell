package za.co.devwell.ui.manager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.co.devwell.model.JoinRequest
import za.co.devwell.model.JoinRequestStatus
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.Team
import za.co.devwell.model.TeamAggregate
import za.co.devwell.ui.auth.AuthFeedback
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*
import za.co.devwell.viewmodel.ManagerViewModel
import za.co.devwell.viewmodel.TeamSettingsRules
import kotlin.math.roundToInt

private fun Double?.oneDecimal(): String = this?.let { "%.1f".format(it) } ?: "-"

/**
 * Roster card wording. The Supabase repository cannot count members yet (RLS hides other users),
 * so Team.memberCount stays at its default 0. Zero or less is treated as "unknown", not "empty".
 */
internal fun memberCountLabel(count: Int): String = when {
    count <= 0 -> "Member count not available yet"
    count == 1 -> "1 member"
    else -> "$count members"
}

/** Button text for each stored cadence. Stored values come from TeamSettingsRules.CADENCES. */
internal fun cadenceLabel(cadence: String): String = when (cadence) {
    "Daily" -> "Every day"
    "Weekly" -> "Once a week"
    else -> cadence
}

/** Rounds a slider position to the nearest 0.5 inside the allowed threshold range. */
internal fun snapThreshold(raw: Float): Double =
    ((raw * 2).roundToInt() / 2.0).coerceIn(TeamSettingsRules.THRESHOLD_RANGE)

/** Same rule as TeamSettingsRules.validate: an unchanged legacy long name is allowed. */
internal fun teamNameTooLong(name: String, savedName: String): Boolean =
    name.trim().length > TeamSettingsRules.NAME_MAX_LENGTH && name.trim() != savedName.trim()

internal fun nameCounterLabel(name: String): String =
    "${name.trim().length}/${TeamSettingsRules.NAME_MAX_LENGTH}"

/** What the team wellbeing card shows. Built from the anonymous team average only. */
internal data class WellbeingDisplay(
    val score: String,
    val band: String,
    val skin: ChipSkin,
    /** Ring fill in 0..1, or null when there is no average to draw. */
    val ringFraction: Float?,
)

/** A missing or non-finite average is shown as "-" with a neutral label, never as 0 or "Needs attention". */
internal fun wellbeingDisplay(average: Double?): WellbeingDisplay {
    val value = average?.takeIf { it.isFinite() }
        ?: return WellbeingDisplay("-", "Not enough data yet", ChipSkin.Neutral, null)
    return WellbeingDisplay(
        score = value.oneDecimal(),
        band = wellbeingBand(value),
        skin = when {
            value >= 4.0 -> ChipSkin.Green
            value >= 3.0 -> ChipSkin.Neutral
            else -> ChipSkin.Amber
        },
        ringFraction = (value / 5.0).toFloat().coerceIn(0f, 1f),
    )
}

/** Text for one Insights card. Built from anonymous team averages only. */
internal data class InsightCardText(val emoji: String, val title: String, val body: String, val amber: Boolean)

/** The three insight helpers return null when the average is missing, so the card is hidden instead of showing 0. */
internal fun workloadInsight(average: Double?): InsightCardText? {
    val workload = average?.takeIf { it.isFinite() } ?: return null
    val heavy = workload > 3.5
    return InsightCardText(
        emoji = if (heavy) "⚠️" else "✅",
        title = if (heavy) "Workload is elevated" else "Workload stable",
        body = if (heavy) {
            ("Team workload averages %.1f/5 right now — heavier than ideal. Worth exploring in " +
                "your next team check-in.").format(workload)
        } else {
            "Team workload has remained relatively balanced — no spikes in the current data."
        },
        amber = heavy,
    )
}

internal fun afterHoursInsight(averageMinutes: Double?): InsightCardText? {
    val minutes = averageMinutes?.takeIf { it.isFinite() } ?: return null
    val high = minutes > 60.0
    return InsightCardText(
        emoji = if (high) "⚠️" else "🌙",
        title = if (high) "After-hours work trending up" else "After-hours work is low",
        body = if (high) {
            ("Average after-hours work rose to %.1f hrs/night. Worth exploring in your next " +
                "team check-in.").format(minutes / 60.0)
        } else {
            "The team is logging %.1f hrs/night after hours — within a healthy range.".format(minutes / 60.0)
        },
        amber = high,
    )
}

internal fun focusInsight(average: Double?): InsightCardText? {
    val focus = average?.takeIf { it.isFinite() } ?: return null
    val solid = focus >= 6.5
    return InsightCardText(
        emoji = "🎯",
        title = if (solid) "Focus time is solid" else "Focus time could use support",
        body = if (solid) {
            "Average team focus is %.1f/10. Keep supporting deep work blocks.".format(focus)
        } else {
            "Average team focus is %.1f/10. Consider protecting more uninterrupted work time.".format(focus)
        },
        amber = !solid,
    )
}

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
internal fun TeamBarChart(bars: List<TeamChartBar>) {
    DevWellCard {
        Text("Workload, energy and focus", style = MaterialTheme.typography.titleMedium, color = Ink)
        bars.forEach { bar ->
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bar.label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Text(
                    "${bar.value.oneDecimal()}/${bar.scaleMax}",
                    style = MaterialTheme.typography.titleSmall,
                    color = DevGreen,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(TrackGreen),
            ) {
                Box(Modifier.fillMaxWidth(bar.fraction).fillMaxHeight().background(DevGreen))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Anonymous team averages. Workload: higher means heavier.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    }
}

@Composable
private fun FeedbackCard(error: String?, message: String?, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AuthFeedback(error = error, message = message)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun ManagerHomeScreen(
    aggregate: TeamAggregate?,
    onJoinRequests: () -> Unit,
    onTeamSettings: () -> Unit,
    onProfile: () -> Unit = {},
    managerName: String = "",
) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    val bars = aggregate?.let { teamChartBars(it) }
    val firstName = managerName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Manager dashboard", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    Text(
                        if (firstName.isEmpty()) "Your team" else "$firstName's Team",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                    )
                }
                Box(Modifier.clickable(onClick = onProfile)) {
                    Avatar(if (firstName.isEmpty()) "M" else firstName.first().uppercase(), size = 46.dp)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(AccentSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, TrackGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Groups, null, Modifier.size(16.dp), tint = AccentGreen)
                Text(
                    "All data is anonymised. Individual responses are never visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                )
            }
        }
        if (aggregate == null) {
            item { DevWellCard { Text("No team data available yet.", color = InkMuted) } }
        } else if (safe == null) {
            item {
                DevWellCard {
                    Text("Not enough participants", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Team charts stay hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                            "distinct participants. Current participants: ${aggregate.responseCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        } else {
            item {
                val wellbeing = wellbeingDisplay(safe.averageWellbeing)
                DevWellCard {
                    Text(
                        "Team well-being score",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    wellbeing.score,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = DevGreen,
                                )
                                if (wellbeing.ringFraction != null) {
                                    Text(
                                        "/5",
                                        Modifier.padding(bottom = 4.dp, start = 4.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkMuted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Chip(wellbeing.band, skin = wellbeing.skin)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${safe.responseCount} anonymous participants this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        ScoreRing(
                            fraction = wellbeing.ringFraction,
                            modifier = Modifier.size(84.dp),
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(
                        value = workloadLabel(safe.averageWorkload),
                        label = "Avg. workload",
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = safe.averageAfterHoursMinutes?.let { "%.1f hrs".format(it / 60.0) } ?: "-",
                        label = "After-hours",
                        modifier = Modifier.weight(1f),
                    )
                    MiniStat(
                        value = safe.responseCount.toString(),
                        label = "Participants",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!bars.isNullOrEmpty()) {
                item { TeamBarChart(bars) }
            }
            item {
                DevWellCard(containerColor = Color.White, borderColor = TrackGreen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(20.dp), tint = AccentGreen)
                        Column {
                            Text(
                                "Anonymity protected",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Team insights are shown because ${safe.responseCount} distinct participants " +
                                    "meet the minimum threshold of ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        }
        item {
            DevWellButton(
                "Review join requests", onJoinRequests,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Outlined.Groups,
            )
        }
        item {
            DevWellButton(
                "Team settings", onTeamSettings,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Outline,
                leadingIcon = Icons.Outlined.Settings,
            )
        }
        item {
            PrivacyNotice("Managers can never open individual check-ins, personal notes or personal histories.")
        }
    }
}

private fun wellbeingBand(wellbeing: Double): String = when {
    wellbeing >= 4.5 -> "Great"
    wellbeing >= 4.0 -> "Good"
    wellbeing >= 3.0 -> "Okay"
    else -> "Needs attention"
}

private fun workloadLabel(value: Double?): String = when {
    value == null -> "-"
    value <= 1.5 -> "Very light"
    value <= 2.5 -> "Light"
    value <= 3.5 -> "Moderate"
    value <= 4.5 -> "Heavy"
    else -> "Very heavy"
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    DevWellCard(modifier = modifier, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = DevGreen,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScoreRing(fraction: Float?, modifier: Modifier = Modifier) {
    // Null means no average: draw the empty track only and show a dash, never "0%".
    val progress = fraction?.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = TrackGreen,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (progress != null) {
                drawArc(
                    color = DevGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            progress?.let { "${(it * 100).toInt()}%" } ?: "-",
            style = MaterialTheme.typography.titleSmall,
            color = if (progress == null) InkMuted else DevGreen,
        )
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
        item { PageTitle("Join requests", "Engineers requesting access to your teams") }
        if (error != null || message != null) {
            item { FeedbackCard(error, message, onDismissFeedback) }
        }
        if (requests.isEmpty()) {
            item {
                DevWellCard {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier.size(64.dp).background(AccentSurface, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.TaskAlt, null, Modifier.size(32.dp), tint = DevGreen)
                        }
                        Text(
                            "No pending requests",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Text(
                            "All team join requests have been reviewed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        items(requests, key = { it.id }) { request ->
            DevWellCard(padding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Avatar(request.userName.initials(), size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            request.userName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Text(request.userEmail, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                    Chip(
                        request.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        skin = when (request.status) {
                            JoinRequestStatus.PENDING -> ChipSkin.Amber
                            JoinRequestStatus.APPROVED -> ChipSkin.Green
                            JoinRequestStatus.REJECTED -> ChipSkin.Red
                        },
                    )
                }
                if (request.status == JoinRequestStatus.PENDING) {
                    Spacer(Modifier.height(14.dp))
                    if (reviewingId == request.id) {
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = DevGreen,
                            trackColor = TrackGreen,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onReview(request.id, true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DevGreen,
                                contentColor = Color.White,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Approve", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onReview(request.id, false) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RedBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedText),
                        ) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Decline", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            PrivacyNotice("Approving a member gives them access to team-level anonymous insights only.")
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
            Text("No managed team loaded.", color = InkMuted)
        }
        return
    }
    var name by remember(team.id) { mutableStateOf(team.name) }
    var timezone by remember(team.id) { mutableStateOf(team.timezone.orEmpty()) }
    // Start from the stored value only if it is still valid, so the controls never show an impossible state.
    var cadence by remember(team.id) {
        mutableStateOf(team.checkInCadence?.takeIf { it in TeamSettingsRules.CADENCES } ?: "Daily")
    }
    var threshold by remember(team.id) {
        mutableDoubleStateOf(team.lowWellbeingThreshold?.takeIf { it in TeamSettingsRules.THRESHOLD_RANGE } ?: 2.0)
    }
    val nameTooLong = teamNameTooLong(name, team.name)

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Team settings", team.name) }
        if (error != null || message != null) {
            item { FeedbackCard(error, message, onDismissFeedback) }
        }
        item {
            DevWellCard {
                SectionLabel("General")
                Spacer(Modifier.height(12.dp))
                DevWellTextField(name, { name = it }, label = "Team name", enabled = !saving, isError = nameTooLong)
                Text(
                    nameCounterLabel(name),
                    Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nameTooLong) RedText else InkMuted,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.height(10.dp))
                DevWellTextField(timezone, { timezone = it }, label = "Time zone", enabled = !saving)
            }
        }
        item {
            DevWellCard {
                SectionLabel("Check-in schedule")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TeamSettingsRules.CADENCES.forEach { option ->
                        CadenceButton(
                            cadenceLabel(option), selected = cadence == option,
                            modifier = Modifier.weight(1f),
                        ) { cadence = option }
                    }
                }
            }
        }
        item {
            DevWellCard {
                SectionLabel("Wellbeing alerts")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Low-wellbeing threshold",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                    Text(threshold.oneDecimal(), style = MaterialTheme.typography.titleSmall, color = DevGreen)
                }
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = snapThreshold(it) },
                    valueRange = 1f..4f,
                    steps = 5,   // 1.0, 1.5, ... 4.0 = 7 stops, 5 between the ends
                    enabled = !saving,
                    colors = SliderDefaults.colors(
                        thumbColor = DevGreen,
                        activeTrackColor = DevGreen,
                        inactiveTrackColor = TrackGreen,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
                Text(
                    "Team-level setting only. No individual scores are shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        item {
            DevWellCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Team members", Modifier.weight(1f))
                    Text(
                        memberCountLabel(team.memberCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(16.dp), tint = DevGreen)
                    Text(
                        "Member names and check-in status appear here once your team connects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }
        item {
            DevWellButton(
                if (saving) "Saving..." else "Save changes",
                onClick = {
                    onSave(
                        team.copy(
                            name = name,
                            timezone = timezone,
                            checkInCadence = cadence,
                            lowWellbeingThreshold = threshold,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && name.isNotBlank() && !nameTooLong,
            )
        }
    }
}

@Composable
private fun CadenceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DevGreen else CardWhite,
        border = BorderStroke(1.dp, if (selected) DevGreen else BorderGreen),
    ) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else InkMuted,
            textAlign = TextAlign.Center,
        )
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

@Composable
fun ManagerTeamScreen(
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
        item { PageTitle("Team Data", "Anonymised team insights only") }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(SurfaceMuted, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Groups, null, Modifier.size(16.dp), tint = AccentGreen)
                Text(
                    "Anonymised aggregates only. No individual data shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        when {
            aggregate == null -> item {
                DevWellCard { Text("No team data available yet.", color = InkMuted) }
            }
            safe == null -> item {
                DevWellCard {
                    Text("Not enough participants", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Team insights become available when at least " +
                            "${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} team members have checked in. " +
                            "Current participants: ${aggregate.responseCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            else -> {
                item {
                    val wellbeing = wellbeingDisplay(safe.averageWellbeing)
                    DevWellCard {
                        Text("Team well-being score", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        wellbeing.score,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = DevGreen,
                                    )
                                    if (wellbeing.ringFraction != null) {
                                        Text(
                                            "/5",
                                            Modifier.padding(bottom = 4.dp, start = 4.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = InkMuted,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Chip(wellbeing.band, skin = wellbeing.skin)
                            }
                            Spacer(Modifier.width(12.dp))
                            ScoreRing(
                                fraction = wellbeing.ringFraction,
                                modifier = Modifier.size(84.dp),
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat(workloadLabel(safe.averageWorkload), "Avg. workload", Modifier.weight(1f))
                        MiniStat(
                            safe.averageAfterHoursMinutes?.let { "%.1f hrs".format(it / 60.0) } ?: "-",
                            "After-hours",
                            Modifier.weight(1f),
                        )
                        MiniStat(safe.responseCount.toString(), "Participants", Modifier.weight(1f))
                    }
                }
                if (!bars.isNullOrEmpty()) {
                    item { TeamBarChart(bars) }
                }
            }
        }
        item {
            DevWellButton(
                "Review join requests", onJoinRequests,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Outline,
                leadingIcon = Icons.Outlined.Groups,
            )
        }
        item {
            DevWellButton(
                "Team settings", onTeamSettings,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Outline,
                leadingIcon = Icons.Outlined.Settings,
            )
        }
        item {
            PrivacyNotice("Managers can never open individual check-ins, personal notes or personal histories.")
        }
    }
}

@Composable
fun ManagerInsightsScreen(aggregate: TeamAggregate?) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Team Insights", "Supportive patterns from anonymised team data") }
        if (safe == null) {
            item {
                DevWellCard {
                    Text("Not enough responses", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Insights become available when at least " +
                            "${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} team members have checked in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        } else {
            val insights = listOfNotNull(
                workloadInsight(safe.averageWorkload),
                afterHoursInsight(safe.averageAfterHoursMinutes),
                focusInsight(safe.averageFocus),
            )
            if (insights.isEmpty()) {
                item {
                    DevWellCard {
                        Text("Not enough data yet", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Workload, after-hours and focus insights appear once team check-ins include them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
            items(insights) { insight ->
                ManagerInsightCard(insight.emoji, insight.title, insight.body, insight.amber)
            }
            // No suggestions when there is no insight data for them to refer to.
            if (insights.isNotEmpty()) {
                item {
                    DevWellCard {
                        Text("Suggested actions", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "Discuss sustainable workload in your next 1:1s",
                            "Encourage team to set hard stop times",
                            "Review recurring meetings for value",
                            "Celebrate the improving focus trends!",
                        ).forEach { action ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(22.dp)
                                        .background(AccentSurface, RoundedCornerShape(50))
                                        .border(1.dp, TrackGreen, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "→",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DevGreen,
                                    )
                                }
                                Text(action, style = MaterialTheme.typography.bodyMedium, color = Ink)
                            }
                        }
                    }
                }
            }
        }
        item {
            PrivacyNotice("Insights are based on anonymised team data. No individual is identified.")
        }
    }
}

@Composable
private fun ManagerInsightCard(emoji: String, title: String, body: String, amber: Boolean) {
    DevWellCard(
        containerColor = if (amber) AmberSurface else SurfaceMuted,
        borderColor = if (amber) AmberBorder else TrackGreen,
        padding = 16.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
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

private val previewMissingAverages = previewAggregate.copy(
    averageWellbeing = null,
    averageWorkload = null,
    averageEnergy = null,
    averageFocus = null,
    averageAfterHoursMinutes = null,
)

@Preview(showBackground = true, name = "Visible: averages missing")
@Composable
private fun ManagerHomeMissingAveragesPreview() {
    DevWellTheme { ManagerHomeScreen(previewMissingAverages, {}, {}) }
}

@Preview(showBackground = true, name = "Insights: averages missing")
@Composable
private fun ManagerInsightsMissingAveragesPreview() {
    DevWellTheme { ManagerInsightsScreen(previewMissingAverages) }
}

private val previewTeam = Team("preview", "c1", "Mobile Team", memberCount = 8)

@Preview(showBackground = true, name = "Settings: valid")
@Composable
private fun TeamSettingsValidPreview() {
    DevWellTheme { TeamSettingsScreen(previewTeam, {}) }
}

@Preview(showBackground = true, name = "Settings: name too long")
@Composable
private fun TeamSettingsLongNamePreview() {
    val team = previewTeam.copy(name = "A".repeat(TeamSettingsRules.NAME_MAX_LENGTH + 1))
    DevWellTheme { TeamSettingsScreen(team, {}, error = TeamSettingsRules.validate(team)) }
}
