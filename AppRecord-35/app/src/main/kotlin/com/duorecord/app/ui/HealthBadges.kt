package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.FrameLayout
import android.view.Gravity
import com.duorecord.app.DeviceHealth
import com.duorecord.app.ChargingSourceChoice
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared geometry keeps the compact badge and its expanded gauge consistent. */
internal class HealthGauge(context:Context,health:DeviceHealth?,private val index:Int,private val expanded:Boolean=false):View(context) {
    var health:DeviceHealth?=health;set(value) { field=value;invalidate() }
    companion object {
        private fun used(h:DeviceHealth?):Long?=h?.totalBytes?.takeIf { it>0 }?.let { total -> h.freeBytes?.let { (total-it.coerceIn(0,total)).coerceAtLeast(0) } }
        private fun percent(bytes:Long,limit:Long)=(bytes.toDouble()/limit.coerceAtLeast(1)*100).roundToInt()
        fun value(h:DeviceHealth?,i:Int,compact:Boolean=false):String=when(i) {
            0 -> (h?.batteryPercent?.let { "$it%" } ?: "—")+if(compact)" · "+(h?.batteryTemperatureC?.let { "${it.roundToInt()}°C" } ?: "—") else ""
            1 -> h?.temperatureText() ?: "—"
            4 -> h?.cpuTemperatureC?.let { if(compact)"${it.roundToInt()}°C" else String.format(Locale.US,"%.1f°C",it) } ?: "—"
            2 -> h?.thermalLabel ?: "—"
            5 -> h?.rollingBytes?.let { bytes ->
                val percent=(bytes.toDouble()/h.rollingLimitBytes.coerceAtLeast(1)*100).roundToInt()
                if(compact)"$percent% · ${bytes/1048576}M" else "$percent% used\n${fileSize(bytes)} / ${fileSize(h.rollingLimitBytes)}"
            } ?: "—"
            else -> used(h)?.let { bytes ->
                val pct=percent(bytes,h!!.totalBytes!!)
                if(compact)"Memory · $pct%" else "$pct% used\n${fileSize(bytes)} / ${fileSize(h.totalBytes)}"
            } ?: if(compact)"Memory · —" else "—"
        }
        fun bars(h:DeviceHealth?,i:Int):Int=when(i) {
            0 -> h?.batteryPercent?.let { ceil(it.coerceIn(0,100)/10.0).toInt() } ?: 0
            1 -> h?.batteryTemperatureC?.let { ceil(it.coerceIn(0.0,60.0)/6.0).toInt() } ?: 0
            4 -> h?.cpuTemperatureC?.let { ceil(it.coerceIn(0.0,100.0)/10.0).toInt() } ?: 0
            2 -> h?.thermalStatus?.let { ceil(it.coerceIn(0,6)*10.0/6).toInt() } ?: 0
            5 -> h?.rollingBytes?.let { ceil((it.toDouble()/h.rollingLimitBytes.coerceAtLeast(1)).coerceIn(0.0,1.0)*10).toInt() } ?: 0
            else -> used(h)?.let { ceil(it.toDouble()/h!!.totalBytes!!*10).toInt() } ?: 0
        }
        fun fill(h:DeviceHealth?,i:Int):Float=when(i) {
            0 -> (h?.batteryPercent ?: 0)/100f
            1 -> ((h?.batteryTemperatureC ?: 0.0)/60).toFloat()
            4 -> ((h?.cpuTemperatureC ?: 0.0)/100).toFloat()
            3 -> used(h)?.let { it.toFloat()/h!!.totalBytes!! } ?: 0f
            5 -> h?.rollingBytes?.let { it.toFloat()/h.rollingLimitBytes.coerceAtLeast(1) } ?: 0f
            else -> bars(h,i)/10f
        }.coerceIn(0f,1f)
        fun bounds(context:Context,w:Int,h:Int,expanded:Boolean,index:Int=0):RectF {
            val d=context.resources.displayMetrics.density
            return if(expanded)RectF(12*d,h-96*d-if(index in setOf(1,4))80*d*12/23 else 0f,w-12*d,h-16*d) else RectF(12*d,12*d,w-12*d,h-12*d)
        }
        fun lineTop(b:RectF,index:Int):Float=when(index) { 1,4 -> b.centerY()-b.height()/14*2.2f;3,5 -> b.top+b.height()*.16f;else -> b.top }
        fun draw(c:Canvas,p:Paint,b:RectF,index:Int,count:Int,fill:Float=count/10f,graduated:Boolean=false,chargePulse:Float=0f) =
            com.duorecord.components.GaugeDrawing.draw(c,p,b,index,count,fill,graduated,chargePulse)

    }
    var chargerRect:RectF?=null;set(value) { field=value;invalidate() }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription="${value(health,index)}. Graduated ${when(index) { 1,4 -> "thermometer";3,5 -> "storage barrel";else -> "battery" }}." }
    override fun onDraw(canvas:Canvas) {
        val hot=index==1 && (health?.batteryTemperatureC ?: 0.0)>=45.0
        val saved=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),if(hot) (155+100*(kotlin.math.sin(android.os.SystemClock.uptimeMillis()/450.0)+1)/2).toInt() else 255)
        draw(canvas,paint,bounds(context,width,height,expanded,index),index,bars(health,index),fill(health,index),true,pulse(health));canvas.restoreToCount(saved)
        if(index==0 && expanded && health?.charging==true)chargerRect?.let { plug ->
            val b=bounds(context,width,height,true,index);val u=b.height()/14f
            val d=resources.displayMetrics.density
            val step=(b.width()-6*u)/10
            val xEntry=b.left+2*u+9*step+(step-.6f*u)/2
            val fit=minOf(plug.width()/(20*d),plug.height()/(26*d))
            val path=Path().apply { moveTo(xEntry,plug.bottom);lineTo(xEntry,b.top+2*u) }
            paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=6*resources.displayMetrics.density;paint.color=0xff39ff73.toInt();paint.clearShadowLayer();canvas.drawPath(path,paint)
            paint.color=Color.BLACK;paint.pathEffect=DashPathEffect(floatArrayOf(4*resources.displayMetrics.density,8*resources.displayMetrics.density),-(android.os.SystemClock.uptimeMillis()%1200)/100f*resources.displayMetrics.density)
            canvas.drawPath(path,paint);paint.pathEffect=null
        }
        if(hot)postInvalidateDelayed(100)
        if(index==0 && health?.charging==true && isShown && windowVisibility==VISIBLE)postInvalidateDelayed(60)
    }
}
internal class HealthBadges(context:Context):TextView(context) {
    var health:DeviceHealth?=null;set(value) { field=value;invalidate() }
    var onBadgeClick:((Int)->Unit)?=null
    val badgeIndices:List<Int> get()=if(health?.cpuTemperatureC!=null)listOf(0,4,3) else listOf(0,3)
    private var touched=0
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN)touched=(event.x/(width/badgeIndices.size.toFloat())).toInt().coerceIn(0,badgeIndices.lastIndex)
        if(event.actionMasked==MotionEvent.ACTION_UP)performClick()
        return true
    }
    override fun performClick():Boolean { super.performClick();onBadgeClick?.invoke(badgeIndices[touched]);return true }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        val d=resources.displayMetrics.density;val cell=width/badgeIndices.size.toFloat()
        for((position,i) in badgeIndices.withIndex()) {
            val box=RectF(position*cell+4*d,2*d,(position+1)*cell-4*d,height-2*d)
            paint.color=0xff15191c.toInt();paint.style=Paint.Style.FILL;canvas.drawRoundRect(box,5*d,5*d,paint)
            paint.color=0xffff982b.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=d;canvas.drawRoundRect(box,5*d,5*d,paint)
            paint.style=Paint.Style.FILL;paint.color=0xffffd52a.toInt();paint.textAlign=Paint.Align.CENTER;paint.textSize=11*d
            // Same font, baseline and horizontal gauge height for all four readings.
            val text=HealthGauge.value(health,i,compact=true)
            while(paint.measureText(text)>box.width()-4*d && paint.textSize>7*d)paint.textSize-=.5f*d
            canvas.drawText(text,box.centerX(),18*d,paint)
            HealthGauge.draw(canvas,paint,RectF(box.left+8*d,26*d,box.right-8*d,39*d),i,HealthGauge.bars(health,i),HealthGauge.fill(health,i),chargePulse=pulse(health))
        }
        if(health?.chargePulse==true && isShown && windowVisibility==VISIBLE)postInvalidateDelayed(60)
    }
}

private fun pulse(health:DeviceHealth?):Float=if(health?.chargePulse==true)((kotlin.math.sin(android.os.SystemClock.uptimeMillis()/1500.0*Math.PI)+1)/2).toFloat() else 0f

/** The label's centre is halfway between the card edge and the tube/body's upper line. */
internal class HealthReading(context:Context,health:DeviceHealth?,val index:Int):FrameLayout(context) {
    val value=context.label("",26f,Palette.mint,true).apply { gravity=Gravity.CENTER;includeFontPadding=false;setLineSpacing(0f,1f);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
    val gauge=HealthGauge(context,health,index,true)
    private var currentHealth=health
    private var energy=false
    private var fahrenheit=context.getSharedPreferences("display_units",0).getBoolean("fahrenheit",false)
    private val sourceRow=android.widget.LinearLayout(context).apply { gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false;tag="charging-source-icons" }
    private val capacity=context.label("— mAh",26f,Palette.muted).apply { gravity=Gravity.CENTER;includeFontPadding=false;tag="battery-capacity";isClickable=false }
    private val energyUnit=context.label("Wh",10.4f,0xff39ff73.toInt()).apply { gravity=Gravity.CENTER;includeFontPadding=false;tag="energy-unit";visibility=View.GONE }
    private val capacityUnit=context.label("mAh",10.4f,Palette.muted).apply { gravity=Gravity.CENTER;includeFontPadding=false;tag="capacity-unit" }
    private val sourceButtons=linkedMapOf<ChargingSourceChoice,SignButton>()
    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);clipChildren=false;clipToPadding=false
        if(index in setOf(0,1,4))value.background=rounded(0xff10161b.toInt(),context.dp(6).toFloat()).apply { setStroke(context.dp(3),0xff39ff73.toInt()) }
        addView(gauge,LayoutParams(-1,-1));addView(value,LayoutParams(-1,context.dp(if(index in setOf(3,5))60 else 44)))
        if(index==0) {
            listOf(ChargingSourceChoice.WALL to HeaderIcon.CHARGER).forEach { (choice,icon) ->
                val control=context.iconButton(icon) {}.apply { squareFace=true;bareGlyph=true;toggleAppearance=true;horizontalCharger=false;isClickable=false;contentDescription="Charging connection" }
                sourceButtons[choice]=control;sourceRow.addView(control,android.widget.LinearLayout.LayoutParams(context.dp(76),context.dp(44)))
            }
            addView(sourceRow,LayoutParams(context.dp(76),context.dp(44),Gravity.TOP or Gravity.LEFT));addView(capacity,LayoutParams(context.dp(100),context.dp(44)));addView(energyUnit,LayoutParams(context.dp(104),context.dp(12)));addView(capacityUnit,LayoutParams(context.dp(100),context.dp(12)))
        }
        if(index==0)value.setOnClickListener { energy=!energy;bind(currentHealth) }
        if(index in setOf(1,4))value.setOnClickListener {
            fahrenheit=!fahrenheit;context.getSharedPreferences("display_units",0).edit().putBoolean("fahrenheit",fahrenheit).apply();bind(currentHealth)
        }
        bind(health)
    }
    fun bind(health:DeviceHealth?) {
        currentHealth=health;gauge.health=health
        capacity.text=health?.estimatedCapacityMah?.let { "${it.roundToInt()}" } ?: "—"
        capacity.contentDescription="Estimated full capacity: ${capacity.text} milliamp-hours"
        sourceButtons.forEach { (choice,button) ->
            button.isSelected=health?.charging==true
            button.pulseGlyph=button.isSelected
            button.stateDescription=if(health?.charging==true)"Charging connection" else "Disconnected"
        }
        val c=if(index==4)health?.cpuTemperatureC else health?.batteryTemperatureC
        val text=if(index in setOf(1,4) && fahrenheit)c?.let { String.format(Locale.US,"%.1f°F",it*9/5+32) } ?: "—" else HealthGauge.value(health,index)
        value.setTextColor(if(index in setOf(0,1,4))0xff39ff73.toInt() else Palette.text)
        val display=if(index==0 && energy)health?.let { h -> h.estimatedCapacityMah?.let { mah -> h.batteryVoltageV?.let { v -> h.batteryPercent?.let { pct -> String.format(Locale.US,"%.1f",mah*v*pct/100000) } } } } ?: "—" else text
        value.contentDescription=if(index==0 && energy)"Estimated remaining battery energy: $display watt-hours. Tap for percentage." else "Reading $display"
        value.text=if(index in setOf(3,5) && '\n' in text)android.text.SpannableString(text).apply {
            val at=text.indexOf('\n')+1
            setSpan(android.text.style.RelativeSizeSpan(.56f),at,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.TypefaceSpan("monospace"),at,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else display
        value.maxLines=1;energyUnit.visibility=if(index==0 && energy)View.VISIBLE else View.GONE
        requestLayout()
    }
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        if(index in setOf(0,1,4)) {
            value.textSize=26f
            val available=MeasureSpec.getSize(widthSpec)
            val w=maxOf(context.dp(104),(value.text.toString().lines().maxOf { value.paint.measureText(it) }+context.dp(24)).toInt()).coerceAtMost((available-context.dp(180)).coerceAtLeast(context.dp(104)))
            value.layoutParams.width=w
            value.layoutParams.height=context.dp(44)
            if(index==0) {
                energyUnit.layoutParams.width=context.dp(22)
                capacityUnit.layoutParams.width=context.dp(24)
                capacity.layoutParams.width=(capacity.paint.measureText(capacity.text.toString())+context.dp(2)).toInt()
                value.setPadding(0,0,if(energy)context.dp(22) else 0,0)
            }
        }
        super.onMeasure(widthSpec,heightSpec)
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        super.onLayout(changed,left,top,right,bottom)
        val line=HealthGauge.lineTop(HealthGauge.bounds(context,width,height,true,index),index)
        val y=if(index in setOf(0,1,4))context.dp(24) else ((line-2*resources.displayMetrics.density)/2-value.measuredHeight/2).toInt()
        if(index==0) {
            val bounds=HealthGauge.bounds(context,width,height,true,index);val u=bounds.height()/14;val step=(bounds.width()-6*u)/10;val center=bounds.left+2*u+9*step+(step-.6f*u)/2;sourceRow.layout(center.toInt()-context.dp(38),y,center.toInt()+context.dp(38),y+context.dp(44));gauge.chargerRect=RectF(sourceRow.left.toFloat(),sourceRow.top.toFloat(),sourceRow.right.toFloat(),sourceRow.bottom.toFloat())
            val numberLeft=(width-value.measuredWidth)/2
            val zoneLeft=context.dp(12);val zoneRight=numberLeft-context.dp(6)
            val total=capacity.measuredWidth+capacityUnit.measuredWidth
            val capLeft=zoneLeft+(zoneRight-zoneLeft-total)/2
            capacity.layout(capLeft,y,capLeft+capacity.measuredWidth,y+context.dp(44))
            capacityUnit.layout(capacity.right,y+context.dp(9),capacity.right+capacityUnit.measuredWidth,y+context.dp(21))
            energyUnit.layout(numberLeft+value.measuredWidth-context.dp(25),y+context.dp(9),numberLeft+value.measuredWidth-context.dp(3),y+context.dp(21))
        }
        if(index in setOf(0,1,4)) { val w=value.measuredWidth;value.layout((width-w)/2,y,(width+w)/2,y+value.measuredHeight) } else value.layout(0,y,width,y+value.measuredHeight)
    }
}
