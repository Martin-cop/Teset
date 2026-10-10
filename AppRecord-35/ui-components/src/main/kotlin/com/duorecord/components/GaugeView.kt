package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.view.View

/** Set fraction in 0..1; usable without AppRecord application/services or permissions. */
class GaugeView(context:Context):View(context) {
    enum class Kind(val index:Int) { BATTERY(0),THERMOMETER(1),STORAGE(3) }
    var kind=Kind.BATTERY;set(value) { field=value;invalidate() }
    var fraction=0f;set(value) { field=value.coerceIn(0f,1f);invalidate() }
    var chargingPulse=0f;set(value) { field=value.coerceIn(0f,1f);invalidate() }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        val d=resources.displayMetrics.density
        GaugeDrawing.draw(canvas,paint,RectF(12*d,12*d,width-12*d,height-12*d),kind.index,(fraction*10).toInt(),fraction,true,chargingPulse)
    }
}
