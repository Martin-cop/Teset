package com.duorecord.capture

import android.annotation.SuppressLint
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.view.Surface
import com.duorecord.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class VideoEncoder(config:RecordingConfig,handler:Handler,private val mux:RollingMuxer,private val error:(String)->Unit,previewState:(String)->Unit) {
    private val preview=EncodedPreview({ keyframe() },previewState)
    private val eos=CountDownLatch(1)
    private val codec:MediaCodec
    val mime:String
    val surface:Surface
    val bitRate:Int
    init {
        val types=if(config.preferHevc)listOf(MediaFormat.MIMETYPE_VIDEO_HEVC,MediaFormat.MIMETYPE_VIDEO_AVC) else listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
        var picked:MediaCodec?=null;var pickedMime="";var pickedSurface:Surface?=null;var pickedRate=0
        for(type in types) {
            val candidates=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder && it.isHardwareAccelerated && it.supportedTypes.any { t -> t.equals(type,true) } }
            for(info in candidates) {
                var candidate:MediaCodec?=null
                var inputSurface:Surface?=null
                try {
                    val caps=info.getCapabilitiesForType(type)
                    if(!caps.videoCapabilities.areSizeAndRateSupported(config.width,config.height,config.fps.toDouble()))continue
                    val rate=caps.videoCapabilities.bitrateRange.clamp(config.videoBitRate)
                    val format=MediaFormat.createVideoFormat(type,config.width,config.height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE,rate)
                        setInteger(MediaFormat.KEY_FRAME_RATE,config.fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)
                        setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
                        setInteger(MediaFormat.KEY_PRIORITY,0)
                    }
                    candidate=MediaCodec.createByCodecName(info.name)
                    candidate.setCallback(object:MediaCodec.Callback() {
                        override fun onInputBufferAvailable(c:MediaCodec,index:Int)=Unit
                        override fun onOutputFormatChanged(c:MediaCodec,format:MediaFormat) { try { if(!config.photoMode)mux.format(true,format);preview.format(format) } catch(t:Throwable) { error(t.message ?: "Video format error.") } }
                        override fun onError(c:MediaCodec,e:MediaCodec.CodecException) { error("Encoder stopped: ${e.diagnosticInfo}") }
                        override fun onOutputBufferAvailable(c:MediaCodec,index:Int,info:MediaCodec.BufferInfo) {
                            try {
                                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                                    val buffer=c.getOutputBuffer(index)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                    val bytes=ByteArray(info.size);buffer.get(bytes)
                                    val sample=EncodedSample(bytes,info.presentationTimeUs,info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
                                    if(!config.photoMode)mux.video(sample);preview.sample(sample)
                                }
                            } catch(t:Throwable) { error(t.message ?: "Could not write video.") }
                            finally { runCatching { c.releaseOutputBuffer(index,false) };if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)eos.countDown() }
                        }
                    },handler)
                    candidate.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface=candidate.createInputSurface()
                    candidate.start()
                    picked=candidate;pickedMime=type;pickedSurface=inputSurface;pickedRate=rate;break
                } catch(_:Throwable) { runCatching { candidate?.release() };runCatching { inputSurface?.release() } }
            }
            if(picked!=null)break
        }
        codec=picked ?: run { preview.release();kotlin.error("No compatible hardware encoder at this quality. Try a lower resolution.") }
        bitRate=pickedRate
        mime=pickedMime
        surface=pickedSurface!!
    }
    fun setPreview(surface:Surface?) { preview.attach(surface) }
    fun keyframe() { runCatching { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0) }) } }
    fun finish() { runCatching { codec.signalEndOfInputStream() };eos.await(3,TimeUnit.SECONDS) }
    fun release() { preview.release();runCatching { codec.stop() };runCatching { codec.release() };runCatching { surface.release() } }
}

@SuppressLint("MissingPermission")
internal class AudioEncoder(private val originNs:Long,initialMuxes:List<RollingMuxer>,private val started:()->Unit={},private val continueWhenSilenced:Boolean=false,private val microphone:(MicrophoneState)->Unit={},private val levels:(FloatArray)->Unit={},private val meter:((com.duorecord.domain.AudioMeter)->Unit)?=null,private val error:(String)->Unit) {
    private val muxes=initialMuxes.toMutableList()
    private val muxLock=Any()
    private var outputFormat:MediaFormat?=null
    fun add(mux:RollingMuxer) { synchronized(muxLock) { outputFormat?.let { mux.format(false,it) };muxes+=mux } }
    fun remove(mux:RollingMuxer) { synchronized(muxLock) { muxes.remove(mux) } }
    private val rate=48_000
    private val recorder:AudioRecord
    private val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    @Volatile private var running=false
    private var worker:Thread?=null
    init {
        val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        recorder=try { AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min,8192)).build()
        } catch(t:Throwable) { codec.release();throw t }
        try {
            check(recorder.state==AudioRecord.STATE_INITIALIZED) { "Microphone unavailable." }
            val format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,64_000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4096)
            }
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
        } catch(t:Throwable) {
            recorder.release();codec.release();throw t
        }
    }
    fun start() {
        running=true
        worker=thread(name="DuoRecord-audio") {
            try {
                microphone(MicrophoneState.STARTING)
                recorder.startRecording()
                check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING) { "Could not start audio." }
                started()
                val pcm=ByteArray(2048);val info=MediaCodec.BufferInfo()
                var frames=0L;var anchorNs=0L;var lastPts=-1L;var lastMeterNs=0L
                val continuity=MicrophoneContinuity(continueWhenSilenced)
                while(running) {
                    val n=recorder.read(pcm,0,pcm.size,AudioRecord.READ_BLOCKING)
                    if(n<=0) { if(running)error("Microphone stream stopped.");break }
                    val decision=continuity.observe(recorder.activeRecordingConfiguration?.isClientSilenced)
                    if(decision.changed)microphone(decision.state)
                    if(decision.stop) { error("Android blocked the microphone.");break }
                    // Never preserve samples from a system-silenced interval. Keep AAC timestamps
                    // advancing so both video muxers can continue without restarting cameras.
                    if(decision.zeroInput)java.util.Arrays.fill(pcm,0,n,0.toByte())
                    levels(com.duorecord.domain.AudioLevels.measure(pcm,n))
                    val meterNow=System.nanoTime()
                    if(meter!=null && meterNow-lastMeterNs>=50_000_000L) { lastMeterNs=meterNow;meter.invoke(com.duorecord.domain.PcmMeter.analyze(pcm,n,rate)) }
                    if(anchorNs==0L)anchorNs=System.nanoTime()-n/2L*1_000_000_000L/rate
                    val timestamp=AudioTimestamp()
                    val ptsNs=if(recorder.getTimestamp(timestamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS)
                        timestamp.nanoTime+(frames-timestamp.framePosition)*1_000_000_000L/rate else anchorNs+frames*1_000_000_000L/rate
                    val pts=((ptsNs-originNs)/1000).coerceAtLeast(lastPts+1).coerceAtLeast(0)
                    var index=codec.dequeueInputBuffer(10_000)
                    while(index<0 && running) { drain(info);index=codec.dequeueInputBuffer(10_000) }
                    if(index<0)break
                    codec.getInputBuffer(index)!!.apply { clear();put(pcm,0,n) }
                    codec.queueInputBuffer(index,0,n,pts,0);lastPts=pts;frames+=n/2
                    drain(info)
                }
                val index=codec.dequeueInputBuffer(500_000)
                if(index>=0)codec.queueInputBuffer(index,0,0,(lastPts+21_334).coerceAtLeast(0),MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                val deadline=System.nanoTime()+1_000_000_000L
                while(System.nanoTime()<deadline && !drain(info))Thread.sleep(5)
            } catch(t:Throwable) { if(running)error(t.message ?: "Audio recording error.") }
            finally { running=false;microphone(MicrophoneState.OFF);runCatching { recorder.stop() };recorder.release();runCatching { codec.stop() };codec.release() }
        }
    }
    private fun drain(info:MediaCodec.BufferInfo):Boolean {
        while(true) {
            val index=codec.dequeueOutputBuffer(info,0)
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)synchronized(muxLock) { outputFormat=codec.outputFormat;muxes.forEach { it.format(false,codec.outputFormat) } }
            else if(index>=0) {
                try {
                    if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                        val buffer=codec.getOutputBuffer(index)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size)
                        val bytes=ByteArray(info.size);buffer.get(bytes);synchronized(muxLock) { muxes.forEach { it.audio(EncodedSample(bytes,info.presentationTimeUs,0)) } }
                    }
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)return true
                } finally { codec.releaseOutputBuffer(index,false) }
            } else return false
        }
    }
    fun finish() {
        running=false;runCatching { recorder.stop() }
        val t=worker
        if(t==null) { recorder.release();runCatching { codec.stop() };codec.release() } else if(t!=Thread.currentThread())t.join(3000)
    }
}
