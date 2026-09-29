package za.co.devwell.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProjectDeadlineParserTest {
    @Test
    fun `parses supported deadline formats deterministically`() {
        val today = LocalDate.of(2026, 9, 25)
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("18 Sep", today))
        assertEquals(LocalDate.of(2027, 9, 18), ProjectDeadlineParser.parse("18 Sep 2027", today))
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("2026-09-18", today))
    }

    @Test
    fun `treats January as next year in December`() {
        assertEquals(
            LocalDate.of(2027, 1, 1),
            ProjectDeadlineParser.parse("1 Jan", LocalDate.of(2026, 12, 31)),
        )
    }
}
