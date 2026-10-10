package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class RecordingRowsTest {
    private fun clip(id:String,camera:CameraSource,start:Long=1000,duration:Long=120_000,session:String?="session",kind:ClipKind=ClipKind.ROLLING,index:Int=0,status:ClipStatus=ClipStatus.READY)=
        Clip(id,"content://video/$id",id,start,duration,1024,kind,status,camera=camera,sessionId=session,segmentIndex=index)
    @Test fun photosPairAcrossSmallCameraDelayAndStaySeparateFromVideo() {
        val rear=clip("r",CameraSource.REAR,start=1000,duration=1).copy(name="r.jpg")
        val front=clip("f",CameraSource.FRONT,start=1120,duration=1).copy(name="f.jpg")
        val video=clip("v",CameraSource.FRONT,start=1000,duration=1000)
        val rows=RecordingRows.group(listOf(rear,front,video))
        assertEquals(2,rows.size)
        assertEquals(listOf(rear,front),rows.single { it.rear!=null }.clips)
        assertEquals(listOf(video),rows.single { it.clips.first().id=="v" }.clips)
    }
    @Test fun pairsOverlappingCamerasAndShowsAggregatedMetadata() {
        val rear=clip("r",CameraSource.REAR);val front=clip("f",CameraSource.FRONT,start=1100,duration=119_900,kind=ClipKind.SELECTED)
        val row=RecordingRows.group(listOf(front,rear)).single()
        assertEquals(rear,row.rear);assertEquals(front,row.front)
        assertEquals(1000,row.startMs);assertEquals(2048,row.bytes);assertEquals(120_000,row.durationMs)
    }
    @Test fun separateSessionsAndUnknownSessionsStaySeparate() {
        val files=listOf(clip("r",CameraSource.REAR,session="one"),clip("f",CameraSource.FRONT,session="two"),clip("r0",CameraSource.REAR,session=null),clip("f0",CameraSource.FRONT,session=null))
        assertEquals(4,RecordingRows.group(files).size)
    }
    @Test fun aReusedSegmentIndexAfterQualityChangeDoesNotPairNonOverlappingFiles() {
        val files=listOf(clip("r-old",CameraSource.REAR,start=1000,duration=1000),clip("r-new",CameraSource.REAR,start=10_000,duration=1000),clip("f",CameraSource.FRONT,start=10_100,duration=1000))
        val rows=RecordingRows.group(files)
        assertEquals(2,rows.size);assertEquals("r-new",rows.first().rear!!.id);assertEquals("f",rows.first().front!!.id)
        assertNull(rows.last().front)
    }
    @Test fun eachFileAppearsOnceEvenWithDifferentSegmentBoundaries() {
        val files=listOf(clip("r1",CameraSource.REAR,start=0,duration=1000),clip("r2",CameraSource.REAR,start=1000,duration=1000),clip("r3",CameraSource.REAR,start=2000,duration=1000),clip("f1",CameraSource.FRONT,start=0,duration=3000))
        val flattened=RecordingRows.group(files).flatMap { it.clips }.map { it.id }
        assertEquals(files.map { it.id }.toSet(),flattened.toSet());assertEquals(files.size,flattened.size)
    }
    @Test fun exportsRemainIndependentAndLegacyFilesRemainAccessible() {
        val files=listOf(clip("r",CameraSource.REAR),clip("f",CameraSource.FRONT,kind=ClipKind.EXTRACT),clip("old",CameraSource.LEGACY))
        val rows=RecordingRows.group(files)
        assertEquals(3,rows.size);assertEquals("old",rows.single { it.legacy!=null }.legacy!!.id)
    }
    @Test fun missingCameraRemainsNullAndRowsAreNewestFirst() {
        val rows=RecordingRows.group(listOf(clip("old",CameraSource.REAR,start=0),clip("new",CameraSource.FRONT,start=500_000)))
        assertEquals("new",rows.first().front!!.id);assertNull(rows.first().rear)
        assertNull(rows.last().front)
    }
}
