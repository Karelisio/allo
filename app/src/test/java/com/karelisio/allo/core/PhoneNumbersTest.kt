package com.karelisio.allo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun normalizesFrenchNational() {
        assertEquals("+33612345678", PhoneNumbers.normalize("06 12 34 56 78"))
        assertEquals("+33612345678", PhoneNumbers.normalize("06.12.34.56.78"))
        assertEquals("+33162000000", PhoneNumbers.normalize("01-62-00-00-00"))
    }

    @Test fun normalizesInternationalForms() {
        assertEquals("+33612345678", PhoneNumbers.normalize("+33 6 12 34 56 78"))
        assertEquals("+33612345678", PhoneNumbers.normalize("0033612345678"))
        assertEquals("+33612345678", PhoneNumbers.normalize("+33 (0)6 12 34 56 78"))
        assertEquals("+442079460958", PhoneNumbers.normalize("+44 20 7946 0958"))
    }

    @Test fun keepsShortNumbersAndCodes() {
        assertEquals("15", PhoneNumbers.normalize("15"))
        assertEquals("3644", PhoneNumbers.normalize("36 44"))
        assertEquals("*#06#", PhoneNumbers.normalize("*#06#"))
    }

    @Test fun nationalForm() {
        assertEquals("0612345678", PhoneNumbers.toNational("+33612345678"))
        assertEquals("0612345678", PhoneNumbers.toNational("06 12 34 56 78"))
    }

    @Test fun formatsForDisplay() {
        assertEquals("06 12 34 56 78", PhoneNumbers.format("+33612345678"))
        assertEquals("06 12 34 56 78", PhoneNumbers.format("0612345678"))
    }

    @Test fun sameNumberAcrossFormats() {
        assertTrue(PhoneNumbers.same("+33 6 12 34 56 78", "0612345678"))
        assertTrue(PhoneNumbers.same("0033612345678", "06.12.34.56.78"))
        assertFalse(PhoneNumbers.same("0612345678", "0612345679"))
        assertFalse(PhoneNumbers.same("", "0612345678"))
    }

    @Test fun prefixMatchingNationalAndInternational() {
        assertTrue(PhoneNumbers.matchesPrefix("+33162123456", "0162"))
        assertTrue(PhoneNumbers.matchesPrefix("0162123456", "+33162"))
        assertTrue(PhoneNumbers.matchesPrefix("09 48 12 34 56", "0948"))
        assertFalse(PhoneNumbers.matchesPrefix("0612345678", "0162"))
    }

    @Test fun hiddenNumbers() {
        assertTrue(PhoneNumbers.isHidden(null))
        assertTrue(PhoneNumbers.isHidden(""))
        assertTrue(PhoneNumbers.isHidden("-2"))
        assertFalse(PhoneNumbers.isHidden("0612345678"))
    }

    @Test fun specialCodes() {
        assertTrue(PhoneNumbers.isSpecialCode("*#06#"))
        assertEquals("4636", PhoneNumbers.secretCode("*#*#4636#*#*"))
        assertNull(PhoneNumbers.secretCode("*#06#"))
        assertFalse(PhoneNumbers.isSpecialCode("0612345678"))
    }
}
