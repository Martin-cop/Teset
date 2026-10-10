package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.roundToInt

/** Discrete supported encoder sizes, with a fine graduated visual ruler. */
internal class QualityRuler(context:Context):View(context) {
    var steps=0;set(value){field=value;invalidate()}
    var selected=0;set(value){field=value;invalidate()}
    var changed:((Int,Boolean)->Unit)?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { isFocusable=true;contentDescription="Recording quality" }
    override fun onDraw(canvas:Canvas) {
        val pad=context.dp(10).toFloat();val mid=height/2f
        paint.isAntiAlias=true;paint.color=0xff000000.toInt();canvas.drawRoundRect(0f,2f,width.toFloat(),height-2f,context.dp(6).toFloat(),context.dp(6).toFloat(),paint)
        val count=maxOf(16,steps*2).coerceAtMost(40);val span=(width-2*pad).coerceAtLeast(1f)
        for(i in 0..count) {
            val x=pad+span*i/count;val h=context.dp(14)/2f
            paint.isAntiAlias=false;paint.color=0xffffffff.toInt();paint.strokeWidth=context.dp(1).toFloat();canvas.drawLine(x,mid-h,x,mid+h,paint)
        }
        val x=pad+span*selected/(steps-1).coerceAtLeast(1)
        paint.color=0xffffdf58.toInt();paint.strokeWidth=context.dp(3).toFloat();canvas.drawLine(x,mid-context.dp(12),x,mid+context.dp(12),paint)
        alpha=if(isEnabled)1f else .45f
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!isEnabled || steps<2)return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val pad=context.dp(10);val index=(((event.x-pad)/(width-2*pad).coerceAtLeast(1))*(steps-1)).roundToInt().coerceIn(0,steps-1)
                if(index!=selected){ selected=index;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);changed?.invoke(index,false) }
            }
            MotionEvent.ACTION_UP -> { changed?.invoke(selected,true);performClick();parent.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> { changed?.invoke(selected,true);parent.requestDisallowInterceptTouchEvent(false) }
        };return true
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        val delta=when(keyCode){KeyEvent.KEYCODE_DPAD_LEFT -> -1;KeyEvent.KEYCODE_DPAD_RIGHT -> 1;else -> return super.onKeyDown(keyCode,event)}
        if(isEnabled && steps>1){selected=(selected+delta).coerceIn(0,steps-1);changed?.invoke(selected,true)};return true
    }
}
