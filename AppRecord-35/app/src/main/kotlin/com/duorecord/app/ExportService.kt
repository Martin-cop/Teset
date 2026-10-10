package com.duorecord.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import com.duorecord.contracts.VideoExporter
import com.duorecord.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class ExportService : Service() {
    companion object { const val START="duorecord.EXPORT";const val CANCEL="duorecord.CANCEL_EXPORT" }
    private val app get()=application as DuoApp
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null
    private var exporter:VideoExporter?=null
    private var wake:PowerManager.WakeLock?=null
    private var monitor:Job?=null
    private var overheated=false
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL) { job?.cancel();exporter?.cancel();if(job==null)stopSelf();return START_NOT_STICKY }
        if(intent?.action!=START || job?.isActive==true)return START_NOT_STICKY
        val type=if(Build.VERSION.SDK_INT>=35)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        startForeground(Notifications.EXPORT,Notifications.export(this,"Preparing export"),type)
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DuoRecord:export").apply { acquire(60*60*1000L) }
        app.export.value=ExportStatus(true,0,"Preparing export…")
        overheated=false
        job=scope.launch {
            var hold:AutoCloseable?=null
            val temps=mutableListOf<File>()
            val completed=mutableListOf<Clip>()
            val outputPins=mutableListOf<AutoCloseable>()
            var pendingCamera:CameraSource?=null
            try {
                check(getSystemService(PowerManager::class.java).currentThermalStatus<PowerManager.THERMAL_STATUS_SEVERE) { "Phone too hot to export." }
                val repo=app.repository ?: error("Storage module unavailable.")
                val plans=withContext(Dispatchers.IO) {
                    val id=intent.getStringExtra("id")
                    val result=if(id!=null) {
                        val clip=repo.get(id) ?: error("Video not found.")
                        val from=intent.getLongExtra("from",0);val to=intent.getLongExtra("to",clip.durationMs)
                        require(clip.status==ClipStatus.READY && from>=0 && to>from && to<=clip.durationMs) { "Invalid time range." }
                        listOf(ExportPlan(clip.startMs+from,clip.startMs+to,listOf(Slice(clip,from,to))))
                    } else {
                        val choice=intent.getStringExtra("camera") ?: "REAR"
                        val sources=if(choice=="BOTH")listOf(CameraSource.REAR,CameraSource.FRONT) else listOf(CameraSource.valueOf(choice))
                        val clips=repo.list()
                        sources.map { source -> TimelinePlanner.plan(clips.filter { it.camera==source },intent.getLongExtra("start",0),intent.getLongExtra("end",0)) }
                    }
                    hold=repo.pin(result.flatMap { it.slices }.map { it.clip.id }.toSet())
                    val estimated=result.sumOf { it.mediaDurationMs }*3_000_000L/8000L
                    require(repo.freeBytes()>estimated*2+256L*1024*1024) { "Not enough space to export." }
                    result
                }
                for((index,plan) in plans.withIndex()) {
                    pendingCamera=plan.slices.first().clip.camera
                    val temp=File(cacheDir,"export-${UUID.randomUUID()}.mp4");temps+=temp
                    exporter=app.editor?.create(this@ExportService) ?: error("Export module unavailable.")
                    exporter!!.export(plan,temp) { progress ->
                        val total=(index*100+progress)/plans.size
                        app.export.value=ExportStatus(true,total,"Exporting: $total%")
                        getSystemService(NotificationManager::class.java).notify(Notifications.EXPORT,Notifications.export(this@ExportService,"Exporting video",total))
                    }
                    ensureActive()
                    val saved=withContext(Dispatchers.IO) { repo.importExtract(temp,plan.startMs,plan.endMs,plan.mediaDurationMs,plan.slices.first().clip.camera,intent.getBooleanExtra("rotateSaved",false)) }
                    outputPins+=withContext(Dispatchers.IO) { repo.pin(setOf(saved.id)) }
                    completed+=saved;temp.delete();app.changed()
                }
                val message=if(completed.size>1)"Rear and front exports saved separately." else "Export saved."
                app.export.value=ExportStatus(false,100,message,completed.first().id)
            } catch(t:Throwable) {
                if(t !is CancellationException)app.diagnostics.add("Export",t.stackTraceToString())
                if(t is SavedQuotaExceeded) {
                    val retry=Intent(intent).apply { if(getStringExtra("id")==null && completed.isNotEmpty())putExtra("camera",pendingCamera?.name ?: "REAR") }
                    app.export.value=ExportStatus(false,0,t.message ?: "Saved space is full.",retryIntent=retry,requiredSavedBytes=t.requiredBytes,error=true)
                } else app.export.value=ExportStatus(false,0,if(overheated)"Export stopped: phone too hot. Originals kept." else if(t is CancellationException)"Export canceled. Originals kept." else (t.message ?: "Export failed.") + if(completed.isNotEmpty())" ${completed.size} completed export(s) kept." else "",error=overheated || t !is CancellationException)
            } finally {
                monitor?.cancel();hold?.close();outputPins.forEach { it.close() };temps.forEach { it.delete() };exporter=null
                wake?.let { if(it.isHeld)it.release() };wake=null
                stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
            }
        }
        monitor=scope.launch {
            while(job?.isActive==true) {
                delay(3000)
                if(getSystemService(PowerManager::class.java).currentThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE) {
                    overheated=true;job?.cancel();break
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int) { job?.cancel();exporter?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy() { monitor?.cancel();job?.cancel();exporter?.cancel();wake?.let { if(it.isHeld)it.release() };scope.cancel();super.onDestroy() }
}
