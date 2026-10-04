package za.co.devwell.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class TrendsPeriod(val label: String) {
    THIS_WEEK("This week"),
    LAST_WEEK("Last week"),
    THIS_MONTH("This month"),
    LAST_THREE_MONTHS("Last 3 months"),
}

data class TrendsDateRange(val start: LocalDate, val endInclusive: LocalDate) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
}

data class TrendsSummary(
    val averageWellbeing: Double?,
    val averageWorkload: Double?,
    val averageEnergy: Double?,
    val averageFocus: Double?,
)

object TrendsAnalysis {
    private val appZone = ZoneId.of("Africa/Johannesburg")

    fun today(): LocalDate = LocalDate.now(appZone)

    fun range(period: TrendsPeriod, today: LocalDate = today()): TrendsDateRange {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (period) {
            TrendsPeriod.THIS_WEEK -> TrendsDateRange(monday, today)
            TrendsPeriod.LAST_WEEK -> TrendsDateRange(monday.minusWeeks(1), monday.minusDays(1))
            TrendsPeriod.THIS_MONTH -> TrendsDateRange(today.withDayOfMonth(1), today)
            TrendsPeriod.LAST_THREE_MONTHS -> TrendsDateRange(today.minusMonths(3), today)
        }
    }

    /** Check-in dates are stored as ISO local dates; invalid labels are ignored safely. */
    fun filter(
        checkIns: List<CheckIn>,
        period: TrendsPeriod,
        today: LocalDate = today(),
    ): List<CheckIn> {
        val range = range(period, today)
        return checkIns.mapNotNull { checkIn ->
            val date = runCatching { LocalDate.parse(checkIn.dateLabel.take(10)) }.getOrNull()
            if (date != null && range.contains(date)) date to checkIn else null
        }.sortedBy { it.first }.map { it.second }
    }

    fun summarize(checkIns: List<CheckIn>): TrendsSummary = TrendsSummary(
        averageWellbeing = checkIns.averageOf { it.values.wellbeing },
        averageWorkload = checkIns.averageOf { it.values.workload },
        averageEnergy = checkIns.averageOf { it.values.energy },
        averageFocus = checkIns.averageOf { it.values.focus },
    )

    private inline fun List<CheckIn>.averageOf(value: (CheckIn) -> Int): Double? =
        if (isEmpty()) null else map { value(it).toDouble() }.average()
}
