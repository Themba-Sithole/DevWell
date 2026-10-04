package za.co.devwell.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.TeamAggregate

class PrivacyPolicyTest {
    @Test fun `four responses remains hidden`() {
        assertFalse(PrivacyPolicy.canShowTeamInsights(4))
    }

    @Test fun `five responses becomes visible`() {
        assertTrue(PrivacyPolicy.canShowTeamInsights(5))
    }

    @Test fun `aggregate remains hidden below the threshold`() {
        val aggregate = TeamAggregate(
            teamId = "team-1",
            responseCount = 4,
            averageWellbeing = 3.7,
            averageWorkload = 3.9,
        )
        assertNull(PrivacyPolicy.safeAggregateOrNull(aggregate))
    }

    @Test fun `aggregate is available at the threshold`() {
        val aggregate = TeamAggregate(teamId = "team-1", responseCount = 5, averageWellbeing = 3.7)
        assertTrue(PrivacyPolicy.safeAggregateOrNull(aggregate) === aggregate)
    }
}
