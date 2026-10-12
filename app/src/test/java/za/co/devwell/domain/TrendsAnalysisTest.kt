package za.co.devwell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.model.CheckIn
import za.co.devwell.model.CheckInDraft
import za.co.devwell.model.TrendsAnalysis
import za.co.devwell.model.TrendsPeriod
import java.time.LocalDate

class TrendsAnalysisTest {
    private fun checkIn(date: String, wellbeing: Int = 3) = CheckIn(
        id = date,
        userId = "current-user",
        dateLabel = date,
        values = CheckInDraft(wellbeing = wellbeing, workload = 4, energy = 7, focus = 8),
    )

    @Test fun thisWeekIncludesMondayThroughTodayAndSortsOldestFirst() {
        val today = LocalDate.of(2026, 10, 4) // Sunday
        val selected = TrendsAnalysis.filter(
            listOf(checkIn("2026-09-27"), checkIn("2026-10-04"), checkIn("2026-09-28")),
            TrendsPeriod.THIS_WEEK,
            today,
        )
        assertEquals(listOf("2026-09-28", "2026-10-04"), selected.map { it.dateLabel })
    }

    @Test fun lastWeekUsesPreviousMondayToSunday() {
        val selected = TrendsAnalysis.filter(
            listOf(checkIn("2026-09-27"), checkIn("2026-09-28"), checkIn("2026-10-04")),
            TrendsPeriod.LAST_WEEK,
            LocalDate.of(2026, 10, 4),
        )
        assertEquals(listOf("2026-09-27"), selected.map { it.dateLabel })
    }

    @Test fun monthAndThreeMonthPeriodsExcludeOlderRows() {
        val entries = listOf(checkIn("2026-06-30"), checkIn("2026-07-04"), checkIn("2026-10-01"))
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(listOf("2026-10-01"), TrendsAnalysis.filter(entries, TrendsPeriod.THIS_MONTH, today).map { it.dateLabel })
        assertEquals(listOf("2026-07-04", "2026-10-01"), TrendsAnalysis.filter(entries, TrendsPeriod.LAST_THREE_MONTHS, today).map { it.dateLabel })
    }

    @Test fun invalidDatesAndEmptyHistoryAreHandled() {
        assertTrue(TrendsAnalysis.filter(listOf(checkIn("not-a-date")), TrendsPeriod.THIS_WEEK, LocalDate.of(2026, 10, 4)).isEmpty())
        val summary = TrendsAnalysis.summarize(emptyList())
        assertNull(summary.averageWellbeing)
        assertNull(summary.averageWorkload)
        assertNull(summary.averageEnergy)
        assertNull(summary.averageFocus)
    }

    @Test fun oneCheckInProducesUsefulPartialSummary() {
        val summary = TrendsAnalysis.summarize(listOf(checkIn("2026-10-04", wellbeing = 5)))
        assertEquals(5.0, summary.averageWellbeing!!, 0.0)
        assertEquals(4.0, summary.averageWorkload!!, 0.0)
        assertEquals(7.0, summary.averageEnergy!!, 0.0)
        assertEquals(8.0, summary.averageFocus!!, 0.0)
    }

    @Test fun chartEntriesKeepTheLatestTwelveCheckInsInAscendingOrder() {
        val entries = (1..14).map { day -> checkIn("2026-09-${day.toString().padStart(2, '0')}") }

        assertEquals(12, TrendsAnalysis.chartEntries(entries).size)
        assertEquals("2026-09-03", TrendsAnalysis.chartEntries(entries).first().dateLabel)
        assertEquals("2026-09-14", TrendsAnalysis.chartEntries(entries).last().dateLabel)
    }
}
