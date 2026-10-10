package com.duorecord.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.duorecord.contracts.*
import com.duorecord.domain.ExportPlan
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class EditingFeature : EditorFactory { override fun create(context:Context):VideoExporter=TransformerExporter(context.applicationContext) }

@UnstableApi
private class TransformerExporter(private val context:Context) : VideoExporter {
    private var transformer:Transformer?=null
    private val handler=Handler(Looper.getMainLooper())
    override suspend fun export(plan:ExportPlan,output:File,progress:(Int)->Unit) = withContext(Dispatchers.Main.immediate) {
        check(transformer==null) { "Export already running." }
        val items=plan.slices.map { slice ->
            val media=MediaItem.Builder().setUri(slice.clip.uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(slice.fromMs).setEndPositionMs(slice.toMs).build()).build()
            EditedMediaItem.Builder(media).build()
        }
        val composition=Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val poll=object:Runnable {
                override fun run() {
                    val t=transformer ?: return
                    val holder=ProgressHolder()
                    if(t.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE)progress(holder.progress)
                    handler.postDelayed(this,400)
                }
            }
            val t=Transformer.Builder(context).apply {
                // Keep a single trimmed file's video codec and enable partial-GOP
                // optimization instead of forcing a full high-resolution H.264 encode.
                experimentalSetTrimOptimizationEnabled(plan.slices.size==1)
                if(plan.slices.size>1)setVideoMimeType(MimeTypes.VIDEO_H264)
            }.setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object:Transformer.Listener {
                    override fun onCompleted(composition:Composition,exportResult:ExportResult) {
                        handler.removeCallbacks(poll);transformer=null;progress(100)
                        if(continuation.isActive)continuation.resume(Unit)
                    }
                    override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException) {
                        handler.removeCallbacks(poll);transformer=null
                        if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Export failed: ${exportException.errorCodeName}",exportException))
                    }
                }).build()
            transformer=t
            continuation.invokeOnCancellation { handler.post { handler.removeCallbacks(poll);transformer?.cancel();transformer=null } }
            try { t.start(composition,output.absolutePath);handler.post(poll) }
            catch(error:Throwable) { handler.removeCallbacks(poll);t.cancel();transformer=null;if(continuation.isActive)continuation.resumeWithException(error) }
        }
    }
    override fun cancel() { handler.post { transformer?.cancel();transformer=null } }
}
