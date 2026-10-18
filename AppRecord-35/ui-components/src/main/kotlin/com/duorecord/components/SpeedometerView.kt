package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import java.util.Locale
import kotlin.math.*

/** Old-car speedometer on the supplied 0..100 km/h dial; the needle follows [speed] like a sprung mechanical needle and rests on the 0/100 stop pins. */
class SpeedometerView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs) {
    /** IMAGE draws speedometer_needle.png (100×99 px, soft when enlarged ~12×); VECTOR draws the same needle as a sharp path. */
    enum class Needle { IMAGE,VECTOR }
    companion object {
        const val MAX_SPEED=100f
        // Measured on speedometer_dial (1248×1228 px): hub centre and the angle of every 5 km/h tick, degrees clockwise from 12 o'clock.
        // The printed scale is uneven (40–70 tighter, 70–80 wider); one degrees-per-km/h factor would miss 70 by about 5°.
        private const val DIAL_W=1248f;private const val DIAL_H=1228f;private const val PIVOT_X=624.8f;private const val PIVOT_Y=612.2f;private const val CAP_R=92f
        private val TICKS=floatArrayOf(-135.2f,-121.5f,-107.5f,-93.7f,-79.9f,-65.2f,-51.5f,-36.6f,-24.2f,-12.2f,-.1f,12.2f,24.25f,36.6f,49.1f,64.1f,79.9f,93.5f,107.4f,121.2f,135.2f)
        // speedometer_needle (100×99 px): hub centre; at 12.4× its hub matches the dial cap and its tip reaches the ticks. The picture points at 79.9° (80 km/h).
        private const val NEEDLE_X=50.01f;private const val NEEDLE_Y=49.41f;private const val NEEDLE_SCALE=12.4f;private const val NEEDLE_ANGLE=79.9f
        // ~1.1 Hz spring, damping ratio 0.75: about 3% overshoot; a 0→60 swing comes to rest in ~1.6 s.
        private const val STIFFNESS=49f;private const val DAMPING=10.5f
        /** Needle angle for [speed] km/h in degrees clockwise from 12 o'clock, interpolated between the measured ticks. */
        fun angleFor(speed:Float):Float { val v=speed.coerceIn(0f,MAX_SPEED)/5;val i=min(v.toInt(),TICKS.size-2);return TICKS[i]+(TICKS[i+1]-TICKS[i])*(v-i) }
    }
    /** km/h, clamped to 0..100; the needle swings to it. [snapTo] moves it without the swing. */
    var speed=0f;set(value) { field=value.coerceIn(0f,MAX_SPEED);contentDescription=String.format(Locale.US,"Speedometer %.0f km/h",field);invalidate() }
    var needle=Needle.VECTOR;set(value) { field=value;invalidate() }
    private var angle=angleFor(0f);private var velocity=0f;private var lastFrame=0L
    private val dial=BitmapFactory.decodeResource(resources,R.drawable.speedometer_dial)
    private val needleImage=BitmapFactory.decodeResource(resources,R.drawable.speedometer_needle)
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter=PorterDuffColorFilter(0x59000000,PorterDuff.Mode.SRC_IN) }
    private val capShader=BitmapShader(dial,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);private val capMatrix=Matrix()
    private val capPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader=capShader }
    // Vector needle in dial pixels pointing to 12 o'clock: tapered red body, white tip and a short tail, colours sampled from speedometer_needle.
    private val needlePath=Path().apply {
        moveTo(22f,0f);lineTo(6f,-485f);quadTo(0f,-505f,-6f,-485f);lineTo(-22f,0f);lineTo(-19f,141f);arcTo(RectF(-19f,122f,19f,160f),180f,-180f,false);close()
    }
    private val needlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader=LinearGradient(0f,0f,0f,-497f,intArrayOf(0xfff14e46.toInt(),0xfff14e46.toInt(),0xfff17062.toInt(),0xfff09d90.toInt(),0xfff6e4dc.toInt(),0xfffffdf6.toInt()),floatArrayOf(0f,.5f,.7f,.85f,.93f,1f),Shader.TileMode.CLAMP)
    }
    private val dst=RectF()
    init { contentDescription="Speedometer 0 km/h" }
    /** Moves the needle to [speed] km/h at once. */
    fun snapTo(speed:Float) { this.speed=speed;angle=angleFor(this.speed);velocity=0f;lastFrame=0L }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val w=resolveSize((300*resources.displayMetrics.density).roundToInt(),widthMeasureSpec)
        setMeasuredDimension(w,resolveSize((w*DIAL_H/DIAL_W).roundToInt(),heightMeasureSpec))
    }
    override fun onDraw(c:Canvas) {
        val now=AnimationUtils.currentAnimationTimeMillis();val target=angleFor(speed)
        var dt=if(lastFrame==0L)0f else min(now-lastFrame,50L)/1000f;lastFrame=now
        while(dt>0f) { val h=min(dt,.004f);velocity+=(STIFFNESS*(target-angle)-DAMPING*velocity)*h;angle+=velocity*h;dt-=h }
        if(angle<TICKS.first()||angle>TICKS.last()) { angle=angle.coerceIn(TICKS.first(),TICKS.last());velocity=0f }
        val settled=abs(target-angle)<.05f&&abs(velocity)<1f
        if(settled) { angle=target;velocity=0f;lastFrame=0L }
        val s=min(width/DIAL_W,height/DIAL_H);val ox=(width-DIAL_W*s)/2;val oy=(height-DIAL_H*s)/2;val x=ox+PIVOT_X*s;val y=oy+PIVOT_Y*s
        dst.set(ox,oy,ox+DIAL_W*s,oy+DIAL_H*s);c.drawBitmap(dial,null,dst,imagePaint)
        drawNeedle(c,x+5*s,y+8*s,s,shadowPaint);drawNeedle(c,x,y,s,null)
        // The dial's own hub cap is redrawn over the needle root, as on a real gauge.
        capMatrix.setScale(s,s);capMatrix.postTranslate(ox,oy);capShader.setLocalMatrix(capMatrix);c.drawCircle(x,y,CAP_R*s,capPaint)
        if(!settled)postInvalidateOnAnimation()
    }
    private fun drawNeedle(c:Canvas,x:Float,y:Float,s:Float,shadow:Paint?) {
        c.save();c.translate(x,y);c.rotate(angle)
        if(needle==Needle.IMAGE) { c.rotate(-NEEDLE_ANGLE);c.scale(NEEDLE_SCALE*s,NEEDLE_SCALE*s);c.drawBitmap(needleImage,-NEEDLE_X,-NEEDLE_Y,shadow ?: imagePaint) }
        else { c.scale(s,s);c.drawPath(needlePath,shadow ?: needlePaint) }
        c.restore()
    }
}
