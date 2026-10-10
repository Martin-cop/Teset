package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PcmMeterTest {
    private fun pcm(values:IntArray)=ByteArray(values.size*2).also { b -> values.forEachIndexed { i,v -> b[i*2]=v.toByte();b[i*2+1]=(v shr 8).toByte() } }
    @Test fun digitalSilenceHasNoBarsAndFiniteFloor() {
        val result=PcmMeter.analyze(ByteArray(2048),2048)
        assertEquals(-90f,result.rmsDb,.001f);assertEquals(-90f,result.peakDb,.001f)
        assertTrue(result.bands.all { it==0f });assertFalse(result.clipped)
        assertEquals(-90f,PcmMeter.analyze(ByteArray(0),0).peakDb,.001f)
    }
    @Test fun oneKhzSineHasCorrectDigitalLevelsAndDominatesItsFrequencyBand() {
        val bytes=pcm(IntArray(1024) { (16384*sin(2*PI*1000*it/48000)).toInt() })
        val result=PcmMeter.analyze(bytes,bytes.size)
        assertEquals(-6.02f,result.peakDb,.05f);assertEquals(-9.03f,result.rmsDb,.1f);assertFalse(result.clipped)
        val peak=result.bands.indices.maxBy { result.bands[it] }
        assertTrue(peak in 9..10);assertTrue(result.bands[peak]>.85f)
        assertTrue(result.bands[2]<.3f);assertTrue(result.bands[18]<.3f)
    }
    @Test fun positiveAndNegativeFullScaleLatchClippingWithoutInventingASecondMicrophone() {
        for(value in listOf(32767,-32768)) {
            val bytes=pcm(IntArray(1024) { value });val result=PcmMeter.analyze(bytes,bytes.size)
            assertTrue(result.clipped);assertEquals(0f,result.peakDb,.01f);assertEquals(0f,result.rmsDb,.01f);assertEquals(20,result.bands.size)
        }
    }
}
