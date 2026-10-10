package com.duorecord.app

import android.Manifest
import android.content.pm.ServiceInfo
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MixedCaptureSettingsTest {
    @Test fun separateSourcesAudioAndBackgroundSurviveStorageAndRecovery() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val c=RecordingConfig(mixedMode=true,mixedRear=true,mixedFront=true,mixedScreen=true,mixedBackground=CameraSource.REAR,screenAudio=true,screenInternalAudio=true,mixedLayout=MixedLayout.write(MixedLayout.read("")),archiveRefreshSeconds=9,archiveInitialLimit=80)
        app.repository!!.saveConfig(c)
        assertEquals(c,app.repository!!.config())
        val recovery=CaptureRecovery(app);recovery.begin(c,false,0)
        val restored=recovery.read()!!.config
        assertEquals(c.mixedSources,restored.mixedSources);assertEquals(CameraSource.REAR,restored.mixedBackground)
        assertTrue(restored.screenAudio && restored.screenInternalAudio);assertEquals(c.mixedLayout,restored.mixedLayout)
        assertEquals(setOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO),CapturePermissions.runtime(c).toSet())
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,CapturePermissions.foregroundTypes(c))
    }
    @Test fun unlimitedCaptureDoesNotExpireOrOverflowAcrossRebootAndDoesNotResetFiniteSchedule() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;val recovery=CaptureRecovery(app)
        val unlimited=recovery.begin(RecordingConfig(maxRecordingMinutes=0),false,0)
        assertEquals(Long.MAX_VALUE,unlimited.endWall);assertEquals(Long.MAX_VALUE,recovery.remaining(unlimited))
        assertEquals(Long.MAX_VALUE,unlimited.remaining(Long.MAX_VALUE-1,Long.MAX_VALUE-1,unlimited.boot+1))
        val end=System.currentTimeMillis()+600000
        val scheduled=recovery.begin(RecordingConfig(maxRecordingMinutes=0),false,end)
        assertEquals(end,scheduled.endWall);assertTrue(recovery.remaining(scheduled) in 590000..600000)
    }
}
