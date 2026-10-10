package com.duorecord.capture

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.*
import android.hardware.camera2.params.*
import android.hardware.display.*
import android.media.projection.*
import android.os.*
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import com.duorecord.contracts.*
import com.duorecord.domain.*
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.concurrent.thread

/** Original source textures feed independent files and a fourth, separately composed file. */
class ScreenCaptureEngine(private val context:Context,private val resultCode:Int,private val grant:Intent?):CaptureEngine {
    private val manager=context.getSystemService(CameraManager::class.java)
    private val capabilities=CameraCapabilities(manager)
    private var worker:HandlerThread?=null;private var queue:Handler?=null
    private var codecThread:HandlerThread?=null
    private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null
    private var renderer:MixedCompositor?=null;private var audio:ScreenAudioEncoder?=null
    private val encoders=linkedMapOf<CameraSource,VideoEncoder>();private val muxes=linkedMapOf<CameraSource,RollingMuxer>()
    private val devices=linkedMapOf<CameraSource,CameraDevice>();private val sessions=mutableListOf<CameraCaptureSession>()
    private val previews=java.util.concurrent.ConcurrentHashMap<CameraSource,Surface>()
    private lateinit var repo:MediaRepository;private lateinit var events:CaptureListener
    private lateinit var config:RecordingConfig
    private var quality=CaptureQuality(720,1280,4000000)
    private var originNs=0L;private var originWall=0L
    private var userPaused=false
    private var ending=false;private var rebuilding=false;private var draining=false;private var pendingFinish:String?=null;private var version=0
    private val callback=object:MediaProjection.Callback() { override fun onStop() { stop("Android ended screen sharing; new permission is required.") } }
    override fun support(config:RecordingConfig)=runCatching {
        CapturePolicy.validate(config)
        val b=context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val width=(b.width().takeIf { it>0 } ?: context.resources.displayMetrics.widthPixels).coerceAtLeast(2)
        val height=(b.height().takeIf { it>0 } ?: context.resources.displayMetrics.heightPixels).coerceAtLeast(2)
        val scale=minOf(1.0,1280.0/maxOf(width,height),720.0/minOf(width,height))
        quality=CaptureQuality(((width*scale).toInt()/2*2).coerceAtLeast(2),((height*scale).toInt()/2*2).coerceAtLeast(2),4000000)
        if(config.mixedMode)cameraInputs(config)
        CaptureSupport(true,if(config.mixedMode)"Mixed · separate source files + composite" else "Screen · ${quality.width}×${quality.height} · ${config.fps.coerceAtMost(30)} fps")
    }.getOrElse { CaptureSupport(false,it.message ?: "Capture unavailable.") }
    private fun cameraInputs(c:RecordingConfig):Map<CameraSource,Pair<CameraInput,CaptureQuality>> {
        val sources=c.mixedSources.filter { it==CameraSource.FRONT || it==CameraSource.REAR }.toSet()
        if(sources.isEmpty())return emptyMap()
        val mode=if(sources.size==2)CameraMode.BOTH else if(CameraSource.FRONT in sources)CameraMode.FRONT else CameraMode.REAR
        val ids=capabilities.ids(mode);val effective=c.copy(cameraMode=mode,preferHevc=false,fps=c.fps.coerceAtMost(30))
        return sources.associateWith { source ->
            val key=if(source==CameraSource.FRONT)c.frontQuality else c.rearQuality
            val available=capabilities.single(ids.getValue(source),effective)
            val bounded=available.filter { maxOf(it.width,it.height)<=1280 && minOf(it.width,it.height)<=720 }
            check(bounded.isNotEmpty()) { "No supported 720p-or-smaller mixed video size for ${source.name}." }
            val requested=available.firstOrNull { it.key==key }
            val q=if(requested in bounded)requested!! else bounded.maxBy { it.width.toLong()*it.height }
            capabilities.input(ids.getValue(source),q) to q
        }.also { check(capabilities.concurrent(it.mapValues { p -> p.value.first })) { "Selected cameras cannot run together." } }
    }
    override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {
        check(worker==null) { "Capture already started." };this.config=config;repo=repository;events=listener
        check(support(config).available) { support(config).detail }
        originNs=System.nanoTime();originWall=System.currentTimeMillis()
        worker=HandlerThread("AppRecord-mixed-control").also { it.start() };queue=Handler(worker!!.looper)
        codecThread=HandlerThread("AppRecord-mixed-codecs").also { it.start() }
        queue!!.post {
            try {
                if(config.requiresProjection) {
                    check(resultCode==Activity.RESULT_OK && grant!=null) { "New screen-sharing permission required." }
                    projection=context.getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode,grant) ?: error("Screen permission denied.")
                    projection!!.registerCallback(callback,queue)
                }
                build()
            } catch(t:Throwable) { fail(t.message ?: "Capture setup failed.") }
        }
    }
    private fun build() {
        if(ending)return
        rebuilding=false
        val v=++version;val c=config;val session=UUID.randomUUID().toString()
        val cameras=if(c.mixedMode)cameraInputs(c) else emptyMap()
        val inputs=cameras.map { it.key to it.value.first }.toMutableList()
        if(c.screenMode || c.mixedScreen && c.mixedMode)inputs+=CameraSource.SCREEN to CameraInput("screen",Size(quality.width,quality.height),0)
        val outputs=inputs.map { (source,_) -> source to (cameras[source]?.second ?: quality) }.toMutableList()
        if(c.mixedMode)outputs+=CameraSource.MIXED to quality
        val targets=outputs.map { (source,q) ->
            val actual=c.copy(width=q.width,height=q.height,videoBitRate=q.bitRate,preferHevc=false,fps=c.fps.coerceAtMost(30),photoMode=false,audioOnly=false)
            events.onCameraState(source,CameraCaptureStatus(pending=true,quality=q,previewMessage="Starting…"))
            val mux=RollingMuxer(repo,actual,originWall,events,source,session);muxes[source]=mux
            if(userPaused)mux.setPaused(true,(System.nanoTime()-originNs)/1000)
            val encoder=VideoEncoder(actual,Handler(codecThread!!.looper),mux,::fail) {};encoders[source]=encoder;encoder.setPreview(previews[source])
            MixedTarget(encoder.surface,source,q.width,q.height)
        }
        audio=ScreenAudioEncoder(projection,originNs,muxes.values.toList(),c,events::onMicrophoneState,::fail).also { it.start() }
        renderer=MixedCompositor(targets,inputs,c,originNs,{ surfaces -> queue?.post {
            if(ending || v!=version)return@post
            try {
                surfaces[CameraSource.SCREEN]?.let { surface ->
                    if(display==null)display=projection!!.createVirtualDisplay("AppRecord",quality.width,quality.height,context.resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,queue)
                    else display!!.surface=surface
                }
                openCameras(cameras,surfaces,v)
            } catch(t:Throwable) { fail(t.message ?: "Source initialization failed.") }
        } },{ queue?.post {
            if(!ending && v==version) {
                events.onStarted(if(c.mixedMode)"H.264 · raw + mixed" else "H.264 · screen")
                outputs.forEach { (source,q) -> events.onCameraState(source,CameraCaptureStatus(recording=true,quality=q,fps=c.fps.coerceAtMost(30),startedMs=originWall)) }
            }
        } },::fail).also { it.prepare() }
    }
    @SuppressLint("MissingPermission")
    private fun openCameras(cameras:Map<CameraSource,Pair<CameraInput,CaptureQuality>>,surfaces:Map<CameraSource,Surface>,v:Int) {
        if(cameras.isEmpty()) { renderer?.start();return }
        fun configure() {
            if(devices.size!=cameras.size || ending || v!=version)return
            var ready=0
            cameras.forEach { (source,input) ->
                val device=devices.getValue(source)
                device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,listOf(OutputConfiguration(surfaces.getValue(source))),Executor { queue?.post(it) },object:CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session:CameraCaptureSession) {
                        if(ending || v!=version) { session.close();return }
                        try {
                            sessions+=session
                            val ranges=manager.getCameraCharacteristics(input.first.id).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
                            val range=ranges.filter { it.contains(config.fps.coerceAtMost(30)) }.minByOrNull { it.upper-it.lower } ?: error("Unsupported camera FPS.")
                            session.setRepeatingRequest(device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(surfaces.getValue(source));set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range)
                                capabilities.configureOrientation(this,input.first.id)
                            }.build(),null,queue)
                            if(++ready==cameras.size)renderer?.start()
                        } catch(t:Throwable) { fail(t.message ?: "Camera configuration failed.") }
                    }
                    override fun onConfigureFailed(session:CameraCaptureSession) { session.close();if(v==version)fail("Camera rejected this mixed recording configuration.") }
                }))
            }
        }
        cameras.forEach { (source,pair) -> manager.openCamera(pair.first.id,object:CameraDevice.StateCallback() {
            override fun onOpened(camera:CameraDevice) { if(ending || v!=version)camera.close() else { devices[source]=camera;configure() } }
            override fun onDisconnected(camera:CameraDevice) { camera.close();if(!ending && v==version)fail("${source.name} camera disconnected.") }
            override fun onError(camera:CameraDevice,error:Int) { camera.close();if(!ending && v==version)fail("${source.name} camera error $error.") }
        },queue) }
    }
    private fun fail(message:String) { queue?.post { if(!ending) { events.onError(message);finish(message) } } }
    private fun drain(done:(String?)->Unit) {
        draining=true
        sessions.forEach { runCatching { it.stopRepeating() };runCatching { it.close() } };sessions.clear()
        devices.values.forEach { runCatching { it.close() } };devices.clear()
        runCatching { display?.surface=null }
        val oldEncoders=encoders.toMap();encoders.clear();val oldMuxes=muxes.toMap();muxes.clear();val oldAudio=audio;audio=null
        val finalize={ thread(name="AppRecord-mixed-finalize") {
            var failure:String?=null
            oldEncoders.values.forEach { runCatching { it.finish() }.onFailure { failure=it.message } }
            oldAudio?.finish()
            oldMuxes.values.forEach { runCatching { it.close() }.onFailure { failure=it.message } }
            oldEncoders.values.forEach { runCatching { it.release() } }
            queue?.post { draining=false;oldMuxes.keys.forEach { events.onCameraState(it,CameraCaptureStatus()) };done(failure);pendingFinish?.let { pendingFinish=null;endResources(it) } }
        };Unit }
        val old=renderer;renderer=null;if(old!=null)old.release(finalize) else finalize()
    }
    override fun stop(reason:String) { queue?.post { finish(reason) } }
    private fun finish(reason:String) {
        if(ending)return;ending=true;version++
        if(draining) { pendingFinish=reason;return }
        drain { error -> endResources(error?.let { "$reason · $it" } ?: reason) }
    }
    private fun endResources(reason:String) {
        runCatching { display?.release() };display=null
        runCatching { projection?.unregisterCallback(callback) };runCatching { projection?.stop() };projection=null
        events.onStopped(reason);codecThread?.quitSafely();worker?.quitSafely()
    }
    override fun split() { queue?.post { muxes.values.forEach { it.split() };encoders.values.forEach { it.keyframe() } } }
    override fun setPaused(paused:Boolean) { queue?.post {
        if(ending)return@post
        try {
            userPaused=paused;muxes.values.forEach { it.setPaused(paused,(System.nanoTime()-originNs)/1000) }
            if(!paused)encoders.values.forEach { it.keyframe() }
            events.onPauseChanged(paused)
        } catch(t:Throwable) { fail(t.message ?: "Could not pause screen capture.") }
    } }
    override fun update(config:RecordingConfig,sources:Set<CameraSource>) { queue?.post {
        if(ending)return@post
        if(rebuilding) { this.config=config;return@post }
        if(this.config.mixedMode && config.mixedSources!=this.config.mixedSources) {
            try {
                check(config.requiresProjection==this.config.requiresProjection || projection!=null) { "Stop and grant screen permission before adding screen or internal audio." }
                cameraInputs(config);rebuilding=true;version++;this.config=config
                drain { error -> if(error!=null)fail(error) else if(!ending)runCatching { build() }.onFailure { fail(it.message ?: "Could not update capture sources.") } }
            } catch(t:Throwable) { events.onError(t.message ?: "Unsupported source change.") }
        } else {
            if(config.screenInternalAudio && projection==null) { events.onError("Internal audio needs a new screen-sharing permission.");return@post }
            this.config=config;audio?.ambient=config.screenAudio;audio?.internal=config.screenInternalAudio;renderer?.update(config)
        }
    } }
    override fun setPreview(source:CameraSource,surface:Surface?) { if(surface==null)previews.remove(source) else previews[source]=surface;queue?.post { encoders[source]?.setPreview(surface) } }
    override fun qualities(source:CameraSource,config:RecordingConfig)=if(source in setOf(CameraSource.FRONT,CameraSource.REAR))capabilities.qualities(source,config) else emptyList()
}
