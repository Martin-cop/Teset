package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class CameraTextureOrientationTest {
    private fun rotate(x:Float,y:Float,angle:Int)=when(angle) { 90 -> 1-y to x;180 -> 1-x to 1-y;270 -> y to 1-x;else -> x to y }
    private fun producer(angle:Int,mirror:Boolean):FloatArray {
        fun sample(x:Float,y:Float):Pair<Float,Float> { val r=rotate(x,y,angle);return (if(mirror).9f-.8f*r.first else .1f+.8f*r.first) to .8f-.6f*r.second }
        val o=sample(0f,0f);val x=sample(1f,0f);val y=sample(0f,1f)
        return FloatArray(16).apply { this[0]=x.first-o.first;this[1]=x.second-o.second;this[4]=y.first-o.first;this[5]=y.second-o.second;this[10]=1f;this[12]=o.first;this[13]=o.second;this[15]=1f }
    }
    private fun sample(m:FloatArray,p:Pair<Float,Float>)=(m[0]*p.first+m[4]*p.second+m[12]) to (m[1]*p.first+m[5]*p.second+m[13])
    @Test fun producerQuarterTurnsDoNotDoubleRotatePortraitCameraFrames() {
        for(sensor in listOf(90,270))for(producer in listOf(0,90,180,270))for(mirror in listOf(false,true)) {
            val actual=producer(producer,mirror);val base=producer(0,mirror)
            assertEquals(producer,CameraTextureOrientation.producerQuarterTurn(actual))
            val remaining=CameraTextureOrientation.remainingRotation(sensor,actual)
            for((x,y) in listOf(0f to 0f,1f to 0f,0f to 1f,1f to 1f,.2f to .7f)) {
                val expected=sample(base,rotate(x,y,sensor));val mapped=sample(actual,rotate(x,y,remaining))
                assertEquals(expected.first,mapped.first,.0001f);assertEquals(expected.second,mapped.second,.0001f)
            }
        }
    }
    @Test fun normalCropFlipAndNegativeSensorAnglesRemainStable() {
        val matrix=producer(0,false)
        assertEquals(0,CameraTextureOrientation.producerQuarterTurn(matrix))
        assertEquals(270,CameraTextureOrientation.remainingRotation(-90,matrix))
        assertEquals(0,CameraTextureOrientation.remainingRotation(360,matrix))
    }
    @Test fun unknownOrLargeVideoCannotAllocateThumbnailDecoders() {
        assertFalse(TrimResourcePolicy.decodeThumbnails(null,null,false));assertFalse(TrimResourcePolicy.decodeThumbnails(7680,4320,false))
        assertFalse(TrimResourcePolicy.decodeThumbnails(3000,4000,false));assertFalse(TrimResourcePolicy.decodeThumbnails(Int.MAX_VALUE,Int.MAX_VALUE,false))
        assertTrue(TrimResourcePolicy.decodeThumbnails(1920,1080,false));assertFalse(TrimResourcePolicy.decodeThumbnails(1920,1080,true))
        assertTrue(TrimResourcePolicy.decodeThumbnails(720,1280,true));assertFalse(TrimResourcePolicy.decodeThumbnails(-1,100,false))
    }
}
