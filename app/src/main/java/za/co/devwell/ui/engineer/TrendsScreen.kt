package za.co.devwell.ui.engineer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckIn
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate
import za.co.devwell.model.TrendsAnalysis
import za.co.devwell.model.TrendsPeriod
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*
import java.util.Locale

private val WELLBEING_LABELS = listOf("Struggling", "Not great", "Okay", "Good", "Great")

@Composable
fun TrendsScreen(checkIns: List<CheckIn>, onBack: () -> Unit = {}) {
    var selectedPeriodName by rememberSaveable { mutableStateOf(TrendsPeriod.THIS_WEEK.name) }
    val selectedPeriod = TrendsPeriod.entries.firstOrNull { it.name == selectedPeriodName }
        ?: TrendsPeriod.THIS_WEEK
    val today = TrendsAnalysis.today()
    val visibleCheckIns = TrendsAnalysis.filter(checkIns, selectedPeriod, today)
    val summary = TrendsAnalysis.summarize(visibleCheckIns)

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Your trends", "Your private check-in history", onBack) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrendsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriodName = period.name },
                        label = { Text(period.label) },
                    )
                }
            }
        }
        if (visibleCheckIns.isEmpty()) {
            item {
                DevWellCard {
                    Text(
                        if (checkIns.isEmpty()) "No check-ins yet. Complete your first check-in from Home."
                        else "No check-ins in ${selectedPeriod.label.lowercase(Locale.ROOT)}. Choose another period to see more history."
                    )
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Avg wellbeing", formatAverage(summary.averageWellbeing, "/5"), Modifier.weight(1f))
                        StatCard("Avg workload", formatAverage(summary.averageWorkload, "/5"), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Avg energy", formatAverage(summary.averageEnergy, "/10"), Modifier.weight(1f))
                        StatCard("Avg focus", formatAverage(summary.averageFocus, "/10"), Modifier.weight(1f))
                    }
                }
            }
            if (visibleCheckIns.size >= 2) {
                item {
                    DevWellCard {
                        Text("Wellbeing", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(
                            "Your ${selectedPeriod.label.lowercase(Locale.ROOT)} check-ins",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(14.dp))
                        LineChart(
                            values = visibleCheckIns.takeLast(12).map { it.values.wellbeing.toFloat() },
                            max = 5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .semantics {
                                    contentDescription = buildString {
                                        append("Wellbeing over time")
                                        visibleCheckIns.takeLast(12).forEach {
                                            append(". ${it.dateLabel}: ${it.values.wellbeing}/5")
                                        }
                                    }
                                },
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(
                                visibleCheckIns.first().dateLabel,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                            Text(
                                visibleCheckIns.last().dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
                item {
                    DevWellCard {
                        Text("Energy and focus", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(
                            "Daily scores out of 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(14.dp))
                        GroupedBarChart(
                            seriesA = visibleCheckIns.takeLast(12).map { it.values.energy.toFloat() },
                            seriesB = visibleCheckIns.takeLast(12).map { it.values.focus.toFloat() },
                            max = 10f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .semantics {
                                    contentDescription = buildString {
                                        append("Energy and focus over time")
                                        visibleCheckIns.takeLast(12).forEach {
                                            append(". ${it.dateLabel}: energy ${it.values.energy}/10")
                                            append(", focus ${it.values.focus}/10")
                                        }
                                    }
                                },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(DevGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("Energy", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            Spacer(Modifier.width(14.dp))
                            LegendDot(LightGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("Focus", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                        }
                    }
                }
            }
            item { SectionLabel("Check-in history") }
            items(visibleCheckIns.asReversed(), key = { it.id }) { checkIn ->
                val wellbeing = checkIn.values.wellbeing
                DevWellCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(checkIn.dateLabel, style = MaterialTheme.typography.titleSmall, color = Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Energy ${checkIn.values.energy}/10 · Focus ${checkIn.values.focus}/10",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                            Text(
                                "Workload ${checkIn.values.workload}/5",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                        Chip(
                            WELLBEING_LABELS.getOrElse(wellbeing - 1) { "—" },
                            skin = when {
                                wellbeing >= 4 -> ChipSkin.Green
                                wellbeing == 3 -> ChipSkin.Neutral
                                else -> ChipSkin.Amber
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatAverage(value: Double?, suffix: String): String =
    value?.let { String.format(Locale.ROOT, "%.1f%s", it, suffix) } ?: "—"

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    DevWellCard(modifier = modifier, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = DevGreen)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
}

@Composable
private fun LineChart(values: List<Float>, max: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val inset = 8.dp.toPx()
        val usableWidth = size.width - inset * 2
        val stepX = usableWidth / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            val fraction = (v / max).coerceIn(0f, 1f)
            Offset(inset + i * stepX, size.height - inset - fraction * (size.height - inset * 2))
        }
        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(AccentSurface, AccentSurface.copy(alpha = 0.2f)),
            ),
        )
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = line,
            color = DevGreen,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEach {
            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = it)
            drawCircle(DevGreen, radius = 3.dp.toPx(), center = it)
        }
        drawLine(
            color = BorderGreen,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun GroupedBarChart(
    seriesA: List<Float>,
    seriesB: List<Float>,
    max: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val count = seriesA.size
        if (count == 0) return@Canvas
        val inset = 4.dp.toPx()
        val groupWidth = size.width / count
        val barWidth = (groupWidth * 0.28f).coerceAtMost(16.dp.toPx())
        val gap = 3.dp.toPx()
        val chartHeight = size.height - inset * 2
        for (i in 0 until count) {
            val center = i * groupWidth + groupWidth / 2
            val heightA = (seriesA[i] / max).coerceIn(0f, 1f) * chartHeight
            val heightB = (seriesB[i] / max).coerceIn(0f, 1f) * chartHeight
            drawRoundRect(
                color = DevGreen,
                topLeft = Offset(center - gap / 2 - barWidth, size.height - inset - heightA),
                size = Size(barWidth, heightA),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            drawRoundRect(
                color = LightGreen,
                topLeft = Offset(center + gap / 2, size.height - inset - heightB),
                size = Size(barWidth, heightB),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
        drawLine(
            color = BorderGreen,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
fun PrivacyPreviewScreen(aggregate: TeamAggregate?, onBack: () -> Unit = {}) {
    val safe = aggregate?.let { PrivacyPolicy.safeAggregateOrNull(it) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(
            "What my manager sees",
            "Exactly the same safe team output available to management",
            onBack,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentSurface, RoundedCornerShape(12.dp))
                .border(2.dp, DevGreen, RoundedCornerShape(12.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = DevGreen)
            Text(
                "This is the exact view your manager sees. Only anonymised team data is shared — " +
                    "names, individual scores and private notes are never included.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .dashedBorder(BorderGreen, 2.dp, 16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Manager's view · team overview (anonymised)")
                if (aggregate == null) {
                    Text("No team data available yet.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                } else if (safe == null) {
                    Text("Not enough participants", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(
                        "Team charts are hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                            "distinct participants are available. Current participants: ${aggregate.responseCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                } else {
                    MetricRow("Wellbeing", safe.averageWellbeing, " / 5")
                    MetricRow("Workload", safe.averageWorkload, " / 5")
                    MetricRow("Energy", safe.averageEnergy, " / 10")
                    MetricRow("Focus", safe.averageFocus, " / 10")
                    DevWellDivider()
                    Text(
                        "Based on ${safe.responseCount} participants — no names or notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }
        DevWellCard {
            Text("What your manager cannot see", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(10.dp))
            listOf(
                "Your name or individual answers",
                "Your exact scores or personal trends",
                "Your private check-in notes",
            ).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = RedText)
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
        PrivacyNotice()
    }
}

@Composable
private fun MetricRow(label: String, value: Double?, suffix: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Text(
            value?.let { "%.1f".format(it) + suffix } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = DevGreen,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f)),
    )
    drawRoundRect(
        color = color,
        style = stroke,
        topLeft = Offset(stroke.width / 2, stroke.width / 2),
        size = Size(size.width - stroke.width, size.height - stroke.width),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}
