package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import com.duorecord.domain.AudioMeter
import java.util.Locale
import kotlin.math.max

/** Real mono spectrum, dBFS RMS/peak meter, 1.2 s peak hold and clipping latch. */
class AudioSpectrumView(context:Context):View(context) {
    companion object { /** Visual emphasis only; the RMS, peak and CLIP readings remain unscaled. */
        fun displayAmplitude(raw:Float)=(kotlin.math.sqrt(raw.coerceIn(0f,1f))*1.35f).coerceIn(0f,1f)
    }
    var meter=AudioMeter();set(value) {
        field=value
        val now=SystemClock.uptimeMillis()
        for(i in value.bands.indices)if(value.bands[i]>=peaks[i] || now>=peakUntil[i]) { peaks[i]=value.bands[i];peakUntil[i]=now+1200 }
        if(value.clipped)clipUntil=now+1200
        contentDescription=String.format(Locale.US,"Microphone spectrum. RMS %.1f dBFS, peak %.1f dBFS%s",value.rmsDb,value.peakDb,if(value.clipped)". Clipping" else "")
        invalidate()
    }
    var sourceLabel="MIC  •  MONO SPECTRUM";set(value) { field=value;invalidate() }
    private val peaks=FloatArray(20);private val peakUntil=LongArray(20);private var clipUntil=0L
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription="Live microphone spectrum and dBFS level";setLayerType(LAYER_TYPE_SOFTWARE,null) }
    override fun onDraw(c:Canvas) {
        val d=resources.displayMetrics.density;val now=SystemClock.uptimeMillis()
        val plot=RectF(31*d,27*d,width-9*d,height-57*d)
        p.style=Paint.Style.FILL;p.color=0xff0b1014.toInt();c.drawRoundRect(RectF(2*d,2*d,width-2*d,height-2*d),8*d,8*d,p)
        p.color=0xff65ff99.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=d;c.drawRoundRect(RectF(2*d,2*d,width-2*d,height-2*d),8*d,8*d,p);p.style=Paint.Style.FILL
        p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=10*d;p.color=0xffe9edf0.toInt();p.textAlign=Paint.Align.LEFT
        c.drawText(sourceLabel,10*d,17*d,p)
        val clip=now<clipUntil;p.color=if(clip)0xffff4545.toInt() else 0xff4c555b.toInt();p.textAlign=Paint.Align.RIGHT;c.drawText("CLIP",width-10*d,17*d,p)
        for(level in 0..60 step 20) {
            val y=plot.top+plot.height()*level/60
            p.color=0xff26343b.toInt();p.strokeWidth=.6f*d;c.drawLine(plot.left,y,plot.right,y,p)
            p.color=0xff9eafb7.toInt();p.textSize=9*d;p.textAlign=Paint.Align.RIGHT;c.drawText(if(level==0)"0" else "−$level",plot.left-5*d,y+3*d,p)
        }
        val step=plot.width()/20;val gap=max(d,step*.25f)
        for(i in 0..19) {
            val raw=meter.bands.getOrElse(i){0f};val amplitude=displayAmplitude(raw);val x=plot.left+i*step
            for(seg in 0..11) {
                val level=(seg+1)/12f;val y=plot.bottom-plot.height()*level
                p.color=if(level>amplitude)0xff17262a.toInt() else if(level>.65f)0xffff4545.toInt() else if(level>.40f)0xffffdf58.toInt() else 0xff65ff99.toInt()
                c.drawRect(x,y,x+step-gap,y+plot.height()/12-1.5f*d,p)
            }
            if(now>=peakUntil[i])peaks[i]=max(amplitude,peaks[i]-.025f)
            if(peaks[i]>0) { p.color=0xffe4f6e8.toInt();c.drawRect(x,plot.bottom-plot.height()*displayAmplitude(peaks[i]),x+step-gap,plot.bottom-plot.height()*displayAmplitude(peaks[i])+d,p) }
        }
        p.color=0xffaab5bb.toInt();p.textSize=9*d;p.textAlign=Paint.Align.CENTER
        listOf("50","200","1k","5k","20k Hz").forEachIndexed { i,t ->
            p.textAlign=if(i==0)Paint.Align.LEFT else if(i==4)Paint.Align.RIGHT else Paint.Align.CENTER
            c.drawText(t,plot.left+plot.width()*i/4,plot.bottom+13*d,p)
        }
        val level=((meter.rmsDb+60)/60).coerceIn(0f,1f)
        val bar=RectF(10*d,height-31*d,width-10*d,height-23*d)
        p.color=0xff17262a.toInt();c.drawRoundRect(bar,2*d,2*d,p)
        p.color=if(meter.peakDb>-.5f)0xffff4545.toInt() else 0xff65ff99.toInt();c.drawRect(bar.left,bar.top,bar.left+bar.width()*level,bar.bottom,p)
        val peak=((meter.peakDb+60)/60).coerceIn(0f,1f);p.color=Color.WHITE;c.drawRect(bar.left+bar.width()*peak-d,bar.top-d,bar.left+bar.width()*peak+d,bar.bottom+d,p)
        p.textSize=10*d;p.textAlign=Paint.Align.LEFT;p.color=0xffe9edf0.toInt()
        fun db(v:Float)=if(v<=-89f)"−∞" else String.format(Locale.US,"%.1f",v)
        c.drawText("RMS ${db(meter.rmsDb)} dBFS",10*d,height-8*d,p);p.textAlign=Paint.Align.RIGHT;c.drawText("PEAK ${db(meter.peakDb)} dBFS",width-10*d,height-8*d,p)
        if(isShown && windowVisibility==VISIBLE && (clip || peaks.any { it>0 }))postInvalidateDelayed(60)
    }
}
