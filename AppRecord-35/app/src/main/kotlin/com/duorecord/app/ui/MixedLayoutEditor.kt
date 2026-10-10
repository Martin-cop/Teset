package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.duorecord.domain.*

/** Drag a source frame; drag its lower-right grip to resize; tap its × to hide it. */
internal class MixedLayoutEditor(context:Context,var config:RecordingConfig,private val changed:(RecordingConfig)->Unit):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var source:CameraSource?=null;private var resizing=false;private var lastX=0f;private var lastY=0f
    init { setLayerType(LAYER_TYPE_SOFTWARE,null);contentDescription="Mixed layout. Drag frames to move, lower-right grip to resize, upper-right cross to hide." }
    override fun onDraw(c:Canvas) {
        val d=resources.displayMetrics.density
        val frames=MixedLayout.read(config.mixedLayout)
        config.mixedSources.filter { it!=config.mixedBackground }.forEach { s -> frames[s]?.takeIf { it.visible }?.let { f ->
            val r=RectF(f.x*width,f.y*height,(f.x+f.width)*width,(f.y+f.height)*height)
            p.style=Paint.Style.STROKE;p.strokeWidth=2*d;p.color=Palette.mint;c.drawRoundRect(r,4*d,4*d,p)
            p.style=Paint.Style.FILL;p.color=0xbb000000.toInt();c.drawRect(r.left,r.top,r.right,r.top+24*d,p)
            p.color=Color.WHITE;p.textSize=12*d;p.typeface=Typeface.DEFAULT_BOLD;c.drawText(if(r.width()<70*d)s.name.take(1) else s.name,r.left+5*d,r.top+17*d,p)
            c.drawText("×",r.right-18*d,r.top+18*d,p)
            p.style=Paint.Style.STROKE;p.strokeWidth=3*d;c.drawLine(r.right-14*d,r.bottom-3*d,r.right-3*d,r.bottom-14*d,p)
        } }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val d=resources.displayMetrics.density;val frames=MixedLayout.read(config.mixedLayout).toMutableMap()
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                source=config.mixedSources.toList().asReversed().firstOrNull { s -> val f=frames[s]!!;s!=config.mixedBackground && f.visible && e.x in f.x*width..(f.x+f.width)*width && e.y in f.y*height..(f.y+f.height)*height }
                val s=source ?: return false;val f=frames.getValue(s)
                if(e.x>(f.x+f.width)*width-24*d && e.y<f.y*height+24*d) {
                    frames[s]=f.copy(visible=false);config=config.copy(mixedLayout=MixedLayout.write(frames));changed(config);invalidate();source=null;performClick();return true
                }
                resizing=e.x>(f.x+f.width)*width-30*d && e.y>(f.y+f.height)*height-30*d
                lastX=e.x;lastY=e.y;parent?.requestDisallowInterceptTouchEvent(true);return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s=source ?: return false;val f=frames.getValue(s);val dx=(e.x-lastX)/width;val dy=(e.y-lastY)/height
                frames[s]=(if(resizing)f.copy(width=f.width+dx,height=f.height+dy) else f.copy(x=f.x+dx,y=f.y+dy)).bounded()
                config=config.copy(mixedLayout=MixedLayout.write(frames));changed(config);lastX=e.x;lastY=e.y;invalidate();return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { source=null;parent?.requestDisallowInterceptTouchEvent(false);performClick();return true }
        }
        return false
    }
    override fun performClick():Boolean { super.performClick();return true }
}
/** Keep decoded output and touch coordinates in the exact output aspect ratio. */
internal class MixedPreviewFrame(context:Context,aspect:Float):FrameLayout(context) {
    private val border=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Palette.mint;style=Paint.Style.STROKE;strokeWidth=2*resources.displayMetrics.density }
    override fun dispatchDraw(canvas:Canvas) {
        super.dispatchDraw(canvas)
        if(childCount>0) { val child=getChildAt(0);canvas.drawRect(child.left+1f,child.top+1f,child.right-1f,child.bottom-1f,border) }
    }
    private val aspect=aspect.takeIf { it.isFinite() && it>0 } ?: 1f
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        super.onMeasure(widthSpec,heightSpec)
        val w=minOf(measuredWidth,(measuredHeight*aspect).toInt()).coerceAtLeast(1)
        val h=minOf(measuredHeight,(w/aspect).toInt()).coerceAtLeast(1)
        for(i in 0 until childCount)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        for(i in 0 until childCount) { val v=getChildAt(i);val x=(width-v.measuredWidth)/2;val y=(height-v.measuredHeight)/2;v.layout(x,y,x+v.measuredWidth,y+v.measuredHeight) }
    }
}
