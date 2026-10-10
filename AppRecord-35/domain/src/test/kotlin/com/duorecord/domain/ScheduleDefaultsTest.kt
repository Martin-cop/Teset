package com.duorecord.domain

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleDefaultsTest {
    @Test fun nextWholeHourIsAlwaysFutureAndHasNoFractionalSeconds() {
        assertEquals(LocalDateTime.parse("2026-10-07T12:00:00"),ScheduleDefaults.nextHour(LocalDateTime.parse("2026-10-07T11:51:35.123")))
        assertEquals(LocalDateTime.parse("2026-10-07T13:00:00"),ScheduleDefaults.nextHour(LocalDateTime.parse("2026-10-07T12:00:00")))
        assertEquals(LocalDateTime.parse("2026-10-08T00:00:00"),ScheduleDefaults.nextHour(LocalDateTime.parse("2026-10-07T23:59:59")))
    }
    @Test fun segmentUnitsProduceEquivalentDurationAndRoundToSeconds() {
        assertEquals(1800,RecordingConfig().segmentSeconds)
        assertEquals(1800,ScheduleDefaults.segmentSeconds("30",1))
        assertEquals(1800,ScheduleDefaults.segmentSeconds("0.5",2))
        assertEquals(73,ScheduleDefaults.segmentSeconds("73",0))
        assertEquals(73,ScheduleDefaults.segmentSeconds("0.020278",2))
    }
    @Test fun invalidDurationsAreRejected() {
        for(value in listOf("0","-1","NaN","999999999999999999")) {
            try { ScheduleDefaults.segmentSeconds(value,1);fail(value) } catch(_:IllegalArgumentException) {}
        }
    }
}
