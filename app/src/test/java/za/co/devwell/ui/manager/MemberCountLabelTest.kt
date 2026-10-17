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

    @Test fun knownZeroShowsNoMembers() {
        assertEquals("No members yet", memberCountLabel(0, known = true))
    }

    @Test fun knownPositiveIsShown() {
        assertEquals("1 member", memberCountLabel(1, known = true))
        assertEquals("3 members", memberCountLabel(3, known = true))
    }
}
