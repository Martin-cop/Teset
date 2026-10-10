package com.duorecord.app

import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CaptureRecoveryTest {
    @Test fun explicitTenHourScheduleUsesItsOwnEndInsteadOfTheManualTwoHourLimit() {
        val now=System.currentTimeMillis();val end=now+600*60000L
        val saved=CaptureRecovery(RuntimeEnvironment.getApplication()).begin(RecordingConfig(maxRecordingMinutes=120),false,end)
        assertEquals(end,saved.endWall)
        assertEquals(120,saved.config.maxRecordingMinutes)
    }
    @Test fun snapshotSurvivesNewStorePreservesEverySettingAndScheduledEnd() {
        val context=RuntimeEnvironment.getApplication()
        val config=RecordingConfig(segmentSeconds=73,quotaBytes=5_000_000_000,reserveBytes=123_000_000,fps=24,preferHevc=false,
            stopBatteryPercent=20,width=720,height=1280,cameraMode=CameraMode.FRONT,savedQuotaBytes=4_000_000_000,
            frontQuality="720x1280",rearQuality="240x320",videoBitRate=2_000_000,simpleTheme=true,maxRecordingMinutes=93,photoMode=true,photoIntervalSeconds=7,continueVideoWithoutMic=false)
        val end=System.currentTimeMillis()+60000
        val saved=CaptureRecovery(context).begin(config,true,end)
        val store=CaptureRecovery(context);assertEquals(saved,store.read());assertEquals(end,saved.endWall)
        store.pause("Camera busy")
        assertEquals(saved.config,store.read()!!.config);assertEquals(saved.endElapsed,store.read()!!.endElapsed)
        store.update(config.copy(cameraMode=CameraMode.BOTH));assertEquals(saved.endWall,store.read()!!.endWall)
        store.clear();assertNull(CaptureRecovery(context).read())
    }
    @Test fun monotonicDeadlineIgnoresWallClockEditsAndBootFallbackDoesNotAddTime() {
        val saved=SavedCapture(RecordingConfig(),1000,10000,8000,4,false)
        assertEquals(3000,saved.remaining(900000,5000,4))
        assertEquals(4000,saved.remaining(6000,100,5))
        assertEquals(0,saved.remaining(11000,100,5))
        assertEquals(0,saved.remaining(0,9000,4))
    }
}
