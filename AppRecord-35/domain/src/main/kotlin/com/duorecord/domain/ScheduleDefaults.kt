package com.duorecord.domain

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object ScheduleDefaults {
    const val maxMinutes=600
    fun durationText(minutes:Int)=if(minutes<60)"$minutes min" else "${minutes/60} h"+if(minutes%60==0)"" else " ${minutes%60} min"
    fun nextHour(now:LocalDateTime)=now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    fun segmentSeconds(value:String,unit:Int):Int {
        val number=value.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a number for file duration.")
        val factor=when(unit) { 0 -> 1;1 -> 60;2 -> 3600;else -> error("Invalid file-duration unit.") }
        val seconds=runCatching { number.multiply(java.math.BigDecimal(factor)).setScale(0,java.math.RoundingMode.HALF_UP).intValueExact() }.getOrElse { throw IllegalArgumentException("File duration is invalid or too large.") }
        require(seconds in 10..86400) { "File duration must be between 10 seconds and 24 hours." }
        return seconds
    }
}
