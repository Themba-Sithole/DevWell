package za.co.devwell.ui.manager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import za.co.devwell.model.TeamMember
import za.co.devwell.ui.auth.AuthFeedback
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*
import za.co.devwell.viewmodel.ManagerViewModel
import za.co.devwell.viewmodel.TeamSettingsRules
import kotlin.math.roundToInt

private fun Double?.oneDecimal(): String = this?.let { "%.1f".format(it) } ?: "-"

/**
 * Roster card wording. [known] is true when the roster loaded (Team.roster != null); only then is 0
 * a real "no members". Otherwise zero or less means the count is not available.
 */
internal fun memberCountLabel(count: Int, known: Boolean = false): String = when {
    known && count == 0 -> "No members yet"
    count <= 0 -> "Member count not available yet"
    count == 1 -> "1 member"
    else -> "$count members"
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

/** True only when both are real numbers and the team average is strictly below the manager's threshold. */
internal fun belowAlertThreshold(average: Double?, threshold: Double?): Boolean =
    average != null && threshold != null && average.isFinite() && threshold.isFinite() && average < threshold

/** One muted line under the wellbeing chip. Uses the anonymous team average and a team-level setting only. */
@Composable
private fun AlertThresholdNote(average: Double?, threshold: Double?) {
    if (!belowAlertThreshold(average, threshold)) return
    Spacer(Modifier.height(4.dp))
    Text(
        "Below your alert threshold (${threshold!!.oneDecimal()})",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
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

/**
 * Everything a manager can see about their team: the anonymisation banner, the well-being score,
 * the workload/after-hours/participants stats, the chart and the anonymity confirmation.
 *
 * This exact composable backs the manager dashboard *and* the member-facing
 * "What my manager sees" preview, so the preview can never drift away from the real view.
 */
@Composable
internal fun ManagerTeamInsights(aggregate: TeamAggregate?, alertThreshold: Double? = null) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    val bars = aggregate?.let { teamChartBars(it) }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
        if (aggregate == null) {
            DevWellCard { Text("No team data available yet.", color = InkMuted) }
        } else if (safe == null) {
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
        } else {
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
                        AlertThresholdNote(safe.averageWellbeing, alertThreshold)
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
            if (!bars.isNullOrEmpty()) {
                TeamBarChart(bars)
            }
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
}

@Composable
fun ManagerHomeScreen(
    aggregate: TeamAggregate?,
    onJoinRequests: () -> Unit,
    onTeamSettings: () -> Unit,
    onProfile: () -> Unit = {},
    managerName: String = "",
    alertThreshold: Double? = null,
    teamName: String? = null,
) {
    val firstName = managerName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val title = teamName?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (firstName.isEmpty()) "Your team" else "$firstName's Team"
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Manager dashboard", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    Text(
                        title,
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
            ManagerTeamInsights(aggregate, alertThreshold)
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
    onBack: () -> Unit = {},
    teamName: String? = null,
) {
    val busy = reviewingId != null
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(
                "Join requests",
                teamName?.let { "Engineers requesting access to $it" }
                    ?: "Engineers requesting access to your teams",
                onBack,
            )
        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(request.userName.initials(), size = 40.dp)
                    Text(
                        request.userName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
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
    onBack: () -> Unit = {},
    removingMemberId: String? = null,
    deletingTeam: Boolean = false,
    onRemoveMember: (userId: String) -> Unit = {},
    onDeleteTeam: () -> Unit = {},
) {
    if (team == null) {
        Column(Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding()) {
            BackHeader("Team settings", null, onBack)
            Spacer(Modifier.height(12.dp))
            Text(
                "You don't manage any teams yet. Create one from Team Data.",
                color = InkMuted,
            )
        }
        return
    }
    var name by remember(team.id) { mutableStateOf(team.name) }
    var threshold by remember(team.id) {
        mutableDoubleStateOf(team.lowWellbeingThreshold?.takeIf { it in TeamSettingsRules.THRESHOLD_RANGE } ?: 2.0)
    }
    var memberToRemove by remember(team.id) { mutableStateOf<TeamMember?>(null) }
    var confirmDelete by remember(team.id) { mutableStateOf(false) }
    val busy = saving || deletingTeam || removingMemberId != null
    val nameTooLong = teamNameTooLong(name, team.name)
    // Called from input callbacks only (not on recomposition): editing hides an old error or "saved" message.
    fun edited() { if (error != null || message != null) onDismissFeedback() }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding().devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Team settings", team.name, onBack) }
        if (error != null || message != null) {
            item { FeedbackCard(error, message, onDismissFeedback) }
        }
        item {
            DevWellCard {
                SectionLabel("General")
                Spacer(Modifier.height(12.dp))
                DevWellTextField(name, { edited(); name = it }, label = "Team name", enabled = !saving, isError = nameTooLong)
                Text(
                    nameCounterLabel(name),
                    Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nameTooLong) RedText else InkMuted,
                    textAlign = TextAlign.End,
                )
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
                    onValueChange = { edited(); threshold = snapThreshold(it) },
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
                    "Team-level setting only. Your Overview shows a note when the team average is below this.",
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
                        memberCountLabel(team.memberCount, known = team.roster != null),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Names only: no check-in status or wellbeing data is shown per member.
                val roster = team.roster
                if (roster.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(16.dp), tint = DevGreen)
                        Text(
                            if (roster == null) "Member names will appear here once they load."
                            else "No engineers have joined this team yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        roster.forEach { member ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Avatar(member.name.initials(), size = 28.dp)
                                Text(
                                    member.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                )
                                TextButton(
                                    onClick = { memberToRemove = member },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text("Remove", color = if (busy) InkMuted else RedText)
                                }
                                if (removingMemberId == member.userId) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Removing a member clears their team. They choose a company and team again before using the app.",
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
                    // Time zone and cadence are no longer editable here, so the stored values
                    // are sent back unchanged (with the same defaults the backend expects).
                    onSave(
                        team.copy(
                            name = name,
                            timezone = team.timezone?.takeIf { it.isNotBlank() } ?: DEFAULT_TIMEZONE,
                            checkInCadence = team.checkInCadence?.takeIf { it in TeamSettingsRules.CADENCES }
                                ?: DEFAULT_CADENCE,
                            lowWellbeingThreshold = threshold,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && name.isNotBlank() && !nameTooLong,
            )
        }
        item {
            DevWellCard {
                SectionLabel("Delete team")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deleting ${team.name} removes it for everyone. Its engineers must choose a " +
                        "company and team again before using the app. Their past check-ins stay private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedText),
                ) {
                    Text(
                        if (deletingTeam) "Deleting..." else "Delete team",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    memberToRemove?.let { member ->
        DevWellDialog(
            title = "Remove ${member.name}?",
            subtitle = "They lose access to ${team.name} and must choose a company and team again. " +
                "Their wellbeing data stays private.",
            confirmLabel = "Remove member",
            confirmEnabled = !busy,
            destructive = true,
            onConfirm = {
                memberToRemove = null
                onRemoveMember(member.userId)
            },
            onDismiss = { if (!busy) memberToRemove = null },
        ) {}
    }
    if (confirmDelete) {
        DevWellDialog(
            title = "Delete ${team.name}?",
            subtitle = "This cannot be undone. Every engineer on the team must choose a company and " +
                "team again, and will need your approval to rejoin.",
            confirmLabel = if (deletingTeam) "Deleting..." else "Delete team",
            confirmEnabled = !busy,
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDeleteTeam()
            },
            onDismiss = { if (!busy) confirmDelete = false },
        ) {}
    }
}

/** Fallbacks for teams loaded before the settings columns had a value. */
private const val DEFAULT_TIMEZONE = "Africa/Johannesburg"
private const val DEFAULT_CADENCE = "Daily"

/** Connects the join-requests screen to its ViewModel. Clears old feedback on entry. */
@Composable
fun JoinRequestsRoute(viewModel: ManagerViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearFeedback() }
    JoinRequestsScreen(
        requests = state.joinRequests,
        onReview = viewModel::reviewJoinRequest,
        reviewingId = state.reviewingId,
        error = state.error,
        message = state.message,
        onDismissFeedback = viewModel::clearFeedback,
        onBack = onBack,
        teamName = state.team?.name,
    )
}

@Composable
fun TeamSettingsRoute(viewModel: ManagerViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearFeedback() }
    TeamSettingsScreen(
        team = state.team,
        onSave = viewModel::updateTeamSettings,
        saving = state.savingSettings,
        error = state.error,
        message = state.message,
        onDismissFeedback = viewModel::clearFeedback,
        onBack = onBack,
        removingMemberId = state.removingMemberId,
        deletingTeam = state.deletingTeam,
        onRemoveMember = viewModel::removeMember,
        onDeleteTeam = viewModel::deleteTeam,
    )
}

@Composable
fun ManagerTeamScreen(
    aggregate: TeamAggregate?,
    onJoinRequests: () -> Unit,
    onTeamSettings: () -> Unit,
    alertThreshold: Double? = null,
    teams: List<Team> = emptyList(),
    selectedTeamId: String? = null,
    onSelectTeam: (String) -> Unit = {},
    onCreateTeam: (String) -> Unit = {},
    creatingTeam: Boolean = false,
    teamError: String? = null,
) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    val bars = aggregate?.let { teamChartBars(it) }
    val listState = rememberLazyListState()
    var showCreateTeam by remember { mutableStateOf(false) }
    var newTeamName by remember { mutableStateOf("") }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageTitle("Team Data", "Anonymised team insights only") }
        if (teams.isNotEmpty()) {
            item {
                TeamSwitcher(
                    teams = teams,
                    selectedTeamId = selectedTeamId,
                    creatingTeam = creatingTeam,
                    error = teamError,
                    onSelect = onSelectTeam,
                    onCreate = { showCreateTeam = true },
                )
            }
        } else {
            item {
                DevWellCard {
                    SectionLabel("Your teams")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You don't have any teams yet. Create one so engineers can request to join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    if (teamError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(teamError, style = MaterialTheme.typography.bodySmall, color = RedText)
                    }
                    Spacer(Modifier.height(12.dp))
                    DevWellButton(
                        "Create team",
                        { showCreateTeam = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !creatingTeam,
                    )
                }
            }
        }
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
                                AlertThresholdNote(safe.averageWellbeing, alertThreshold)
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

    if (showCreateTeam) {
        DevWellDialog(
            title = "New team",
            subtitle = "Engineers can request to join this team and you approve each request.",
            confirmLabel = if (creatingTeam) "Creating..." else "Create team",
            confirmEnabled = !creatingTeam && newTeamName.isNotBlank(),
            onConfirm = {
                onCreateTeam(newTeamName)
                showCreateTeam = false
                newTeamName = ""
            },
            onDismiss = { showCreateTeam = false; newTeamName = "" },
        ) {
            DevWellTextField(
                value = newTeamName,
                onValueChange = { newTeamName = it },
                label = "Team name",
                placeholder = "Platform Engineering",
                enabled = !creatingTeam,
            )
        }
    }
}

/**
 * Lets a manager move between the teams they own and add another one to their
 * company. The selected team drives the join requests and roster shown.
 */
@Composable
private fun TeamSwitcher(
    teams: List<Team>,
    selectedTeamId: String?,
    creatingTeam: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    DevWellCard {
        SectionLabel("Your teams")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            teams.forEach { team ->
                val selected = team.id == selectedTeamId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(team.id) },
                    label = { Text(team.name, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
            AssistChip(
                onClick = onCreate,
                enabled = !creatingTeam,
                label = { Text(if (creatingTeam) "Creating..." else "New team") },
                leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(16.dp)) },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = RedText)
        }
    }
}

@Composable
fun ManagerInsightsScreen(aggregate: TeamAggregate?) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
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

@Preview(showBackground = true, name = "Visible: below alert threshold")
@Composable
private fun ManagerHomeBelowThresholdPreview() {
    DevWellTheme {
        ManagerHomeScreen(
            previewAggregate.copy(averageWellbeing = 1.8), {}, {},
            alertThreshold = 2.0,
            teamName = "Platform Squad",
        )
    }
}

@Preview(showBackground = true, name = "Title: 60-character team name")
@Composable
private fun ManagerHomeLongNamePreview() {
    DevWellTheme {
        ManagerHomeScreen(
            previewAggregate, {}, {},
            teamName = "Platform Engineering & Developer Experience Squad, Cape Town",  // 60 chars = NAME_MAX_LENGTH
        )
    }
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

private val previewTeam = Team(
    "preview", "c1", "Mobile Team",
    memberCount = 3,
    roster = listOf(
        TeamMember("u1", "Aisha Khan"),
        TeamMember("u2", "Ben Mokoena"),
        TeamMember("u3", "Chloe Smith"),
    ),
)

@Preview(showBackground = true, name = "Settings: valid")
@Composable
private fun TeamSettingsValidPreview() {
    DevWellTheme { TeamSettingsScreen(previewTeam, {}) }
}

@Preview(showBackground = true, name = "Settings: empty roster")
@Composable
private fun TeamSettingsEmptyRosterPreview() {
    DevWellTheme { TeamSettingsScreen(previewTeam.copy(memberCount = 0, roster = emptyList()), {}) }
}

@Preview(showBackground = true, name = "Settings: name too long")
@Composable
private fun TeamSettingsLongNamePreview() {
    val team = previewTeam.copy(name = "A".repeat(TeamSettingsRules.NAME_MAX_LENGTH + 1))
    DevWellTheme { TeamSettingsScreen(team, {}, error = TeamSettingsRules.validate(team)) }
}
