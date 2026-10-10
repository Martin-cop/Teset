package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class RecordingDurationTest {
    @Test fun defaultIsTwoHoursAndUserDurationIsValidated() {
        assertEquals(120,RecordingConfig().maxRecordingMinutes)
        CapturePolicy.validate(RecordingConfig(maxRecordingMinutes=1))
        CapturePolicy.validate(RecordingConfig(maxRecordingMinutes=1440))
        CapturePolicy.validate(RecordingConfig(maxRecordingMinutes=0))
        for(minutes in listOf(-1,1441))assertThrows(IllegalArgumentException::class.java) { CapturePolicy.validate(RecordingConfig(maxRecordingMinutes=minutes)) }
    }
}
