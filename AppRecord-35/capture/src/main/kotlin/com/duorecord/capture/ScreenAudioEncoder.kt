package com.duorecord.capture

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import com.duorecord.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Both sources share one 48 kHz timeline. Source switches do not reopen the AAC codec. */
@SuppressLint("MissingPermission")
internal class ScreenAudioEncoder(private val projection:MediaProjection?,private val originNs:Long,private val muxes:List<RollingMuxer>,config:RecordingConfig,private val microphone:(MicrophoneState)->Unit,private val error:(String)->Unit) {
    @Volatile var ambient=config.screenAudio
    @Volatile var internal=config.screenInternalAudio
    private val running=AtomicBoolean(false)
    private var worker:Thread?=null
    fun start() {
        running.set(true)
        worker=thread(name="AppRecord-screen-audio") {
            var mic:AudioRecord?=null;var playback:AudioRecord?=null;var codec:MediaCodec?=null
            try {
                codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,48000,1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE,96000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4096)
                },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
                val info=MediaCodec.BufferInfo();var frames=0L
                val anchor=System.nanoTime();val a=ByteArray(2048);val b=ByteArray(2048)
                var lastState=MicrophoneState.OFF
                fun drain():Boolean {
                    while(true) {
                        val index=codec.dequeueOutputBuffer(info,0)
                        if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)muxes.forEach { it.format(false,codec.outputFormat) }
                        else if(index>=0) {
                            try {
                                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                                    val buffer=codec.getOutputBuffer(index)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                    val bytes=ByteArray(info.size);buffer.get(bytes)
                                    muxes.forEach { it.audio(EncodedSample(bytes,info.presentationTimeUs,0)) }
                                }
                                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)return true
                            } finally { codec.releaseOutputBuffer(index,false) }
                        } else return false
                    }
                }
                while(running.get()) {
                    if(ambient && mic==null)mic=record(false).also { it.startRecording() }
                    if(!ambient && mic!=null) { mic?.stop();mic?.release();mic=null }
                    if(internal && playback==null)playback=record(true).also { it.startRecording() }
                    if(!internal && playback!=null) { playback?.stop();playback?.release();playback=null }
                    a.fill(0);b.fill(0)
                    fun read(r:AudioRecord?,buf:ByteArray):Int {
                        if(r==null)return 0
                        val n=r.read(buf,0,buf.size,AudioRecord.READ_NON_BLOCKING)
                        check(n>=0) { "Audio input disconnected ($n)." };return n
                    }
                    read(mic,a);read(playback,b)
                    val silenced=mic?.activeRecordingConfiguration?.isClientSilenced==true
                    if(silenced)a.fill(0)
                    val state=if(mic==null)MicrophoneState.OFF else if(silenced)MicrophoneState.SILENCED else MicrophoneState.ACTIVE
                    if(state!=lastState) { lastState=state;microphone(state) }
                    val pcm=AudioLevels.mix(a,b,a.size,ambient,internal)
                    var index=codec.dequeueInputBuffer(10000)
                    while(index<0 && running.get()) { drain();index=codec.dequeueInputBuffer(10000) }
                    if(index<0)break
                    codec.getInputBuffer(index)!!.apply { clear();put(pcm) }
                    codec.queueInputBuffer(index,0,pcm.size,((anchor-originNs)/1000+frames*1000000/48000).coerceAtLeast(0),0)
                    frames+=pcm.size/2;drain()
                    val remaining=anchor+frames*1000000000/48000-System.nanoTime()
                    if(remaining>0)Thread.sleep(remaining/1000000,(remaining%1000000).toInt())
                }
                val index=codec.dequeueInputBuffer(500000)
                if(index>=0)codec.queueInputBuffer(index,0,0,((anchor-originNs)/1000+frames*1000000/48000).coerceAtLeast(0),MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                val deadline=System.nanoTime()+1000000000
                while(System.nanoTime()<deadline && !drain())Thread.sleep(5)
            } catch(t:Throwable) { if(running.get())error(t.message ?: "Screen audio failed.") }
            finally {
                running.set(false);microphone(MicrophoneState.OFF)
                listOf(mic,playback).forEach { runCatching { it?.stop() };runCatching { it?.release() } }
                runCatching { codec?.stop() };runCatching { codec?.release() }
            }
        }
    }
    private fun record(device:Boolean):AudioRecord {
        val builder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(16384,AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)))
        if(device)builder.setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(projection ?: kotlin.error("Internal audio requires screen-sharing permission."))
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build())
        else builder.setAudioSource(MediaRecorder.AudioSource.MIC)
        return builder.build().also { check(it.state==AudioRecord.STATE_INITIALIZED) { "Selected audio source unavailable." } }
    }
    fun finish() { running.set(false);worker?.takeIf { it!=Thread.currentThread() }?.join(3000) }
}
