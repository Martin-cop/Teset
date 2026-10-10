package com.duorecord.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.duorecord.contracts.*
import com.duorecord.domain.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StorageFeature : DataFactory {
    override fun create(context:Context):MediaRepository=FolderRepository(context.applicationContext)
}

/** App-owned MediaStore files need no storage permission or directory picker. */
internal class FolderRepository(private val context:Context):MediaRepository {
    companion object {
        const val RECORDINGS="Movies/Record/"
        const val SELECTED="Movies/Record/Selected/"
        const val AUDIO="Music/Record/"
        const val SAVED_AUDIO="Music/Record/Selected/"
        const val PHOTOS="Pictures/Record/"
        const val SAVED_PHOTOS="Pictures/Record/Selected/"
    }
    private val db=ClipDatabase(context)
    private val prefs=context.getSharedPreferences("duorecord",Context.MODE_PRIVATE)
    init {
        if(prefs.getInt("segmentDefaultRevision",0)<7) {
            val update=prefs.edit().putInt("segmentDefaultRevision",7)
            if(prefs.getInt("segment",120)==120)update.putInt("segment",1800)
            update.commit()
        }
    }
    private val resolver=context.contentResolver
    private val collection=MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val audioCollection=MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private fun path(clip:Clip,saved:Boolean)=when { clip.isPhoto -> if(saved)SAVED_PHOTOS else PHOTOS;clip.isAudio -> if(saved)SAVED_AUDIO else AUDIO;else -> if(saved)SELECTED else RECORDINGS }
    private val pins=mutableMapOf<String,Int>()
    private val activeWrites=mutableSetOf<String>()
    private enum class Presence { PRESENT, MISSING, UNKNOWN }
    private fun presence(uri:Uri):Presence=try {
        resolver.openFileDescriptor(uri,"r")?.use { Presence.PRESENT } ?: Presence.UNKNOWN
    } catch(_:java.io.FileNotFoundException) { if(folderReady())Presence.MISSING else Presence.UNKNOWN }
      catch(_:Exception) { Presence.UNKNOWN }
    private fun uniqueTitle(time:Long,camera:CameraSource,extension:String="mp4"):String {
        val titles=mutableSetOf<String>()
        val collection=if(camera==CameraSource.AUDIO)audioCollection else if(extension=="jpg")MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else collection
        resolver.query(collection,arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),
            "${MediaStore.Video.Media.RELATIVE_PATH} IN (?,?) AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME}=?",
            arrayOf(if(camera==CameraSource.AUDIO)AUDIO else if(extension=="jpg")PHOTOS else RECORDINGS,if(camera==CameraSource.AUDIO)SAVED_AUDIO else if(extension=="jpg")SAVED_PHOTOS else SELECTED,context.packageName),null)?.use { cursor ->
            while(cursor.moveToNext()) {
                cursor.getString(1)?.let(titles::add)
            }
        }
        // Include pending recordings in the database even if a provider hides pending rows.
        db.all().forEach { titles+=it.name }
        return VideoNames.unique(time,camera,titles,extension)
    }
    private fun isMedia(uri:Uri)=uri.authority==MediaStore.AUTHORITY
    private fun name(uri:Uri,fallback:String)=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
        if(it.moveToFirst())it.getString(0) else fallback
    } ?: fallback
    private fun bytes(uri:Uri):Long=resolver.openFileDescriptor(uri,"r")?.use { it.statSize.coerceAtLeast(0) } ?: 0
    private fun exists(uri:Uri):Boolean=runCatching {
        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { it.moveToFirst() }==true
    }.getOrDefault(false)
    private fun remove(uri:Uri):Boolean=runCatching {
        if(isMedia(uri))resolver.delete(uri,null,null)>0 else DocumentsContract.deleteDocument(resolver,uri)
    }.getOrDefault(false)
    private fun publish(uri:Uri) {
        if(isMedia(uri))check(resolver.update(uri,ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING,0) },null,null)>0) {
            "Could not finish saving the video."
        }
    }
    private fun create(name:String,startMs:Long,protected:Boolean,audio:Boolean=false):Uri {
        prepareStorage()
        // MediaStore creates Record and Selected automatically with the first file.
        return resolver.insert(if(audio)audioCollection else collection,ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,name)
            put(MediaStore.Video.Media.MIME_TYPE,if(audio)"audio/aac" else "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,if(audio) { if(protected)SAVED_AUDIO else AUDIO } else if(protected)SELECTED else RECORDINGS)
            if(audio)put(MediaStore.Audio.Media.DATE_ADDED,startMs/1000) else put(MediaStore.Video.Media.DATE_TAKEN,startMs)
            put(MediaStore.Video.Media.IS_PENDING,1)
        }) ?: error("Could not create the video.")
    }
    override fun folderReady()=Environment.getExternalStorageState()==Environment.MEDIA_MOUNTED
    @Synchronized override fun prepareStorage() { check(folderReady()) { "Storage unavailable." } }
    override fun storagePath()=when { config().audioOnly -> "Music/Record";config().photoMode -> "Pictures/Record";else -> "Movies/Record" }
    override fun config()=RecordingConfig(
        screenInternalAudio=prefs.getBoolean("screenInternal",false),mixedMode=prefs.getBoolean("mixedMode",false),
        mixedFront=prefs.getBoolean("mixedFront",true),mixedRear=prefs.getBoolean("mixedRear",false),mixedScreen=prefs.getBoolean("mixedScreen",true),
        mixedBackground=runCatching { CameraSource.valueOf(prefs.getString("mixedBackground","SCREEN")!!) }.getOrDefault(CameraSource.SCREEN),mixedLayout=prefs.getString("mixedLayout","")!!,
        archiveRefreshSeconds=prefs.getInt("archiveRefresh",2),archiveInitialLimit=prefs.getInt("archiveCount",100),
        continueVideoWithoutMic=prefs.getBoolean("continueVideoWithoutMic",true),screenMode=prefs.getBoolean("screenMode",false),screenAudio=prefs.getBoolean("screenAudio",false),audioOnly=prefs.getBoolean("audioOnly",false),photoMode=prefs.getBoolean("photoMode",false),photoIntervalSeconds=prefs.getInt("photoInterval",10),
        segmentSeconds=prefs.getInt("segment",1800),quotaBytes=prefs.getLong("quota",8L*1024*1024*1024),
        fps=prefs.getInt("fps",15),preferHevc=prefs.getBoolean("hevc",true),
        maxRecordingMinutes=prefs.getInt("maxMinutes",120),reserveBytes=prefs.getLong("reserve",1073741824L),stopBatteryPercent=prefs.getInt("battery",10),
        frontQuality=prefs.getString("frontQuality","480x640")!!,rearQuality=prefs.getString("rearQuality","480x640")!!,simpleTheme=prefs.getBoolean("simpleTheme",false),
        savedQuotaBytes=prefs.getLong("savedQuota",2L*1024*1024*1024),
        cameraMode=runCatching { CameraMode.valueOf(prefs.getString("cameraMode","BOTH")!!) }.getOrDefault(CameraMode.BOTH)
    )
    @Synchronized override fun saveConfig(config:RecordingConfig) {
        CapturePolicy.validate(config)
        require(config.photoIntervalSeconds in 1..86400) { "Photo interval must be 1–86400 seconds." }
        prefs.edit().putBoolean("screenInternal",config.screenInternalAudio).putBoolean("mixedMode",config.mixedMode)
            .putBoolean("mixedFront",config.mixedFront).putBoolean("mixedRear",config.mixedRear).putBoolean("mixedScreen",config.mixedScreen)
            .putString("mixedBackground",config.mixedBackground.name).putString("mixedLayout",config.mixedLayout)
            .putInt("archiveRefresh",config.archiveRefreshSeconds).putInt("archiveCount",config.archiveInitialLimit).putBoolean("screenAudio",config.screenAudio).putBoolean("screenMode",config.screenMode).putBoolean("continueVideoWithoutMic",config.continueVideoWithoutMic).putBoolean("audioOnly",config.audioOnly).putBoolean("photoMode",config.photoMode).putInt("photoInterval",config.photoIntervalSeconds).putInt("segment",config.segmentSeconds).putLong("quota",config.quotaBytes)
            .putInt("maxMinutes",config.maxRecordingMinutes).putLong("reserve",config.reserveBytes).putInt("battery",config.stopBatteryPercent)
            .putString("frontQuality",config.frontQuality).putString("rearQuality",config.rearQuality).putBoolean("simpleTheme",config.simpleTheme)
            .putLong("savedQuota",config.savedQuotaBytes).putInt("fps",config.fps).putBoolean("hevc",config.preferHevc).putString("cameraMode",config.cameraMode.name).commit().also { check(it) { "Could not save settings." } }
    }
    override fun list():List<Clip> = db.all()
    override fun get(id:String):Clip?=db.find(id)
    @Synchronized override fun createSegment(startMs:Long,camera:CameraSource,sessionId:String?,segmentIndex:Int):OutputFile {
        enforceRetention()
        require(freeBytes()>=config().reserveBytes) { "Storage full. Saved videos are protected." }
        val title=uniqueTitle(startMs,camera,if(camera==CameraSource.AUDIO)"aac" else "mp4")
        val uri=create(title,startMs,false,camera==CameraSource.AUDIO)
        try {
            val pfd=resolver.openFileDescriptor(uri,"rw") ?: error("Could not open the recording.")
            val clip=Clip(UUID.randomUUID().toString(),uri.toString(),name(uri,title),startMs,0,0,ClipKind.ROLLING,ClipStatus.WRITING,camera=camera,sessionId=sessionId,segmentIndex=segmentIndex)
            try { db.put(clip);activeWrites+=clip.id } catch(t:Throwable) { pfd.close();throw t }
            return OutputFile(clip,pfd)
        } catch(t:Throwable) { remove(uri);throw t }
    }
    @Synchronized override fun savePhoto(jpeg:ByteArray,startMs:Long,camera:CameraSource,sessionId:String,segmentIndex:Int):Clip {
        require(jpeg.size>4 && jpeg[0]==0xff.toByte() && jpeg[1]==0xd8.toByte() && jpeg[jpeg.lastIndex-1]==0xff.toByte() && jpeg.last()==0xd9.toByte()) { "Invalid JPEG photo." }
        enforceRetention();prepareStorage()
        require(freeBytes()>=config().reserveBytes+jpeg.size) { "Not enough space for a photo." }
        val title=uniqueTitle(startMs,camera,"jpg")
        val uri=resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,title);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,PHOTOS);put(MediaStore.Images.Media.DATE_TAKEN,startMs)
            put(MediaStore.Images.Media.IS_PENDING,1)
        }) ?: error("Could not create photo.")
        try {
            resolver.openOutputStream(uri,"w")!!.use { it.write(jpeg) }
            check(bytes(uri)==jpeg.size.toLong()) { "Incomplete photo." };publish(uri)
            val clip=Clip(UUID.randomUUID().toString(),uri.toString(),title,startMs,1,jpeg.size.toLong(),ClipKind.ROLLING,ClipStatus.READY,camera=camera,sessionId=sessionId,segmentIndex=segmentIndex)
            db.put(clip);return clip
        } catch(t:Throwable) { remove(uri);throw t }
    }
    @Synchronized override fun completeSegment(id:String,durationMs:Long) {
        activeWrites.remove(id)
        val clip=get(id) ?: return
        val uri=Uri.parse(clip.uri);val size=bytes(uri)
        publish(uri)
        db.put(clip.copy(durationMs=durationMs.coerceAtLeast(1),bytes=size,status=ClipStatus.READY))
    }
    @Synchronized override fun damageSegment(id:String) {
        activeWrites.remove(id)
        val clip=get(id) ?: return
        db.put(clip.copy(bytes=runCatching { bytes(Uri.parse(clip.uri)) }.getOrDefault(0),status=ClipStatus.DAMAGED))
    }
    @Synchronized override fun pin(ids:Set<String>):AutoCloseable {
        val available=db.all().associateBy { it.id }
        require(ids.all { id -> available[id]?.let { it.status==ClipStatus.READY && exists(Uri.parse(it.uri)) }==true }) {
            "A selected video is no longer available."
        }
        ids.forEach { pins[it]=(pins[it] ?: 0)+1 }
        var released=false
        return AutoCloseable { synchronized(this) {
            if(!released) { ids.forEach { id -> val n=(pins[id] ?: 1)-1;if(n<=0)pins.remove(id) else pins[id]=n };released=true }
        } }
    }
    /** Selection is a reversible tag on the original file, never an extra copy. */
    @Synchronized override fun protect(id:String,allowSavedRotation:Boolean):Clip {
        val clip=get(id) ?: error("Video not found.")
        require(clip.status==ClipStatus.READY) { "This video is still recording." }
        val uri=Uri.parse(clip.uri)
        if(clip.protected) {
            // Older versions stored copies/exports under Selected. Return deselected files to rolling storage.
            if(isMedia(uri))check(resolver.update(uri,ContentValues().apply {
                put(MediaStore.Video.Media.RELATIVE_PATH,path(clip,false))
                put(MediaStore.Video.Media.DISPLAY_NAME,clip.name)
            },null,null)>0) { "Could not remove the saved tag." }
            val result=clip.copy(kind=ClipKind.ROLLING,name=name(uri,clip.name),sourceId=null)
            db.put(result);return result
        }
        val quota=config().savedQuotaBytes
        val used=db.all().filter { it.protected }.sumOf { it.bytes }
        if(used+clip.bytes>quota) {
            if(!allowSavedRotation)throw SavedQuotaExceeded(used+clip.bytes,quota)
            val doomed=SavedRetentionPolicy.candidates(db.all(),clip.bytes,quota,pins.keys)
            for(old in doomed) {
                val oldUri=Uri.parse(old.uri)
                check(!exists(oldUri) || remove(oldUri)) { "Could not remove an old saved video. Increase the saved limit." }
                db.delete(old.id)
            }
        }
        if(isMedia(uri))check(resolver.update(uri,ContentValues().apply {
            put(MediaStore.Video.Media.RELATIVE_PATH,path(clip,true))
            put(MediaStore.Video.Media.DISPLAY_NAME,clip.name)
        },null,null)>0) { "Could not move the protected file to Selected." }
        val result=clip.copy(kind=ClipKind.SELECTED,name=name(uri,clip.name),sourceId=null)
        db.put(result);return result
    }
    @Synchronized override fun importExtract(file:File,startMs:Long,endMs:Long,durationMs:Long,camera:CameraSource,allowSavedRotation:Boolean):Clip {
        require(file.exists() && file.length()>0) { "Export is empty." }
        val savedUsed=db.all().filter { it.protected }.sumOf { it.bytes }
        if(savedUsed+file.length()>config().savedQuotaBytes) {
            if(!allowSavedRotation)throw SavedQuotaExceeded(savedUsed+file.length(),config().savedQuotaBytes)
            SavedRetentionPolicy.candidates(db.all(),file.length(),config().savedQuotaBytes,pins.keys).forEach { old ->
                val oldUri=Uri.parse(old.uri)
                check(!exists(oldUri) || remove(oldUri)) { "Could not remove an old saved video. Increase the saved limit." }
                db.delete(old.id)
            }
        }
        require(freeBytes()>file.length()+64L*1024*1024) { "Not enough space to save the export." }
        val title=uniqueTitle(startMs,camera)
        val uri=create(title,startMs,true)
        try {
            val count=file.inputStream().use { input -> resolver.openOutputStream(uri,"w")!!.use { input.copyTo(it,256*1024) } }
            check(count==file.length() && bytes(uri)==count) { "Could not save the complete export." }
            publish(uri)
            val clip=Clip(UUID.randomUUID().toString(),uri.toString(),name=name(uri,title),startMs,durationMs,count,ClipKind.EXTRACT,ClipStatus.READY,camera=camera)
            db.put(clip);return clip
        } catch(t:Throwable) { remove(uri);throw t }
    }
    @Synchronized override fun delete(ids:Set<String>) {
        val files=db.all().filter { it.id in ids }
        require(files.none { it.status==ClipStatus.WRITING || it.id in pins }) { "A selected file is recording or in use. Close playback/export before deleting." }
        files.forEach { clip ->
            val uri=Uri.parse(clip.uri)
            check(!exists(uri) || remove(uri)) { "Could not delete ${clip.name}." }
            db.delete(clip.id)
        }
    }
    override fun freeBytes():Long=StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
    @Synchronized override fun enforceRetention() {
        val c=config()
        val doomed=RetentionPolicy.candidates(db.all(),c.quotaBytes,freeBytes(),c.reserveBytes,pins.keys)
        doomed.forEach { clip ->
            val uri=Uri.parse(clip.uri)
            if(!exists(uri)) { db.delete(clip.id);return@forEach }
            if(isMedia(uri)) {
                val path=resolver.query(uri,arrayOf(MediaStore.Video.Media.RELATIVE_PATH),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null }
                // A video moved out of the rolling folder must never be auto-deleted.
                if(path !in setOf(RECORDINGS,PHOTOS,AUDIO))return@forEach
            }
            if(remove(uri))db.delete(clip.id)
        }
    }
    private data class Found(val uri:Uri,val name:String,val kind:ClipKind,val duration:Long,val size:Long,val pending:Boolean,val path:String?=null)
    private fun completePhoto(uri:Uri):Boolean=runCatching {
        val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        resolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
        if(options.outWidth<=0 || options.outHeight<=0)return@runCatching false
        resolver.openInputStream(uri)!!.use { input ->
            val buffer=ByteArray(8192);var before=-1;var last=-1
            while(true) { val n=input.read(buffer);if(n<0)break;if(n==0)continue
                before=if(n>1)buffer[n-2].toInt() and 255 else last;last=buffer[n-1].toInt() and 255
            }
            before==255 && last==217
        }
    }.getOrDefault(false)
    private fun scanMedia(images:Boolean=false,audio:Boolean=false):List<Found> {
        val collection=if(audio)audioCollection else if(images)MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else collection
        val args=Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"${MediaStore.Video.Media.RELATIVE_PATH} IN (?,?) AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME}=?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arrayOf(if(audio)AUDIO else if(images)PHOTOS else RECORDINGS,if(audio)SAVED_AUDIO else if(images)SAVED_PHOTOS else SELECTED,context.packageName))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING,MediaStore.MATCH_INCLUDE)
        }
        val projection=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.RELATIVE_PATH,
            if(images)MediaStore.Images.Media.DATE_TAKEN else MediaStore.Video.Media.DURATION,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.IS_PENDING)
        return resolver.query(collection,projection,args,null)?.use { cursor ->
            buildList { while(cursor.moveToNext()) {
                val title=cursor.getString(1) ?: continue
                val kind=when {
                    title.startsWith("Clip_") -> ClipKind.EXTRACT
                    title.startsWith("Keep_") || cursor.getString(2) in setOf(SELECTED,SAVED_PHOTOS,SAVED_AUDIO) -> ClipKind.SELECTED
                    else -> ClipKind.ROLLING
                }
                add(Found(ContentUris.withAppendedId(collection,cursor.getLong(0)),title,kind,if(images)1 else cursor.getLong(3),cursor.getLong(4),cursor.getInt(5)==1,cursor.getString(2)))
            } }
        } ?: error("Recording library unavailable.")
    }
    /** Preserve already-granted access to beta1 files; no picker or new grant is requested. */
    private fun scanLegacy():List<Found> = runCatching {
        val uri=prefs.getString("folder",null) ?: return@runCatching emptyList()
        val root=DocumentFile.fromTreeUri(context,Uri.parse(uri)) ?: return@runCatching emptyList()
        if(!root.exists())return@runCatching emptyList()
        val files=root.listFiles().filter { it.isFile }.map { it to ClipKind.ROLLING } +
            root.findFile("Selected")?.listFiles().orEmpty().filter { it.isFile }.map { it to if(it.name?.startsWith("Clip_")==true)ClipKind.EXTRACT else ClipKind.SELECTED }
        files.mapNotNull { (file,kind) -> file.name?.let { Found(file.uri,it,kind,0,file.length(),false) } }
    }.getOrDefault(emptyList())
    private val scanLock=Any()
    override fun reconcileRecent(activeRecording:Boolean)=synchronized(scanLock) { reconcileFiles(activeRecording,config().archiveInitialLimit) }
    override fun reconcile(activeRecording:Boolean)=synchronized(scanLock) { reconcileFiles(activeRecording,Int.MAX_VALUE) }
    private fun reconcileFiles(activeRecording:Boolean,limit:Int) {
        if(!folderReady())return
        val existing=db.all().associateBy { it.uri }
        val discovered=mutableSetOf<String>()
        val found=(scanMedia()+scanMedia(true)+scanMedia(audio=true)+scanLegacy())
        // Query metadata once; open/inspect only the newest files for the automatic scan.
        found.forEach { discovered+=it.uri.toString() }
        found.map { (VideoNames.time(it.name) ?: existing[it.uri.toString()]?.startMs ?: 0L) to it }.sortedByDescending { it.first }.take(limit).forEach fileLoop@ { (_,file) -> synchronized(this) {
            val key=file.uri.toString()
            val previous=db.byUri(key)
            if(!(file.name.endsWith(".mp4",true) || file.name.endsWith(".jpg",true) || file.name.endsWith(".m4a",true) || file.name.endsWith(".aac",true)) || (previous==null && !listOf("Record_","DuoRecord_","Keep_","Clip_").any { file.name.startsWith(it) } && VideoNames.time(file.name)==null))return@fileLoop
            discovered+=key
            if(previous!=null && ((activeRecording && previous.id in activeWrites) || previous.id in pins))return@fileLoop
            when(presence(file.uri)) {
                Presence.MISSING -> { previous?.let { db.delete(it.id) };return@fileLoop }
                Presence.UNKNOWN -> return@fileLoop
                Presence.PRESENT -> Unit
            }
            if(previous?.protected==true && file.path in setOf(RECORDINGS,PHOTOS,AUDIO) && !file.pending) {
                check(resolver.update(file.uri,ContentValues().apply {
                    put(MediaStore.Video.Media.RELATIVE_PATH,path(previous,true))
                },null,null)>0) { "Could not move a previously protected video into Selected: ${file.name}" }
            }
            val kind=previous?.kind ?: file.kind
            if(previous?.status==ClipStatus.READY && !file.pending) {
                val updated=previous.copy(kind=kind,name=file.name,
                    bytes=file.size.takeIf { it>0 } ?: previous.bytes,
                    durationMs=file.duration.takeIf { it>0 } ?: previous.durationMs)
                if(updated!=previous)db.put(updated)
                return@fileLoop
            }
            // An interrupted copy/export has no verified expected length; do not promote it.
            val duration=if(file.name.endsWith(".jpg",true)) { if(completePhoto(file.uri))1L else 0L } else if(file.name.endsWith(".aac",true))runCatching { resolver.openInputStream(file.uri)!!.use(Adts::durationMs) }.getOrDefault(0L) else if(file.pending && kind!=ClipKind.ROLLING)0L else if(file.duration>0)file.duration else runCatching {
                MediaMetadataRetriever().use { reader ->
                    resolver.openFileDescriptor(file.uri,"r")!!.use { reader.setDataSource(it.fileDescriptor);reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                }
            }.getOrDefault(0L)
            if(duration>0 && file.pending)publish(file.uri)
            val timestamp=Regex("\\d{8}_\\d{6}_\\d{3}(?:[+-]\\d{4})?").find(file.name)?.value
            val start=previous?.startMs ?: VideoNames.time(file.name) ?: runCatching {
                val pattern=if(timestamp?.length==24)"yyyyMMdd_HHmmss_SSSZ" else "yyyyMMdd_HHmmss_SSS"
                SimpleDateFormat(pattern,Locale.US).apply { isLenient=false }.parse(timestamp ?: "")!!.time
            }.getOrDefault(0L)
            db.put(Clip(previous?.id ?: UUID.randomUUID().toString(),key,file.name,start,duration,
                runCatching { bytes(file.uri) }.getOrDefault(file.size),kind,if(duration>0)ClipStatus.READY else ClipStatus.DAMAGED,previous?.sourceId,
                previous?.camera ?: VideoNames.camera(file.name) ?: when { "_REAR" in file.name -> CameraSource.REAR; "_FRONT" in file.name -> CameraSource.FRONT;else -> CameraSource.LEGACY },
                previous?.sessionId ?: Regex("_(?:REAR|FRONT)_([a-f0-9-]+)_([0-9]+)\\.mp4$").find(file.name)?.groupValues?.get(1),
                previous?.segmentIndex ?: Regex("_(?:REAR|FRONT)_([a-f0-9-]+)_([0-9]+)\\.mp4$").find(file.name)?.groupValues?.get(2)?.toIntOrNull() ?: 0))
        }
        }
        // Cached media rows are not proof that the physical file still exists.
        existing.values.filter { it.uri !in discovered }.take(limit).forEach { old -> synchronized(this) {
            val clip=db.find(old.id) ?: return@forEach
            if(clip.id !in pins && clip.id !in activeWrites && presence(Uri.parse(clip.uri))==Presence.MISSING)db.delete(clip.id)
        } }
    }
}
