package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/** Clips all list drawing, including glows and decorations, below the toolbar. */
internal class ClippedListFrame(context:Context):FrameLayout(context) {
    init { clipChildren=true;clipToPadding=true }
    override fun dispatchDraw(canvas:Canvas) {
        val saved=canvas.save();canvas.clipRect(0,0,width,height)
        super.dispatchDraw(canvas);canvas.restoreToCount(saved)
    }
}
internal class SelectAllCheckBox(context:Context):CheckBox(context) {
    private val check=object:Drawable() {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG);var partial=false
        override fun draw(canvas:Canvas) {
            val d=resources.displayMetrics.density
            val rect=RectF(bounds.left+3*d,bounds.top+3*d,bounds.right-3*d,bounds.bottom-3*d)
            paint.color=Palette.mint;paint.strokeWidth=2*d;paint.style=if(isChecked || partial)Paint.Style.FILL else Paint.Style.STROKE
            canvas.drawRoundRect(rect,2*d,2*d,paint)
            if(isChecked || partial) {
                paint.color=Color.BLACK;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND
                if(partial)canvas.drawLine(rect.left+4*d,rect.centerY(),rect.right-4*d,rect.centerY(),paint)
                else canvas.drawPath(Path().apply { moveTo(rect.left+3*d,rect.centerY());lineTo(rect.centerX()-1*d,rect.bottom-4*d);lineTo(rect.right-3*d,rect.top+4*d) },paint)
            }
        }
        override fun getIntrinsicWidth()=context.dp(24)
        override fun getIntrinsicHeight()=context.dp(24)
        override fun setAlpha(alpha:Int) { paint.alpha=alpha }
        override fun setColorFilter(filter:ColorFilter?) { paint.colorFilter=filter }
        @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    init { buttonDrawable=check;textSize=12f;setTextColor(Palette.text);setPadding(0,0,context.dp(4),0);contentDescription="Select all files" }
    fun bind(all:Boolean,some:Boolean) {
        isChecked=all;check.partial=some && !all
        stateDescription=if(all)"All selected" else if(some)"Partly selected" else "None selected"
        check.invalidateSelf()
    }
}
/** A fixed 32 dp thumb with a standard wider invisible touch target. */
internal class ArchiveScrollHandle(context:Context,private val list:RecyclerView):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging=false;private var grab=0f
    private val listener=object:RecyclerView.OnScrollListener() { override fun onScrolled(rv:RecyclerView,dx:Int,dy:Int) { invalidate() } }
    init { isFocusable=true;contentDescription="Drag to scroll recordings" }
    private fun maxOffset()=(list.computeVerticalScrollRange()-list.computeVerticalScrollExtent()).coerceAtLeast(0)
    fun thumbBounds():RectF {
        val size=context.dp(32).toFloat().coerceAtMost(height.toFloat())
        val inset=context.dp(4).toFloat()
        val travel=(height-size-2*inset).coerceAtLeast(0f)
        val fraction=if(maxOffset()>0)list.computeVerticalScrollOffset().toFloat()/maxOffset() else 0f
        val top=inset+travel*fraction.coerceIn(0f,1f)
        return RectF(width-context.dp(14).toFloat(),top,width-context.dp(4).toFloat(),top+size)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();list.addOnScrollListener(listener) }
    override fun onDetachedFromWindow() { list.removeOnScrollListener(listener);super.onDetachedFromWindow() }
    override fun onDraw(canvas:Canvas) {
        if(maxOffset()==0)return
        paint.color=if(dragging)0xffffdf58.toInt() else 0xffbfc5c9.toInt()
        canvas.drawRoundRect(thumbBounds(),context.dp(3).toFloat(),context.dp(3).toFloat(),paint)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        val thumb=thumbBounds()
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(maxOffset()==0 || event.y<thumb.top-context.dp(12) || event.y>thumb.bottom+context.dp(12))return false
                dragging=true;grab=event.y-thumb.top;parent.requestDisallowInterceptTouchEvent(true);invalidate();return true
            }
            MotionEvent.ACTION_MOVE -> if(dragging) {
                val inset=context.dp(4).toFloat();val travel=(height-thumb.height()-2*inset).coerceAtLeast(1f)
                val target=((event.y-grab-inset)/travel).coerceIn(0f,1f)*maxOffset()
                list.scrollBy(0,(target-list.computeVerticalScrollOffset()).toInt());invalidate();return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> if(dragging) { dragging=false;parent.requestDisallowInterceptTouchEvent(false);invalidate();if(event.actionMasked==MotionEvent.ACTION_UP)performClick();return true }
        }
        return false
    }
    override fun performClick():Boolean { super.performClick();return true }
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.SeekBar"
        info.rangeInfo=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0f,maxOffset().toFloat(),list.computeVerticalScrollOffset().toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action:Int,args:android.os.Bundle?):Boolean {
        if(action in setOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            list.scrollBy(0,list.height*if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1);invalidate();return true
        }
        return super.performAccessibilityAction(action,args)
    }
}
