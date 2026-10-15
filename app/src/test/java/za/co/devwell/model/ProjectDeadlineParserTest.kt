package za.co.devwell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectDeadlineParserTest {
    private val today = LocalDate.of(2026, 9, 25)

    @Test
    fun `parses supported deadline formats deterministically`() {
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

    @Test
    fun `month matching is case-insensitive and tolerates a trailing dot`() {
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("18 sep", today))
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("18 SEP", today))
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("18 Sep.", today))
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("18 sep. 2026", today))
    }

    @Test
    fun `collapses surrounding and repeated whitespace`() {
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("  18   Sep  ", today))
        assertEquals(LocalDate.of(2026, 9, 18), ProjectDeadlineParser.parse("\t18 Sep ", today))
    }

    @Test
    fun `rejects impossible calendar dates and malformed labels`() {
        listOf(
            "31 Feb 2027",
            "0 Sep",
            "32 Sep",
            "18 Xyz",
            "18 Sep 27",
            "18 Sep 1899",
            "2026-13-01",
            "2026-02-30",
            "not-a-date",
            "18",
            "18 Sep 2026 extra",
            "",
        ).forEach { label ->
            assertFalse("expected \"$label\" to be invalid", ProjectDeadlineParser.isValid(label))
        }
    }

    @Test
    fun `isValid reflects parse success`() {
        assertTrue(ProjectDeadlineParser.isValid("18 Sep"))
        assertFalse(ProjectDeadlineParser.isValid("18 Smarch"))
    }

    @Test
    fun `toStorage and toDisplay round-trip within the current year`() {
        val stored = ProjectDeadlineParser.toStorage("18 Sep", today)
        assertEquals("2026-09-18", stored)
        assertEquals("18 Sep", ProjectDeadlineParser.toDisplay(stored, today))
    }

    @Test
    fun `toDisplay keeps the year for other years`() {
        assertEquals("18 Sep 2027", ProjectDeadlineParser.toDisplay("2027-09-18", today))
    }
}
