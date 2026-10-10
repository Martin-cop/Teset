package com.duorecord.app

import android.content.Context
import android.os.SystemClock

/** Active recording time only. Monotonic time excludes user pause, startup and recovery downtime. */
internal class CaptureClock(context:Context?=null,private val now:()->Long={SystemClock.elapsedRealtime()}) {
    private val prefs=context?.getSharedPreferences("active_capture_clock",0)
    private var session=prefs?.getLong("session",0) ?: 0L
    private var accumulated=prefs?.getLong("elapsed",0) ?: 0L
    private var activeSince:Long?=null
    private var savedAt=0L
    @Synchronized fun transition(start:Long,phase:Phase) {
        val at=now()
        if(start>0 && start!=session) { session=start;accumulated=0;activeSince=null }
        if(phase==Phase.RECORDING && activeSince==null)activeSince=at
        if(phase!=Phase.RECORDING) { activeSince?.let { accumulated+=(at-it).coerceAtLeast(0) };activeSince=null }
        save(at)
    }
    @Synchronized fun elapsedMs():Long { val at=now();if(at-savedAt>=5000)save(at);return elapsed(at) }
    private fun elapsed(at:Long)=accumulated+(activeSince?.let { (at-it).coerceAtLeast(0) } ?: 0)
    private fun save(at:Long) { savedAt=at;prefs?.edit()?.putLong("session",session)?.putLong("elapsed",elapsed(at))?.apply() }
}
