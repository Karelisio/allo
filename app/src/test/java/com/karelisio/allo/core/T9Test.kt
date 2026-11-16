package com.karelisio.allo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    @Test fun lettersToDigits() {
        assertEquals("5326", T9.toDigits("Jean"))
        assertEquals("3532", T9.toDigits("Éléa"))
    }

    @Test fun matchesStartOfFirstWord() {
        val m = T9.matchName("Jean Dupont", "532")
        assertNotNull(m)
        assertEquals(listOf(0..2), m!!.ranges)
    }

    @Test fun matchesStartOfOtherWord() {
        val m = T9.matchName("Jean Dupont", "3876")
        assertNotNull(m)
        assertEquals(listOf(5..8), m!!.ranges)
    }

    @Test fun matchesAcrossWords() {
        // « jeand » → 53263, la plage saute l'espace.
        val m = T9.matchName("Jean Dupont", "53263")
        assertNotNull(m)
        assertEquals(listOf(0..3, 5..5), m!!.ranges)
    }

    @Test fun matchesInitials() {
        // « MD » = 63 (alors que « Ma » = 62).
        val m = T9.matchName("Marie Dupont", "63")
        assertNotNull(m)
        assertEquals(listOf(0..0, 6..6), m!!.ranges)
    }

    @Test fun ignoresAccents() {
        assertNotNull(T9.matchName("Hélène", "4353"))
    }

    @Test fun noMatchInMiddleOfWord() {
        assertNull(T9.matchName("Jean", "26"))
    }

    @Test fun firstWordScoresHigher() {
        val a = T9.matchName("Marie Paul", "627")!!
        val b = T9.matchName("Paul Marie", "627")!!
        assertTrue(a.score > b.score)
    }

    @Test fun matchesNumberWithSpaces() {
        val m = T9.matchNumber("06 12 34 56 78", "1234")
        assertNotNull(m)
        // « 12 34 » : positions 3-4 et 6-7.
        assertEquals(listOf(3..4, 6..7), m!!.ranges)
    }

    @Test fun matchesNumberNationalVsInternational() {
        assertNotNull(T9.matchNumber("+33 612 345 678", "0612"))
        assertNotNull(T9.matchNumber("06 12 34 56 78", "33612"))
    }
}
