package za.co.devwell.ui.engineer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.CheckIn
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate
import za.co.devwell.model.TrendsAnalysis
import za.co.devwell.model.TrendsPeriod
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice
import java.util.Locale

@Composable
fun TrendsScreen(checkIns: List<CheckIn>) {
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
        item { PageTitle("Personal trends", "Your private check-in history") }
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
                        SummaryCard("Avg wellbeing", summary.averageWellbeing, "/5", Modifier.weight(1f))
                        SummaryCard("Avg workload", summary.averageWorkload, "/5", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("Avg energy", summary.averageEnergy, "/10", Modifier.weight(1f))
                        SummaryCard("Avg focus", summary.averageFocus, "/10", Modifier.weight(1f))
                    }
                }
            }
            item {
                TrendLineCard(
                    title = "Workload over time",
                    unit = "/5",
                    values = visibleCheckIns.map { it.dateLabel to it.values.workload.toFloat() },
                    maximum = 5f,
                )
            }
            item {
                TrendLineCard(
                    title = "Energy and focus",
                    unit = "/10",
                    values = visibleCheckIns.map { it.dateLabel to it.values.energy.toFloat() },
                    secondValues = visibleCheckIns.map { it.dateLabel to it.values.focus.toFloat() },
                    maximum = 10f,
                    secondLabel = "Focus",
                )
            }
            item {
                Text("Check-ins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(visibleCheckIns.asReversed(), key = { it.id }) { item ->
                DevWellCard {
                    Text(item.dateLabel, fontWeight = FontWeight.Bold)
                    Text("Wellbeing ${item.values.wellbeing}/5 • Workload ${item.values.workload}/5")
                    Text("Energy ${item.values.energy}/10 • Focus ${item.values.focus}/10")
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: Double?, scale: String, modifier: Modifier = Modifier) {
    DevWellCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = "${value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"}$scale",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TrendLineCard(
    title: String,
    unit: String,
    values: List<Pair<String, Float>>,
    maximum: Float,
    secondValues: List<Pair<String, Float>> = emptyList(),
    secondLabel: String = "",
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = buildString {
        append(title)
        values.forEach { (date, value) -> append(". $date: ${value.toInt()}$unit") }
        secondValues.forEach { (date, value) -> append(". $date $secondLabel: ${value.toInt()}$unit") }
    }
    DevWellCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description },
        ) {
            val inset = 14.dp.toPx()
            val chartWidth = (size.width - inset * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - inset * 2).coerceAtLeast(1f)
            for (line in 0..2) {
                val y = inset + chartHeight * line / 2f
                drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
            }
            fun drawSeries(series: List<Pair<String, Float>>, color: androidx.compose.ui.graphics.Color) {
                if (series.isEmpty()) return
                val points = series.mapIndexed { index, (_, value) ->
                    val x = if (series.size == 1) size.width / 2f
                    else inset + chartWidth * index / (series.size - 1)
                    val y = inset + chartHeight * (1f - (value / maximum).coerceIn(0f, 1f))
                    Offset(x, y)
                }
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                }
                points.forEach { drawCircle(color, radius = 5.dp.toPx(), center = it) }
            }
            drawSeries(values, primary)
            drawSeries(secondValues, secondary)
        }
        if (values.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(values.first().first, style = MaterialTheme.typography.labelSmall)
                Text(values.last().first, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (secondValues.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● Energy", color = primary, style = MaterialTheme.typography.labelMedium)
                Text("● $secondLabel", color = secondary, style = MaterialTheme.typography.labelMedium)
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
                Text("Not enough participants", fontWeight = FontWeight.Bold)
                Text(
                    "Team charts are hidden until at least ${PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES} " +
                        "participants are available. Current participants: ${aggregate.responseCount}",
                )
            }
        } else {
            DevWellCard {
                Text("Team overview (anonymous)", style = MaterialTheme.typography.titleMedium)
                Text("Wellbeing ${safe.averageWellbeing.displayAverage()}/5")
                Text("Workload ${safe.averageWorkload.displayAverage()}/5")
                Text("Energy ${safe.averageEnergy.displayAverage()}/10 • Focus ${safe.averageFocus.displayAverage()}/10")
                Text("Based on ${safe.responseCount} participants — no names or notes.")
            }
        }
    }
}

private fun Double?.displayAverage(): String =
    this?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
