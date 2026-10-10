package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackRatesTest {
    @Test fun rulerHasPauseSlowMotionAndEveryIntegerThroughThirty() {
        assertEquals(0f,PlaybackRates.values.first());assertEquals(30f,PlaybackRates.values.last())
        assertTrue(PlaybackRates.values.containsAll(listOf(.25f,.5f,.75f)))
        (1..30).forEach { assertTrue(PlaybackRates.values.contains(it.toFloat())) }
        assertEquals(PlaybackRates.values.distinct().sorted(),PlaybackRates.values)
    }
    @Test fun rejectsNonFiniteNegativeAndOverThirtyRates() {
        listOf(-1f,31f,Float.NaN,Float.POSITIVE_INFINITY).forEach {
            try { PlaybackRates.validate(it);fail("Invalid rate was accepted") } catch(_:IllegalArgumentException) {}
        }
        PlaybackRates.validate(0f);PlaybackRates.validate(30f)
    }
}
