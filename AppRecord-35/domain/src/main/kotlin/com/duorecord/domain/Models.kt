package com.duorecord.domain

enum class CameraSource { LEGACY, REAR, FRONT, AUDIO, SCREEN, MIXED }
enum class CameraMode { REAR, FRONT, BOTH }
enum class ClipKind { ROLLING, SELECTED, EXTRACT }
enum class ClipStatus { WRITING, READY, DAMAGED }
data class Clip(
    val id: String, val uri: String, val name: String,
    val startMs: Long, val durationMs: Long, val bytes: Long,
    val kind: ClipKind, val status: ClipStatus,
    val sourceId: String? = null, val camera: CameraSource = CameraSource.LEGACY,
    val sessionId: String? = null, val segmentIndex: Int = 0
) {
    val isPhoto:Boolean get()=name.endsWith(".jpg",true)
    val isAudio:Boolean get()=camera==CameraSource.AUDIO || name.endsWith(".m4a",true) || name.endsWith(".aac",true)
    val endMs: Long get() = startMs + durationMs
    val protected: Boolean get() = kind != ClipKind.ROLLING
}
data class RecordingConfig(
    val segmentSeconds: Int = 1800, val quotaBytes: Long = 8L * 1024 * 1024 * 1024,
    val reserveBytes: Long = 1024L * 1024 * 1024, val fps: Int = 15,
    val preferHevc: Boolean = true, val stopBatteryPercent: Int = 10,
    val width: Int = 480, val height: Int = 640, val cameraMode: CameraMode = CameraMode.BOTH,
    val savedQuotaBytes: Long = 2L * 1024 * 1024 * 1024,
    val frontQuality: String = "480x640", val rearQuality: String = "480x640",
    val videoBitRate: Int = 1_000_000, val simpleTheme: Boolean = false,
    val maxRecordingMinutes: Int = 120,
    val photoMode:Boolean=false,val photoIntervalSeconds:Int=10,val photoEpochMs:Long=0,
    val audioOnly:Boolean=false,val continueVideoWithoutMic:Boolean=true,val screenMode:Boolean=false,val screenAudio:Boolean=false, val screenInternalAudio:Boolean=false,
    val mixedMode:Boolean=false,val mixedFront:Boolean=true,val mixedRear:Boolean=false,val mixedScreen:Boolean=true,
    val mixedBackground:CameraSource=CameraSource.SCREEN,val mixedLayout:String="",
    val archiveRefreshSeconds:Int=2,val archiveInitialLimit:Int=100
) {
    val requiresProjection:Boolean get()=screenMode || mixedMode
    val mixedSources:Set<CameraSource> get()=buildSet { if(mixedFront)add(CameraSource.FRONT);if(mixedRear)add(CameraSource.REAR);if(mixedScreen)add(CameraSource.SCREEN) }
}
data class Slice(val clip: Clip, val fromMs: Long, val toMs: Long) {
    val durationMs: Long get() = toMs - fromMs
}
data class ExportPlan(val startMs: Long, val endMs: Long, val slices: List<Slice>) {
    val mediaDurationMs: Long get() = slices.sumOf { it.durationMs }
}

object TimelinePlanner {
    /** Prefer ordinary recordings, then protected copies. Never duplicate overlapping coverage. */
    fun plan(clips: List<Clip>, startMs: Long, endMs: Long, gapToleranceMs: Long = 200): ExportPlan {
        require(endMs > startMs) { "End must be after start." }
        require(clips.filter { !it.isPhoto && !it.isAudio && it.status == ClipStatus.READY && it.endMs > startMs && it.startMs < endMs }.map { it.camera }.distinct().size <= 1) { "Select one camera before planning an export." }
        val candidates = clips.filter { !it.isPhoto && !it.isAudio && it.status == ClipStatus.READY && it.durationMs > 0 && it.endMs > startMs && it.startMs < endMs }
        val slices = mutableListOf<Slice>()
        var cursor = startMs
        while (cursor < endMs) {
            val covering = candidates.filter { it.startMs <= cursor && it.endMs > cursor }
            val chosen = covering.minWithOrNull(compareBy<Clip> { if (it.kind == ClipKind.ROLLING) 0 else 1 }.thenByDescending { it.endMs })
                ?: candidates.filter { it.startMs > cursor && it.startMs - cursor <= gapToleranceMs }
                    .minByOrNull { it.startMs }
                ?: throw IllegalArgumentException("Part of this time range is missing.")
            val begin = maxOf(cursor, chosen.startMs)
            val finish = minOf(endMs, chosen.endMs)
            require(finish > begin) { "No playable video in this range." }
            slices += Slice(chosen, begin - chosen.startMs, finish - chosen.startMs)
            cursor = finish
        }
        return ExportPlan(startMs, endMs, slices)
    }
}

object RetentionPolicy {
    /** Only finalized rolling clips may ever be removed. Selected copies, extracts and pinned inputs are excluded. */
    fun candidates(clips: List<Clip>, quotaBytes: Long, freeBytes: Long, reserveBytes: Long, pinned: Set<String> = emptySet()): List<Clip> {
        var total = clips.filter { it.kind == ClipKind.ROLLING }.sumOf { it.bytes }
        var free = freeBytes
        val result = mutableListOf<Clip>()
        for (clip in clips.filter { it.kind == ClipKind.ROLLING && it.status == ClipStatus.READY && it.id !in pinned }.sortedBy { it.startMs }) {
            if (total <= quotaBytes && free >= reserveBytes) break
            result += clip; total -= clip.bytes; free += clip.bytes
        }
        return result
    }
}

class SavedQuotaExceeded(val requiredBytes:Long,val quotaBytes:Long):IllegalStateException("Saved space is full. Increase its limit or remove the oldest saved files.")
object SavedRetentionPolicy {
    fun candidates(clips:List<Clip>,incomingBytes:Long,quotaBytes:Long,pinned:Set<String> = emptySet()):List<Clip> {
        require(incomingBytes<=quotaBytes) { "This file is larger than the saved limit. Increase the limit." }
        var total=clips.filter { it.protected }.sumOf { it.bytes }+incomingBytes
        val result=mutableListOf<Clip>()
        for(clip in clips.filter { it.protected && it.status==ClipStatus.READY && it.id !in pinned }.sortedBy { it.startMs }) {
            if(total<=quotaBytes)break
            result+=clip;total-=clip.bytes
        }
        require(total<=quotaBytes) { "Saved files in use cannot be removed. Increase the limit." }
        return result
    }
}
