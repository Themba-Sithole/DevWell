package za.co.devwell.ui.manager

import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.viewmodel.TeamSettingsRules

class TeamSettingsFormTest {
    @Test fun cadenceLabelsMatchAllowedValues() {
        assertEquals("Every day", cadenceLabel("Daily"))
        assertEquals("Once a week", cadenceLabel("Weekly"))
        // Every stored value has a friendly label, so buttons never show raw DB values.
        TeamSettingsRules.CADENCES.forEach { assertNotEquals(it, cadenceLabel(it)) }
    }

    @Test fun thresholdSnapsToHalfSteps() {
        assertEquals(1.0, snapThreshold(1.0f), 0.0)
        assertEquals(2.0, snapThreshold(2.2f), 0.0)
        assertEquals(2.5, snapThreshold(2.3f), 0.0)
        assertEquals(4.0, snapThreshold(4.0f), 0.0)
    }

    @Test fun thresholdIsClampedToRange() {
        assertEquals(1.0, snapThreshold(0.2f), 0.0)
        assertEquals(4.0, snapThreshold(9f), 0.0)
    }

    @Test fun nameLimitIsSixtyAfterTrimming() {
        assertFalse(teamNameTooLong("A".repeat(60), "Old"))
        assertFalse(teamNameTooLong("  " + "A".repeat(60) + "  ", "Old"))
        assertTrue(teamNameTooLong("A".repeat(61), "Old"))
    }

    @Test fun unchangedLongSavedNameIsAllowed() {
        val legacy = "A".repeat(61)
        assertFalse(teamNameTooLong(legacy, legacy))
    }

    @Test fun counterShowsTrimmedLength() {
        assertEquals("4/60", nameCounterLabel("  Team  "))
    }
}
