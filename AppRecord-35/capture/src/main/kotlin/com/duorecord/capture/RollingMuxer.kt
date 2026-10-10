package com.duorecord.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.duorecord.contracts.*
import com.duorecord.domain.*
import java.nio.ByteBuffer
import java.util.ArrayDeque

internal data class EncodedSample(val bytes:ByteArray,val ptsUs:Long,val flags:Int) {
    val key:Boolean get()=flags and MediaCodec.BUFFER_FLAG_KEY_FRAME!=0
}

/** Encoders stay open at segment boundaries. Each stream waits for the other stream's
 * watermark. A late AAC callback cannot lose the final audio packets of the previous file. */
internal class RollingMuxer(
    private val repository:MediaRepository, private val config:RecordingConfig,
    private val originWallMs:Long, private val listener:CaptureListener,
    private val camera:CameraSource,private val sessionId:String
) {
    private var segmentIndex=0
    private var videoFormat:MediaFormat?=null
    private var audioFormat:MediaFormat?=null
    private val pendingVideo=ArrayDeque<EncodedSample>()
    private val pendingAudio=ArrayDeque<EncodedSample>()
    private var muxer:MediaMuxer?=null
    private var audioOutput:java.io.OutputStream?=null
    private var output:OutputFile?=null
    private var videoTrack=-1;private var audioTrack=-1
    private var baseUs=0L;private var lastVideoUs=0L;private var lastAudioUs=-1L
    private var videoSamples=0;private var audioSamples=0
    private var receivedAudioUs=Long.MIN_VALUE
    private var closed=false;private var splitRequested=false
    private var paused=false
    private var resumeAfterUs=Long.MIN_VALUE
    private var pausedAtUs=0L
    private var timelineOffsetUs=0L
    private var needResumeKey=false

    /** The encoder remains alive. No sample from the pause interval reaches storage. */
    @Synchronized fun setPaused(value:Boolean,atUs:Long) {
        if(closed || paused==value)return
        paused=value
        if(value) {
            if(config.audioOnly) { drainAudioOnly() }
            else { drainVideo(true);flushAudio(lastVideoUs+1_000_000L/config.fps,false) }
            pausedAtUs=atUs
            pendingVideo.clear();pendingAudio.clear();receivedAudioUs=Long.MIN_VALUE
        } else { timelineOffsetUs+=(atUs-pausedAtUs).coerceAtLeast(0);resumeAfterUs=atUs;needResumeKey=!config.audioOnly }
    }

    @Synchronized fun format(video:Boolean,format:MediaFormat) {
        if(closed || config.photoMode)return
        if(video)videoFormat=format else audioFormat=format
        if(config.audioOnly)drainAudioOnly() else drainVideo()
    }
    @Synchronized fun video(sample:EncodedSample) {
        if(closed || config.photoMode || paused || sample.ptsUs<resumeAfterUs)return
        check(pendingVideo.size<config.fps*10) { "Audio stream stalled. Recording stopped." }
        if(needResumeKey) { if(!sample.key)return;needResumeKey=false }
        pendingVideo.add(sample.copy(ptsUs=sample.ptsUs-timelineOffsetUs))
        drainVideo()
    }
    @Synchronized fun audio(sample:EncodedSample) {
        if(closed || config.photoMode || paused || sample.ptsUs<resumeAfterUs)return
        val adjusted=sample.copy(ptsUs=sample.ptsUs-timelineOffsetUs)
        pendingAudio.add(adjusted)
        receivedAudioUs=maxOf(receivedAudioUs,adjusted.ptsUs)
        if(config.audioOnly) { drainAudioOnly();return }
        check(pendingAudio.size<1500) { "Video stream stopped." }
        drainVideo()
        flushAudio(lastVideoUs,false)
    }
    private fun drainVideo(final:Boolean=false) {
        if(videoFormat==null || audioFormat==null)return
        while(pendingVideo.isNotEmpty() && (final || pendingVideo.first.ptsUs<=receivedAudioUs)) {
            writeVideo(pendingVideo.removeFirst())
        }
    }
    @Synchronized fun split() { splitRequested=true }
    private fun open(atUs:Long) {
        val f=repository.createSegment(originWallMs+(atUs+timelineOffsetUs)/1000,camera,sessionId,segmentIndex++)
        output=f
        try {
            if(config.audioOnly)audioOutput=android.os.ParcelFileDescriptor.AutoCloseOutputStream(f.descriptor)
            else muxer=MediaMuxer(f.descriptor.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also {
                videoTrack=it.addTrack(videoFormat!!)
                audioTrack=it.addTrack(audioFormat!!);it.start()
            }
            baseUs=atUs;lastAudioUs=-1;videoSamples=0;audioSamples=0;splitRequested=false
        } catch(t:Throwable) {
            runCatching { audioOutput?.close() };audioOutput=null;runCatching { muxer?.release() };muxer=null;runCatching { f.descriptor.close() };repository.damageSegment(f.clip.id);output=null;throw t
        }
    }
    private fun drainAudioOnly() {
        if(audioFormat==null)return
        while(pendingAudio.isNotEmpty()) {
            val s=pendingAudio.first
            if(audioOutput==null)open(s.ptsUs)
            else if(audioSamples>0 && (splitRequested || s.ptsUs-baseUs>=config.segmentSeconds*1_000_000L)) {
                finish(s.ptsUs);open(s.ptsUs)
            }
            flushAudio(s.ptsUs,false)
        }
    }
    private fun writeVideo(sample:EncodedSample) {
        if(muxer==null) {
            if(!sample.key)return
            open(sample.ptsUs)
        } else if(sample.key && videoSamples>0 && (splitRequested || sample.ptsUs-baseUs>=config.segmentSeconds*1_000_000L)) {
            flushAudio(sample.ptsUs,true)
            finish(sample.ptsUs)
            open(sample.ptsUs)
        }
        if(sample.ptsUs<baseUs)return
        val info=MediaCodec.BufferInfo().apply { set(0,sample.bytes.size,sample.ptsUs-baseUs,sample.flags) }
        muxer!!.writeSampleData(videoTrack,ByteBuffer.wrap(sample.bytes),info)
        lastVideoUs=sample.ptsUs;videoSamples++
        flushAudio(lastVideoUs,false)
    }
    private fun flushAudio(untilUs:Long,exclusive:Boolean) {
        if(muxer==null && audioOutput==null)return
        while(pendingAudio.isNotEmpty()) {
            val s=pendingAudio.first
            if(s.ptsUs>untilUs || (exclusive && s.ptsUs==untilUs))break
            pendingAudio.removeFirst()
            if(s.ptsUs<baseUs || s.ptsUs<=lastAudioUs)continue
            val info=MediaCodec.BufferInfo().apply { set(0,s.bytes.size,s.ptsUs-baseUs,s.flags) }
            if(config.audioOnly)audioOutput!!.apply { write(Adts.header(s.bytes.size));write(s.bytes) }
            else muxer!!.writeSampleData(audioTrack,ByteBuffer.wrap(s.bytes),info)
            lastAudioUs=s.ptsUs;audioSamples++
        }
    }
    private fun finish(endUs:Long) {
        val f=output ?: return
        val m=muxer;val stream=audioOutput
        muxer=null;audioOutput=null;output=null
        var failure:Throwable?=null
        try { stream?.flush();m?.stop() } catch(t:Throwable) { failure=t }
        finally { runCatching { stream?.close() }.onFailure { failure=it };runCatching { m?.release() };runCatching { f.descriptor.close() } }
        if(failure==null && (config.audioOnly || videoSamples>0) && audioSamples>0) {
            repository.completeSegment(f.clip.id,((endUs-baseUs)/1000).coerceAtLeast(1))
            repository.get(f.clip.id)?.let { listener.onSegment(it) }
        } else {
            repository.damageSegment(f.clip.id)
            throw IllegalStateException("Last segment incomplete. Earlier videos are safe.",failure)
        }
    }
    @Synchronized fun close() {
        if(closed)return
        closed=true
        try {
            if(config.audioOnly) { drainAudioOnly();finish(lastAudioUs+21_334L);return }
            drainVideo(true)
            flushAudio(lastVideoUs+1_000_000L/config.fps,false)
            finish(lastVideoUs+1_000_000L/config.fps)
        } catch(t:Throwable) {
            if(muxer!=null || audioOutput!=null)runCatching { finish(lastVideoUs+1_000_000L/config.fps) }
            throw t
        }
        finally { pendingVideo.clear();pendingAudio.clear() }
    }
}
