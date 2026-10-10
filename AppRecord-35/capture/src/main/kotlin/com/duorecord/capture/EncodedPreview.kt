package com.duorecord.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.util.concurrent.atomic.AtomicInteger

/** Decodes the exact samples sent to the MP4 muxer. Never blocks or substitutes a raw camera feed. */
internal class EncodedPreview(private val keyframe:()->Unit,private val state:(String)->Unit) {
    private val thread=HandlerThread("DuoRecord-preview").apply { start() }
    private val handler=Handler(thread.looper)
    private val queued=AtomicInteger()
    private val epoch=AtomicInteger()
    private var decoderEpoch=0
    private var format:MediaFormat?=null
    private var surface:Surface?=null
    private var decoder:MediaCodec?=null
    private var waitForKey=true
    private var first=true
    @Volatile private var closed=false
    fun format(value:MediaFormat) { handler.post { format=value;configure() } }
    fun attach(value:Surface?) { handler.post { surface=value;configure() } }
    fun sample(value:EncodedSample) {
        if(closed)return
        if(queued.incrementAndGet()>6) { queued.decrementAndGet();epoch.incrementAndGet();return }
        val sampleEpoch=epoch.get()
        handler.post {
            queued.decrementAndGet()
            try {
                val codec=decoder ?: return@post
                if(sampleEpoch!=epoch.get())return@post
                if(decoderEpoch!=sampleEpoch) { codec.flush();decoderEpoch=sampleEpoch;waitForKey=true;keyframe() }
                drain()
                // A backlog/drop invalidates predictive frames; resume at a fresh keyframe.
                if(waitForKey && !value.key)return@post
                val index=codec.dequeueInputBuffer(0)
                if(index<0) { codec.flush();waitForKey=true;keyframe();return@post }
                val buffer=codec.getInputBuffer(index)!!
                check(buffer.capacity()>=value.bytes.size) { "Preview decoder buffer too small." }
                buffer.clear();buffer.put(value.bytes)
                codec.queueInputBuffer(index,0,value.bytes.size,value.ptsUs,0)
                waitForKey=false;drain()
            } catch(t:Throwable) { failed(t) }
        }
    }
    private fun configure() {
        releaseDecoder()
        val target=surface ?: return
        val f=format ?: return
        if(!target.isValid)return
        try {
            val codec=MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            decoder=codec
            codec.configure(f,target,null,0);codec.start();waitForKey=true;first=true;decoderEpoch=epoch.get()
            state("Waiting for encoded frame…");keyframe();handler.post(pump)
        } catch(t:Throwable) { failed(t) }
    }
    private val pump=object:Runnable {
        override fun run() {
            if(decoder==null || closed)return
            try { drain() } catch(t:Throwable) { failed(t);return }
            handler.postDelayed(this,20)
        }
    }
    private fun drain() {
        val codec=decoder ?: return
        val info=MediaCodec.BufferInfo()
        while(true) {
            val index=codec.dequeueOutputBuffer(info,0)
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)continue
            if(index<0)break
            val frame=info.flags and (MediaCodec.BUFFER_FLAG_END_OF_STREAM or MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0
            codec.releaseOutputBuffer(index,frame)
            if(first && frame) { first=false;state("") }
        }
    }
    private fun failed(t:Throwable) { releaseDecoder();state("Preview unavailable: ${t.message ?: "decoder error"}") }
    private fun releaseDecoder() {
        handler.removeCallbacks(pump)
        decoder?.let { runCatching { it.stop() };runCatching { it.release() } };decoder=null
    }
    fun release() { closed=true;handler.post { releaseDecoder();surface=null;thread.quitSafely() } }
}
