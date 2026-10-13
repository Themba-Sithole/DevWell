package za.co.devwell.ui.manager

import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.ui.components.ChipSkin

class MissingAverageDisplayTest {
    @Test fun nullWellbeingShowsDashAndNeutralLabel() {
        val display = wellbeingDisplay(null)
        assertEquals("-", display.score)
        assertEquals("Not enough data yet", display.band)
        assertEquals(ChipSkin.Neutral, display.skin)
        assertNull(display.ringFraction)
    }

    @Test fun nonFiniteWellbeingIsTreatedAsMissing() {
        assertEquals(wellbeingDisplay(null), wellbeingDisplay(Double.NaN))
        assertEquals(wellbeingDisplay(null), wellbeingDisplay(Double.POSITIVE_INFINITY))
    }

    @Test fun realZeroIsStillAScore() {
        val display = wellbeingDisplay(0.0)
        assertEquals("%.1f".format(0.0), display.score)
        assertEquals("Needs attention", display.band)
        assertEquals(ChipSkin.Amber, display.skin)
        assertEquals(0f, display.ringFraction!!, 0f)
    }

    @Test fun bandsAndSkinsKeepExistingThresholds() {
        assertEquals("Great" to ChipSkin.Green, wellbeingDisplay(4.5).let { it.band to it.skin })
        assertEquals("Good" to ChipSkin.Green, wellbeingDisplay(4.0).let { it.band to it.skin })
        assertEquals("Okay" to ChipSkin.Neutral, wellbeingDisplay(3.0).let { it.band to it.skin })
        assertEquals("Needs attention" to ChipSkin.Amber, wellbeingDisplay(2.9).let { it.band to it.skin })
    }

    @Test fun ringFractionIsScaledAndClamped() {
        assertEquals(0.5f, wellbeingDisplay(2.5).ringFraction!!, 0.001f)
        assertEquals(1f, wellbeingDisplay(6.0).ringFraction!!, 0f)
    }

    @Test fun missingInsightValuesHideTheCard() {
        listOf(null, Double.NaN).forEach { value ->
            assertNull(workloadInsight(value))
            assertNull(afterHoursInsight(value))
            assertNull(focusInsight(value))
        }
    }

    @Test fun workloadInsightFormatsValue() {
        val heavy = workloadInsight(3.9)!!
        assertTrue(heavy.amber)
        assertTrue(heavy.body.contains("%.1f".format(3.9)))
        assertFalse(heavy.body.contains("%"))
        assertFalse(workloadInsight(3.0)!!.amber)
    }

    @Test fun afterHoursInsightUsesHours() {
        val high = afterHoursInsight(90.0)!!
        assertTrue(high.amber)
        assertTrue(high.body.contains("%.1f".format(1.5)))
        assertFalse(high.body.contains("%"))
        val low = afterHoursInsight(30.0)!!
        assertFalse(low.amber)
        assertFalse(low.body.contains("%"))
    }

    @Test fun focusInsightThreshold() {
        assertFalse(focusInsight(6.5)!!.amber)
        assertTrue(focusInsight(6.4)!!.amber)
    }
}
