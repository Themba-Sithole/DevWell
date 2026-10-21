package za.co.devwell.ui.manager

import org.junit.Assert.*
import org.junit.Test

class AlertThresholdTest {
    @Test fun averageBelowThresholdShowsNote() {
        assertTrue(belowAlertThreshold(1.9, 2.0))
    }

    @Test fun averageEqualToThresholdDoesNotShowNote() {
        assertFalse(belowAlertThreshold(2.0, 2.0))
    }

    @Test fun averageAboveThresholdDoesNotShowNote() {
        assertFalse(belowAlertThreshold(3.4, 2.0))
    }

    @Test fun missingAverageOrThresholdDoesNotShowNote() {
        assertFalse(belowAlertThreshold(null, 2.0))
        assertFalse(belowAlertThreshold(1.5, null))
        assertFalse(belowAlertThreshold(null, null))
    }

    @Test fun nonFiniteValuesDoNotShowNote() {
        assertFalse(belowAlertThreshold(Double.NaN, 2.0))
        assertFalse(belowAlertThreshold(1.5, Double.NaN))
        assertFalse(belowAlertThreshold(Double.NEGATIVE_INFINITY, 2.0))
        assertFalse(belowAlertThreshold(1.5, Double.POSITIVE_INFINITY))
    }
}
