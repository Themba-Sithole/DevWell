package za.co.devwell.ui.engineer

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInDraftTest {
    @Test
    fun `afterHoursIndexFor maps each bucket exactly`() {
        assertEquals(0, afterHoursIndexFor(0))
        assertEquals(1, afterHoursIndexFor(15))
        assertEquals(2, afterHoursIndexFor(45))
        assertEquals(3, afterHoursIndexFor(90))
        assertEquals(4, afterHoursIndexFor(150))
    }

    @Test
    fun `afterHoursIndexFor rounds to the nearest bucket`() {
        assertEquals(1, afterHoursIndexFor(20))
        assertEquals(2, afterHoursIndexFor(50))
        assertEquals(4, afterHoursIndexFor(200))
    }

    @Test
    fun `afterHoursIndexFor is the inverse of afterHoursMinutesFor`() {
        (0..4).forEach { index ->
            assertEquals(index, afterHoursIndexFor(afterHoursMinutesFor(index)))
        }
    }
}
