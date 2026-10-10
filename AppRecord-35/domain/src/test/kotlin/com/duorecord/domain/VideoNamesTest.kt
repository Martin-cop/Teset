package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class VideoNamesTest {
    @Test fun photoNamesRecoverCameraAndUseIndependentCollisionSequence() {
        val time=VideoNames.time("2026-10-07_14-32-05_R.jpg")!!
        val used=setOf("2026-10-07_14-32-05_R.jpg","2026-10-07_14-32-05_R.mp4")
        assertEquals("2026-10-07_14-32-05_R_1.jpg",VideoNames.unique(time,CameraSource.REAR,used,"jpg"))
        assertEquals(CameraSource.FRONT,VideoNames.camera("2026-10-07_14-32-05_L_2.jpg"))
        val photo=clip("photo",CameraSource.FRONT,2000).copy(name="photo.jpg")
        val video=clip("video",CameraSource.FRONT,1000)
        assertEquals(null to null,PlaybackNavigation.neighbors(listOf(video,photo),photo))
        try { TimelinePlanner.plan(listOf(photo),2000,2001);fail("Photo entered a video export") } catch(_:IllegalArgumentException) {}
    }
    @Test fun normalNameHasSecondsAndCameraSuffixOnly() {
        val time=VideoNames.time("2026-10-07_14-32-05_R.mp4")!!
        assertEquals("2026-10-07_14-32-05_R.mp4",VideoNames.unique(time,CameraSource.REAR,emptySet()))
        assertEquals("2026-10-07_14-32-05_L.mp4",VideoNames.unique(time,CameraSource.FRONT,emptySet()))
    }
    @Test fun collisionAddsFirstAvailableNumberWithoutChangingOtherCamera() {
        val time=VideoNames.time("2026-10-07_14-32-05_R.mp4")!!
        val names=setOf("2026-10-07_14-32-05_R.mp4","2026-10-07_14-32-05_R_1.mp4")
        assertEquals("2026-10-07_14-32-05_R_2.mp4",VideoNames.unique(time,CameraSource.REAR,names))
        assertEquals("2026-10-07_14-32-05_L.mp4",VideoNames.unique(time,CameraSource.FRONT,names))
    }
    @Test fun recoveryReadsNumberedFilesAndRejectsInvalidDates() {
        assertEquals(CameraSource.REAR,VideoNames.camera("2026-10-07_14-32-05_R_2.mp4"))
        assertEquals(CameraSource.FRONT,VideoNames.camera("2026-10-07_14-32-05_L_1.mp4"))
        assertEquals(VideoNames.time("2026-10-07_14-32-05_R.mp4"),VideoNames.time("2026-10-07_14-32-05_R_2.mp4"))
        assertNull(VideoNames.time("2026-02-31_14-32-05_R.mp4"))
    }
    private fun clip(id:String,camera:CameraSource,time:Long,status:ClipStatus=ClipStatus.READY)=
        Clip(id,"content://video/$id",id,time,1000,1024,ClipKind.ROLLING,status,camera=camera)
    @Test fun navigationSkipsOtherCameraAndIncompleteFiles() {
        val old=clip("old",CameraSource.FRONT,1000);val current=clip("current",CameraSource.FRONT,3000);val next=clip("next",CameraSource.FRONT,5000)
        val files=listOf(next,clip("rear",CameraSource.REAR,2000),current,clip("writing",CameraSource.FRONT,4000,ClipStatus.WRITING),old)
        assertEquals(old to next,PlaybackNavigation.neighbors(files,current))
        assertEquals(null to current,PlaybackNavigation.neighbors(files,old))
        assertEquals(current to null,PlaybackNavigation.neighbors(files,next))
    }
    @Test fun allTypesTimelineCrossesPhotoAudioScreenAndVideoButTypedNavigationStaysOnSource() {
        val video=clip("v",CameraSource.REAR,1000)
        val photo=clip("p",CameraSource.FRONT,2000).copy(name="p.jpg")
        val audio=clip("a",CameraSource.AUDIO,3000).copy(name="a.aac")
        val screen=clip("s",CameraSource.SCREEN,4000).copy(name="s.mp4")
        val nextPhoto=photo.copy(id="p2",startMs=5000)
        val files=listOf(nextPhoto,screen,audio,photo,video,clip("unfinished",CameraSource.FRONT,2500,ClipStatus.WRITING))
        assertEquals(video to audio,PlaybackNavigation.neighbors(files,photo,false))
        assertEquals(photo to screen,PlaybackNavigation.neighbors(files,audio,false))
        assertEquals(null to nextPhoto,PlaybackNavigation.neighbors(files,photo,true))
        assertEquals(null to null,PlaybackNavigation.neighbors(files,audio,true))
    }
    @Test fun absentCurrentDoesNotNavigateToUnrelatedVideo() {
        assertEquals(null to null,PlaybackNavigation.neighbors(emptyList(),clip("missing",CameraSource.REAR,0)))
    }
}
