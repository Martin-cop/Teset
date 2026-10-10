package com.duorecord.app

import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import com.duorecord.app.ui.MainActivity
import com.duorecord.contracts.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class BackgroundRecordingTest {
    private class Engine:CaptureEngine {
        val lightRequests=mutableListOf<Boolean>()
        var starts=0;val configs=mutableListOf<RecordingConfig>()
        var started=false;var stopReason:String?=null;lateinit var listener:CaptureListener
        override fun support(config:RecordingConfig)=CaptureSupport(true,"test")
        override fun qualities(source:CameraSource,config:RecordingConfig)=listOf(CaptureQuality(480,640,1_000_000))
        override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {
            this.listener=listener;started=true;starts++;configs+=config;listener.onTorchState(TorchStatus(available=true));listener.onCameraState(if(config.audioOnly)CameraSource.AUDIO else CameraSource.REAR,CameraCaptureStatus(recording=true,startedMs=1000))
        }
        override fun stop(reason:String) { stopReason=reason;listener.onStopped(reason) }
        override fun setTorch(enabled:Boolean) { lightRequests+=enabled;listener.onTorchState(TorchStatus(enabled,true)) }
        override fun setPaused(paused:Boolean) { listener.onPauseChanged(paused) }
        override fun split()=Unit
        override fun update(config:RecordingConfig,sources:Set<CameraSource>)=Unit
        override fun setPreview(source:CameraSource,surface:android.view.Surface?)=Unit
    }
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000L
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue("Condition timed out",condition())
    }
    private fun setup(mode:CameraMode=CameraMode.REAR,storageFailure:Boolean=true,audioOnly:Boolean=false):Pair<Engine,org.robolectric.android.controller.ServiceController<RecordingService>> {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        shadowOf(app.getSystemService(BatteryManager::class.java)).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,80)
        shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        if(audioOnly)shadowOf(app).denyPermissions(android.Manifest.permission.CAMERA) else shadowOf(app).grantPermissions(android.Manifest.permission.CAMERA)
        var retentionCalls=0
        val repo=Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,method,_ ->
            when(method.name) {
                "config" -> RecordingConfig(cameraMode=mode,audioOnly=audioOnly)
                "list" -> emptyList<Clip>()
                "folderReady" -> true
                "storagePath" -> "Movies/Record"
                "freeBytes" -> 10L*1024*1024*1024
                "enforceRetention" -> { if(++retentionCalls>1 && storageFailure)throw IllegalStateException("storage provider unavailable");Unit }
                else -> Unit
            }
        } as MediaRepository
        val engine=Engine();val factory=object:CaptureFactory { override fun create(context:android.content.Context)=engine }
        fun inject(name:String,value:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { value }) }
        inject("repository",repo);inject("capture",factory)
        val service=Robolectric.buildService(RecordingService::class.java).create()
        service.get().onStartCommand(Intent(app,RecordingService::class.java).setAction(RecordingService.START),0,1)
        waitUntil { engine.started }
        return engine to service
    }
    @Test fun userPauseWaitsForResumeAndPreservesOriginalDeadline() {
        val (engine,service)=setup(storageFailure=false)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        waitUntil { app.status.value.phase==Phase.RECORDING }
        val end=CaptureRecovery(app).read()!!.endWall
        service.get().onStartCommand(Intent().setAction(RecordingService.PAUSE),0,2)
        waitUntil { app.status.value.phase==Phase.USER_PAUSED }
        assertTrue(CaptureRecovery(app).userPaused)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(1,engine.starts);assertEquals(Phase.USER_PAUSED,app.status.value.phase)
        assertEquals(end,CaptureRecovery(app).read()!!.endWall)
        engine.listener.onStopped("Resource lost during user pause")
        waitUntil { app.activeEngine==null && app.status.value.phase==Phase.USER_PAUSED }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12));assertEquals(1,engine.starts)
        RecordingService.resumeIfPending(app);assertEquals(Phase.USER_PAUSED,app.status.value.phase)
        service.get().onStartCommand(Intent().setAction(RecordingService.RESUME),0,3)
        waitUntil { engine.starts==2 && app.status.value.phase==Phase.RECORDING }
        assertFalse(CaptureRecovery(app).userPaused);assertEquals(end,CaptureRecovery(app).read()!!.endWall)
        service.get().onStartCommand(Intent().setAction(RecordingService.PAUSE),0,4);waitUntil { app.status.value.phase==Phase.USER_PAUSED }
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,5);waitUntil { app.status.value.phase==Phase.IDLE }
        assertNull(CaptureRecovery(app).read());assertFalse(CaptureRecovery(app).userPaused);service.destroy()
    }
    @Test fun manualPauseCannotExtendTheDeadline() {
        val (engine,service)=setup(storageFailure=false);val app=RuntimeEnvironment.getApplication() as DuoApp
        waitUntil { app.status.value.phase==Phase.RECORDING }
        service.get().onStartCommand(Intent().setAction(RecordingService.PAUSE),0,2);waitUntil { app.status.value.phase==Phase.USER_PAUSED }
        val recovery=CaptureRecovery(app);val saved=recovery.read()!!
        recovery.save(saved.copy(endElapsed=android.os.SystemClock.elapsedRealtime()-1,endWall=System.currentTimeMillis()-1))
        service.get().onStartCommand(Intent().setAction(RecordingService.RESUME),0,3)
        waitUntil { app.status.value.phase==Phase.IDLE };assertEquals(1,engine.starts);assertNull(recovery.read());service.destroy()
    }
    @Test fun cameraInterruptionRetriesSavedConfigAndDeadlineWithoutAcceptingOldCallbacks() {
        val (engine,service)=setup(CameraMode.BOTH,false)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val before=CaptureRecovery(app).read()!!;val oldListener=engine.listener
        oldListener.onError("Camera in use by another app");oldListener.onStopped("Camera in use by another app")
        waitUntil { app.status.value.phase==Phase.PAUSED }
        assertEquals(before.endElapsed,CaptureRecovery(app).read()!!.endElapsed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        waitUntil { engine.starts==2 && app.status.value.phase==Phase.RECORDING }
        assertEquals(before.config,engine.configs.last().copy(photoEpochMs=0))
        assertEquals(before.endWall,CaptureRecovery(app).read()!!.endWall)
        oldListener.onStopped("Late callback from the old camera")
        shadowOf(Looper.getMainLooper()).idle();assertEquals(Phase.RECORDING,app.status.value.phase)
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2);waitUntil { app.status.value.phase==Phase.IDLE };service.destroy()
    }
    @Test fun manualStopCancelsPendingRetryAndDoesNotRestart() {
        val (engine,service)=setup(storageFailure=false);val app=RuntimeEnvironment.getApplication() as DuoApp
        engine.listener.onStopped("Microphone occupied");waitUntil { app.status.value.phase==Phase.PAUSED }
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE };assertNull(CaptureRecovery(app).read())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12));assertEquals(1,engine.starts);service.destroy()
    }
    @Test fun recoveryExpiresAtOriginalEndAndNeverStartsAfterIt() {
        val (engine,service)=setup(storageFailure=false);val app=RuntimeEnvironment.getApplication() as DuoApp
        engine.listener.onStopped("Camera occupied");waitUntil { app.status.value.phase==Phase.PAUSED }
        val store=CaptureRecovery(app);store.save(store.read()!!.copy(endWall=System.currentTimeMillis()+1000,endElapsed=android.os.SystemClock.elapsedRealtime()+1000))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        waitUntil { app.status.value.phase==Phase.IDLE };assertNull(store.read());assertEquals(1,engine.starts);service.destroy()
    }
    @Test fun restartedServiceUsesPersistentSessionInsteadOfNewDuration() {
        val (engine,first)=setup(storageFailure=false);val app=RuntimeEnvironment.getApplication() as DuoApp
        val saved=CaptureRecovery(app).read()!!
        first.destroy();assertEquals(saved.endElapsed,CaptureRecovery(app).read()!!.endElapsed)
        val next=Robolectric.buildService(RecordingService::class.java).create()
        next.get().onStartCommand(null,0,2)
        waitUntil { engine.starts==2 && app.status.value.phase==Phase.RECORDING }
        assertEquals(saved.startWall,CaptureRecovery(app).read()!!.startWall);assertEquals(saved.endWall,CaptureRecovery(app).read()!!.endWall)
        next.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,3);waitUntil { app.status.value.phase==Phase.IDLE };next.destroy()
    }
    @Test fun lowBatteryKeepsRecoveryPausedUntilChargeRecovers() {
        val (engine,service)=setup(storageFailure=false);val app=RuntimeEnvironment.getApplication() as DuoApp
        shadowOf(app.getSystemService(BatteryManager::class.java)).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,5)
        engine.listener.onStopped("Low battery");waitUntil { app.status.value.phase==Phase.PAUSED }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));waitUntil { app.status.value.phase==Phase.PAUSED };assertEquals(1,engine.starts)
        shadowOf(app.getSystemService(BatteryManager::class.java)).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,80)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));waitUntil { engine.starts==2 && app.status.value.phase==Phase.RECORDING }
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2);waitUntil { app.status.value.phase==Phase.IDLE };service.destroy()
    }
    @Test fun minimizingActivityKeepsForegroundRecordingAlive() {
        val (engine,service)=setup()
        val activity=Robolectric.buildActivity(MainActivity::class.java).setup()
        activity.pause().stop();shadowOf(Looper.getMainLooper()).idle()
        assertNull(engine.stopReason)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        assertSame(engine,app.activeEngine)
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE }
        assertTrue(app.diagnostics.entries().isEmpty())
        activity.destroy();service.destroy()
    }
    @Test fun serviceRoutesLightForFrontRearAndBothWithoutStoppingCapture() {
        for(mode in CameraMode.entries) {
            val (engine,service)=setup(mode)
            val app=RuntimeEnvironment.getApplication() as DuoApp
            service.get().onStartCommand(Intent().setAction(RecordingService.TORCH).putExtra("enabled",true),0,2)
            assertTrue(app.torch.value.enabled);assertFalse(app.torch.value.pending)
            service.get().onStartCommand(Intent().setAction(RecordingService.TORCH).putExtra("enabled",false),0,3)
            assertFalse(app.torch.value.enabled);assertEquals(listOf(true,false),engine.lightRequests);assertNull(engine.stopReason)
            service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,4)
            waitUntil { app.status.value.phase==Phase.IDLE };assertFalse(app.torch.value.enabled);service.destroy()
        }
    }
    @Test fun preselectedLightIsAppliedWhenRecordingStarts() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.lightForNextRecording=true
        val (engine,service)=setup()
        assertEquals(listOf(true),engine.lightRequests);assertTrue(app.torch.value.enabled)
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE }
        assertFalse(app.torch.value.enabled);assertTrue(app.lightForNextRecording)
        service.destroy()
    }
    @Test fun scheduledStopIsLoggedAsAutomaticRatherThanUserStop() {
        val (_,service)=setup()
        val app=RuntimeEnvironment.getApplication() as DuoApp
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP).putExtra("userRequested",false),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE }
        assertTrue(app.diagnostics.entries().single().contains("scheduled end time reached"))
        service.destroy()
    }
    @Test fun monitorStorageFailureFinalizesAndRecordsReasonInsteadOfCrashing() {
        val (engine,service)=setup()
        repeat(10) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3)) }
        waitUntil { engine.stopReason!=null }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(engine.stopReason!!.contains("storage provider unavailable"))
        val app=RuntimeEnvironment.getApplication() as DuoApp
        assertEquals(Phase.PAUSED,app.status.value.phase)
        assertNotNull(CaptureRecovery(app).read())
        assertTrue(app.diagnostics.entries().single().contains("storage provider unavailable"))
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE }
        service.destroy()
    }

    @Test fun audioRunsWithoutCameraPermissionAndMicInterruptionResumesWithinOriginalEnd() {
        val (engine,service)=setup(storageFailure=false,audioOnly=true)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,app.checkSelfPermission(android.Manifest.permission.CAMERA))
        waitUntil { app.status.value.phase==Phase.RECORDING }
        assertEquals(setOf(CameraSource.AUDIO),app.status.value.cameras.filterValues { it.recording }.keys)
        assertFalse(app.status.value.message.contains("+ audio"))
        val store=CaptureRecovery(app);val original=store.read()!!
        engine.listener.onError("Android blocked the microphone.");engine.listener.onStopped("Android blocked the microphone.")
        waitUntil { app.status.value.phase==Phase.PAUSED }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        waitUntil { engine.starts==2 && app.status.value.phase==Phase.RECORDING }
        assertTrue(engine.configs.last().audioOnly);assertEquals(original.endWall,store.read()!!.endWall)
        assertEquals(original.endElapsed,store.read()!!.endElapsed)
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE };assertNull(store.read());service.destroy()
    }

    @Test fun systemSilencingDoesNotStopVideoAndRestoredAudioDoesNotRestartCameras() {
        val (engine,service)=setup(storageFailure=false)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        waitUntil { app.status.value.phase==Phase.RECORDING }
        val saved=CaptureRecovery(app).read()!!;val cameras=app.status.value.cameras
        engine.listener.onMicrophoneState(MicrophoneState.ACTIVE)
        engine.listener.onMicrophoneState(MicrophoneState.SILENCED)
        waitUntil { app.status.value.microphone==MicrophoneState.SILENCED }
        assertEquals(Phase.RECORDING,app.status.value.phase);assertNull(engine.stopReason);assertEquals(1,engine.starts)
        assertTrue(app.status.value.message.contains("video continues"));assertFalse(app.status.value.error)
        assertEquals(saved.endElapsed,CaptureRecovery(app).read()!!.endElapsed)
        repeat(10) { engine.listener.onMicrophoneState(MicrophoneState.SILENCED) }
        shadowOf(Looper.getMainLooper()).idle();assertEquals(1,app.diagnostics.entries().size)
        engine.listener.onMicrophoneState(MicrophoneState.ACTIVE)
        waitUntil { app.status.value.microphone==MicrophoneState.ACTIVE }
        assertEquals(cameras,app.status.value.cameras);assertEquals(1,engine.starts);assertNull(engine.stopReason)
        assertEquals(2,app.diagnostics.entries().size)
        assertTrue(app.diagnostics.entries().any { it.contains("restored without restarting cameras") })
        assertTrue(app.diagnostics.entries().any { it.contains("Audio source: MIC") && it.contains("microphone state: SILENCED") })
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE };service.destroy()
    }
    @Test fun recoverySuccessNeedsBothActualCamerasAndUnsilencedMicAndRejectsOldCallbacks() {
        val (engine,service)=setup(CameraMode.BOTH,false)
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val store=CaptureRecovery(app);val original=store.read()!!;val oldListener=engine.listener
        oldListener.onStopped("Camera occupied");waitUntil { app.status.value.phase==Phase.PAUSED }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));waitUntil { engine.starts==2 }
        assertTrue(app.diagnostics.entries().any { it.contains("Recovery attempt started") })
        assertFalse(app.diagnostics.entries().any { it.contains("Capture resumed successfully") })
        engine.listener.onMicrophoneState(MicrophoneState.ACTIVE);shadowOf(Looper.getMainLooper()).idle()
        assertFalse(app.diagnostics.entries().any { it.contains("Capture resumed successfully") })
        engine.listener.onCameraState(CameraSource.FRONT,CameraCaptureStatus(recording=true,startedMs=1000))
        waitUntil { app.diagnostics.entries().any { it.contains("Capture resumed successfully") } }
        assertEquals(original.endWall,store.read()!!.endWall);assertEquals(original.endElapsed,store.read()!!.endElapsed)
        assertTrue(store.read()!!.reason.isBlank())
        oldListener.onMicrophoneState(MicrophoneState.SILENCED);shadowOf(Looper.getMainLooper()).idle()
        assertEquals(MicrophoneState.ACTIVE,app.status.value.microphone)
        engine.listener.onCameraState(CameraSource.FRONT,CameraCaptureStatus(recording=true,startedMs=1000))
        engine.listener.onMicrophoneState(MicrophoneState.ACTIVE);shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,app.diagnostics.entries().count { it.contains("Capture resumed successfully") })
        service.get().onStartCommand(Intent().setAction(RecordingService.STOP),0,2)
        waitUntil { app.status.value.phase==Phase.IDLE };service.destroy()
    }
}
