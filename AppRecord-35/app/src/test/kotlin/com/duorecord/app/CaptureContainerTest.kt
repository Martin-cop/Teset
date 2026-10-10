package com.duorecord.app

import android.content.pm.ProviderInfo
import android.media.MediaFormat
import android.os.Environment
import com.duorecord.contracts.*
import com.duorecord.data.StorageFeature
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class CaptureContainerTest {
    private val muxType=Class.forName("com.duorecord.capture.RollingMuxer")
    private val sampleType=Class.forName("com.duorecord.capture.EncodedSample")
    private fun sample(time:Long)=sampleType.getDeclaredConstructor(ByteArray::class.java,Long::class.javaPrimitiveType,Int::class.javaPrimitiveType).newInstance(ByteArray(20) { 0x12 },time,1)
    private fun create(config:RecordingConfig):Triple<Any,StorageActionsTest.Provider,MutableList<Clip>> {
        val context=RuntimeEnvironment.getApplication()
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(),4_000_000,3_000_000,3_000_000)
        val provider=StorageActionsTest.Provider();provider.attachInfo(context,ProviderInfo().apply { authority="media" })
        ShadowContentResolver.registerProviderInternal("media",provider)
        val repo=StorageFeature().create(context);repo.saveConfig(config)
        val completed=mutableListOf<Clip>()
        val listener=object:CaptureListener {
            override fun onStarted(codec:String) {}
            override fun onSegment(clip:Clip) { completed+=clip }
            override fun onStopped(reason:String) {}
            override fun onError(message:String) { fail(message) }
        }
        val mux=muxType.declaredConstructors.single().apply { isAccessible=true }.newInstance(repo,config,1791372000000L,listener,if(config.audioOnly)CameraSource.AUDIO else CameraSource.FRONT,"container-test")
        return Triple(mux,provider,completed)
    }
    @Test fun audioPacketsProduceSeparateAacFilesAtRollingBoundaryWithNoVideoContainer() {
        val (mux,provider,completed)=create(RecordingConfig(audioOnly=true,segmentSeconds=10))
        muxType.getDeclaredMethod("format",Boolean::class.javaPrimitiveType,MediaFormat::class.java).invoke(mux,false,MediaFormat.createAudioFormat("audio/mp4a-latm",48000,1))
        val audio=muxType.getDeclaredMethod("audio",sampleType)
        repeat(480) { audio.invoke(mux,sample(it*1024L*1_000_000/48000)) }
        muxType.getDeclaredMethod("close").invoke(mux)
        assertEquals(2,completed.size);assertEquals(2,provider.files.size)
        completed.forEach { assertTrue(it.name.endsWith(".aac"));assertTrue(it.isAudio);assertTrue(it.durationMs>0) }
        provider.rows.values.forEach { assertEquals("audio/aac",it.getAsString("mime_type"));assertEquals(0,it.getAsInteger("is_pending")) }
        provider.files.values.forEach { file ->
            val bytes=file.readBytes();assertEquals(255,bytes[0].toInt() and 255);assertEquals(241,bytes[1].toInt() and 255)
            assertTrue(Adts.durationMs(bytes.inputStream())>0);assertEquals(0,bytes.size%27)
        }
    }
    @Test fun pauseKeepsSameAudioFileAndDiscardsPausedAndLatePackets() {
        val (mux,provider,completed)=create(RecordingConfig(audioOnly=true))
        muxType.getDeclaredMethod("format",Boolean::class.javaPrimitiveType,MediaFormat::class.java).invoke(mux,false,MediaFormat.createAudioFormat("audio/mp4a-latm",48000,1))
        val audio=muxType.getDeclaredMethod("audio",sampleType)
        val pause=muxType.getDeclaredMethod("setPaused",Boolean::class.javaPrimitiveType,Long::class.javaPrimitiveType)
        repeat(10) { audio.invoke(mux,sample(it*21334L)) }
        pause.invoke(mux,true,213340L)
        assertTrue(completed.isEmpty())
        val before=provider.files.values.sumOf { it.length() }
        repeat(100) { audio.invoke(mux,sample(213340L+it*21334L)) }
        assertEquals(before,provider.files.values.sumOf { it.length() })
        pause.invoke(mux,false,5_000_000L)
        audio.invoke(mux,sample(4_999_000L)) // callback delayed across Resume must be discarded
        repeat(10) { audio.invoke(mux,sample(5_000_000L+it*21334L)) }
        muxType.getDeclaredMethod("close").invoke(mux)
        assertEquals(1,completed.size)
        assertEquals(before*2,provider.files.values.sumOf { it.length() });assertTrue(completed.single().durationMs in 425..430)
    }
    @Test fun videoPauseRetainsTheMuxerAndUsesCompactedPtsOnResume() {
        val (mux,_,completed)=create(RecordingConfig(fps=15))
        val format=muxType.getDeclaredMethod("format",Boolean::class.javaPrimitiveType,MediaFormat::class.java)
        format.invoke(mux,true,MediaFormat.createVideoFormat("video/avc",480,640))
        format.invoke(mux,false,MediaFormat.createAudioFormat("audio/mp4a-latm",48000,1))
        val audio=muxType.getDeclaredMethod("audio",sampleType);val video=muxType.getDeclaredMethod("video",sampleType)
        audio.invoke(mux,sample(0));video.invoke(mux,sample(0))
        audio.invoke(mux,sample(66667));video.invoke(mux,sample(66667))
        val writer=muxType.getDeclaredField("muxer").apply { isAccessible=true };val original=writer.get(mux);assertNotNull(original)
        val pause=muxType.getDeclaredMethod("setPaused",Boolean::class.javaPrimitiveType,Long::class.javaPrimitiveType)
        pause.invoke(mux,true,133334L);assertSame(original,writer.get(mux));assertTrue(completed.isEmpty())
        audio.invoke(mux,sample(200000));video.invoke(mux,sample(200000))
        pause.invoke(mux,false,5000000L)
        audio.invoke(mux,sample(5000000));video.invoke(mux,sample(5000000))
        assertSame(original,writer.get(mux))
        val last=muxType.getDeclaredField("lastVideoUs").apply { isAccessible=true }.getLong(mux)
        assertEquals(133334L,last)
        muxType.getDeclaredMethod("close").invoke(mux);assertEquals(1,completed.size);assertTrue(completed.single().durationMs in 199..201)
    }
    @Test fun photoPreviewPacketsNeverOpenAnyMp4OrAudioFile() {
        val (mux,provider,completed)=create(RecordingConfig(photoMode=true,photoIntervalSeconds=1))
        val format=muxType.getDeclaredMethod("format",Boolean::class.javaPrimitiveType,MediaFormat::class.java)
        format.invoke(mux,true,MediaFormat.createVideoFormat("video/avc",480,640))
        format.invoke(mux,false,MediaFormat.createAudioFormat("audio/mp4a-latm",48000,1))
        repeat(2000) { n ->
            muxType.getDeclaredMethod("video",sampleType).invoke(mux,sample(n*1000L))
            muxType.getDeclaredMethod("audio",sampleType).invoke(mux,sample(n*1000L))
        }
        muxType.getDeclaredMethod("close").invoke(mux)
        assertTrue(provider.rows.isEmpty());assertTrue(completed.isEmpty())
    }
}
