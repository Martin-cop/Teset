package com.duorecord.contracts

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.Surface
import com.duorecord.domain.*
import java.io.File

data class OutputFile(val clip: Clip, val descriptor: ParcelFileDescriptor)
interface MediaRepository {
    fun folderReady(): Boolean
    fun prepareStorage()
    fun storagePath(): String
    fun config(): RecordingConfig
    fun saveConfig(config: RecordingConfig)
    fun list(): List<Clip>
    fun get(id: String): Clip?
    fun createSegment(startMs: Long, camera: CameraSource = CameraSource.LEGACY, sessionId: String? = null, segmentIndex: Int = 0): OutputFile
    fun savePhoto(jpeg:ByteArray,startMs:Long,camera:CameraSource,sessionId:String,segmentIndex:Int):Clip = error("Photo storage unavailable.")
    fun completeSegment(id: String, durationMs: Long)
    fun damageSegment(id: String)
    fun protect(id: String, allowSavedRotation: Boolean = false): Clip
    fun importExtract(file: File, startMs: Long, endMs: Long, durationMs: Long, camera: CameraSource = CameraSource.LEGACY, allowSavedRotation: Boolean = false): Clip
    fun pin(ids: Set<String>): AutoCloseable
    fun delete(ids:Set<String>)
    fun enforceRetention()
    fun freeBytes(): Long
    fun reconcileRecent(activeRecording:Boolean=false) { reconcile(activeRecording) }
    fun reconcile(activeRecording:Boolean=false)
}
interface DataFactory { fun create(context: Context): MediaRepository }

data class CaptureSupport(val available: Boolean, val detail: String)
data class CameraCaptureStatus(val recording:Boolean=false,val pending:Boolean=false,val quality:CaptureQuality?=null,val fps:Int=0,val startedMs:Long=0,val previewMessage:String="")
data class TorchStatus(val enabled:Boolean=false,val available:Boolean=false,val pending:Boolean=false,val message:String="")
interface CaptureListener {
    fun onPauseChanged(paused:Boolean) {}
    fun onAudioLevel(levels:FloatArray) {}
    fun onAudioMeter(meter:AudioMeter) {}
    fun onMicrophoneState(state:MicrophoneState) {}
    fun onPhotoLight(source:CameraSource,active:Boolean) {}
    fun onTorchState(state:TorchStatus) {}
    fun onCameraState(source:CameraSource,state:CameraCaptureStatus) {}
    fun onStarted(codec: String)
    fun onSegment(clip: Clip)
    fun onStopped(reason: String)
    fun onError(message: String)
}
interface CaptureEngine {
    /** Finalize current files on pause; resume in new files in the same session. */
    fun setPaused(paused:Boolean) {}
    fun support(config: RecordingConfig = RecordingConfig()): CaptureSupport
    fun start(config: RecordingConfig, repository: MediaRepository, listener: CaptureListener)
    fun stop(reason: String = "Stopped")
    fun split()
    fun qualities(source:CameraSource,config:RecordingConfig):List<CaptureQuality>
    fun update(config:RecordingConfig,sources:Set<CameraSource>)
    fun setTorch(enabled:Boolean) {}
    fun setPreview(source:CameraSource,surface:Surface?)
}
interface CaptureFactory { fun create(context: Context): CaptureEngine }

interface PlayerComponent {
    val view: View
    fun open(clip: Clip, companion: Clip? = null)
    fun positionMs(): Long
    fun durationMs(): Long
    fun seekTo(positionMs: Long)
    fun setSpeed(speed:Float)
    fun speed():Float
    fun setViewingRotation(degrees:Int) {}
    fun isPlaying():Boolean = false
    fun audioMeter():AudioMeter = AudioMeter()
    fun play()
    fun pause()
    fun release()
}
interface PlayerFactory { fun create(context: Context, onError: (String) -> Unit): PlayerComponent }

interface VideoExporter {
    suspend fun export(plan: ExportPlan, output: File, progress: (Int) -> Unit)
    fun cancel()
}
interface EditorFactory { fun create(context: Context): VideoExporter }
