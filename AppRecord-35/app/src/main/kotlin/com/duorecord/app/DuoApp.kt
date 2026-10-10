package com.duorecord.app

import android.app.Application
import com.duorecord.contracts.*
import com.duorecord.domain.CameraSource
import com.duorecord.domain.Clip
import android.view.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, STARTING, RECORDING, STOPPING, PAUSED, PAUSING, USER_PAUSED }
data class RuntimeStatus(val phase:Phase=Phase.IDLE,val message:String="Ready",val startedMs:Long=0,val revision:Long=0,val codec:String="",val error:Boolean=false,val cameras:Map<CameraSource,CameraCaptureStatus> = emptyMap(),val microphone:com.duorecord.domain.MicrophoneState=com.duorecord.domain.MicrophoneState.OFF)
data class ExportStatus(val busy:Boolean=false,val progress:Int=0,val message:String="",val clipId:String?=null,val retryIntent:android.content.Intent?=null,val requiredSavedBytes:Long=0,val error:Boolean=false)

class DuoApp : Application() {
    override fun attachBaseContext(base:android.content.Context) { super.attachBaseContext(base.inEnglish()) }
    override fun onCreate() {
        super.onCreate()
        ScreenControls.migrate(this)
        scope.launch { status.collect { captureClock.transition(it.startedMs,it.phase) } }
        // Explicit AppRecord-3 request restores the supplied neon design once on upgrade.
        val appearance=getSharedPreferences("appearance_revision",0)
        if(appearance.getInt("neon",0)<3) {
            repository?.let { it.saveConfig(it.config().copy(simpleTheme=false)) }
            appearance.edit().putInt("neon",3).apply()
        }
        val archiveRevision=getSharedPreferences("archive_revision",0)
        if(archiveRevision.getInt("default_count",0)<30) { repository?.let { repo -> val c=repo.config();if(c.archiveInitialLimit==30)repo.saveConfig(c.copy(archiveInitialLimit=100)) };archiveRevision.edit().putInt("default_count",30).apply() }
        runCatching { diagnostics.recover() }
        runCatching { if(CaptureRecovery(this).read()!=null)CaptureRecovery(this).pause("Previous process ended; restoring saved session.") }
        val original=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            runCatching { diagnostics.crash(error) }
            runCatching { getSharedPreferences("runtime",0).edit().putString("lastStop","Crash: ${error.javaClass.simpleName}: ${error.message?.take(200)}").putBoolean("unfinished",false).putLong("stopTime",System.currentTimeMillis()).commit() }
            original?.uncaughtException(thread,error)
        }
    }
    internal val diagnostics by lazy { RecordingDiagnostics(this) }
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    internal val captureClock by lazy { CaptureClock(this) }
    val status=MutableStateFlow(RuntimeStatus())
    val audioLevels=MutableStateFlow(FloatArray(12))
    val uiVisible=MutableStateFlow(false)
    val audioMeter=MutableStateFlow(com.duorecord.domain.AudioMeter())
    val latestCapture=MutableStateFlow<Clip?>(null)
    val torch=MutableStateFlow(TorchStatus())
    val photoLighting=MutableStateFlow<Set<CameraSource>>(emptySet())
    val photoCaptured=kotlinx.coroutines.flow.MutableSharedFlow<CameraSource>(extraBufferCapacity=16)
    var screenLightWanted:Boolean
        get()=getSharedPreferences("light",0).getBoolean("screen",false)
        set(value) { getSharedPreferences("light",0).edit().putBoolean("screen",value).apply() }
    var lightForNextRecording:Boolean
        get()=getSharedPreferences("light",0).getBoolean("enabled",false)
        set(value) { getSharedPreferences("light",0).edit().putBoolean("enabled",value).apply() }
    val export=MutableStateFlow(ExportStatus())
    var activeEngine:CaptureEngine?=null
    @Volatile internal var batteryHeatAlerted=false
    @Volatile internal var pendingTemperaturePage=false
    @Synchronized internal fun observeBatteryTemperature(temp:Double?) {
        temp ?: return
        if(temp<=42.0) { batteryHeatAlerted=false;pendingTemperaturePage=false }
        if(temp<45.0 || batteryHeatAlerted)return
        batteryHeatAlerted=true;pendingTemperaturePage=true
        scope.launch { runCatching {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,60).also { tone ->
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,180)
                launch { delay(250);tone.release() }
            }
        } }
    }
    private val previewSurfaces=mutableMapOf<CameraSource,Surface>()
    private val previewOwners=mutableMapOf<CameraSource,Any>()
    fun preview(source:CameraSource,owner:Any,surface:Surface?) {
        if(surface==null) {
            if(previewOwners[source]!==owner)return
            previewSurfaces.remove(source);previewOwners.remove(source)
        } else { previewSurfaces[source]=surface;previewOwners[source]=owner }
        activeEngine?.setPreview(source,surface)
    }
    fun connectPreviews() { previewSurfaces.forEach { (source,surface) -> activeEngine?.setPreview(source,surface) } }
    val repository:MediaRepository? by lazy { load<DataFactory>("com.duorecord.data.StorageFeature")?.create(this) }
    val capture:CaptureFactory? by lazy { load("com.duorecord.capture.CaptureFeature") }
    val player:PlayerFactory? by lazy { load("com.duorecord.player.PlaybackFeature") }
    val editor:EditorFactory? by lazy { load("com.duorecord.editor.EditingFeature") }
    private inline fun <reified T> load(name:String):T?=runCatching { Class.forName(name).getDeclaredConstructor().newInstance() as T }.getOrNull()
    fun changed() { status.update { it.copy(revision=it.revision+1) } }
}
