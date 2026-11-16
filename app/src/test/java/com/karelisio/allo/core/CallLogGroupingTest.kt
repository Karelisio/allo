package com.karelisio.allo.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CallLogGroupingTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.of(2026, 11, 16, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private var id = 0L
    private fun e(n: String, k: CallKind, t: Long, d: Long = 30) = CallEntry(++id, n, null, k, t, d)

    @Test fun groupsConsecutiveSameNumberSameDay() {
        val entries = listOf(
            e("0612345678", CallKind.MISSED, at(16, 17)),
            e("+33612345678", CallKind.MISSED, at(16, 16)),
            e("0711111111", CallKind.OUTGOING, at(16, 15)),
            e("0612345678", CallKind.INCOMING, at(16, 14)),
        )
        val s = CallLogGrouping.group(entries, zone, now)
        assertEquals(1, s.size)
        assertEquals("Aujourd'hui", s[0].label)
        assertEquals(listOf(2, 1, 1), s[0].groups.map { it.count })
    }

    @Test fun splitsByDayWithLabels() {
        val entries = listOf(
            e("0612345678", CallKind.INCOMING, at(16, 9)),
            e("0612345678", CallKind.INCOMING, at(15, 20)),
            e("0612345678", CallKind.OUTGOING, at(12, 10)),
        )
        val s = CallLogGrouping.group(entries, zone, now)
        assertEquals(listOf("Aujourd'hui", "Hier", "Jeudi 12 novembre"), s.map { it.label })
    }

    @Test fun filterKeepsOnlyMatchingKinds() {
        val entries = listOf(
            e("0612345678", CallKind.MISSED, at(16, 10)),
            e("0612345678", CallKind.OUTGOING, at(16, 9)),
            e("0612345678", CallKind.MISSED, at(16, 8)),
        )
        val s = CallLogGrouping.group(entries, zone, now, CallFilter.MISSED)
        assertEquals(1, s[0].groups.size)
        assertEquals(2, s[0].groups[0].count)
    }

    @Test fun totalDurationAndFormat() {
        val g = CallGroup(listOf(e("1", CallKind.INCOMING, at(16, 1), 65), e("1", CallKind.INCOMING, at(16, 2), 70)))
        assertEquals(135, g.totalDuration)
        assertEquals("2 min 15 s", CallLogGrouping.formatDuration(135))
        assertEquals("1 h 01 min", CallLogGrouping.formatDuration(3660))
        assertEquals("02:15", CallLogGrouping.formatClock(135))
        assertEquals("1:01:00", CallLogGrouping.formatClock(3660))
    }

    @Test fun hiddenNumbersGroupTogether() {
        val entries = listOf(e("", CallKind.MISSED, at(16, 10)), e("", CallKind.MISSED, at(16, 9)))
        assertEquals(1, CallLogGrouping.group(entries, zone, now)[0].groups.size)
    }

    @Test fun showsYearForOlderYears() {
        val old = LocalDateTime.of(2025, 3, 2, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val s = CallLogGrouping.group(listOf(e("1", CallKind.INCOMING, old)), zone, now)
        assertEquals("Dimanche 2 mars 2025", s[0].label)
    }
}
