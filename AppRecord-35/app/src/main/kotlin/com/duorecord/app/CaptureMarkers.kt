package com.duorecord.app

import android.content.Context
import com.duorecord.domain.CameraSource
import org.json.JSONArray
import org.json.JSONObject

internal data class CaptureMarker(val at:Long,val sessionStart:Long,val sources:Set<CameraSource>,val clipId:String?=null,val offsetMs:Long?=null)
/** Local bookmarks reference wall time; they never alter or protect media automatically. */
internal class CaptureMarkers(context:Context) {
    private val prefs=context.getSharedPreferences("capture_markers",0)
    fun list():List<CaptureMarker> = runCatching {
        val items=JSONArray(prefs.getString("items","[]"))
        (0 until items.length()).mapNotNull { i -> runCatching {
            val item=items.getJSONObject(i);val names=item.getJSONArray("sources")
            CaptureMarker(item.getLong("at"),item.getLong("session"),(0 until names.length()).map { CameraSource.valueOf(names.getString(it)) }.toSet(),item.optString("clip","").takeIf { it.isNotEmpty() },if(item.has("offset"))item.getLong("offset") else null)
        }.getOrNull() }
    }.getOrDefault(emptyList())
    @Synchronized fun add(sessionStart:Long,sources:Set<CameraSource>,at:Long=System.currentTimeMillis(),clipId:String?=null,offsetMs:Long?=null):CaptureMarker {
        require(sources.isNotEmpty()) { "No active capture to mark." }
        val result=CaptureMarker(at,sessionStart,sources,clipId,offsetMs)
        val items=JSONArray()
        (list()+result).takeLast(1000).forEach { mark -> items.put(JSONObject().put("at",mark.at).put("session",mark.sessionStart).put("sources",JSONArray(mark.sources.map { it.name })).apply { mark.clipId?.let { put("clip",it) };mark.offsetMs?.let { put("offset",it) } }) }
        check(prefs.edit().putString("items",items.toString()).commit()) { "Could not save marked moment." }
        return result
    }
}
