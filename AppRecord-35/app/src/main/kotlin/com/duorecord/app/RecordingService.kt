package com.duorecord.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.*
import androidx.core.content.ContextCompat
import com.duorecord.contracts.*
import com.duorecord.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest

/** Keeps the existing foreground service alive while a recoverable interruption clears. */
class RecordingService : Service(),CaptureListener {
    companion object {
        const val PAUSE="duorecord.PAUSE";const val RESUME="duorecord.RESUME"
        const val START="duorecord.START";const val STOP="duorecord.STOP";const val SPLIT="duorecord.SPLIT";const val UPDATE="duorecord.UPDATE";const val TORCH="duorecord.TORCH"
        fun resumeIfPending(context:Context) {
            val app=context.applicationContext as DuoApp
            if(app.activeEngine!=null || app.status.value.phase in setOf(Phase.STARTING,Phase.STOPPING))return
            val saved=CaptureRecovery(context).read() ?: return
            if((saved.config.screenMode || saved.config.mixedMode)) { CaptureRecovery(context).clear();return }
            if(CaptureRecovery(context).remaining(saved)<=0) { CaptureRecovery(context).clear();return }
            if(CaptureRecovery(context).userPaused) {
                app.status.update { it.copy(phase=Phase.USER_PAUSED,message="Paused by you. Tap Resume to continue.",startedMs=saved.startWall) };return
            }
            ContextCompat.startForegroundService(context,Intent(context,RecordingService::class.java).setAction(START).putExtra("resume",true))
        }
    }
    private val app get()=application as DuoApp
    private val recovery by lazy { CaptureRecovery(this) }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var screenGrant:Intent?=null
    private var engine:CaptureEngine?=null
    private var floating:com.duorecord.app.ui.MixedFloatingControls?=null
    private var floatingVisibility:Job?=null
    private var returnToPlayback=false
    private var preferredPlaybackSource:CameraSource?=null
    @Volatile private var finishedPlaybackClip:Clip?=null
    private var mixedConfigSave:Job?=null
    private var wake:PowerManager.WakeLock?=null
    private var monitor:Job?=null
    private var healthSampling:Job?=null
    private var launch:Job?=null
    private var retry:Job?=null
    private var ending=false;private var destroyed=false;private var foreground=false
    private var callbacks=false
    @Volatile private var attempt=0
    private var recoveryConfirmed=false
    private val handler=Handler(Looper.getMainLooper())
    private val cameras by lazy { getSystemService(CameraManager::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val cameraAvailability=object:CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId:String) { requestRetry() }
    }
    private val microphoneAvailability=object:AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs:List<AudioRecordingConfiguration>) { requestRetry() }
    }
    private val batteryChanges=object:BroadcastReceiver() { override fun onReceive(c:Context?,i:Intent?) { requestRetry() } }
    private val heatChanges=PowerManager.OnThermalStatusChangedListener { requestRetry() }
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) {
            PAUSE -> if(!ending && engine!=null && app.status.value.phase==Phase.RECORDING) {
                recovery.userPaused=true
                app.status.update { it.copy(phase=Phase.PAUSING,message="Pausing and saving current files…") }
                engine?.setPaused(true)
            }
            RESUME -> if(!ending && app.status.value.phase==Phase.USER_PAUSED) {
                val saved=recovery.read()
                if(saved==null || recovery.remaining(saved)<=0) { recovery.clear();stopCapture("Stopped: original recording end time reached.") }
                else {
                    recovery.userPaused=false
                    if(engine==null) { recovery.pause("Resuming after user pause.");beginForegroundAndCapture() } else {
                        app.status.update { it.copy(phase=Phase.PAUSING,message="Resuming capture…") };engine?.setPaused(false)
                    }
                }
            }
            STOP -> {
                if(intent.getBooleanExtra("openPlayback",false) || !app.uiVisible.value && intent.getBooleanExtra("userRequested",true) && app.repository?.config()?.requiresProjection==true)returnToPlayback=true
                if(intent.getBooleanExtra("userRequested",true))app.diagnostics.userStop()
                recovery.clear()
                stopCapture(if(intent.getBooleanExtra("userRequested",true))"Stopped" else "Stopped: scheduled end time reached.")
            }
            TORCH -> if(!ending) {
                val enabled=intent.getBooleanExtra("enabled",false)
                app.lightForNextRecording=enabled;recovery.light(enabled)
                if(engine!=null) { app.torch.value=app.torch.value.copy(pending=true,message="Updating light…");engine?.setTorch(enabled) }
            }
            SPLIT -> engine?.split()
            UPDATE -> if(engine!=null && !ending) {
                val sources=intent.getStringArrayExtra("sources").orEmpty().mapNotNull { runCatching { CameraSource.valueOf(it) }.getOrNull() }.filter { it!=CameraSource.LEGACY }.toSet()
                if(sources.isEmpty()) { app.diagnostics.userStop();recovery.clear() }
                val config=app.repository!!.config()
                if(config.mixedMode || config.screenMode)recovery.update(config) else if(sources.isNotEmpty())recovery.update(config.copy(cameraMode=if(sources.size==2)CameraMode.BOTH else if(CameraSource.FRONT in sources)CameraMode.FRONT else CameraMode.REAR))
                engine!!.update(config,sources);floating?.sync(config)
                app.status.update { if(it.phase==Phase.RECORDING && !it.error)it.copy(message=recordingMessage(it.cameras,it.microphone)) else it }
            }
            START -> if(engine==null && launch?.isActive!=true && !ending) {
                if(!intent.getBooleanExtra("resume",false)) {
                    val config=app.repository?.config() ?: return START_NOT_STICKY
                    screenGrant=if(config.requiresProjection)intent.getParcelableExtra("screenGrant") else null
                    recovery.begin(config,app.lightForNextRecording,getSharedPreferences("automation",0).getLong("activeEnd",0))
                }
                beginForegroundAndCapture()
            }
            else -> if(intent==null && recovery.read()!=null && !ending)beginForegroundAndCapture() else if(engine==null)stopSelf()
        }
        return if(recovery.read()!=null)START_STICKY else START_NOT_STICKY
    }
    private fun beginForegroundAndCapture() {
        val saved=recovery.read() ?: run { cleanup();return }
        if(recovery.remaining(saved)<=0) { recovery.clear();finish("Stopped: original recording end time reached.");return }
        if(recovery.userPaused) { app.status.update { it.copy(phase=Phase.USER_PAUSED,message="Paused by you. Tap Resume to continue.",startedMs=saved.startWall) };cleanup();return }
        try {
            if(!foreground) {
                startForeground(Notifications.RECORD,Notifications.record(this,if((saved.config.screenMode || saved.config.mixedMode))"Starting screen capture" else if(saved.config.audioOnly)"Starting microphone" else "Starting cameras"),CapturePermissions.foregroundTypes(saved.config))
                foreground=true;registerAvailability()
            }
            launchCapture(saved)
        } catch(t:Throwable) {
            val reason="Android requires reopening the app to resume: ${t.message ?: t.javaClass.simpleName}"
            if((saved.config.screenMode || saved.config.mixedMode)) { recovery.clear();app.diagnostics.add(reason);finish(reason);return }
            recovery.pause(reason);app.diagnostics.add(reason)
            app.status.update { it.copy(phase=Phase.PAUSED,message=reason,error=true,cameras=emptyMap()) }
            // A rejected foreground launch cannot run a hidden background camera retry.
            cleanup()
        }
    }
    private fun launchCapture(saved:SavedCapture) {
        if(destroyed || ending || engine!=null || launch?.isActive==true)return
        retry?.cancel();retry=null
        app.status.update { it.copy(phase=Phase.STARTING,message=if(saved.reason.isBlank()) { if((saved.config.screenMode || saved.config.mixedMode))"Starting screen capture…" else if(saved.config.audioOnly)"Starting microphone…" else "Starting cameras…" } else "Resuming capture…",startedMs=saved.startWall,error=false,cameras=emptyMap(),microphone=MicrophoneState.STARTING) }
        recoveryConfirmed=false
        launch=scope.launch {
            var diagnosticStarted=false
            try {
                check(recovery.remaining(saved)>0) { "Original recording end time reached." }
                val config=saved.config
                check(!config.requiresProjection || ScreenControls.floating(this@RecordingService) || ScreenControls.notificationsAllowed(this@RecordingService)) { "Enable AppRecord recording notifications to use notification controls." }
                preferredPlaybackSource=if(config.mixedMode)CameraSource.MIXED else if(config.screenMode)CameraSource.SCREEN else null
                finishedPlaybackClip=null
                check(CapturePermissions.runtime(config).all { checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED }) { "Camera or microphone permission unavailable." }
                val repo=app.repository ?: error("Storage module unavailable.")
                withContext(Dispatchers.IO) { repo.prepareStorage();repo.reconcile();repo.enforceRetention();check(repo.freeBytes()>=config.reserveBytes) { "Storage full. Saved files protected." } }
                if(ending || destroyed)return@launch
                val power=getSystemService(PowerManager::class.java);val battery=getSystemService(BatteryManager::class.java)
                check(power.currentThermalStatus<PowerManager.THERMAL_STATUS_SEVERE) { "Phone too hot to record." }
                check(battery.isCharging || battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) !in 0..config.stopBatteryPercent) { "Battery too low to record." }
                val candidate=if(config.screenMode || config.mixedMode) {
                    val grant=if(config.requiresProjection)screenGrant ?: error("Start screen again to grant Android permission.") else null
                    screenGrant=null
                    com.duorecord.capture.ScreenCaptureEngine(this@RecordingService,Activity.RESULT_OK,grant)
                } else app.capture?.create(this@RecordingService) ?: error("Recording module unavailable.")
                val support=candidate.support(config);check(support.available) { support.detail }
                getSharedPreferences("runtime",0).edit().putBoolean("unfinished",true).commit()
                app.diagnostics.begin(if(config.mixedMode)"Mixed: ${config.mixedSources}; background: ${config.mixedBackground}; internal: ${config.screenInternalAudio}; ambient: ${config.screenAudio}; original end: ${saved.endWall}" else if(config.screenMode)"Screen recording; internal audio: ${config.screenInternalAudio}; ambient audio: ${config.screenAudio}; original end: ${saved.endWall}" else if(config.audioOnly)"Audio only: AAC 48 kHz mono 64 kbps; original end: ${saved.endWall}" else "Cameras: ${config.cameraMode}; front: ${config.frontQuality}; rear: ${config.rearQuality}; FPS: ${config.fps}; original end: ${saved.endWall}")
                diagnosticStarted=true
                if(saved.reason.isNotBlank())app.diagnostics.add("Recovery attempt started after: ${saved.reason}","Original end: ${saved.endWall}")
                wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DuoRecord:recording").apply { acquire(6*60*60*1000L) }
                engine=candidate
                val version=++attempt
                val target=this@RecordingService
                val listener=object:CaptureListener {
                    fun valid()=version==attempt && !destroyed
                    override fun onPauseChanged(paused:Boolean) { if(valid())target.onPauseChanged(paused) }
                    override fun onAudioMeter(meter:AudioMeter) { if(valid())target.onAudioMeter(meter) }
                    override fun onAudioLevel(levels:FloatArray) { if(valid())target.onAudioLevel(levels) }
                    override fun onMicrophoneState(state:MicrophoneState) { if(valid())target.onMicrophoneState(state) }
                    override fun onTorchState(state:TorchStatus) { if(valid())target.onTorchState(state) }
                    override fun onPhotoLight(source:CameraSource,active:Boolean) { if(valid())target.onPhotoLight(source,active) }
                    override fun onCameraState(source:CameraSource,state:CameraCaptureStatus) { if(valid())target.onCameraState(source,state) }
                        override fun onStarted(codec:String) { if(valid())target.onStarted(codec) }
                    override fun onSegment(clip:Clip) { if(valid())target.onSegment(clip) }
                    override fun onStopped(reason:String) { if(valid())target.onStopped(reason) }
                    override fun onError(message:String) { if(valid())target.onError(message) }
                }
                try { candidate.start(config.copy(photoEpochMs=if(saved.reason.isNotBlank())saved.startWall else 0),repo,listener) }
                catch(t:Throwable) { attempt++;engine=null;runCatching { candidate.stop("Capture start failed.") };throw t }
                if(saved.light && !config.audioOnly && !config.screenMode)candidate.setTorch(true)
                app.activeEngine=engine;app.connectPreviews()
                if((config.mixedMode || config.screenMode) && ScreenControls.floating(this@RecordingService)) {
                    floating=com.duorecord.app.ui.MixedFloatingControls(this@RecordingService,{ repo.config() },{ next ->
                        engine?.update(next,if(next.mixedMode)next.mixedSources else setOf(CameraSource.SCREEN));recovery.update(next)
                        mixedConfigSave?.cancel();mixedConfigSave=scope.launch {
                            delay(120)
                            try { withContext(Dispatchers.IO) { repo.saveConfig(next) };app.status.update { if(it.phase==Phase.RECORDING && !it.error)it.copy(message=recordingMessage(it.cameras,it.microphone)) else it } }
                            catch(t:Throwable) { if(t !is CancellationException)onError(t.message ?: "Could not change mixed capture.") }
                        }
                    },{ startService(Intent(this@RecordingService,RecordingService::class.java).setAction(STOP).putExtra("openPlayback",true)) })
                    floating?.show()
                    floatingVisibility?.cancel();floatingVisibility=scope.launch { app.uiVisible.collectLatest { floating?.setAppVisible(it) } }
                }
                startMonitor(config)
            } catch(t:Throwable) {
                if(t !is CancellationException) {
                    val reason=t.message ?: "Could not start capture."
                    app.diagnostics.error(reason)
                    if(engine!=null)stopCapture(reason) else {
                        if(diagnosticStarted)app.diagnostics.finish(reason) else app.diagnostics.add(if(saved.reason.isBlank())"Capture could not start: $reason" else "Recovery attempt failed: $reason","Original end: ${saved.endWall}")
                        pauseForRecovery(reason)
                    }
                }
            } finally { launch=null }
        }
    }
    private fun startMonitor(config:RecordingConfig) {
        monitor?.cancel();healthSampling?.cancel()
        healthSampling=scope.launch(Dispatchers.IO) { while(isActive) {
            runCatching { DeviceHealthReader.read(this@RecordingService,config.stopBatteryPercent,config.reserveBytes);app.captureClock.elapsedMs() }
            delay(3000)
        } }
        monitor=scope.launch {
            var cycles=0
            try { while(isActive) {
                delay(3000)
                val saved=recovery.read()
                val power=getSystemService(PowerManager::class.java);val battery=getSystemService(BatteryManager::class.java)
                val level=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val why=when {
                    saved==null || recovery.remaining(saved)<=0 -> { recovery.clear();"Stopped: original recording end time reached." }
                    power.currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE -> "Stopped: phone too hot."
                    !battery.isCharging && level in 0..config.stopBatteryPercent -> "Stopped: low battery."
                    else -> null
                }
                if(why!=null) { stopCapture(why);break }
                if(++cycles%5==0)app.diagnostics.heartbeat()
                if(cycles%10==0)withContext(Dispatchers.IO) { app.repository!!.enforceRetention();check(app.repository!!.freeBytes()>=config.reserveBytes) { "Storage full. Saved files protected." } }
                if(cycles%3600==0)wake?.let { if(it.isHeld)it.release();it.acquire(6*60*60*1000L) }
            } } catch(t:Throwable) { if(t !is CancellationException) { val reason="Recording monitor failed: ${t.message ?: t.javaClass.simpleName}";app.diagnostics.error(reason);stopCapture(reason) } }
        }
    }
    private fun registerAvailability() {
        if(callbacks)return
        callbacks=true
        runCatching { cameras.registerAvailabilityCallback(cameraAvailability,handler) }
        runCatching { audio.registerAudioRecordingCallback(microphoneAvailability,handler) }
        runCatching { registerReceiver(batteryChanges,IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        runCatching { getSystemService(PowerManager::class.java).addThermalStatusListener(mainExecutor,heatChanges) }
    }
    private fun requestRetry() { handler.post { if(app.status.value.phase==Phase.PAUSED && !recovery.userPaused && foreground && !ending && !destroyed)scheduleRetry(750) } }
    private fun scheduleRetry(delayMs:Long=8000) {
        retry?.cancel()
        retry=scope.launch {
            delay(delayMs)
            val saved=recovery.read() ?: return@launch
            if(recovery.userPaused)return@launch
            if(recovery.remaining(saved)<=0) { recovery.clear();finish("Stopped: original recording end time reached.") }
            else launchCapture(saved)
        }
    }
    private fun pauseForRecovery(reason:String) {
        getSharedPreferences("runtime",0).edit().putString("lastStop",reason).putLong("stopTime",System.currentTimeMillis()).apply()
        if(destroyed)return
        val saved=recovery.read()
        if(saved?.config?.let { it.screenMode || it.mixedMode }==true) { recovery.clear();finish(reason);return }
        if(saved==null || recovery.remaining(saved)<=0) { recovery.clear();finish(reason);return }
        recovery.pause(reason);app.audioLevels.value=FloatArray(12);app.audioMeter.value=AudioMeter();ending=false;engine=null;app.activeEngine=null;app.torch.value=TorchStatus();app.photoLighting.value=emptySet()
        monitor?.cancel();wake?.let { if(it.isHeld)it.release() };wake=null
        app.status.update { it.copy(phase=if(recovery.userPaused)Phase.USER_PAUSED else Phase.PAUSED,message=if(recovery.userPaused)"Paused by you • $reason" else "Waiting to resume • $reason",error=false,startedMs=saved.startWall,cameras=emptyMap(),microphone=MicrophoneState.OFF,revision=it.revision+1) }
        getSharedPreferences("runtime",0).edit().putBoolean("unfinished",true).putString("lastStop",reason).putLong("stopTime",System.currentTimeMillis()).commit()
        if(foreground) { getSystemService(NotificationManager::class.java).notify(Notifications.RECORD,Notifications.record(this,app.status.value.message));if(!recovery.userPaused)scheduleRetry() }
    }
    private fun stopCapture(reason:String) {
        getSharedPreferences("runtime",0).edit().putString("lastStop",reason).putLong("stopTime",System.currentTimeMillis()).apply()
        if(ending)return
        ending=true;monitor?.cancel();retry?.cancel()
        app.status.update { it.copy(phase=Phase.STOPPING,message="Finishing…") }
        if(engine!=null)engine?.stop(reason) else finish(reason)
    }
    override fun onCameraState(source:CameraSource,state:CameraCaptureStatus) { scope.launch {
        if(destroyed || app.status.value.phase==Phase.PAUSED)return@launch
        if(app.status.value.phase in setOf(Phase.PAUSING,Phase.USER_PAUSED)) { app.status.update { it.copy(cameras=it.cameras+(source to state)) };return@launch }
        val quality=state.quality
        if(source in setOf(CameraSource.FRONT,CameraSource.REAR) && state.recording && !state.pending && quality!=null)recovery.read()?.let { saved ->
            val c=saved.config
            val actual=if(source==CameraSource.FRONT)c.copy(frontQuality=quality.key) else c.copy(rearQuality=quality.key)
            if(actual!=c)recovery.update(actual)
        }
        app.status.update { old ->
            val map=old.cameras+(source to state);val running=map.filterValues { it.recording }.keys;val busy=map.values.any { it.pending }
            old.copy(cameras=map,phase=if(ending)Phase.STOPPING else if(running.isNotEmpty())Phase.RECORDING else if(busy)Phase.STARTING else old.phase,
                message=if(old.error)old.message else recordingMessage(map,old.microphone),
                startedMs=recovery.read()?.startWall ?: map.values.filter { it.recording }.minOfOrNull { it.startedMs } ?: 0)
        }
        confirmRecovery()
        if(!ending)getSystemService(NotificationManager::class.java).notify(Notifications.RECORD,Notifications.record(this@RecordingService,app.status.value.message))
    } }
    private fun recordingMessage(cameras:Map<CameraSource,CameraCaptureStatus>,mic:MicrophoneState):String {
        val running=cameras.filterValues { it.recording }.keys
        if(cameras.values.any { it.pending })return "Updating cameras…"
        if(running.isEmpty())return "Finishing…"
        val config=recovery.read()?.config ?: app.repository?.config() ?: RecordingConfig()
        if(config.mixedMode)return "Recording • Mixed · ${config.mixedBackground.name} background"
        if(config.screenMode)return "Recording • Screen · "+listOfNotNull("ambient".takeIf { config.screenAudio },"internal".takeIf { config.screenInternalAudio }).joinToString(" + ").ifEmpty { "no audio" }
        if(config.audioOnly)return "Recording • Audio"+when(mic) { MicrophoneState.SILENCED -> " · silenced by Android";MicrophoneState.UNKNOWN,MicrophoneState.STARTING -> " · checking microphone";else -> "" }
        val base="${if(config.photoMode)"Photos" else "Recording"} • ${running.joinToString(" + ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }}"
        if(config.photoMode)return base
        return base+when(mic) { MicrophoneState.ACTIVE -> " + audio";MicrophoneState.SILENCED -> " · audio silenced by Android · video continues";MicrophoneState.OFF -> " · audio unavailable";else -> " · checking microphone" }
    }
    override fun onMicrophoneState(state:MicrophoneState) { scope.launch {
        if(destroyed || ending || app.status.value.phase==Phase.PAUSED)return@launch
        if(app.status.value.phase in setOf(Phase.PAUSING,Phase.USER_PAUSED)) { app.status.update { it.copy(microphone=state) };return@launch }
        val previous=app.status.value.microphone
        app.status.update { old -> old.copy(microphone=state,message=if(old.error || old.cameras.values.none { it.recording })old.message else recordingMessage(old.cameras,state)) }
        val config=recovery.read()?.config ?: return@launch
        if(state==MicrophoneState.SILENCED && previous!=state) {
            val action=if(!config.audioOnly && config.continueVideoWithoutMic)"video continues with silent audio" else "capture will pause"
            app.diagnostics.event("Android silenced the microphone; $action",microphoneDetails())
        } else if(state==MicrophoneState.ACTIVE && previous==MicrophoneState.SILENCED) {
            app.diagnostics.event("Microphone audio restored without restarting cameras",microphoneDetails())
        }
        confirmRecovery()
        if(foreground)getSystemService(NotificationManager::class.java).notify(Notifications.RECORD,Notifications.record(this@RecordingService,app.status.value.message))
    } }
    private fun microphoneDetails():String {
        val manager=getSystemService(AudioManager::class.java)
        val mode=when(manager.mode) { AudioManager.MODE_NORMAL -> "Normal";AudioManager.MODE_RINGTONE -> "Ringtone";AudioManager.MODE_IN_CALL -> "In call";AudioManager.MODE_IN_COMMUNICATION -> "In communication";else -> manager.mode.toString() }
        return "Audio source: MIC; microphone state: ${app.status.value.microphone}; audio mode: $mode; system microphone mute: ${manager.isMicrophoneMute}\nAndroid does not reveal the identity of a competing app through this capture status."
    }
    private fun confirmRecovery() {
        if(recoveryConfirmed || ending || destroyed)return
        val saved=recovery.read() ?: return
        if(saved.reason.isBlank())return
        val expected=if(saved.config.audioOnly)setOf(CameraSource.AUDIO) else CapturePolicy.sources(saved.config.cameraMode)
        val current=app.status.value
        val ready=expected.all { source -> current.cameras[source]?.let { it.recording && !it.pending }==true }
        if(!ready || (!saved.config.photoMode && current.microphone!=MicrophoneState.ACTIVE))return
        recoveryConfirmed=true
        app.diagnostics.event("Capture resumed successfully", "Original end: ${saved.endWall}; confirmed streams: ${expected.joinToString()}; microphone: ${current.microphone}")
        recovery.pause("")
    }
    override fun onStarted(codec:String) { scope.launch { if(!ending && !destroyed)app.status.update { it.copy(codec=codec) } } }
    override fun onTorchState(state:TorchStatus) { if(!destroyed)app.torch.value=state }
    override fun onPhotoLight(source:CameraSource,active:Boolean) { if(!destroyed)app.photoLighting.update { if(active)it+source else it-source } }
    override fun onAudioMeter(meter:AudioMeter) { if(!destroyed)app.audioMeter.value=if(recovery.userPaused)AudioMeter() else meter }
    override fun onAudioLevel(levels:FloatArray) { if(!destroyed)app.audioLevels.value=if(recovery.userPaused)FloatArray(12) else levels }
    override fun onPauseChanged(paused:Boolean) { scope.launch {
        if(destroyed || ending)return@launch
        recovery.noteUserPause(paused)
        val reason=if(paused)"Paused. Tap Record or Resume to continue the same file. Original end time unchanged." else recordingMessage(app.status.value.cameras,app.status.value.microphone)
        app.status.update { it.copy(phase=if(paused)Phase.USER_PAUSED else Phase.RECORDING,message=reason,error=false,revision=it.revision+1) }
        if(paused) {
            app.audioLevels.value=FloatArray(12);app.audioMeter.value=AudioMeter();app.photoLighting.value=emptySet()
            getSharedPreferences("runtime",0).edit().putString("lastStop","Paused by you.").putLong("stopTime",System.currentTimeMillis()).apply()
        }
        app.repository?.config()?.let { floating?.sync(it) }
        app.diagnostics.event(if(paused)"User paused capture" else "User resumed capture","Original end: ${recovery.read()?.endWall}")
        if(foreground)getSystemService(NotificationManager::class.java).notify(Notifications.RECORD,Notifications.record(this@RecordingService,reason))
    } }
    override fun onSegment(clip:Clip) { if(clip.camera==preferredPlaybackSource && clip.status==ClipStatus.READY)finishedPlaybackClip=clip;app.latestCapture.value=clip; if(clip.isPhoto)app.photoCaptured.tryEmit(clip.camera);app.changed() }
    override fun onError(message:String) { app.diagnostics.error(message);app.status.update { it.copy(message=message,error=true) } }
    override fun onStopped(reason:String) { handler.post { if(!destroyed) { app.diagnostics.finish(reason);if(recovery.read()!=null)pauseForRecovery(reason) else finish(reason) } } }
    private fun finish(reason:String) {
        if(destroyed)return
        val saved=recovery.read()
        if(saved!=null && recovery.remaining(saved)>0) { pauseForRecovery(reason);return }
        recovery.clear();ending=true;engine=null
        app.diagnostics.finish(reason)
        getSharedPreferences("automation",0).edit().putLong("activeEnd",0).apply()
        getSharedPreferences("runtime",0).edit().putBoolean("unfinished",false).putString("lastStop",reason).putLong("stopTime",System.currentTimeMillis()).commit()
        app.status.update { it.copy(phase=Phase.IDLE,message=reason,startedMs=0,revision=it.revision+1,cameras=emptyMap(),microphone=MicrophoneState.OFF) }
        if(returnToPlayback) {
            returnToPlayback=false
            try {
                val destination=Intent(this,com.duorecord.app.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                finishedPlaybackClip?.let { destination.putExtra("clip",it.id) }
                startActivity(destination)
            } catch(t:Throwable) { app.diagnostics.add("Playback return unavailable",t.stackTraceToString()) }
        }
        cleanup()
    }
    private fun cleanup() { healthSampling?.cancel();healthSampling=null;floatingVisibility?.cancel();floatingVisibility=null;floating?.hide();floating=null;app.audioLevels.value=FloatArray(12);app.audioMeter.value=AudioMeter(); app.torch.value=TorchStatus();app.photoLighting.value=emptySet();app.activeEngine=null;monitor?.cancel();retry?.cancel();wake?.let { if(it.isHeld)it.release() };wake=null;foreground=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy() {
        healthSampling?.cancel();floatingVisibility?.cancel();floating?.hide();floating=null;app.audioMeter.value=AudioMeter()
        val screenSession=recovery.read()?.config?.let { it.screenMode || it.mixedMode }==true
        destroyed=true;app.activeEngine=null;app.photoLighting.value=emptySet()
        if(screenSession && !ending) {
            recovery.clear();ending=true;getSharedPreferences("runtime",0).edit().putBoolean("unfinished",false).commit();engine?.stop("Screen service interrupted; new permission required.")
            app.diagnostics.finish("Screen service interrupted; new permission required.")
            app.status.update { it.copy(phase=Phase.IDLE,cameras=emptyMap(),message="Start screen again to grant permission.") }
        }
        if(!ending && recovery.read()!=null) { recovery.pause("Recording service destroyed unexpectedly.");app.diagnostics.finish("Recording service destroyed unexpectedly.");engine?.stop("Recording service stopped.");app.status.update { it.copy(phase=Phase.PAUSED,cameras=emptyMap(),microphone=MicrophoneState.OFF,message="Recording interrupted. Waiting to resume.") } }
        if(callbacks) {
            runCatching { cameras.unregisterAvailabilityCallback(cameraAvailability) };runCatching { audio.unregisterAudioRecordingCallback(microphoneAvailability) }
            runCatching { unregisterReceiver(batteryChanges) };runCatching { getSystemService(PowerManager::class.java).removeThermalStatusListener(heatChanges) }
        }
        wake?.let { if(it.isHeld)it.release() };scope.cancel();super.onDestroy()
    }
}
