package com.duorecord.app

import android.Manifest
import android.content.pm.ServiceInfo
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class AudioCaptureTest {
    @Test fun audioNeedsOnlyMicrophoneWhilePhotosAndVideoUseTheirOwnResources() {
        val audio=RecordingConfig(audioOnly=true)
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO),CapturePermissions.runtime(audio))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,CapturePermissions.foregroundTypes(audio))
        val photos=audio.copy(audioOnly=false,photoMode=true)
        assertEquals(listOf(Manifest.permission.CAMERA),CapturePermissions.runtime(photos))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,CapturePermissions.foregroundTypes(photos))
        assertEquals(setOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO),CapturePermissions.runtime(RecordingConfig()).toSet())
    }
    @Test fun audioRecoveryPersistsCaptureTypeSegmentAndOriginalDeadlineAfterAnInterruption() {
        val context=RuntimeEnvironment.getApplication()
        val config=RecordingConfig(audioOnly=true,cameraMode=CameraMode.FRONT,segmentSeconds=30,maxRecordingMinutes=45)
        val store=CaptureRecovery(context)
        val end=System.currentTimeMillis()+600_000
        val before=store.begin(config,false,end)
        store.pause("Android blocked the microphone.")
        val after=CaptureRecovery(context).read()!!
        assertEquals(config,after.config);assertTrue(after.config.audioOnly)
        assertEquals(before.endWall,after.endWall);assertEquals(before.endElapsed,after.endElapsed)
        assertEquals("Android blocked the microphone.",after.reason)
        store.clear();assertNull(store.read())
    }
}
