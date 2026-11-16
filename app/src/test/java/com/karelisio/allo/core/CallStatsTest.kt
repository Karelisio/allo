package com.karelisio.allo.core

import com.karelisio.allo.ui.settings.CallStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CallStatsTest {
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 11, d, h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun computesTotalsAndTop() {
        val e = listOf(
            CallEntry(1, "0612345678", "Camille", CallKind.INCOMING, at(16, 10), 120),
            CallEntry(2, "+33612345678", "Camille", CallKind.OUTGOING, at(16, 9), 60),
            CallEntry(3, "0700000000", null, CallKind.MISSED, at(15, 9), 0),
            CallEntry(4, "0162123456", null, CallKind.BLOCKED, at(15, 8), 0),
            CallEntry(5, "0145454545", "Alex", CallKind.OUTGOING, at(1, 8), 30),
        )
        val s = CallStats.from(e, sinceMillis = at(10, 0))
        assertEquals(3, s.count) // le bloqué et l'appel trop ancien sont exclus
        assertEquals(180, s.talkSec)
        assertEquals(1, s.incoming); assertEquals(1, s.outgoing); assertEquals(1, s.missed)
        assertEquals("Camille", s.top.first().first)
        assertEquals(180L, s.top.first().third)
        assertEquals(3, s.byWeekday.sum())
    }
}
