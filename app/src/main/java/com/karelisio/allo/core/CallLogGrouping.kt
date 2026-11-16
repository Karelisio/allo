package com.karelisio.allo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL }

enum class CallFilter { ALL, MISSED, INCOMING, OUTGOING, BLOCKED;
    fun accepts(k: CallKind) = when (this) {
        ALL -> true
        MISSED -> k == CallKind.MISSED || k == CallKind.REJECTED
        INCOMING -> k == CallKind.INCOMING
        OUTGOING -> k == CallKind.OUTGOING
        BLOCKED -> k == CallKind.BLOCKED
    }
}

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val kind: CallKind,
    val date: Long,
    val durationSec: Long,
    val simSlot: Int? = null,
    val photoUri: String? = null,
    val lookupUri: String? = null,
    val isNew: Boolean = false,
) {
    val key: String get() = if (PhoneNumbers.isHidden(number)) "hidden" else PhoneNumbers.key(number)
}

/** Appels consécutifs du même numéro, le même jour. */
data class CallGroup(val entries: List<CallEntry>) {
    val first: CallEntry get() = entries.first()
    val count: Int get() = entries.size
    val totalDuration: Long get() = entries.sumOf { it.durationSec }
    val id: Long get() = first.id
    val kinds: List<CallKind> get() = entries.map { it.kind }
}

data class CallSection(val day: LocalDate, val label: String, val groups: List<CallGroup>)

object CallLogGrouping {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val dayYearFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> (if (day.year == today.year) dayFmt else dayYearFmt).format(day)
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }

    /** `entries` est trié du plus récent au plus ancien. */
    fun group(
        entries: List<CallEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
        filter: CallFilter = CallFilter.ALL,
    ): List<CallSection> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val sections = mutableListOf<CallSection>()
        var curDay: LocalDate? = null
        var groups = mutableListOf<CallGroup>()
        var cur = mutableListOf<CallEntry>()

        fun flushGroup() { if (cur.isNotEmpty()) { groups += CallGroup(cur); cur = mutableListOf() } }
        fun flushDay() {
            flushGroup()
            val d = curDay
            if (d != null && groups.isNotEmpty()) sections += CallSection(d, dayLabel(d, today), groups)
            groups = mutableListOf()
        }

        for (e in entries) {
            if (!filter.accepts(e.kind)) continue
            val day = Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()
            if (day != curDay) { flushDay(); curDay = day }
            val last = cur.lastOrNull()
            if (last != null && last.key != e.key) flushGroup()
            cur += e
        }
        flushDay()
        return sections
    }

    fun formatDuration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "%d h %02d min".format(h, m)
            m > 0 -> "%d min %02d s".format(m, s)
            else -> "%d s".format(s)
        }
    }

    fun formatClock(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
