package com.duorecord.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import com.duorecord.contracts.*
import com.duorecord.domain.*
import java.util.concurrent.Executor
import kotlin.concurrent.thread

class CaptureFeature : CaptureFactory { override fun create(context:Context):CaptureEngine=RoutingCaptureEngine(context.applicationContext) }

/** One microphone; independent camera/encoder/muxer lifetimes. All topology changes run on control. */
internal class NativeDualEngine(context:Context):CaptureEngine {
    private val manager=context.getSystemService(CameraManager::class.java)
    private val capabilities=CameraCapabilities(manager)
    private var controlThread:HandlerThread?=null
    private var codecThread:HandlerThread?=null
    private var control:Handler?=null
    private lateinit var repository:MediaRepository
    private lateinit var listener:CaptureListener
    private var config=RecordingConfig()
    private var desired=emptySet<CameraSource>()
    private val pipelines=mutableMapOf<CameraSource,Pipeline>()
    private val previews=java.util.concurrent.ConcurrentHashMap<CameraSource,Surface>()
    private var audio:AudioEncoder?=null
    @Volatile private var userPaused=false
    private var torchWanted=false
    private val photoLighting=mutableSetOf<CameraSource>()
    private fun torchActive()=!userPaused && torchWanted && (!config.photoMode || photoLighting.isNotEmpty())
    private var torchCallbackRegistered=false
    private var torchObserved:Boolean?=null
    private var externalTorchAvailable=false
    private var torchWaiting=false
    private var torchGeneration=0
    private var originNs=0L;private var originWall=0L
    private var sessionId=""
    private var ending=false;private var finalizing=false;private var stoppingReason="Stopped"
    private var generation=0;private var configuring=false
    private class Pipeline(val source:CameraSource,val input:CameraInput,val config:RecordingConfig,val quality:CaptureQuality,val writer:RollingMuxer) {
        var encoder:VideoEncoder?=null
        var compositor:DualCompositor?=null
        var surface:Surface?=null
        var device:CameraDevice?=null
        var opening=false
        var session:CameraCaptureSession?=null
        var closing=false
        var state=CameraCaptureStatus(pending=true,quality=quality,fps=config.fps,previewMessage="Starting…")
    }
    private fun torchId():String?=runCatching {
        val rear=pipelines[CameraSource.REAR]?.input?.id
        fun flash(id:String)=manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true
        rear?.takeIf { flash(it) } ?: manager.cameraIdList.firstOrNull {
            val chars=manager.getCameraCharacteristics(it)
            chars.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK && flash(it)
        }
    }.getOrNull()
    private fun ownsTorch(id:String)=pipelines[CameraSource.REAR]?.let { it.input.id==id && !it.closing && (it.device!=null || it.opening) }==true
    private val torchCallback=object:CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId:String,enabled:Boolean) {
            if(ending || cameraId!=torchId() || ownsTorch(cameraId))return
            torchObserved=enabled;externalTorchAvailable=true;torchWaiting=false
            listener.onTorchState(TorchStatus(enabled,true))
        }
        override fun onTorchModeUnavailable(cameraId:String) {
            if(ending || cameraId!=torchId() || ownsTorch(cameraId))return
            torchObserved=false;externalTorchAvailable=false;torchWaiting=false
            listener.onTorchState(TorchStatus(message="Light unavailable with the current camera resources."))
        }
    }
    override fun setTorch(enabled:Boolean) { control?.post {
        if(ending)return@post
        torchWanted=enabled;applyTorch()
    } }
    private fun applyTorch() {
        val id=torchId()
        if(id==null) { torchWanted=false;listener.onTorchState(TorchStatus(message="No rear flashlight is available."));return }
        val rear=pipelines[CameraSource.REAR]
        val enabled=torchActive()
        val generation=++torchGeneration
        torchWaiting=false
        try {
            if(ownsTorch(id)) {
                val session=rear?.session
                if(session==null) { listener.onTorchState(TorchStatus(available=true,pending=true,message="Updating light…"));return }
                session.setRepeatingRequest(recordRequest(rear),null,control)
                listener.onTorchState(TorchStatus(enabled,true))
            } else {
                if(externalTorchAvailable && torchObserved==enabled) {
                    listener.onTorchState(TorchStatus(enabled,true));return
                }
                val maximum=if(android.os.Build.VERSION.SDK_INT>=33 && enabled && config.photoMode)manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1 else 1
                if(android.os.Build.VERSION.SDK_INT>=33 && maximum>1)manager.turnOnTorchWithStrengthLevel(id,maximum) else manager.setTorchMode(id,enabled)
                torchWaiting=true
                control?.postDelayed({
                    if(!ending && generation==torchGeneration && torchWaiting) {
                        torchWaiting=false
                        listener.onTorchState(TorchStatus(torchObserved==true,true,message="Light state could not be confirmed by Android."))
                    }
                },2000)
                // The system callback, rather than a desired flag, confirms external-torch state.
                listener.onTorchState(TorchStatus(available=true,pending=true,message="Updating light…"))
            }
        } catch(t:Exception) {
            torchWanted=false
            listener.onTorchState(TorchStatus(available=true,message="Light unavailable: ${t.message ?: t.javaClass.simpleName}"))
        }
    }
    private fun recordRequest(p:Pipeline):CaptureRequest {
        val chars=manager.getCameraCharacteristics(p.input.id)
        val ranges=chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        val range=ranges.filter { it.contains(p.config.fps) }.minWithOrNull(compareBy<Range<Int>> { it.upper }.thenByDescending { it.lower })
            ?: error("Requested frame rate is unsupported.")
        return p.device!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(p.surface!!);set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range)
            capabilities.configureOrientation(this,p.input.id)
            if(chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)==true)
                set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.FLASH_MODE,if(p.source==CameraSource.REAR && chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true && torchActive())CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            if(android.os.Build.VERSION.SDK_INT>=35 && config.photoMode && p.source==CameraSource.REAR && torchActive() && chars.availableCaptureRequestKeys?.contains(CaptureRequest.FLASH_STRENGTH_LEVEL)==true) {
                chars.get(CameraCharacteristics.FLASH_TORCH_STRENGTH_MAX_LEVEL)?.takeIf { it>1 }?.let { set(CaptureRequest.FLASH_STRENGTH_LEVEL,it) }
            }
        }.build()
    }
    override fun qualities(source:CameraSource,config:RecordingConfig)=capabilities.qualities(source,config)
    override fun support(config:RecordingConfig)=runCatching {
        CapturePolicy.validate(config)
        selected(config,CapturePolicy.sources(config.cameraMode))
        CaptureSupport(true,"Selected camera configuration available.")
    }.getOrElse { CaptureSupport(false,it.message ?: "Camera check failed.") }
    private fun selected(config:RecordingConfig,sources:Set<CameraSource>):Map<CameraSource,Pair<CameraInput,CaptureQuality>> {
        if(sources.isEmpty())return emptyMap()
        val mode=if(sources.size==2)CameraMode.BOTH else if(CameraSource.FRONT in sources)CameraMode.FRONT else CameraMode.REAR
        val ids=capabilities.ids(mode)
        val effective=config.copy(cameraMode=mode)
        val result=sources.associateWith { source ->
            val key=if(source==CameraSource.FRONT)config.frontQuality else config.rearQuality
            val options=qualities(source,effective)
            val quality=CapturePolicy.choose(options,key)
            // Do not silently downgrade an explicitly chosen resolution in a concurrent pair.
            require(mode!=CameraMode.BOTH || options.any { it.key==key }) { "Selected dual-camera quality is unsupported. Lower one camera's quality and try again." }
            capabilities.input(ids.getValue(source),quality) to quality
        }
        check(capabilities.concurrent(result.mapValues { it.value.first })) { "Selected camera combination is unsupported." }
        return result
    }
    override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {
        check(control==null) { "Capture engine already started." }
        this.repository=repository;this.listener=listener;this.config=config
        desired=CapturePolicy.sources(config.cameraMode)
        originNs=System.nanoTime();originWall=System.currentTimeMillis();sessionId=java.util.UUID.randomUUID().toString()
        controlThread=HandlerThread("DuoRecord-camera").apply { start() };control=Handler(controlThread!!.looper)
        codecThread=HandlerThread("DuoRecord-video").apply { start() }
        control!!.post {
            try {
                val available=runCatching {
                    manager.registerTorchCallback(torchCallback,control);torchCallbackRegistered=true;torchId()!=null
                }.getOrDefault(false)
                listener.onTorchState(TorchStatus(available=available,message=if(!available)"Rear flashlight control is unavailable." else ""))
                if(!config.photoMode) { audio=AudioEncoder(originNs,emptyList(),continueWhenSilenced=config.continueVideoWithoutMic,microphone={ state -> control?.post { if(!ending)listener.onMicrophoneState(state) } },error=::fail);audio!!.start() };reconcile() }
            catch(t:Throwable) { fail(t.message ?: "Could not start recording.") }
        }
    }
    override fun update(config:RecordingConfig,sources:Set<CameraSource>) { control?.post {
        if(ending)return@post
        try {
            selected(config,sources);this.config=config;desired=sources;reconcile()
            // A repeated quality/target request may require no rebuild; acknowledge it as well.
            pipelines.values.forEach { listener.onCameraState(it.source,it.state) }
        }
        catch(t:Throwable) { listener.onError(t.message ?: "Unsupported camera settings.");listOf(CameraSource.FRONT,CameraSource.REAR).forEach { listener.onCameraState(it,pipelines[it]?.state ?: CameraCaptureStatus()) } }
    } }
    override fun setPreview(source:CameraSource,surface:Surface?) {
        if(surface==null)previews.remove(source) else previews[source]=surface
        control?.post { pipelines[source]?.encoder?.setPreview(surface) }
    }
    private fun publish(p:Pipeline,state:CameraCaptureStatus) { p.state=state;listener.onCameraState(p.source,state) }
    private fun reconcile() {
        if(finalizing || pipelines.values.any { it.closing })return
        try {
            val selection=if(ending)emptyMap() else selected(config,desired)
            val removed=pipelines.values.filter { p ->
                val target=selection[p.source]
                target==null || target.first!=p.input || target.second!=p.quality || p.config.fps!=config.fps || p.config.preferHevc!=config.preferHevc || p.config.photoMode!=config.photoMode || p.config.photoIntervalSeconds!=config.photoIntervalSeconds
            }
            if(removed.isNotEmpty()) { removed.forEach { close(it) };return }
            if(selection.isEmpty()) { finish();return }
            val missing=selection.filterKeys { it !in pipelines }
            if(missing.isNotEmpty()) {
                // Camera2 requires both devices open before configuring concurrent sessions.
                generation++;configuring=false
                pipelines.values.forEach { it.compositor?.pause();runCatching { it.session?.close() };it.session=null }
                missing.forEach { (source,pair) -> create(source,pair.first,pair.second) }
            }
            openReady()
            if(torchWanted && pipelines[CameraSource.REAR]==null)applyTorch()
        } catch(t:Throwable) { fail(t.message ?: "Camera setup failed.") }
    }
    private fun create(source:CameraSource,input:CameraInput,quality:CaptureQuality) {
        val actual=config.copy(width=quality.width,height=quality.height,videoBitRate=quality.bitRate)
        val mux=RollingMuxer(repository,actual,originWall,listener,source,sessionId)
        if(userPaused)mux.setPaused(true,(System.nanoTime()-originNs)/1000)
        val p=Pipeline(source,input,actual,quality,mux);pipelines[source]=p
        publish(p,p.state)
        audio?.add(mux)
        p.encoder=VideoEncoder(actual,Handler(codecThread!!.looper),mux,::fail) { message -> control?.post {
            if(pipelines[source]===p && !p.closing)publish(p,p.state.copy(previewMessage=message))
        } }
        p.encoder!!.setPreview(previews[source])
        p.compositor=DualCompositor(listOf(p.encoder!!.surface),listOf(input),actual,originNs,
            { surfaces -> control?.post { if(pipelines[source]===p && !p.closing && !ending) { p.surface=surfaces.first();openReady() } } },
            { control?.post {
                if(pipelines[source]===p && !p.closing && !ending) {
                    publish(p,p.state.copy(recording=true,pending=false,quality=quality.copy(bitRate=p.encoder!!.bitRate),startedMs=System.currentTimeMillis()))
                    listener.onStarted(if(actual.photoMode)"JPEG · interval photos" else p.encoder!!.mime)
                }
            } },::fail,if(actual.photoMode) { jpeg,time,index ->
                listener.onSegment(repository.savePhoto(jpeg,time,source,sessionId,index))
            } else null,preparePhoto={ ready -> control?.post {
                if(pipelines[source]===p && !p.closing && !ending && !userPaused) {
                    photoLighting.add(source);listener.onPhotoLight(source,true)
                    if(torchWanted)applyTorch()
                    // Allow lighting and exposure to settle, then require a fresh camera frame.
                    control?.postDelayed({ if(pipelines[source]===p && !p.closing && !ending && !userPaused)ready() else endPhotoLight(p) },500)
                }
            } },finishPhoto={ control?.post { if(pipelines[source]===p)endPhotoLight(p) } })
        p.compositor!!.setCapturePaused(userPaused);p.compositor!!.prepare()
        control!!.postDelayed({ if(pipelines[source]===p && !p.closing && !p.state.recording)fail("${source.name} camera startup timed out.") },20_000)
    }
    @SuppressLint("MissingPermission")
    private fun openReady() {
        if(ending || pipelines.isEmpty() || pipelines.values.any { it.closing || it.surface==null })return
        try {
            pipelines.values.filter { it.device==null && !it.opening }.forEach { p ->
                p.opening=true
                manager.openCamera(p.input.id,object:CameraDevice.StateCallback() {
                    override fun onOpened(camera:CameraDevice) {
                        if(ending || p.closing || pipelines[p.source]!==p) { camera.close();return }
                        p.device=camera;openReady()
                    }
                    override fun onDisconnected(camera:CameraDevice) { camera.close();if(!p.closing && !ending)fail("${p.source.name} camera disconnected.") }
                    override fun onError(camera:CameraDevice,error:Int) { camera.close();if(!p.closing && !ending)fail("${p.source.name} camera error: $error") }
                },control)
            }
            if(pipelines.values.all { it.device!=null } && pipelines.values.any { it.session==null } && !configuring)configureSessions()
        } catch(t:Throwable) { fail(t.message ?: "Could not open camera.") }
    }
    private fun configureSessions() {
        configuring=true
        val version=++generation
        val current=pipelines.values.toList()
        val executor=Executor { control?.post(it) }
        current.forEach { p ->
            p.compositor?.pause();runCatching { p.session?.close() };p.session=null
            p.device!!.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(p.surface!!)),executor,object:CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session:CameraCaptureSession) {
                        if(ending || p.closing || version!=generation) { session.close();return }
                        try {
                            p.session=session
                            try { session.setRepeatingRequest(recordRequest(p),null,control) }
                            catch(t:Exception) {
                                if(!torchWanted || p.source!=CameraSource.REAR)throw t
                                torchWanted=false;session.setRepeatingRequest(recordRequest(p),null,control)
                                listener.onTorchState(TorchStatus(available=true,message="Light could not be enabled: ${t.message}"))
                            }
                            if(p.source==CameraSource.REAR)listener.onTorchState(TorchStatus(torchActive(),torchId()!=null))
                            if(current.all { it.session!=null }) { configuring=false;current.forEach { it.compositor!!.start() };if(torchWanted && pipelines[CameraSource.REAR]==null)applyTorch() }
                        } catch(t:Throwable) { fail(t.message ?: "Camera configuration failed.") }
                    }
                    override fun onConfigureFailed(session:CameraCaptureSession) { session.close();if(version==generation && !ending)fail("Camera rejected this quality. Try a lower resolution.") }
                }))
        }
    }
    private fun close(p:Pipeline) {
        if(p.closing)return
        endPhotoLight(p)
        p.closing=true
        publish(p,p.state.copy(pending=true,previewMessage="Finishing…"))
        runCatching { p.session?.stopRepeating() };runCatching { p.session?.close() };p.session=null
        runCatching { p.device?.close() };p.device=null
        val drain={
            thread(name="DuoRecord-finalize-${p.source}") {
                var failure:Throwable?=null
                try { p.encoder?.finish() } catch(t:Throwable) { failure=t }
                audio?.remove(p.writer)
                try { p.writer.close() } catch(t:Throwable) { failure=t }
                p.encoder?.release()
                control?.post {
                    if(pipelines[p.source]===p)pipelines.remove(p.source)
                    listener.onCameraState(p.source,CameraCaptureStatus(pending=!ending && p.source in desired))
                    failure?.let { listener.onError(it.message ?: "Could not finish video.") }
                    reconcile()
                }
            }
            Unit
        }
        val renderer=p.compositor
        if(renderer!=null)renderer.release(drain) else drain()
    }
    private fun fail(message:String) { control?.post {
        if(ending)return@post
        listener.onError(message);stop(message)
    } }
    private fun endPhotoLight(p:Pipeline) {
        if(photoLighting.remove(p.source)) {
            listener.onPhotoLight(p.source,false)
            if(torchWanted && !ending)applyTorch()
        }
    }
    override fun split() { control?.post { pipelines.values.filter { !it.closing }.forEach { it.writer.split();it.encoder?.keyframe() } } }
    override fun setPaused(paused:Boolean) { control?.post {
        if(ending)return@post
        try {
            userPaused=paused
            pipelines.values.filter { !it.closing }.forEach {
                it.compositor?.setCapturePaused(paused);it.writer.setPaused(paused,(System.nanoTime()-originNs)/1000)
                if(paused)endPhotoLight(it) else it.encoder?.keyframe()
            }
            applyTorch();listener.onPauseChanged(paused)
        } catch(t:Throwable) { fail(t.message ?: "Could not pause cameras.") }
    } }
    override fun stop(reason:String) { control?.post {
        if(ending)return@post
        torchWanted=false
        torchId()?.let { id -> if(!ownsTorch(id))runCatching { manager.setTorchMode(id,false) } }
        ending=true;desired=emptySet();stoppingReason=reason;generation++;reconcile()
    } }
    private fun finish() {
        if(finalizing)return
        finalizing=true;ending=true
        thread(name="DuoRecord-audio-finalize") {
            runCatching { audio?.finish() };audio=null
            runCatching { repository.enforceRetention() }
            torchId()?.let { id -> runCatching { manager.setTorchMode(id,false) } }
            if(torchCallbackRegistered)runCatching { manager.unregisterTorchCallback(torchCallback) }
            listener.onTorchState(TorchStatus())
            previews.clear();codecThread?.quitSafely();controlThread?.quitSafely()
            listener.onStopped(stoppingReason)
        }
    }
}
