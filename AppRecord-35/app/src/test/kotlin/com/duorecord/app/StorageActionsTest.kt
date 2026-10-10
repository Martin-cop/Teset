package com.duorecord.app

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.duorecord.data.StorageFeature
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowStatFs
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class StorageActionsTest {
    class Provider:ContentProvider() {
        @Volatile var queries=0
        @Volatile var opens=0
        @Volatile var beforeOpen:(()->Unit)?=null
        var denyRead=false
        val rows=linkedMapOf<Long,ContentValues>();val files=linkedMapOf<Long,File>();private var next=1L
        override fun onCreate()=true
        override fun getType(uri:Uri)=uri.lastPathSegment?.toLongOrNull()?.let { rows[it]?.getAsString("mime_type") } ?: "video/mp4"
        override fun insert(uri:Uri,values:ContentValues?):Uri {
            val id=next++;rows[id]=ContentValues(values!!).apply { put("_id",id);put("duration",1000);put("owner_package_name",context!!.packageName) }
            files[id]=File(context!!.cacheDir,"provider-$id.mp4").apply { writeBytes(byteArrayOf(1,2,3,4)) }
            return ContentUris.withAppendedId(uri,id)
        }
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
            queries++
            val columns=projection ?: arrayOf("_id","_display_name");val cursor=MatrixCursor(columns)
            val id=uri.lastPathSegment?.toLongOrNull()
            rows.filter { (key,row) -> (id==null || id==key) && when { "/images/" in uri.path.orEmpty() -> row.getAsString("mime_type").orEmpty().startsWith("image/");"/audio/" in uri.path.orEmpty() -> row.getAsString("mime_type").orEmpty().startsWith("audio/");else -> row.getAsString("mime_type").orEmpty().startsWith("video/") } }.forEach { (key,row) ->
                cursor.addRow(columns.map { if(it=="_size")files[key]!!.length() else row[it] }.toTypedArray())
            }
            return cursor
        }
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int {
            val row=rows[uri.lastPathSegment!!.toLong()] ?: return 0;row.putAll(values!!);return 1
        }
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int {
            val id=uri.lastPathSegment!!.toLong();files.remove(id)?.delete();return if(rows.remove(id)!=null)1 else 0
        }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            opens++;beforeOpen?.invoke()
            if(denyRead)throw SecurityException("Temporary permission loss")
            val file=files[uri.lastPathSegment!!.toLong()] ?: throw java.io.FileNotFoundException()
            if(!file.exists())throw java.io.FileNotFoundException("Physical video removed")
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE)
        }
    }
    private fun setup():Pair<com.duorecord.contracts.MediaRepository,Provider> {
        val context=RuntimeEnvironment.getApplication()
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(),4_000_000,3_000_000,3_000_000)
        val provider=Provider();provider.attachInfo(context,ProviderInfo().apply { authority="media" })
        ShadowContentResolver.registerProviderInternal("media",provider)
        return StorageFeature().create(context) to provider
    }
    @Test fun thousandsOfFilesUseBoundedAutomaticInspectionAndDoNotBlockConfigReaders() {
        val (repo,provider)=setup()
        val collection=MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        repeat(2444) { n -> provider.insert(collection,ContentValues().apply {
            put("_display_name",VideoNames.base(1791372000000+n*1000L,CameraSource.REAR)+".mp4")
            put("mime_type","video/mp4");put("relative_path","Movies/Record/");put("is_pending",0)
        }) }
        repo.reconcileRecent();assertEquals(100,repo.list().size);assertTrue(provider.opens<=200)
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        provider.beforeOpen={ entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS)) }
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val scan=pool.submit { repo.reconcile() }
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1800,pool.submit<Int> { repo.config().segmentSeconds }.get(1,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(100,pool.submit<Int> { repo.list().size }.get(1,java.util.concurrent.TimeUnit.SECONDS))
            provider.beforeOpen=null;release.countDown();scan.get(30,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(2444,repo.list().size)
        } finally { provider.beforeOpen=null;release.countDown();pool.shutdownNow() }
    }
    @Test fun intervalPhotoPublishesProtectsRefreshesAndDeletesRealJpeg() {
        val (repo,provider)=setup()
        val bitmap=android.graphics.Bitmap.createBitmap(32,48,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes=java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,this) }.toByteArray();bitmap.recycle()
        repo.saveConfig(repo.config().copy(photoMode=true,photoIntervalSeconds=7))
        assertTrue(repo.config().photoMode);assertEquals(7,repo.config().photoIntervalSeconds)
        val photo=repo.savePhoto(bytes,1791372000000,CameraSource.FRONT,"photos",0)
        assertTrue(photo.isPhoto);assertTrue(photo.name.endsWith("_L.jpg"));assertEquals(ClipStatus.READY,photo.status)
        assertEquals("Pictures/Record/",provider.rows.values.single().getAsString("relative_path"))
        assertEquals("image/jpeg",provider.rows.values.single().getAsString("mime_type"))
        assertEquals(0,provider.rows.values.single().getAsInteger("is_pending"))
        assertArrayEquals(bytes,provider.files.values.single().readBytes())
        repo.protect(photo.id);assertEquals("Pictures/Record/Selected/",provider.rows.values.single().getAsString("relative_path"))
        repo.reconcile();assertEquals(1,repo.list().size);assertEquals(CameraSource.FRONT,repo.get(photo.id)!!.camera)
        repo.protect(photo.id);assertEquals("Pictures/Record/",provider.rows.values.single().getAsString("relative_path"))
        provider.files.values.single().delete();repo.reconcile();assertTrue(repo.list().isEmpty())
    }
    @Test fun legacyDefaultMigratesToThirtyMinutesAndCustomLengthIsPreserved() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("duorecord",0)
        prefs.edit().putInt("segment",120).remove("segmentDefaultRevision").commit()
        assertEquals(1800,StorageFeature().create(context).config().segmentSeconds)
        prefs.edit().putInt("segment",73).remove("segmentDefaultRevision").commit()
        assertEquals(73,StorageFeature().create(context).config().segmentSeconds)
    }
    @Test fun saveMovesExistingFileAndUnsaveReturnsItWithoutCopyOrRename() {
        val (repo,provider)=setup();val file=repo.createSegment(1791372000000,CameraSource.REAR,"session",0);file.descriptor.close();repo.completeSegment(file.clip.id,1000)
        val saved=repo.protect(file.clip.id)
        assertEquals(file.clip.name,saved.name);assertTrue(saved.protected);assertEquals(1,provider.rows.size)
        assertEquals("Movies/Record/Selected/",provider.rows.values.single().getAsString("relative_path"))
        // Simulate a pre-v5 protected file still in the main folder; refresh migrates it.
        provider.update(Uri.parse(saved.uri),ContentValues().apply { put("relative_path","Movies/Record/") },null,null)
        repo.reconcile(true)
        assertEquals("Movies/Record/Selected/",provider.rows.values.single().getAsString("relative_path"))
        assertEquals(saved.name,repo.get(saved.id)!!.name)
        val unselected=repo.protect(file.clip.id)
        assertFalse(unselected.protected);assertEquals(saved.name,unselected.name)
        assertEquals("Movies/Record/",provider.rows.values.single().getAsString("relative_path"))
    }
    @Test fun deletionRejectsPinnedVideoThenRemovesFileAndMetadata() {
        val (repo,provider)=setup();val file=repo.createSegment(1791372000000,CameraSource.FRONT);file.descriptor.close();repo.completeSegment(file.clip.id,1000)
        val pin=repo.pin(setOf(file.clip.id))
        try { repo.delete(setOf(file.clip.id));fail("Pinned file was deleted") } catch(_:IllegalArgumentException) {}
        assertEquals(1,provider.rows.size);pin.close();repo.delete(setOf(file.clip.id))
        assertTrue(provider.rows.isEmpty());assertTrue(repo.list().isEmpty())
    }
    @Test fun refreshRemovesExternallyDeletedFilesAndPreservesActivePendingStatus() {
        val (repo,provider)=setup()
        val done=repo.createSegment(1791372000000,CameraSource.REAR);done.descriptor.close();repo.completeSegment(done.clip.id,1000)
        val pending=repo.createSegment(1791372001000,CameraSource.FRONT);pending.descriptor.close()
        provider.delete(Uri.parse(done.clip.uri),null,null);repo.reconcile(true)
        assertNull(repo.get(done.clip.id));assertEquals(ClipStatus.WRITING,repo.get(pending.clip.id)!!.status)
        repo.reconcile(false);assertEquals(ClipStatus.READY,repo.get(pending.clip.id)!!.status)
    }
    @Test fun physicalDeletionClearsCachedMediaRowsAndOrphanedWritingRecordsWithoutReturning() {
        val (repo,provider)=setup()
        val completed=repo.createSegment(1791372000000,CameraSource.REAR);completed.descriptor.close();repo.completeSegment(completed.clip.id,1000)
        val orphan=repo.createSegment(1791372001000,CameraSource.FRONT);orphan.descriptor.close()
        provider.files.values.forEach { it.delete() }
        val recovered=StorageFeature().create(RuntimeEnvironment.getApplication())
        val active=recovered.createSegment(1791372002000,CameraSource.REAR);active.descriptor.close()
        recovered.reconcile(true)
        assertNull(recovered.get(completed.clip.id));assertNull(recovered.get(orphan.clip.id))
        assertEquals(ClipStatus.WRITING,recovered.get(active.clip.id)!!.status)
        recovered.reconcile(true)
        assertEquals(listOf(active.clip.id),recovered.list().map { it.id })
    }
    @Test fun temporaryAccessFailurePreservesMetadataAndOrphanedWriterCanRecover() {
        val (repo,provider)=setup()
        val file=repo.createSegment(1791372000000,CameraSource.REAR);file.descriptor.close()
        val recovered=StorageFeature().create(RuntimeEnvironment.getApplication())
        provider.denyRead=true;recovered.reconcile(true)
        assertEquals(ClipStatus.WRITING,recovered.get(file.clip.id)!!.status)
        provider.denyRead=false;recovered.reconcile(true)
        assertEquals(ClipStatus.READY,recovered.get(file.clip.id)!!.status)
    }

    @Test fun audioUsesMusicCollectionPersistsModeAndMovesWithoutDuplicating() {
        val (repo,provider)=setup()
        repo.saveConfig(repo.config().copy(audioOnly=true))
        assertTrue(repo.config().audioOnly);assertEquals("Music/Record",repo.storagePath())
        val output=repo.createSegment(1791372000000,CameraSource.AUDIO,"audio-session",0)
        assertTrue(output.clip.name.endsWith("_V.aac"));assertTrue(output.clip.uri.contains("/audio/media/"))
        assertEquals("audio/aac",provider.rows.values.single().getAsString("mime_type"))
        assertEquals("Music/Record/",provider.rows.values.single().getAsString("relative_path"))
        output.descriptor.close();repo.completeSegment(output.clip.id,2500)
        assertEquals(0,provider.rows.values.single().getAsInteger("is_pending"))
        val saved=repo.protect(output.clip.id)
        assertEquals("Music/Record/Selected/",provider.rows.values.single().getAsString("relative_path"))
        assertEquals(output.clip.uri,saved.uri);assertEquals(1,provider.rows.size)
        repo.reconcile();assertEquals(1,repo.list().size);assertEquals(CameraSource.AUDIO,repo.list().single().camera)
        repo.protect(saved.id);assertEquals("Music/Record/",provider.rows.values.single().getAsString("relative_path"))
        provider.files.values.single().delete();repo.reconcile();assertTrue(repo.list().isEmpty())
    }
    @Test fun refreshDiscoversAudioAndDoesNotConvertAnActiveAudioWriterToReady() {
        val (repo,provider)=setup()
        val uri=provider.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply {
            put("_display_name",VideoNames.base(1791372000000,CameraSource.AUDIO)+".m4a")
            put("mime_type","audio/mp4");put("relative_path","Music/Record/");put("is_pending",0)
        })
        repo.reconcile()
        assertEquals(uri.toString(),repo.list().single().uri);assertTrue(repo.list().single().isAudio)
        val writer=repo.createSegment(1791372001000,CameraSource.AUDIO,"live",1)
        repo.reconcile(true);assertEquals(ClipStatus.WRITING,repo.get(writer.clip.id)!!.status)
        writer.descriptor.close();repo.completeSegment(writer.clip.id,1000)
        repo.reconcile();assertEquals(2,repo.list().size)
    }

    @Test fun microphoneContinuityPreferenceSurvivesRepositoryRecreation() {
        val (repo,_)=setup();assertTrue(repo.config().continueVideoWithoutMic)
        repo.saveConfig(repo.config().copy(continueVideoWithoutMic=false))
        assertFalse(StorageFeature().create(RuntimeEnvironment.getApplication()).config().continueVideoWithoutMic)
    }
}
