package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class MixedRecordingTest {
    @Test fun everyNonEmptySourceCombinationSupportsItsOwnBackground() {
        for(mask in 1..7) {
            val c=RecordingConfig(mixedMode=true,mixedFront=mask and 1!=0,mixedRear=mask and 2!=0,mixedScreen=mask and 4!=0)
            c.mixedSources.forEach { background -> CapturePolicy.validate(c.copy(mixedBackground=background)) }
            assertEquals(Integer.bitCount(mask),c.mixedSources.size)
        }
        assertThrows(IllegalArgumentException::class.java) { CapturePolicy.validate(RecordingConfig(mixedMode=true,mixedFront=false,mixedRear=false,mixedScreen=false)) }
        assertThrows(IllegalArgumentException::class.java) { CapturePolicy.validate(RecordingConfig(mixedMode=true,mixedScreen=false,mixedBackground=CameraSource.SCREEN)) }
    }
    @Test fun cameraBackgroundAndScreenInsetRoundTripWithoutMovingRawSourceCoordinates() {
        val f=MixedFrame(.8f,.9f,.4f,.3f).bounded()
        assertEquals(.6f,f.x,.0001f);assertEquals(.7f,f.y,.0001f)
        val frames=MixedLayout.read("").toMutableMap();frames[CameraSource.SCREEN]=f.copy(visible=false)
        assertEquals(frames,MixedLayout.read(MixedLayout.write(frames)))
        assertEquals(MixedLayout.read(""),MixedLayout.read("FRONT:NaN,0,0.3,0.3,1;bad"))
    }
    @Test fun sourceAndCompositeFileNamesStayDistinctAtTheSameSecondAndAppearInArchive() {
        val sources=listOf(CameraSource.FRONT,CameraSource.REAR,CameraSource.SCREEN,CameraSource.MIXED)
        val names=sources.map { VideoNames.unique(1791380000000,it,emptySet()) }
        assertEquals(4,names.toSet().size)
        sources.zip(names).forEach { (source,name) -> assertEquals(source,VideoNames.camera(name)) }
        val clips=sources.zip(names).map { (source,name) -> Clip(name,"content://test/$name",name,1000,3000,100,ClipKind.ROLLING,ClipStatus.READY,camera=source,sessionId="mixed") }
        assertEquals(clips.map { it.id }.toSet(),RecordingRows.group(clips).flatMap { it.clips }.map { it.id }.toSet())
    }
    @Test fun audioMixSelectionClippingAndLevelMeterUseActualPcm() {
        fun pcm(vararg samples:Int)=ByteArray(samples.size*2).also { bytes -> samples.forEachIndexed { i,n -> bytes[2*i]=n.toByte();bytes[2*i+1]=(n shr 8).toByte() } }
        val a=pcm(30000,-30000);val b=pcm(20000,-20000)
        assertArrayEquals(a,AudioLevels.mix(a,b,4,true,false))
        assertArrayEquals(b,AudioLevels.mix(a,b,4,false,true))
        assertArrayEquals(ByteArray(4),AudioLevels.mix(a,b,4,false,false))
        assertArrayEquals(pcm(32767,-32768),AudioLevels.mix(a,b,4,true,true))
        assertTrue(AudioLevels.measure(ByteArray(96),96).all { it==0f })
        assertTrue(AudioLevels.measure(pcm(*IntArray(48) { 12000 }),96).all { it>0f })
    }
    @Test fun archiveRefreshAndLimitRejectInvalidValues() {
        CapturePolicy.validate(RecordingConfig(archiveRefreshSeconds=5,archiveInitialLimit=100))
        for(c in listOf(RecordingConfig(archiveRefreshSeconds=0),RecordingConfig(archiveInitialLimit=0),RecordingConfig(archiveInitialLimit=10001)))assertThrows(IllegalArgumentException::class.java) { CapturePolicy.validate(c) }
    }
}
