package com.duorecord.domain

/** A slider step is an actual, camera/codec-supported output size, not a cosmetic percentage. */
data class CaptureQuality(val width:Int,val height:Int,val bitRate:Int) {
    val key:String get()="${width}x${height}"
    val pixels:Long get()=width.toLong()*height
}

object CapturePolicy {
    fun sources(mode:CameraMode):Set<CameraSource> = when(mode) {
        CameraMode.FRONT -> setOf(CameraSource.FRONT)
        CameraMode.REAR -> setOf(CameraSource.REAR)
        CameraMode.BOTH -> setOf(CameraSource.FRONT,CameraSource.REAR)
    }
    fun toggle(active:Set<CameraSource>,mode:CameraMode):Set<CameraSource> {
        val target=sources(mode)
        return if(active.containsAll(target))active-target else active+target
    }
    fun choose(options:List<CaptureQuality>,key:String):CaptureQuality {
        require(options.isNotEmpty()) { "No supported recording quality." }
        return options.firstOrNull { it.key==key }
            ?: options.minBy { kotlin.math.abs(it.pixels-480L*640) }
    }
    fun validate(config:RecordingConfig) {
        require(listOf(config.photoMode,config.audioOnly,config.screenMode,config.mixedMode).count { it }<=1) { "Choose either interval photos or audio only." }
        require(config.archiveRefreshSeconds in 1..3600 && config.archiveInitialLimit in 1..10000) { "Archive refresh must be 1–3600 seconds and initial count 1–10000." }
        if(config.mixedMode)require(config.mixedSources.isNotEmpty() && config.mixedBackground in config.mixedSources) { "Choose a background from the selected sources." }
        require(config.photoIntervalSeconds in 1..86400) { "Photo interval must be 1–86400 seconds." }
        require(config.maxRecordingMinutes in 0..1440) { "Recording duration must be 0–1440 minutes; 0 means no time limit." }
        require(config.segmentSeconds in 10..86400) { "File duration must be 10–86400 seconds." }
        require(config.fps in 1..120) { "Frame rate must be 1–120 fps; camera support is checked before recording." }
        require(config.quotaBytes in 1048576L..10995116277760L && config.savedQuotaBytes in 1048576L..10995116277760L) { "Storage limits must be 1 MB–10 TB." }
        require(config.reserveBytes in 1048576L..10995116277760L) { "Free-space reserve must be 1 MB–10 TB." }
        require(config.stopBatteryPercent in 1..95) { "Battery threshold must be 1–95%." }
    }
}
