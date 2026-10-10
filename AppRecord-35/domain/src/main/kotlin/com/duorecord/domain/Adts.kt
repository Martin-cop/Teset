package com.duorecord.domain

import java.io.InputStream

/** AAC-LC in its own ADTS audio stream, without an MP4 container. */
object Adts {
    private val rates=listOf(96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350)
    fun header(payload:Int,sampleRate:Int=48000,channels:Int=1):ByteArray {
        val index=rates.indexOf(sampleRate);require(index>=0 && channels in 1..7 && payload in 1..8184)
        val size=payload+7
        return byteArrayOf(0xff.toByte(),0xf1.toByte(),((1 shl 6) or (index shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (size shr 11)).toByte(),(size shr 3).toByte(),(((size and 7) shl 5) or 0x1f).toByte(),0xfc.toByte())
    }
    fun durationMs(input:InputStream):Long {
        val samples=LongArray(rates.size);var frames=0
        while(true) {
            val first=input.read();if(first<0)break
            val h=IntArray(7);h[0]=first
            for(i in 1..6) { h[i]=input.read();if(h[i]<0)return 0 }
            if(h[0]!=255 || (h[1] and 0xf6)!=0xf0)return 0
            val index=(h[2] shr 2) and 15
            if(index !in rates.indices)return 0
            val length=((h[3] and 3) shl 11) or (h[4] shl 3) or (h[5] shr 5)
            if(length<=7)return 0
            var remaining=length-7
            while(remaining>0) { val skipped=input.skip(remaining.toLong()).toInt();if(skipped>0)remaining-=skipped else if(input.read()>=0)remaining-- else return 0 }
            samples[index]+=1024L*((h[6] and 3)+1);frames++
        }
        return if(frames>0)samples.withIndex().sumOf { it.value*1000/rates[it.index] }.coerceAtLeast(1) else 0
    }
}
