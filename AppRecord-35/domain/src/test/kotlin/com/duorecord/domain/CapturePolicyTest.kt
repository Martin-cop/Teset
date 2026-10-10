package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class CapturePolicyTest {
    private val front=CameraSource.FRONT
    private val rear=CameraSource.REAR
    @Test fun independentStopKeepsOtherCameraRunning() {
        assertEquals(setOf(rear),CapturePolicy.toggle(setOf(front,rear),CameraMode.FRONT))
        assertEquals(setOf(front),CapturePolicy.toggle(setOf(front,rear),CameraMode.REAR))
    }
    @Test fun bothAddsMissingCameraThenStopsBoth() {
        val both=CapturePolicy.toggle(setOf(front),CameraMode.BOTH)
        assertEquals(setOf(front,rear),both)
        assertTrue(CapturePolicy.toggle(both,CameraMode.BOTH).isEmpty())
    }
    @Test fun eachToggleWorksFromEveryRecordingCombination() {
        val states=listOf(emptySet(),setOf(front),setOf(rear),setOf(front,rear))
        for(active in states) {
            val frontResult=CapturePolicy.toggle(active,CameraMode.FRONT)
            assertEquals(rear in active,rear in frontResult)
            assertNotEquals(front in active,front in frontResult)
            val rearResult=CapturePolicy.toggle(active,CameraMode.REAR)
            assertEquals(front in active,front in rearResult)
            assertNotEquals(rear in active,rear in rearResult)
        }
    }
    @Test fun missingStoredQualityResolvesToAvailableSizeWithoutInventingProfile() {
        val options=listOf(CaptureQuality(240,320,128000),CaptureQuality(720,1280,2000000))
        assertEquals(options.first(),CapturePolicy.choose(options,"480x640"))
        assertEquals(options.last(),CapturePolicy.choose(options,"720x1280"))
    }
    @Test(expected=IllegalArgumentException::class) fun noCameraProfileIsAnError() { CapturePolicy.choose(emptyList(),"480x640") }
    @Test fun acceptsCustomNumericSettingsOutsideOldPresets() {
        CapturePolicy.validate(RecordingConfig(segmentSeconds=73,fps=24,quotaBytes=3L*1073741824,savedQuotaBytes=512L*1048576,reserveBytes=300L*1048576,stopBatteryPercent=17))
    }
    @Test fun rejectsInvalidNumericSettings() {
        val invalid=listOf(RecordingConfig(segmentSeconds=0),RecordingConfig(fps=0),RecordingConfig(fps=121),RecordingConfig(quotaBytes=0),RecordingConfig(savedQuotaBytes=Long.MAX_VALUE),RecordingConfig(reserveBytes=0),RecordingConfig(stopBatteryPercent=100))
        invalid.forEach { config ->
            try { CapturePolicy.validate(config);fail("Invalid settings accepted: $config") }
            catch(_:IllegalArgumentException) { }
        }
    }
}
