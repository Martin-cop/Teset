package com.duorecord.domain

/** Each physical file appears once. Overlap pairs only ordinary front/rear files from the same session. */
data class RecordingRow(val rear:Clip?=null,val front:Clip?=null,val legacy:Clip?=null,val audio:Clip?=null,val screen:Clip?=null) {
    val clips:List<Clip> get()=listOfNotNull(rear,front,legacy,audio,screen)
    val startMs:Long get()=clips.minOf { it.startMs }
    val bytes:Long get()=clips.sumOf { it.bytes }
    val durationMs:Long get()=clips.maxOf { it.durationMs }
}
object RecordingRows {
    fun group(clips:List<Clip>):List<RecordingRow> {
        val rows=mutableListOf<RecordingRow>()
        val sessions=clips.filter { it.sessionId!=null && it.kind!=ClipKind.EXTRACT && it.camera in setOf(CameraSource.REAR,CameraSource.FRONT) }.groupBy { it.sessionId to it.isPhoto }
        for(session in sessions.values) {
            val rear=session.filter { it.camera==CameraSource.REAR }.sortedBy { it.startMs }
            val front=session.filter { it.camera==CameraSource.FRONT }.sortedBy { it.startMs }
            var r=0;var f=0
            fun end(clip:Clip)=if(clip.status==ClipStatus.WRITING)Long.MAX_VALUE else clip.endMs
            while(r<rear.size && f<front.size) {
                val back=rear[r];val forward=front[f]
                val overlap=maxOf(back.startMs,forward.startMs)<minOf(end(back),end(forward))
                // A damaged file may have no known duration; preserve a corresponding start, without enabling playback.
                val sameUnknownStart=(back.durationMs==0L || forward.durationMs==0L) && back.segmentIndex==forward.segmentIndex && kotlin.math.abs(back.startMs-forward.startMs)<=500
                val samePhoto=back.isPhoto && forward.isPhoto && back.segmentIndex==forward.segmentIndex && kotlin.math.abs(back.startMs-forward.startMs)<=500
                if(overlap || sameUnknownStart || samePhoto) { rows+=RecordingRow(back,forward);r++;f++ }
                else if(back.startMs<=forward.startMs) { rows+=RecordingRow(rear=back);r++ }
                else { rows+=RecordingRow(front=forward);f++ }
            }
            while(r<rear.size)rows+=RecordingRow(rear=rear[r++])
            while(f<front.size)rows+=RecordingRow(front=front[f++])
        }
        clips.filter { it.sessionId==null || it.kind==ClipKind.EXTRACT || it.camera in setOf(CameraSource.LEGACY,CameraSource.AUDIO,CameraSource.SCREEN,CameraSource.MIXED) }.forEach { clip ->
            rows+=when(clip.camera) { CameraSource.REAR -> RecordingRow(rear=clip);CameraSource.FRONT -> RecordingRow(front=clip);CameraSource.LEGACY -> RecordingRow(legacy=clip);CameraSource.AUDIO -> RecordingRow(audio=clip);CameraSource.SCREEN,CameraSource.MIXED -> RecordingRow(screen=clip) }
        }
        return rows.sortedWith(compareByDescending<RecordingRow> { it.startMs }.thenBy { it.clips.first().id })
    }
}
