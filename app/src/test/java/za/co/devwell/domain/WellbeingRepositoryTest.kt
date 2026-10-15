package za.co.devwell.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import za.co.devwell.data.MockWellbeingRepository
import za.co.devwell.model.CheckInDraft
import za.co.devwell.model.TrendsAnalysis

class WellbeingRepositoryTest {
    private val repository = MockWellbeingRepository()

    @Test
    fun `saving twice in one day updates the same check-in`() = runBlocking {
        val sizeBefore = repository.checkIns.first().size

        repository.saveCheckIn(
            CheckInDraft(wellbeing = 5, workload = 1, energy = 9, focus = 9, afterHoursMinutes = 0, note = "first"),
        )
        val afterFirst = repository.checkIns.first()
        assertEquals(sizeBefore, afterFirst.size)
        val firstToday = TrendsAnalysis.todayCheckIn(afterFirst)
        assertNotNull(firstToday)
        assertEquals("first", firstToday!!.values.note)

        repository.saveCheckIn(
            CheckInDraft(wellbeing = 2, workload = 5, energy = 3, focus = 3, afterHoursMinutes = 150, note = "corrected"),
        )
        val afterSecond = repository.checkIns.first()
        assertEquals(sizeBefore, afterSecond.size)
        val corrected = TrendsAnalysis.todayCheckIn(afterSecond)
        assertNotNull(corrected)
        assertEquals("corrected", corrected!!.values.note)
        assertEquals(2, corrected.values.wellbeing)
    }

    @Test
    fun `only one check-in exists per day`() = runBlocking {
        repository.saveCheckIn(CheckInDraft(wellbeing = 4, note = "a"))
        repository.saveCheckIn(CheckInDraft(wellbeing = 4, note = "b"))
        repository.saveCheckIn(CheckInDraft(wellbeing = 4, note = "c"))

        val labelCounts = repository.checkIns.first().groupingBy { it.dateLabel }.eachCount()
        assertEquals(1, labelCounts[TrendsAnalysis.today().toString()])
    }
}
