package za.co.devwell.ui.engineer

import org.junit.Assert.*
import org.junit.Test

class TeamHighlightsTest {
    private val goodTexts = setOf("Workload is balanced", "Focus time is solid", "After-hours work is low")

    @Test fun missingAveragesHideEveryLine() {
        assertTrue(teamHighlights(null, null, null).isEmpty())
        assertTrue(teamHighlights(Double.NaN, Double.NaN, Double.NaN).isEmpty())
    }

    @Test fun onlyMissingLineIsHidden() {
        val texts = teamHighlights(3.0, null, 30.0).map { it.text }
        assertEquals(listOf("Workload is balanced", "After-hours work is low"), texts)
    }

    @Test fun goodResultsAreGreenWithGoodText() {
        val highlights = teamHighlights(3.0, 7.0, 30.0)
        assertEquals(
            listOf("Workload is balanced", "Focus time is solid", "After-hours work is low"),
            highlights.map { it.text },
        )
        assertTrue(highlights.all { it.positive })
    }

    @Test fun badResultsAreAmberWithMatchingText() {
        val highlights = teamHighlights(4.2, 5.0, 90.0)
        assertEquals(
            listOf("Workload is elevated", "Focus time could use support", "After-hours work is trending up"),
            highlights.map { it.text },
        )
        assertTrue(highlights.none { it.positive })
    }

    @Test fun boundaries() {
        assertTrue(teamHighlights(3.5, null, null).single().positive)
        assertFalse(teamHighlights(3.6, null, null).single().positive)
        assertTrue(teamHighlights(null, 6.5, null).single().positive)
        assertFalse(teamHighlights(null, 6.4, null).single().positive)
        assertTrue(teamHighlights(null, null, 60.0).single().positive)
        assertFalse(teamHighlights(null, null, 60.1).single().positive)
    }

    @Test fun dotAgreesWithSentence() {
        listOf(
            teamHighlights(3.0, 7.0, 30.0),
            teamHighlights(4.2, 5.0, 90.0),
            teamHighlights(3.5, 6.4, 60.0),
        ).flatten().forEach { assertEquals(it.text, it.positive, it.text in goodTexts) }
    }
}
