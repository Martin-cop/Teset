package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Reusable timestamped column chart; null readings leave gaps instead of inventing measurements. */
class HistoryChartView(context:Context):View(context) {
    var points:List<Pair<Long,Double?>> = emptyList();set(value) { field=value;invalidate() }
    var unit="W"
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas) {
        val d=resources.displayMetrics.density;val b=RectF(50*d,20*d,width-20*d,height-40*d)
        pen.color=0xff080c10.toInt();c.drawColor(pen.color)
        pen.style=Paint.Style.FILL
        val valid=points.mapNotNull { it.second };val low=minOf(0.0,valid.minOrNull() ?: 0.0);val high=maxOf(low+1.0,valid.maxOrNull() ?: 1.0)
        pen.textSize=11*d;pen.textAlign=Paint.Align.RIGHT
        for(i in 0..4) { val y=b.bottom-b.height()*i/4;pen.color=0xff3b464c.toInt();pen.strokeWidth=d;c.drawLine(b.left,y,b.right,y,pen);pen.color=Color.RED;c.drawText(String.format(Locale.US,"%.1f",low+(high-low)*i/4),b.left-6*d,y+4*d,pen) }
        if(points.size<2) { pen.textAlign=Paint.Align.CENTER;c.drawText("Collecting $unit history…",b.centerX(),b.centerY(),pen);return }
        val start=points.first().first;val end=points.last().first.coerceAtLeast(start+1);val columns=maxOf(1,(b.width()/(5*d)).toInt())
        val grouped=points.groupBy { (((it.first-start).toDouble()/(end-start))*columns).toInt().coerceIn(0,columns-1) }
        val baseline=b.bottom-((0.0-low)/(high-low)).toFloat()*b.height()
        pen.style=Paint.Style.FILL
        for((bucket,samples) in grouped) {
            val readings=samples.mapNotNull { it.second?.takeIf(Double::isFinite) }
            if(readings.isEmpty())continue
            val value=readings.average();val y=b.bottom-((value-low)/(high-low)).toFloat()*b.height()
            val x=b.left+bucket*b.width()/columns
            pen.color=if(value>=0)0xff65ff99.toInt() else 0xffff6565.toInt()
            c.drawRect(x,minOf(y,baseline),x+b.width()/columns*.72f,maxOf(y,baseline),pen)
        }
        pen.color=Color.RED
        val format=SimpleDateFormat("HH:mm",Locale.US);pen.textAlign=Paint.Align.LEFT;c.drawText(format.format(Date(start)),b.left,height-15*d,pen);pen.textAlign=Paint.Align.RIGHT;c.drawText(format.format(Date(end)),b.right,height-15*d,pen)
    }
}
