package za.co.devwell.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.model.PrivacyPolicy

class PrivacyPolicyTest {
    @Test fun `four responses remains hidden`() {
        assertFalse(PrivacyPolicy.canShowTeamInsights(4))
    }

    @Test fun `five responses becomes visible`() {
        assertTrue(PrivacyPolicy.canShowTeamInsights(5))
    }
}
