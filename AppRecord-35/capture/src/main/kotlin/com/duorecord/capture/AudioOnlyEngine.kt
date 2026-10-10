package com.duorecord.capture

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import com.duorecord.contracts.*
import com.duorecord.domain.*
import java.util.UUID
import java.util.concurrent.Executors

/** Audio mode never opens a camera or requests a camera foreground-service type. */
internal class AudioOnlyEngine:CaptureEngine {
    private var control:java.util.concurrent.ExecutorService?=null
    private var encoder:AudioEncoder?=null
    private var writer:RollingMuxer?=null
    private var listener:CaptureListener?=null
    private var ended=false
    @Volatile private var userPaused=false
    private var originNs=0L
    override fun support(config:RecordingConfig)=runCatching {
        CapturePolicy.validate(config)
        check(MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_AUDIO_AAC,true) } }) { "AAC audio encoder unavailable." }
        CaptureSupport(true,"AAC · 48 kHz · mono · 64 kbps")
    }.getOrElse { CaptureSupport(false,it.message ?: "Audio unavailable.") }
    override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {
        check(control==null) { "Audio capture already started." }
        this.listener=listener
        ended=false
        val queue=Executors.newSingleThreadExecutor { task -> Thread(task,"AppRecord-audio-control") }
        control=queue
        val wall=System.currentTimeMillis();val mono=System.nanoTime();originNs=mono
        queue.execute {
            try {
                val mux=RollingMuxer(repository,config.copy(audioOnly=true,photoMode=false),wall,listener,CameraSource.AUDIO,UUID.randomUUID().toString())
                writer=mux
                listener.onCameraState(CameraSource.AUDIO,CameraCaptureStatus(pending=true,previewMessage="Starting microphone…"))
                encoder=AudioEncoder(mono,listOf(mux),started={
                    // Serialize actual microphone-start with Stop and error callbacks.
                    runCatching { queue.execute {
                        if(!ended) {
                            listener.onStarted("AAC · 48 kHz · mono · 64 kbps")
                            listener.onCameraState(CameraSource.AUDIO,CameraCaptureStatus(recording=true,startedMs=wall))
                        }
                    } }
                },microphone={ state -> runCatching { queue.execute { if(!ended)listener.onMicrophoneState(state) } } },levels={ listener.onAudioLevel(if(userPaused)FloatArray(12) else it) },meter={ listener.onAudioMeter(if(userPaused)AudioMeter() else it) },error={ reason -> fail(reason) }).also { it.start() }
            } catch(t:Throwable) { fail(t.message ?: "Could not start microphone.") }
        }
    }
    private fun fail(reason:String) {
        val queue=control ?: return
        runCatching { queue.execute { if(!ended) { listener?.onError(reason);finish(reason) } } }
    }
    override fun stop(reason:String) {
        val queue=control ?: return
        runCatching { queue.execute { finish(reason) } }
    }
    private fun finish(reason:String) {
        if(ended)return
        ended=true
        var result=reason
        try { encoder?.finish() } catch(t:Throwable) { result=t.message ?: "Could not finish audio.";listener?.onError(result) }
        encoder=null
        try { writer?.close() } catch(t:Throwable) { result=t.message ?: "Incomplete audio file.";listener?.onError(result) }
        writer=null
        listener?.onCameraState(CameraSource.AUDIO,CameraCaptureStatus())
        listener?.onStopped(result)
        control?.shutdown()
    }
    override fun split() { runCatching { control?.execute { if(!ended)writer?.split() } } }
    override fun setPaused(paused:Boolean) { control?.execute {
        if(!ended)try { userPaused=paused;writer?.setPaused(paused,(System.nanoTime()-originNs)/1000);listener?.onPauseChanged(paused) }
        catch(t:Throwable) { fail(t.message ?: "Could not pause audio.") }
    } }
    override fun update(config:RecordingConfig,sources:Set<CameraSource>) { if(CameraSource.AUDIO !in sources)stop() }
    override fun qualities(source:CameraSource,config:RecordingConfig)=emptyList<CaptureQuality>()
    override fun setPreview(source:CameraSource,surface:Surface?)=Unit
}

/** Keep camera capability discovery out of audio-only starts, including permission checks. */
internal class RoutingCaptureEngine(private val context:Context):CaptureEngine {
    private var delegate:CaptureEngine?=null
    private val previews=mutableMapOf<CameraSource,Surface?>()
    override fun support(config:RecordingConfig)=if(config.audioOnly)AudioOnlyEngine().support(config) else NativeDualEngine(context).support(config)
    override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {
        check(delegate==null)
        val engine=if(config.audioOnly)AudioOnlyEngine() else NativeDualEngine(context)
        delegate=engine
        previews.forEach { (source,surface) -> engine.setPreview(source,surface) }
        engine.start(config,repository,listener)
    }
    override fun stop(reason:String) { delegate?.stop(reason) }
    override fun setPaused(paused:Boolean) { delegate?.setPaused(paused) }
    override fun split() { delegate?.split() }
    override fun qualities(source:CameraSource,config:RecordingConfig)=if(config.audioOnly)emptyList() else NativeDualEngine(context).qualities(source,config)
    override fun update(config:RecordingConfig,sources:Set<CameraSource>) { delegate?.update(config,sources) }
    override fun setTorch(enabled:Boolean) { delegate?.setTorch(enabled) }
    override fun setPreview(source:CameraSource,surface:Surface?) { previews[source]=surface;delegate?.setPreview(source,surface) }
}
