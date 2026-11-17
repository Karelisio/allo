package com.karelisio.allo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInfoTest {
    @Test fun frenchNumbers() {
        assertEquals("Mobile", NumberInfo.describe("06 12 34 56 78"))
        assertEquals("Mobile", NumberInfo.describe("+33712345678"))
        assertEquals("Fixe · Île-de-France", NumberInfo.describe("0145454545"))
        assertEquals("Fixe · Sud-Est", NumberInfo.describe("+33 4 78 00 00 00"))
        assertEquals("Fixe · box internet", NumberInfo.describe("0950000000"))
        assertEquals("Numéro gratuit", NumberInfo.describe("0800 123 456"))
        assertEquals("Numéro surtaxé", NumberInfo.describe("0899123456"))
    }

    @Test fun telemarketingWinsOverRegion() {
        assertEquals("Démarchage (préfixe officiel)", NumberInfo.describe("0162123456"))
        assertEquals("Démarchage (préfixe officiel)", NumberInfo.describe("+33948123456"))
    }

    @Test fun international() {
        assertEquals("International · Belgique", NumberInfo.describe("+32 2 123 45 67"))
        assertEquals("International · Suisse", NumberInfo.describe("0041 22 123 45 67"))
        assertEquals("International · Portugal", NumberInfo.describe("+351912345678"))
        assertEquals("International · Guadeloupe", NumberInfo.describe("+590690000000"))
    }

    @Test fun nothingUseful() {
        assertNull(NumberInfo.describe(null))
        assertNull(NumberInfo.describe("*#06#"))
        assertEquals("Numéro court", NumberInfo.describe("3644"))
    }
}
