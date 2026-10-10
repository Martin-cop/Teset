package com.duorecord.domain
import org.junit.Assert.*
import org.junit.Test
class AdtsTest {
    @Test fun aacHeaderContainsCorrectRateChannelsAndFrameLengthAndDuration() {
        val payload=ByteArray(200) { 0x12 }
        val header=Adts.header(payload.size)
        assertEquals(0xff,header[0].toInt() and 255);assertEquals(0xf1,header[1].toInt() and 255)
        assertEquals(3,(header[2].toInt() shr 2) and 15)
        assertEquals(1,(header[3].toInt() shr 6) and 3)
        val frame=header+payload
        val bytes=java.io.ByteArrayOutputStream().apply { repeat(48) { write(frame) } }.toByteArray()
        assertEquals(1024,Adts.durationMs(bytes.inputStream()))
        assertEquals(0,Adts.durationMs(bytes.copyOf(bytes.size-1).inputStream()))
    }
    @Test fun invalidFramesAndUnsupportedParametersAreRejected() {
        assertEquals(0,Adts.durationMs(byteArrayOf(0,0,0,0,0,0,0).inputStream()))
        for(size in listOf(0,8185))try { Adts.header(size);fail() } catch(_:IllegalArgumentException) {}
        try { Adts.header(20,12345);fail() } catch(_:IllegalArgumentException) {}
    }
}
