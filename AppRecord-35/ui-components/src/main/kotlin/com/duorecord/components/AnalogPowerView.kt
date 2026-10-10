package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.view.View
import java.util.Locale
import kotlin.math.*

/** Actual numeric readings; unavailable input stays unavailable rather than a simulated needle. */
class AnalogPowerView(context:Context):View(context) {
    var value:Double?=null;set(v) { field=v;invalidate() }
    var unit="V"
    var fullScale=5.0
    var showUnitInValue=true
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { setLayerType(LAYER_TYPE_SOFTWARE,null) }
    override fun onDraw(c:Canvas) {
        val d=resources.displayMetrics.density;val b=RectF(6*d,6*d,width-6*d,height-6*d)
        // Explicit soft outer bands remain visible on both software and hardware canvases.
        p.shader=null;p.clearShadowLayer();p.style=Paint.Style.STROKE
        for((stroke,alpha) in listOf(10f to 28,8f to 48,6f to 78,4f to 130)) {
            p.strokeWidth=stroke*d;p.color=Color.argb(alpha,255,213,42);c.drawRoundRect(b,8*d,8*d,p)
        }
        p.style=Paint.Style.FILL;p.shader=LinearGradient(0f,0f,0f,height.toFloat(),0xff253342.toInt(),0xff080e15.toInt(),Shader.TileMode.CLAMP);p.setShadowLayer(6*d,0f,0f,0xffffd52a.toInt());c.drawRoundRect(b,8*d,8*d,p);p.clearShadowLayer();p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=d;p.color=0xff53758e.toInt();c.drawRoundRect(b,8*d,8*d,p)
        val x=b.centerX();val y=b.top+b.height()*.72f;val radius=min(b.width()*.42f,b.height()*.55f)
        for(i in 0..20) {
            val a=Math.toRadians(210.0+i*6);val major=i%5==0;val inner=radius-if(major)10*d else 5*d
            p.color=if(i>15)0xffff6b54.toInt() else if(i>10)0xffffd966.toInt() else 0xff95dfff.toInt();p.strokeWidth=if(major)1.8f*d else .7f*d
            c.drawLine(x+cos(a).toFloat()*inner,y+sin(a).toFloat()*inner,x+cos(a).toFloat()*radius,y+sin(a).toFloat()*radius,p)
        }
        value?.let { v ->
            val a=Math.toRadians(210+abs(v).coerceAtMost(fullScale)/fullScale*120)
            p.color=0xffff655d.toInt();p.strokeWidth=2*d;c.drawLine(x,y,x+cos(a).toFloat()*(radius-1*d),y+sin(a).toFloat()*(radius-1*d),p)
        }
        p.style=Paint.Style.FILL;p.color=0xffcde8ef.toInt();c.drawCircle(x,y,4*d,p)
        p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=16*d;p.color=0xffe5f5ff.toInt();c.drawText(unit,x,b.top+23*d,p)
        p.typeface=Typeface.MONOSPACE;p.textSize=12*d;p.color=0xff65ff99.toInt()
        c.drawText(value?.let { String.format(Locale.US,if(showUnitInValue)"%.2f %s" else "%.2f",it,unit) } ?: if(showUnitInValue)"— $unit" else "—",x,b.bottom-9*d,p)
        contentDescription="$unit ${value ?: "unavailable"}; scale 0 to $fullScale; signed number, magnitude needle"
    }
}
