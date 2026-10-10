package com.duorecord.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.duorecord.domain.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Media3 owns buffer forwarding; the observer never modifies or blocks playback data. */
@UnstableApi
internal class PlaybackMeterProcessor(report:(AudioMeter)->Unit):AudioProcessor by TeeAudioProcessor(MeterSink(report))

@UnstableApi
private class MeterSink(private val report:(AudioMeter)->Unit):TeeAudioProcessor.AudioBufferSink {
    private var rate=48000;private var channels=1;private var encoding=C.ENCODING_PCM_16BIT
    private var count=0;private var bytesPerSample=2
    private val mono=ByteArray(PcmMeter.SIZE*2)
    override fun flush(sampleRateHz:Int,channelCount:Int,encoding:Int) {
        rate=sampleRateHz;channels=channelCount.coerceAtLeast(1);this.encoding=encoding;count=0
        bytesPerSample=when(encoding) { C.ENCODING_PCM_8BIT -> 1;C.ENCODING_PCM_16BIT -> 2;C.ENCODING_PCM_24BIT -> 3;C.ENCODING_PCM_FLOAT,C.ENCODING_PCM_32BIT -> 4;else -> 0 }
        runCatching { report(AudioMeter()) }
    }
    override fun handleBuffer(buffer:ByteBuffer) {
        if(bytesPerSample==0)return
        runCatching {
            val data=buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            while(data.remaining()>=channels*bytesPerSample) {
                var sum=0L
                repeat(channels) {
                    val sample=when(encoding) {
                        C.ENCODING_PCM_8BIT -> ((data.get().toInt() and 255)-128)*256
                        C.ENCODING_PCM_FLOAT -> (data.float.let { if(it.isFinite())it else 0f }.coerceIn(-1f,1f)*32767).toInt()
                        C.ENCODING_PCM_32BIT -> data.int shr 16
                        C.ENCODING_PCM_24BIT -> { data.get();(data.get().toInt() and 255) or (data.get().toInt() shl 8) }
                        else -> data.short.toInt()
                    };sum+=sample
                }
                val sample=(sum/channels).toInt();mono[count++]=sample.toByte();mono[count++]=(sample shr 8).toByte()
                if(count==mono.size) { count=0;report(PcmMeter.analyze(mono,mono.size,rate)) }
            }
        }.onFailure { count=0;runCatching { report(AudioMeter()) } }
    }
}
