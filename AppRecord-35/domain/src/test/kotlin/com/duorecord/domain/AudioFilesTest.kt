package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class AudioFilesTest {
    private fun clip(id:String,time:Long,camera:CameraSource,session:String="same")=Clip(id,"content://media/$id",if(camera==CameraSource.AUDIO)"$id.m4a" else "$id.mp4",time,1000,100,ClipKind.ROLLING,ClipStatus.READY,camera=camera,sessionId=session)
    @Test fun audioAppearsOnceAndNeverPairsWithCameraFilesEvenInSameSession() {
        val audio=clip("audio",1000,CameraSource.AUDIO)
        val rows=RecordingRows.group(listOf(audio,clip("rear",1000,CameraSource.REAR),clip("front",1000,CameraSource.FRONT)))
        assertEquals(2,rows.size);assertEquals(3,rows.sumOf { it.clips.size })
        assertEquals(listOf(audio),rows.single { it.audio!=null }.clips)
        assertTrue(rows.single { it.audio==null }.rear!=null)
    }
    @Test fun audioNamesUseTimestampAndVAndOnlyCollisionsGainANumber() {
        val time=1791372000000L
        val base=VideoNames.unique(time,CameraSource.AUDIO,emptySet(),"aac")
        assertTrue(base.endsWith("_V.aac"));assertEquals(CameraSource.AUDIO,VideoNames.camera(base))
        assertEquals(time,VideoNames.time(base))
        assertEquals(CameraSource.AUDIO,VideoNames.camera("2026-10-07_10-00-00_A.m4a"))
        val collision=VideoNames.unique(time,CameraSource.AUDIO,setOf(base),"aac")
        assertTrue(collision.endsWith("_V_1.aac"));assertEquals(CameraSource.AUDIO,VideoNames.camera(collision))
    }
    @Test fun audioNavigationSkipsInterleavedCameraVideos() {
        val before=clip("a",1000,CameraSource.AUDIO);val current=clip("b",3000,CameraSource.AUDIO);val after=clip("c",5000,CameraSource.AUDIO)
        val (prev,next)=PlaybackNavigation.neighbors(listOf(after,clip("rear",4000,CameraSource.REAR),current,before),current)
        assertEquals(before,prev);assertEquals(after,next)
    }
    @Test fun videoExportNeverUsesAudioAsCoverage() {
        try { TimelinePlanner.plan(listOf(clip("audio",1000,CameraSource.AUDIO)),1000,2000);fail("Audio must not be exported as video") } catch(_:IllegalArgumentException) {}
    }
}
