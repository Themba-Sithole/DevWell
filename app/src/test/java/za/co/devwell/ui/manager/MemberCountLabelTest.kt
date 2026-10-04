package za.co.devwell.ui.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberCountLabelTest {
    @Test fun zeroIsShownAsUnknown() {
        assertEquals("Member count not available yet", memberCountLabel(0))
    }

    @Test fun negativeIsShownAsUnknown() {
        assertEquals("Member count not available yet", memberCountLabel(-3))
    }

    @Test fun oneUsesSingular() {
        assertEquals("1 member", memberCountLabel(1))
    }

    @Test fun positiveCountIsShown() {
        assertEquals("8 members", memberCountLabel(8))
    }
}
