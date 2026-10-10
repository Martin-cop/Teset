package com.duorecord.app

import android.content.pm.ServiceInfo
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class ScreenModeTest {
    @Test fun screenReservesAudioPermissionForLiveSourceSwitchingWithoutCameraPermission() {
        val cfg=RecordingConfig(screenMode=true)
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO),CapturePermissions.runtime(cfg))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,CapturePermissions.foregroundTypes(cfg))
        CapturePolicy.validate(cfg)
        assertTrue(runCatching { CapturePolicy.validate(cfg.copy(audioOnly=true)) }.isFailure)
        assertTrue(runCatching { CapturePolicy.validate(cfg.copy(photoMode=true)) }.isFailure)
    }
    @Test fun ambientScreenAudioRequestsOnlyMicAndProjectionAndPersists() {
        val cfg=RecordingConfig(screenMode=true,screenAudio=true)
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO),CapturePermissions.runtime(cfg))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,CapturePermissions.foregroundTypes(cfg))
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(cfg);assertTrue(app.repository!!.config().screenAudio)
        val recovery=CaptureRecovery(app);recovery.begin(cfg,false,0);assertTrue(recovery.read()!!.config.screenAudio)
    }
    @Test fun screenChoicePersistsAndNamesRoundTripWithoutMixingWithCameras() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val repo=app.repository!!
        repo.saveConfig(repo.config().copy(screenMode=true,audioOnly=false,photoMode=false))
        assertTrue(repo.config().screenMode)
        val first=VideoNames.unique(1791370000000,CameraSource.SCREEN,emptySet())
        val second=VideoNames.unique(1791370000000,CameraSource.SCREEN,setOf(first))
        assertTrue(first.endsWith("_S.mp4"));assertTrue(second.endsWith("_S_1.mp4"))
        assertEquals(CameraSource.SCREEN,VideoNames.camera(first));assertEquals(CameraSource.SCREEN,VideoNames.camera(second))
        assertEquals(VideoNames.time(first),VideoNames.time(second))
        repo.saveConfig(repo.config().copy(screenMode=false))
        assertFalse(repo.config().screenMode)
    }
    @Test fun screenRecoveryDoesNotReuseConsentOrRestartService() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val store=CaptureRecovery(app)
        store.begin(RecordingConfig(screenMode=true),false,0)
        assertTrue(store.read()!!.config.screenMode)
        RecordingService.resumeIfPending(app)
        assertNull(store.read())
        assertNull(org.robolectric.Shadows.shadowOf(app).nextStartedService)
    }
    @Test fun cancelledScreenConsentDoesNotStartCapture() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(app.repository!!.config().copy(screenMode=true))
        val c=org.robolectric.Robolectric.buildActivity(com.duorecord.app.ui.MainActivity::class.java).setup()
        com.duorecord.app.ui.MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,android.content.Intent::class.java).apply { isAccessible=true }.invoke(c.get(),903,android.app.Activity.RESULT_CANCELED,null)
        assertEquals(Phase.IDLE,app.status.value.phase)
        assertNull(org.robolectric.Shadows.shadowOf(app).nextStartedService)
        c.pause().stop().destroy()
    }
    @Test fun formulaUsesSignedNetBatteryPowerAndUnknownIsNotZero() {
        val h=DeviceHealth(50,35.0,true,"Good",0,10000000,false,false,batteryCurrentMa=1200.0,batteryVoltageV=3.8)
        assertEquals("3.80 V × 1.20 A = 4.56 W",h.powerFormula())
        assertEquals("3.80 V × -1.20 A = -4.56 W",h.copy(batteryCurrentMa=-1200.0).powerFormula())
        assertTrue(h.copy(batteryCurrentMa=null).powerFormula().contains("—"))
    }
}
