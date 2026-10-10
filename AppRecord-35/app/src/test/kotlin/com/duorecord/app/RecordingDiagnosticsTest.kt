package com.duorecord.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RecordingDiagnosticsTest {
    @Test fun unexpectedStopPersistsWithContextAndUserStopDoesNotCreateEntry() {
        val context=RuntimeEnvironment.getApplication();val log=RecordingDiagnostics(context)
        log.visibility(false);log.begin("Rear at 480x640");log.error("camera disconnected");log.finish("Camera stopped")
        val saved=RecordingDiagnostics(context).entries().single()
        assertTrue(saved.contains("camera disconnected"));assertTrue(saved.contains("Rear at 480x640"));assertTrue(saved.contains("visible: false"))
        log.begin("Front");log.userStop();log.finish("Stopped")
        assertEquals(1,log.entries().size)
    }
    @Test fun interruptedSessionRecoversOnceWithExplicitUnknownReason() {
        val log=RecordingDiagnostics(RuntimeEnvironment.getApplication())
        log.begin("Both");log.recover()
        assertTrue(log.entries().single().contains("exact process-exit reason unavailable"))
        log.recover();assertEquals(1,log.entries().size)
    }
    @Test fun crashRetainsStackAndBoundedJournalKeepsLatest200() {
        val log=RecordingDiagnostics(RuntimeEnvironment.getApplication())
        log.crash(IllegalStateException("test failure"))
        assertTrue(log.entries().single().contains("RecordingDiagnosticsTest"))
        for(i in 1..205)log.add("event $i")
        assertEquals(200,log.entries().size);assertTrue(log.entries().first().contains("event 205"))
    }
}
