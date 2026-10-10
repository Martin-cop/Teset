package com.duorecord.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Bounded persistent journal. Abrupt process exits are recovered on the next app launch. */
internal class RecordingDiagnostics(private val context:Context) {
    private val prefs=context.getSharedPreferences("recording_diagnostics",0)
    @Synchronized fun entries():List<String> = runCatching {
        val array=JSONArray(prefs.getString("events","[]"))
        (array.length()-1 downTo 0).map { array.getJSONObject(it).let { item ->
            "${java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss",java.util.Locale.US).format(java.util.Date(item.getLong("time")))}\n${item.getString("reason")}\n${item.optString("details")}" }
        }
    }.getOrDefault(emptyList())
    @Synchronized fun add(reason:String,details:String="") {
        val old=runCatching { JSONArray(prefs.getString("events","[]")) }.getOrDefault(JSONArray())
        val next=JSONArray();for(i in maxOf(0,old.length()-199) until old.length())next.put(old.getJSONObject(i))
        next.put(JSONObject().put("time",System.currentTimeMillis()).put("reason",reason.take(1000)).put("details",details.take(12000)))
        prefs.edit().putString("events",next.toString()).commit()
    }
    @Synchronized fun begin(details:String) {
        prefs.edit().putBoolean("active",true).putBoolean("userStop",false).putLong("start",System.currentTimeMillis())
            .putString("session",details).putString("error","").commit()
        heartbeat()
    }
    @Synchronized fun event(reason:String,extra:String="") { heartbeat();add(reason,details()+"\n"+extra) }
    fun visibility(visible:Boolean) { prefs.edit().putBoolean("visible",visible).apply() }
    @Synchronized fun heartbeat() {
        val health=runCatching { DeviceHealthReader.read(context,0,0) }.getOrNull()
        prefs.edit().putLong("heartbeat",System.currentTimeMillis()).putString("snapshot",
            "Model: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}; app ${BuildConfig.VERSION_NAME}\n"+
                "Battery: ${health?.batteryPercent ?: "unknown"}%; battery temperature: ${health?.temperatureText() ?: "unknown"}; heat: ${health?.thermalLabel ?: "unknown"}; free: ${health?.freeBytes ?: "unknown"} bytes; visible: ${prefs.getBoolean("visible",false)}").commit()
    }
    fun error(message:String) { prefs.edit().putString("error",message.take(1000)).commit() }
    fun userStop() { prefs.edit().putBoolean("userStop",true).commit() }
    private fun details()="${prefs.getString("session","")}\nLast checkpoint: ${prefs.getLong("heartbeat",0)}\n${prefs.getString("snapshot","")}\nLast error: ${prefs.getString("error","") }"
    @Synchronized fun finish(reason:String) {
        if(prefs.getBoolean("active",false) && !prefs.getBoolean("userStop",false))add(reason,details())
        prefs.edit().putBoolean("active",false).commit()
    }
    @Synchronized fun crash(error:Throwable) {
        add("Unhandled exception: ${error.javaClass.simpleName}: ${error.message}",details()+"\n"+error.stackTraceToString())
        prefs.edit().putBoolean("active",false).commit()
    }
    @Synchronized fun recover() {
        if(!prefs.getBoolean("active",false))return
        val start=prefs.getLong("start",0)
        val exit=runCatching { context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName,0,10).firstOrNull { it.timestamp>=start } }.getOrNull()
        if(!prefs.getBoolean("userStop",false) && exit?.reason !in listOf(ApplicationExitInfo.REASON_USER_REQUESTED,ApplicationExitInfo.REASON_USER_STOPPED)) {
            val reason=when(exit?.reason) {
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Android ended the process: low memory"
                ApplicationExitInfo.REASON_CRASH -> "Android reported a Java crash"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Android reported a native crash"
                ApplicationExitInfo.REASON_ANR -> "Android reported an unresponsive app (ANR)"
                else -> if(exit==null)"Recording interrupted; exact process-exit reason unavailable" else "Recording interrupted; Android exit reason ${exit.reason}"
            }
            context.getSharedPreferences("runtime",0).edit().putString("lastStop",reason).putLong("stopTime",System.currentTimeMillis()).commit()
            add(reason,details()+"\nAndroid description: ${exit?.description ?: "unavailable"}; status: ${exit?.status ?: "unknown"}; exit time: ${exit?.timestamp ?: "unknown"}")
        }
        prefs.edit().putBoolean("active",false).commit()
    }
}
