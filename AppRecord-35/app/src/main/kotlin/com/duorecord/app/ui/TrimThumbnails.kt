package com.duorecord.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.duorecord.domain.TrimResourcePolicy
import kotlinx.coroutines.CancellationException

/** No video decoder for unknown/large inputs; all resources have bounded ownership. */
internal object TrimThumbnails {
    fun load(context:Context,uri:Uri,durationMs:Long,lowRam:Boolean,keepGoing:()->Boolean,report:(String)->Unit):List<Bitmap> {
        val frames=mutableListOf<Bitmap>()
        try {
            val extractor=MediaExtractor()
            val size=try {
                extractor.setDataSource(context,uri,null)
                (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }?.let {
                    (if(it.containsKey(MediaFormat.KEY_WIDTH))it.getInteger(MediaFormat.KEY_WIDTH) else null) to
                    (if(it.containsKey(MediaFormat.KEY_HEIGHT))it.getInteger(MediaFormat.KEY_HEIGHT) else null)
                }
            } finally { extractor.release() }
            if(!TrimResourcePolicy.decodeThumbnails(size?.first,size?.second,lowRam) || !keepGoing())return emptyList()
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,uri)
                for(index in 0..7) {
                    if(!keepGoing())break
                    retriever.getScaledFrameAtTime(durationMs*1000*index/8,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,96,72)?.let { frames+=it }
                }
            } finally { retriever.release() }
            if(!keepGoing()) { frames.forEach { it.recycle() };return emptyList() }
            return frames
        } catch(t:Throwable) {
            frames.forEach { if(!it.isRecycled)it.recycle() }
            if(t is CancellationException)throw t
            report("Trim thumbnails unavailable: ${t.javaClass.simpleName}: ${t.message}")
            return emptyList()
        }
    }
}
