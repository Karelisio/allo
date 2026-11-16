package com.karelisio.allo.ui

import com.karelisio.allo.ui.log.timeLabel
import org.junit.Assert.assertTrue
import org.junit.Test

/** Régression : timeLabel ne doit dépendre d'aucun Context (crash 0.1.0). */
class TimeLabelTest {
    @Test fun formatsHourWithoutContext() {
        assertTrue(Regex("\\d{2}:\\d{2}").matches(timeLabel(1_763_300_000_000L)))
    }
}
