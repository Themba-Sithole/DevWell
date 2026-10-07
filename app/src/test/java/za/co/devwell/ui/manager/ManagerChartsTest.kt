package za.co.devwell.ui.manager

import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate

class ManagerChartsTest {
    private val full = TeamAggregate(
        teamId = "t1", responseCount = PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES,
        averageWellbeing = 3.7, averageWorkload = 3.9, averageEnergy = 6.4,
        averageFocus = 6.8, averageAfterHoursMinutes = 45.0,
    )

    @Test fun belowThresholdReturnsNoBars() {
        assertNull(teamChartBars(full.copy(responseCount = 4)))
        assertNull(teamChartBars(full.copy(responseCount = 0)))
    }

    @Test fun atThresholdReturnsOnlyWorkloadEnergyFocus() {
        val bars = teamChartBars(full)!!
        assertEquals(listOf("Workload", "Energy", "Focus"), bars.map { it.label })
        assertEquals(listOf(3.9, 6.4, 6.8), bars.map { it.value })
        assertEquals(listOf(5, 10, 10), bars.map { it.scaleMax })
    }

    @Test fun missingAndNonFiniteAveragesAreSkipped() {
        val bars = teamChartBars(full.copy(averageEnergy = null, averageFocus = Double.NaN))!!
        assertEquals(listOf("Workload"), bars.map { it.label })
    }

    @Test fun fractionIsScaledAndClamped() {
        assertEquals(0.78f, TeamChartBar("Workload", 3.9, 5).fraction, 0.001f)
        assertEquals(1f, TeamChartBar("Energy", 12.0, 10).fraction, 0f)
        assertEquals(0f, TeamChartBar("Focus", -1.0, 10).fraction, 0f)
    }
}
