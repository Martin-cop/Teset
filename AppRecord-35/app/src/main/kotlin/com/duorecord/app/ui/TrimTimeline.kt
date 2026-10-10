package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.view.*

internal class TrimTimeline(context:Context,val duration:Long):View(context) {
    var from=0L;private set
    var to=duration;private set
    var frames=emptyList<Bitmap>();set(value){field=value;invalidate()}
    var changed:((Long,Long)->Unit)?=null
    private var leftHandle=true
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun x(time:Long)=context.dp(16)+(width-context.dp(32))*time.toFloat()/duration.coerceAtLeast(1)
    init { contentDescription="Drag left and right handles to trim video";isFocusable=true }
    override fun onDraw(c:Canvas) {
        val top=8f;val bottom=height-8f
        paint.color=0xff303030.toInt();c.drawRoundRect(0f,top,width.toFloat(),bottom,20f,20f,paint)
        frames.forEachIndexed { i,b -> c.drawBitmap(b,null,RectF(width*i.toFloat()/frames.size,top,width*(i+1f)/frames.size,bottom),paint) }
        paint.color=0xb0000000.toInt();c.drawRect(0f,top,x(from),bottom,paint);c.drawRect(x(to),top,width.toFloat(),bottom,paint)
        paint.color=0xff4285ff.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=context.dp(4).toFloat()
        c.drawRoundRect(x(from),top,x(to),bottom,12f,12f,paint);paint.style=Paint.Style.FILL
        for(pos in listOf(x(from),x(to))){c.drawRoundRect(pos-context.dp(7),top,pos+context.dp(7),bottom,8f,8f,paint);paint.color=Color.WHITE;c.drawLine(pos,top+16,pos,bottom-16,paint);paint.color=0xff4285ff.toInt()}
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(duration<=0)return false
        if(e.actionMasked==MotionEvent.ACTION_DOWN)leftHandle=kotlin.math.abs(e.x-x(from))<=kotlin.math.abs(e.x-x(to))
        when(e.actionMasked){MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP -> {
            parent.requestDisallowInterceptTouchEvent(true)
            val value=(((e.x-context.dp(16))/(width-context.dp(32)).coerceAtLeast(1)).coerceIn(0f,1f)*duration).toLong()
            val minimum=minOf(250L,duration)
            if(leftHandle)from=value.coerceIn(0,(to-minimum).coerceAtLeast(0)) else to=value.coerceIn((from+minimum).coerceAtMost(duration),duration)
            invalidate();changed?.invoke(from,to)
            if(e.actionMasked==MotionEvent.ACTION_UP){performClick();parent.requestDisallowInterceptTouchEvent(false)}
        };MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)};return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}
