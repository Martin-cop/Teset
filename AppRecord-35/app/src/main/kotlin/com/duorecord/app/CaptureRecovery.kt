package com.duorecord.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.duorecord.domain.*
import org.json.JSONObject

/** One durable user-started session. Retry never resets its original end time. */
internal data class SavedCapture(val config:RecordingConfig,val startWall:Long,val endWall:Long,val endElapsed:Long,val boot:Int,val light:Boolean,val reason:String="") {
    fun remaining(nowWall:Long,nowElapsed:Long,currentBoot:Int):Long=
        if(endWall==Long.MAX_VALUE)Long.MAX_VALUE else (if(boot==currentBoot)endElapsed-nowElapsed else endWall-nowWall).coerceAtLeast(0)
}
internal class CaptureRecovery(private val context:Context) {
    private val prefs=context.getSharedPreferences("capture_recovery",0)
    var userPaused:Boolean
        get()=prefs.getBoolean("userPaused",false)
        set(value) { check(prefs.edit().putBoolean("userPaused",value).commit()) { "Could not save pause state." } }
    private fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    @Synchronized fun read():SavedCapture?=runCatching {
        val o=JSONObject(prefs.getString("session",null) ?: return null);val c=o.getJSONObject("config")
        SavedCapture(RecordingConfig(
            segmentSeconds=c.getInt("segment"),quotaBytes=c.getLong("quota"),reserveBytes=c.getLong("reserve"),
            fps=c.getInt("fps"),preferHevc=c.getBoolean("hevc"),stopBatteryPercent=c.getInt("battery"),
            width=c.getInt("width"),height=c.getInt("height"),cameraMode=CameraMode.valueOf(c.getString("mode")),savedQuotaBytes=c.getLong("saved"),
            frontQuality=c.getString("front"),rearQuality=c.getString("rear"),videoBitRate=c.getInt("bitrate"),simpleTheme=c.getBoolean("simple"),
            maxRecordingMinutes=c.getInt("minutes"),screenInternalAudio=c.optBoolean("screenInternal",false),mixedMode=c.optBoolean("mixedMode",false),
            mixedFront=c.optBoolean("mixedFront",true),mixedRear=c.optBoolean("mixedRear",false),mixedScreen=c.optBoolean("mixedScreen",true),
            mixedBackground=runCatching { CameraSource.valueOf(c.optString("mixedBackground","SCREEN")) }.getOrDefault(CameraSource.SCREEN),mixedLayout=c.optString("mixedLayout",""),continueVideoWithoutMic=c.optBoolean("continueVideoWithoutMic",true),screenMode=c.optBoolean("screenMode",false),screenAudio=c.optBoolean("screenAudio",false),audioOnly=c.optBoolean("audioOnly",false),photoMode=c.getBoolean("photos"),photoIntervalSeconds=c.getInt("interval")
        ),o.getLong("start"),o.getLong("end"),o.getLong("elapsedEnd"),o.getInt("boot"),o.getBoolean("light"),o.optString("reason"))
    }.getOrNull()
    @Synchronized fun save(s:SavedCapture) {
        val c=s.config
        val config=JSONObject().put("segment",c.segmentSeconds).put("quota",c.quotaBytes).put("reserve",c.reserveBytes).put("fps",c.fps)
            .put("hevc",c.preferHevc).put("battery",c.stopBatteryPercent).put("width",c.width).put("height",c.height).put("mode",c.cameraMode.name)
            .put("saved",c.savedQuotaBytes).put("front",c.frontQuality).put("rear",c.rearQuality).put("bitrate",c.videoBitRate).put("simple",c.simpleTheme)
            .put("screenInternal",c.screenInternalAudio).put("mixedMode",c.mixedMode).put("mixedFront",c.mixedFront).put("mixedRear",c.mixedRear).put("mixedScreen",c.mixedScreen).put("mixedBackground",c.mixedBackground.name).put("mixedLayout",c.mixedLayout).put("minutes",c.maxRecordingMinutes).put("continueVideoWithoutMic",c.continueVideoWithoutMic).put("screenMode",c.screenMode).put("screenAudio",c.screenAudio).put("audioOnly",c.audioOnly).put("photos",c.photoMode).put("interval",c.photoIntervalSeconds)
        val o=JSONObject().put("config",config).put("start",s.startWall).put("end",s.endWall).put("elapsedEnd",s.endElapsed).put("boot",s.boot)
            .put("light",s.light).put("reason",s.reason)
        check(prefs.edit().putString("session",o.toString()).commit()) { "Could not save recovery state." }
    }
    @Synchronized fun begin(config:RecordingConfig,light:Boolean,scheduledEnd:Long):SavedCapture {
        userPaused=false
        prefs.edit().putString("pauses","[]").commit()
        val now=System.currentTimeMillis()
        val end=if(config.maxRecordingMinutes==0 && scheduledEnd==0L)Long.MAX_VALUE else scheduledEnd.takeIf { it>0 } ?: (now+config.maxRecordingMinutes*60000L)
        return SavedCapture(config,now,end,if(end==Long.MAX_VALUE)Long.MAX_VALUE else SystemClock.elapsedRealtime()+(end-now).coerceAtLeast(0),boot(),light).also(::save)
    }
    fun remaining(s:SavedCapture)=s.remaining(System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot())
    @Synchronized fun noteUserPause(paused:Boolean,at:Long=System.currentTimeMillis()) {
        val items=org.json.JSONArray(prefs.getString("pauses","[]"))
        val last=if(items.length()>0)items.getJSONObject(items.length()-1) else null
        if(paused && (last==null || last.optLong("end")>0))items.put(JSONObject().put("start",at).put("end",0))
        if(!paused && last!=null && last.optLong("end")==0L)last.put("end",at)
        prefs.edit().putString("pauses",items.toString()).commit()
    }
    fun pausedBetween(start:Long,end:Long):Long = runCatching {
        val items=org.json.JSONArray(prefs.getString("pauses","[]"));var total=0L
        for(i in 0 until items.length()) { val o=items.getJSONObject(i);val finish=o.optLong("end").takeIf { it>0 } ?: end;total+=(minOf(end,finish)-maxOf(start,o.getLong("start"))).coerceAtLeast(0) }
        total
    }.getOrDefault(0L)
    @Synchronized fun pause(reason:String) { read()?.let { save(it.copy(reason=reason)) } }
    @Synchronized fun update(config:RecordingConfig) { read()?.let { save(it.copy(config=config)) } }
    @Synchronized fun light(enabled:Boolean) { read()?.let { save(it.copy(light=enabled)) } }
    @Synchronized fun clear() { check(prefs.edit().remove("session").remove("userPaused").commit()) { "Could not clear recovery state." } }
}
