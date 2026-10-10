package com.duorecord.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PowerSample(val at:Long,val voltage:Double?,val amps:Double?,val watts:Double?,val connected:Boolean)
/** Bounded on-device history. Writes run outside the UI thread; missing hardware data remains missing. */
internal class PowerHistory(private val context:Context) {
    companion object {
        private val lock=Any()
        private var loaded=false
        private val samples=ArrayList<PowerSample>()
        private var newest=0L
        private val writer=java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r,"power-history").apply { isDaemon=true } }
        const val INTERVAL_MS=10000L
        const val WINDOW_MS=6*60*60*1000L
        fun retain(values:List<PowerSample>,at:Long)=values.filter { it.at>=at-WINDOW_MS && it.at<=at }.sortedBy { it.at }.takeLast(2161)
    }
    private fun load() {
        if(loaded)return
        val data=runCatching { JSONArray(context.getSharedPreferences("power_history",0).getString("samples","[]")) }.getOrDefault(JSONArray())
        for(i in 0 until data.length())runCatching { val o=data.getJSONObject(i)
            fun number(k:String):Double? = if(o.isNull(k))null else o.getDouble(k)
            samples+=PowerSample(o.getLong("at"),number("v"),number("a"),number("w"),o.getBoolean("connected")) }
        newest=samples.lastOrNull()?.at ?: 0;loaded=true
    }
    fun record(health:DeviceHealth,at:Long=System.currentTimeMillis()) {
        writer.execute { synchronized(lock) {
            load()
            if(at>=newest && at-newest<INTERVAL_MS && samples.lastOrNull()?.connected==health.charging)return@synchronized
            val next=PowerSample(at,health.batteryVoltageV,health.batteryCurrentMa?.div(1000),health.batteryPowerWatts,health.charging)
            val kept=retain(samples+next,at);samples.clear();samples.addAll(kept);newest=at
            val json=JSONArray();samples.forEach { s -> json.put(JSONObject().put("at",s.at).put("v",s.voltage ?: JSONObject.NULL).put("a",s.amps ?: JSONObject.NULL).put("w",s.watts ?: JSONObject.NULL).put("connected",s.connected)) }
            context.getSharedPreferences("power_history",0).edit().putString("samples",json.toString()).apply()
        } }
    }
    fun read(at:Long=System.currentTimeMillis()):List<PowerSample> = synchronized(lock) { load();retain(samples,at) }
}
