package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    private fun clip(id: String, start: Long, duration: Long = 120_000, bytes: Long = 100,
                     kind: ClipKind = ClipKind.ROLLING, status: ClipStatus = ClipStatus.READY) =
        Clip(id, "content://test/$id", "$id.mp4", start, duration, bytes, kind, status)
    @Test fun extractsAcrossBoundariesWithoutDuplicateProtectedCopy() {
        val a = clip("a", 0); val b = clip("b", 120_000)
        val p = TimelinePlanner.plan(listOf(a, b, a.copy(id="saved",kind=ClipKind.SELECTED)), 110_000, 130_000)
        assertEquals(listOf("a","b"),p.slices.map { it.clip.id }); assertEquals(20_000L,p.mediaDurationMs)
        assertEquals(110_000L,p.slices[0].fromMs); assertEquals(10_000L,p.slices[1].toMs)
    }
    @Test(expected=IllegalArgumentException::class) fun refusesUnrecordedGap() {
        TimelinePlanner.plan(listOf(clip("a",0),clip("b",130_000)),110_000,140_000)
    }
    @Test(expected=IllegalArgumentException::class) fun refusesMissingEnd() {
        TimelinePlanner.plan(listOf(clip("a",0)),110_000,140_000)
    }
    @Test fun protectedCopyCanFillDeletedOriginal() {
        assertEquals("p",TimelinePlanner.plan(listOf(clip("p",0,kind=ClipKind.SELECTED)),1000,2000).slices.single().clip.id)
    }
    @Test fun ignoresWritingFiles() {
        val p=TimelinePlanner.plan(listOf(clip("a",0),clip("w",0,status=ClipStatus.WRITING)),0,1000)
        assertEquals("a",p.slices.single().clip.id)
    }
    @Test fun retentionNeverDeletesProtectedOrActiveOrPinned() {
        val clips=listOf(clip("old",0),clip("pin",1),clip("new",2),clip("selected",3,kind=ClipKind.SELECTED),clip("extract",4,kind=ClipKind.EXTRACT),clip("active",5,status=ClipStatus.WRITING))
        assertEquals(listOf("old","new"),RetentionPolicy.candidates(clips,0,0,1000,setOf("pin")).map { it.id })
    }
    @Test fun retentionKeepsQuotaAndFreeSpaceWhenAlreadySatisfied() {
        assertTrue(RetentionPolicy.candidates(listOf(clip("a",0)),1000,2000,1000).isEmpty())
    }
    @Test fun retentionRemovesOnlyEnoughOldFiles() {
        assertEquals(listOf("a"),RetentionPolicy.candidates(listOf(clip("b",2),clip("a",0),clip("c",3)),200,2000,1000).map { it.id })
    }
    @Test(expected=IllegalArgumentException::class) fun refusesInvertedTimes() { TimelinePlanner.plan(emptyList(),2000,1000) }

    @Test(expected=IllegalArgumentException::class) fun rejectsMixedCameraTimeline() {
        TimelinePlanner.plan(listOf(clip("rear",0).copy(camera=CameraSource.REAR),clip("front",0).copy(camera=CameraSource.FRONT)),0,1000)
    }
    @Test fun rearExportDoesNotSwitchToFrontWhenRearGapOccurs() {
        val rear=clip("rear",0,1000).copy(camera=CameraSource.REAR)
        val front=clip("front",0,2000).copy(camera=CameraSource.FRONT)
        try { TimelinePlanner.plan(listOf(rear,front).filter { it.camera==CameraSource.REAR },0,2000);fail("Rear gap must be rejected") }
        catch(_:IllegalArgumentException) { }
    }
    @Test fun cameraSpecificCopiesFillTheirOwnTimeline() {
        val rear=clip("rear",0).copy(camera=CameraSource.REAR)
        val saved=rear.copy(id="keep",kind=ClipKind.SELECTED)
        val plan=TimelinePlanner.plan(listOf(saved),100,200)
        assertEquals(CameraSource.REAR,plan.slices.single().clip.camera)
    }

    @Test fun savedQuotaRotatesOldestProtectedFilesOnly() {
        val items=listOf(clip("normal",0),clip("old",1,kind=ClipKind.SELECTED),clip("new",2,kind=ClipKind.EXTRACT))
        assertEquals(listOf("old"),SavedRetentionPolicy.candidates(items,100,200).map { it.id })
    }
    @Test fun savedQuotaSkipsPinnedAndWritingFiles() {
        val items=listOf(clip("pin",0,kind=ClipKind.SELECTED),clip("ready",1,kind=ClipKind.SELECTED),clip("writing",2,kind=ClipKind.EXTRACT,status=ClipStatus.WRITING))
        assertEquals(listOf("ready"),SavedRetentionPolicy.candidates(items,100,300,setOf("pin")).map { it.id })
    }
    @Test(expected=IllegalArgumentException::class) fun savedQuotaRefusesOversizeVideo() {
        SavedRetentionPolicy.candidates(emptyList(),300,200)
    }
    @Test fun removingProtectedTagReturnsFileToRollingPolicy() {
        val selected=clip("selected",0,kind=ClipKind.SELECTED)
        assertTrue(RetentionPolicy.candidates(listOf(selected),0,0,1000).isEmpty())
        assertEquals(listOf("selected"),RetentionPolicy.candidates(listOf(selected.copy(kind=ClipKind.ROLLING)),0,0,1000).map { it.id })
    }
}
