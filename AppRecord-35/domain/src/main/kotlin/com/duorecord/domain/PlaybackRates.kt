package com.duorecord.domain

object PlaybackRates {
    /** Quarter-speed steps below 1×; integer speeds through 30×, with 0 meaning pause. */
    val values:List<Float> = listOf(0f,.25f,.5f,.75f)+(1..30).map { it.toFloat() }
    fun validate(speed:Float) { require(speed.isFinite() && speed in 0f..30f) { "Playback speed must be 0–30×." } }
}
