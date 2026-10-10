package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class PhotoExposureWindowTest {
    @Test fun exposureWaitsForLightingAndThenANewFrame() {
        val w=PhotoExposureWindow();val token=w.begin()
        assertFalse(w.takeFreshFrame(500));w.ready(token,1000)
        assertFalse(w.takeFreshFrame(999));assertTrue(w.takeFreshFrame(1001))
        assertFalse(w.takeFreshFrame(1002))
    }
    @Test fun cameraReconfigurationCancelsPendingLightAndLateReadyCannotExposeTheNextPhoto() {
        val w=PhotoExposureWindow();val old=w.begin()
        assertTrue(w.cancel());val next=w.begin()
        w.ready(old,1000);assertFalse(w.takeFreshFrame(2000))
        w.ready(next,3000);assertFalse(w.takeFreshFrame(2999));assertTrue(w.takeFreshFrame(3001))
    }
    @Test fun stoppingCancelsExposuresEvenIfLightingAcknowledgementArrivesLate() {
        val w=PhotoExposureWindow();val token=w.begin();w.ready(token,1000)
        assertTrue(w.cancel());w.ready(token,1100);assertFalse(w.takeFreshFrame(2000));assertFalse(w.cancel())
    }
}
