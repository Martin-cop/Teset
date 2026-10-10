package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class MixedSetupTest {
    @Test fun defaultPresentationStartsTopLeftAndReadingSavedLayoutPreservesUserPlacement() {
        val cfg=RecordingConfig(mixedMode=true)
        assertEquals(CameraSource.SCREEN,cfg.mixedBackground)
        assertEquals(setOf(CameraSource.FRONT,CameraSource.SCREEN),cfg.mixedSources)
        assertEquals(0f,MixedLayout.read(cfg.mixedLayout).getValue(CameraSource.FRONT).x,0f)
        assertEquals(0f,MixedLayout.read(cfg.mixedLayout).getValue(CameraSource.FRONT).y,0f)
        val changed=MixedLayout.read("").toMutableMap();changed[CameraSource.FRONT]=changed.getValue(CameraSource.FRONT).copy(x=.45f,y=.50f)
        assertEquals(.45f,MixedLayout.read(MixedLayout.write(changed)).getValue(CameraSource.FRONT).x,0f)
    }
    @Test fun choosingScreenBackgroundEnablesItEvenWhenItWasNotPreviouslySelected() {
        val before=RecordingConfig(mixedMode=true,mixedScreen=false,mixedRear=true,mixedBackground=CameraSource.REAR)
        val after=MixedSetup.background(before,CameraSource.SCREEN)
        assertTrue(after.mixedScreen);assertEquals(CameraSource.SCREEN,after.mixedBackground)
        assertTrue(after.mixedFront && after.mixedRear);CapturePolicy.validate(after)
        for(source in MixedSetup.sources) { val next=MixedSetup.background(before,source);assertTrue(source in next.mixedSources);assertEquals(source,next.mixedBackground);CapturePolicy.validate(next) }
    }
    @Test fun sourceRemovalNeverLeavesAnAbsentBackgroundOrAnEmptyCapture() {
        val before=MixedSetup.presentation(RecordingConfig(),true)
        val withoutScreen=MixedSetup.toggle(before,CameraSource.SCREEN)
        assertFalse(withoutScreen.mixedScreen);assertTrue(withoutScreen.mixedBackground in withoutScreen.mixedSources)
        val onlyScreen=RecordingConfig(mixedMode=true,mixedFront=false,mixedScreen=true)
        assertThrows(IllegalArgumentException::class.java) { MixedSetup.toggle(onlyScreen,CameraSource.SCREEN) }
    }
    @Test fun quickPresentationRestoresVisibleCameraInsetsButKeepsUserSoundAndDuration() {
        val frames=MixedLayout.read("").mapValues { it.value.copy(visible=false) }
        val before=RecordingConfig(screenAudio=true,screenInternalAudio=true,maxRecordingMinutes=45,mixedLayout=MixedLayout.write(frames))
        val after=MixedSetup.presentation(before)
        assertEquals(setOf(CameraSource.FRONT,CameraSource.SCREEN),after.mixedSources);assertEquals(CameraSource.SCREEN,after.mixedBackground)
        assertTrue(after.screenAudio && after.screenInternalAudio);assertEquals(45,after.maxRecordingMinutes)
        assertTrue(MixedLayout.read(after.mixedLayout).getValue(CameraSource.FRONT).visible)
    }
}
