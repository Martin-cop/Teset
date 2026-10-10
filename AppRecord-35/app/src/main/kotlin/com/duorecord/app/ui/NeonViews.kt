package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

/** Direct Kotlin adaptation of design/UI Neon.md, Video to Text v56 drawing geometry. */
internal class GlowPanel(context:Context,title:String?=null,private val compact:Boolean=false,private val rectangular:Boolean=false):LinearLayout(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds=RectF()
    private var cachedBackground:Bitmap?=null
    private var cachedSimple=false
    private val d=resources.displayMetrics.density
    init {
        orientation=VERTICAL;setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT);clipChildren=false;clipToPadding=false
        setPadding(context.dp(12),context.dp(12),context.dp(12),context.dp(24))
        if(title!=null) {
            addWide(context.label(title,16f,Palette.text,true).apply {
                gravity=Gravity.CENTER;includeFontPadding=false;tag="panel-title-strip"
                setPadding(context.dp(10),context.dp(3),context.dp(10),context.dp(7))
                background=rounded(0xff050607.toInt()).apply { cornerRadii=floatArrayOf(14*d,14*d,14*d,14*d,0f,0f,0f,0f) }
            })
            addView(View(context).apply { setBackgroundColor(0xff343638.toInt()) },LayoutParams(-1,context.dp(2)).apply { leftMargin=-context.dp(2);rightMargin=-context.dp(2) })
            addGap(12)
        }
    }
    override fun onDraw(canvas:Canvas) {
        // Rasterize only the decorative background in software. A software layer on this
        // entire parent would prevent its live TextureView descendants from drawing.
        if(width>0 && height>0 && (cachedBackground?.width!=width || cachedBackground?.height!=height || cachedSimple!=Palette.simple)) {
            cachedBackground=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
                density=resources.displayMetrics.densityDpi
                drawPanel(Canvas(this))
            }
            cachedSimple=Palette.simple
        }
        cachedBackground?.let { canvas.drawBitmap(it,0f,0f,null) }
        super.onDraw(canvas)
    }
    private fun drawPanel(canvas:Canvas) {
        val inset=(if(compact)4 else 10)*d
        val radius=(if(rectangular)0 else if(compact)12 else 17)*d
        val r=bounds.apply { set(inset,inset,width-inset,height-inset) }
        if(Palette.simple) { paint.clearShadowLayer();paint.style=Paint.Style.FILL;paint.color=0xff242b36.toInt();val simpleRadius=if(rectangular)0f else 12*d;canvas.drawRoundRect(r,simpleRadius,simpleRadius,paint);return }
        paint.style=Paint.Style.FILL;paint.color=0xff212121.toInt();paint.setShadowLayer((if(compact)4 else 10)*d,0f,0f,0xff00bfff.toInt());canvas.drawRoundRect(r,radius,radius,paint)
        paint.setShadowLayer((if(compact)2 else 5)*d,0f,0f,0xff42bfff.toInt());canvas.drawRoundRect(r,radius,radius,paint)
        paint.clearShadowLayer();paint.style=Paint.Style.STROKE;paint.strokeWidth=(if(compact)2 else 4)*d;paint.color=0xff050607.toInt();canvas.drawRoundRect(r,radius,radius,paint)
    }
    fun addContent(view:View,height:Int=LayoutParams.WRAP_CONTENT) {
        addView(view,LayoutParams(-1,if(height>=0)context.dp(height) else height).apply { leftMargin=context.dp(12);rightMargin=context.dp(12) })
    }
}

internal enum class ButtonRole { INSIDE, OUTSIDE }
internal enum class HeaderIcon { GUIDE, ABOUT, SETTINGS, CLOSE, REFRESH, RECORDINGS, FOLDER, EXPORT, LIGHT, SCREEN_LIGHT, SCHEDULE, DELETE, INFO_TOOLS, STATISTICS, PREVIOUS, NEXT, PREVIOUS_ALL, NEXT_ALL, VIDEO_MODE, PHOTO_MODE, AUDIO_MODE, SCREEN_MODE, MICROPHONE, INTERNAL_AUDIO, MIXED_MODE, PLAY, STOP, RECORD, PAUSE, RESUME, BOOKMARK, TRIM, SHARE, COPY, SELECT, NORMAL_SPEED, CHARGER, POWER_BANK }

internal class SignButton(context:Context):Button(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val face=RectF()
    private val glyphBounds=RectF()
    private val letters=Path()
    var scheduleArmed=false;set(value) { field=value;invalidate() }
    var badgeCount=0;set(value) { field=value;invalidate() }
    var bareGlyph=false
    var pulseGlyph=false
    var semanticTransport=false
    var horizontalCharger=false
    var showUnavailablePause=false
    var blueUnderlight=false
    var strongUnderlight=false
    var compactHeader=false
    var squareFace=false
    var toggleAppearance=false
    var persistentSelectionColors=false
    var unavailableRed=false;set(value) { field=value;invalidate() }
    var captureState:Int?=null;set(value) { field=value;invalidate() }
    var icon:HeaderIcon?=null
    init { background=null;backgroundTintList=null;foreground=null;stateListAnimator=null;elevation=0f;isForceDarkAllowed=false;setLayerType(View.LAYER_TYPE_SOFTWARE,null);isAllCaps=false;setPadding(0,0,0,0);minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0 }
    override fun setSelected(value:Boolean) { super.setSelected(value);invalidate() }
    override fun drawableStateChanged() { super.drawableStateChanged();invalidate() }
    override fun onDraw(c:Canvas) {
        val active=if(scheduleArmed)true else if(captureState!=null)captureState==2 else (isEnabled || persistentSelectionColors) && (isSelected || isPressed)
        val pauseHint=showUnavailablePause && icon==HeaderIcon.PAUSE
        val lit=pauseHint || captureState!=null || persistentSelectionColors || (isEnabled && (!toggleAppearance || active))
        val captureColor=if(persistentSelectionColors && (isEnabled || isSelected)) { if(isSelected)Palette.mint else 0xffffdf58.toInt() } else if(icon==HeaderIcon.SCHEDULE && scheduleArmed)0xffffdf58.toInt() else if(!isEnabled && captureState!=2)0xff70777b.toInt() else when(captureState) { 2 -> Palette.mint;1 -> 0xffffdf58.toInt();else -> if((isSelected || isPressed) && icon!=HeaderIcon.PLAY)0xffffdf58.toInt() else if(isSelected && icon==HeaderIcon.PLAY)Palette.mint else Color.WHITE }
        val shift=if(isEnabled && isPressed)2*d else 0f
        val inset=if(compactHeader || squareFace || icon!=null)4*d else if(blueUnderlight)6*d else 5*d
        face.set(inset,(if(compactHeader || squareFace)4*d else if(icon!=null || blueUnderlight)6*d else 4*d)+shift,width-inset,height-(if(compactHeader || squareFace)4 else 6)*d+shift)
        paint.style=Paint.Style.FILL;paint.shader=null;paint.clearShadowLayer();paint.color=Color.BLACK
        if(lit && !Palette.simple)paint.setShadowLayer((if(strongUnderlight)4 else if(icon!=null)2 else if(blueUnderlight)4 else 3)*d,0f,0f,captureColor ?: if(icon==HeaderIcon.LIGHT || icon==HeaderIcon.SCREEN_LIGHT) { if(active)0xff44ff80.toInt() else 0xffffd22c.toInt() } else if(icon!=null || blueUnderlight)0x8838cfff.toInt() else if(active)0xff44ff80.toInt() else 0xffffd22c.toInt())
        if(!bareGlyph)c.drawRoundRect(face,7*d,7*d,paint)
        if((blueUnderlight || strongUnderlight) && lit && !Palette.simple) {
            // A narrow concealed light below the face remains inside its reserved gutter.
            paint.color=0xff36bbef.toInt();paint.setShadowLayer((if(strongUnderlight)3 else 2)*d,0f,d,0xaa42bfff.toInt())
            c.drawRoundRect(RectF(face.left+5*d,face.bottom-d,face.right-5*d,face.bottom+d),d,d,paint)
            paint.clearShadowLayer();paint.color=Color.BLACK;c.drawRoundRect(face,7*d,7*d,paint)
        }
        paint.clearShadowLayer();paint.style=Paint.Style.STROKE;paint.strokeWidth=if(icon==HeaderIcon.SCHEDULE)2*d else d;paint.color=captureColor ?: if(isEnabled && isSelected)Palette.mint else if(active)0xff557766.toInt() else 0xff697076.toInt();if(!bareGlyph)c.drawRoundRect(face,7*d,7*d,paint);paint.style=Paint.Style.FILL
        if(icon!=null && icon!=HeaderIcon.GUIDE) {
            val cx=face.centerX();val cy=face.centerY()
            val glyphSave=c.save();val fit=if(bareGlyph && icon in setOf(HeaderIcon.CHARGER,HeaderIcon.POWER_BANK))if(horizontalCharger)minOf(width/(26*d),height/(18*d)) else minOf(width/(20*d),height/(26.9f*d)) else minOf((face.width()-10*d)/(28*d),(face.height()-10*d)/(28*d),if(compactHeader)1.1f else 1f).coerceAtLeast(.3f);c.scale(fit,fit,cx,cy);if(bareGlyph && icon==HeaderIcon.CHARGER && !horizontalCharger)c.translate(0f,.55f*d)
            paint.color=if(icon==HeaderIcon.RECORD)Palette.red else if(icon==HeaderIcon.PLAY)Palette.mint else if(icon==HeaderIcon.PAUSE)0xffffdf58.toInt() else captureColor
            if(scheduleArmed) { paint.alpha=(110+145*(kotlin.math.sin(android.os.SystemClock.uptimeMillis()/6000.0*Math.PI)+1)/2).toInt();postInvalidateDelayed(200) }
            if(pulseGlyph && isSelected) { if(icon==HeaderIcon.CHARGER) { paint.color=Color.rgb(35,(200+55*(kotlin.math.sin(android.os.SystemClock.uptimeMillis()/1500.0*Math.PI)+1)/2).toInt(),85);paint.alpha=255 } else paint.alpha=(110+145*(kotlin.math.sin(android.os.SystemClock.uptimeMillis()/1500.0*Math.PI)+1)/2).toInt();postInvalidateDelayed(60) }
            if(lit && !Palette.simple && icon!=HeaderIcon.CHARGER)paint.setShadowLayer(2*d,0f,0f,paint.color)
            if(icon==HeaderIcon.LIGHT) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                glyphBounds.set(cx-4*d,cy-1*d,cx+4*d,cy+11*d);c.drawRoundRect(glyphBounds,2*d,2*d,paint)
                letters.reset();letters.moveTo(cx-8*d,cy-10*d);letters.lineTo(cx+8*d,cy-10*d)
                letters.lineTo(cx+4*d,cy-1*d);letters.lineTo(cx-4*d,cy-1*d);letters.close();c.drawPath(letters,paint)
                c.drawLine(cx-8*d,cy-7*d,cx+8*d,cy-7*d,paint);c.drawCircle(cx,cy+3*d,d,paint)
                if(isSelected)for(offset in -1..1)c.drawLine(cx+offset*5*d,cy-13*d,cx+offset*8*d,cy-17*d,paint)
            } else if(icon==HeaderIcon.SCREEN_LIGHT) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                glyphBounds.set(cx-7*d,cy-12*d,cx+7*d,cy+12*d);c.drawRoundRect(glyphBounds,2*d,2*d,paint)
                c.drawLine(cx-3*d,cy+8*d,cx+3*d,cy+8*d,paint)
                if(isSelected) { paint.style=Paint.Style.FILL;glyphBounds.set(cx-4*d,cy-8*d,cx+4*d,cy+5*d);c.drawRect(glyphBounds,paint) }
            } else if(icon==HeaderIcon.RECORDINGS) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                glyphBounds.set(cx-12*d,cy-10*d,cx+12*d,cy+10*d);c.drawRoundRect(glyphBounds,2*d,2*d,paint)
                for(y in -6..6 step 6) {
                    c.drawLine(cx-12*d,cy+y*d,cx-9*d,cy+y*d,paint);c.drawLine(cx+9*d,cy+y*d,cx+12*d,cy+y*d,paint)
                }
                letters.reset();letters.moveTo(cx-3*d,cy-5*d);letters.lineTo(cx+5*d,cy);letters.lineTo(cx-3*d,cy+5*d);letters.close()
                paint.style=Paint.Style.FILL;c.drawPath(letters,paint)
            } else if(icon==HeaderIcon.COPY) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-8*d,cy-8*d,cx+4*d,cy+5*d),2*d,2*d,paint)
                c.drawRoundRect(RectF(cx-3*d,cy-3*d,cx+9*d,cy+10*d),2*d,2*d,paint)
            } else if(icon==HeaderIcon.SELECT) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-9*d,cy-9*d,cx+9*d,cy+9*d),2*d,2*d,paint)
                c.drawLine(cx-5*d,cy,cx-1*d,cy+4*d,paint);c.drawLine(cx-1*d,cy+4*d,cx+6*d,cy-5*d,paint)
            } else if(icon==HeaderIcon.NORMAL_SPEED) {
                paint.textSize=17*d;paint.textAlign=Paint.Align.CENTER;paint.style=Paint.Style.FILL;c.drawText("1×",cx,cy-(paint.ascent()+paint.descent())/2,paint)
            } else if(icon==HeaderIcon.SHARE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawLine(cx-8*d,cy,cx+7*d,cy-8*d,paint);c.drawLine(cx-8*d,cy,cx+7*d,cy+8*d,paint)
                for((x,y) in listOf(-8f to 0f,7f to -8f,7f to 8f))c.drawCircle(cx+x*d,cy+y*d,3*d,paint)
            } else if(icon==HeaderIcon.TRIM) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawCircle(cx-7*d,cy+7*d,4*d,paint);c.drawCircle(cx+7*d,cy+7*d,4*d,paint)
                c.drawLine(cx-5*d,cy+4*d,cx+9*d,cy-11*d,paint);c.drawLine(cx+5*d,cy+4*d,cx-9*d,cy-11*d,paint)
            } else if(icon==HeaderIcon.DELETE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawLine(cx-10*d,cy-7*d,cx+10*d,cy-7*d,paint)
                c.drawRoundRect(RectF(cx-7*d,cy-7*d,cx+7*d,cy+11*d),2*d,2*d,paint)
                c.drawLine(cx-4*d,cy-13.1f*d,cx+4*d,cy-13.1f*d,paint)
                for(x in listOf(-3,3))c.drawLine(cx+x*d,cy-3*d,cx+x*d,cy+7*d,paint)
            } else if(icon==HeaderIcon.SCHEDULE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawCircle(cx,cy,12*d,paint)
                val angle=if(scheduleArmed)Math.toRadians((android.os.SystemClock.uptimeMillis()/200%60)*6.0-90) else -Math.PI/2
                paint.color=0xff81dfff.toInt();c.drawLine(cx,cy,cx+kotlin.math.cos(angle).toFloat()*10*d,cy+kotlin.math.sin(angle).toFloat()*10*d,paint);c.drawLine(cx,cy,cx+6*d,cy+3*d,paint)
            } else if(icon==HeaderIcon.FOLDER) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                letters.reset();letters.moveTo(cx-13*d,cy+10*d);letters.lineTo(cx-13*d,cy-10*d)
                letters.lineTo(cx-4*d,cy-10*d);letters.lineTo(cx,cy-6*d);letters.lineTo(cx+13*d,cy-6*d)
                letters.lineTo(cx+13*d,cy+10*d);letters.close();c.drawPath(letters,paint)
                c.drawLine(cx-13*d,cy-3*d,cx+13*d,cy-3*d,paint)
            } else if(icon==HeaderIcon.EXPORT) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawLine(cx,cy+4*d,cx,cy-12*d,paint)
                c.drawLine(cx,cy-12*d,cx-6*d,cy-6*d,paint);c.drawLine(cx,cy-12*d,cx+6*d,cy-6*d,paint)
                letters.reset();letters.moveTo(cx-12*d,cy+2*d);letters.lineTo(cx-12*d,cy+11*d)
                letters.lineTo(cx+12*d,cy+11*d);letters.lineTo(cx+12*d,cy+2*d);c.drawPath(letters,paint)
            } else if(icon==HeaderIcon.CLOSE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.strokeCap=Paint.Cap.ROUND
                c.drawLine(cx-7*d,cy-7*d,cx+7*d,cy+7*d,paint);c.drawLine(cx+7*d,cy-7*d,cx-7*d,cy+7*d,paint)
                paint.strokeCap=Paint.Cap.BUTT
            } else if(icon==HeaderIcon.REFRESH) {
                // Clockwise circular stroke ends at the upper-left of the circle;
                // its arrow points right, in the direction of the incoming tangent.
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f*d;paint.strokeCap=Paint.Cap.ROUND
                c.drawArc(RectF(cx-10*d,cy-10*d,cx+10*d,cy+10*d),10f,260f,false,paint)
                letters.reset();letters.moveTo(cx-1*d,cy-13.1f*d);letters.lineTo(cx+4*d,cy-10*d);letters.lineTo(cx-1*d,cy-6*d)
                c.drawPath(letters,paint)
                paint.style=Paint.Style.FILL;c.drawCircle(cx+9.85f*d,cy+1.74f*d,1.5f*d,paint)
                paint.strokeCap=Paint.Cap.BUTT
            } else if(icon in setOf(HeaderIcon.AUDIO_MODE,HeaderIcon.MICROPHONE)) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-4*d,cy-10*d,cx+4*d,cy+3*d),4*d,4*d,paint)
                c.drawArc(RectF(cx-7*d,cy-8*d,cx+7*d,cy+7*d),0f,180f,false,paint)
                c.drawLine(cx,cy+7*d,cx,cy+11*d,paint);c.drawLine(cx-5*d,cy+11*d,cx+5*d,cy+11*d,paint)
            } else if(icon==HeaderIcon.INTERNAL_AUDIO) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                letters.reset();letters.moveTo(cx-10*d,cy-4*d);letters.lineTo(cx-4*d,cy-4*d);letters.lineTo(cx+2*d,cy-10*d);letters.lineTo(cx+2*d,cy+10*d);letters.lineTo(cx-4*d,cy+4*d);letters.lineTo(cx-10*d,cy+4*d);letters.close();c.drawPath(letters,paint)
                c.drawArc(RectF(cx-3*d,cy-9*d,cx+14*d,cy+9*d),-60f,120f,false,paint)
            } else if(icon==HeaderIcon.MIXED_MODE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-13*d,cy-10*d,cx+10*d,cy+8*d),2*d,2*d,paint)
                paint.style=Paint.Style.FILL;c.drawRoundRect(RectF(cx-7*d,cy-4*d,cx+14*d,cy+12*d),2*d,2*d,paint)
                paint.color=Color.BLACK;c.drawRect(cx+2*d,cy+1*d,cx+11*d,cy+8*d,paint)
            } else if(icon==HeaderIcon.RECORD) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;c.drawCircle(cx,cy,11*d,paint)
                paint.style=Paint.Style.FILL;c.drawCircle(cx,cy,7*d,paint)
            } else if(icon==HeaderIcon.PAUSE) {
                c.drawRoundRect(RectF(cx-8*d,cy-10*d,cx-3*d,cy+10*d),d,d,paint)
                c.drawRoundRect(RectF(cx+3*d,cy-10*d,cx+8*d,cy+10*d),d,d,paint)
            } else if(icon==HeaderIcon.BOOKMARK) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                letters.reset();letters.moveTo(cx-8*d,cy-11*d);letters.lineTo(cx+8*d,cy-11*d);letters.lineTo(cx+8*d,cy+11*d);letters.lineTo(cx,cy+5*d);letters.lineTo(cx-8*d,cy+11*d);letters.close();c.drawPath(letters,paint)
            } else if(icon in setOf(HeaderIcon.CHARGER,HeaderIcon.POWER_BANK)) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f*d
                if(icon==HeaderIcon.CHARGER) {
                    val chargerSave=c.save();if(horizontalCharger)c.rotate(90f,cx,cy)
                    c.drawRoundRect(RectF(cx-8*d,cy-5*d,cx+8*d,cy+7*d),2*d,2*d,paint)
                    c.drawLine(cx-4*d,cy-5*d,cx-4*d,cy-13.1f*d,paint);c.drawLine(cx+4*d,cy-5*d,cx+4*d,cy-13.1f*d,paint)
                    if(isSelected) {
                        val phase=((android.os.SystemClock.uptimeMillis()%2400)/2400f)
                        paint.style=Paint.Style.FILL
                        for(i in 0..4) { paint.color=if(i/5f<=phase)0xff39ff73.toInt() else 0xff163d21.toInt();c.drawRect(cx-5*d,cy-3*d+i*1.7f*d,cx+5*d,cy-2*d+i*1.7f*d,paint) }
                    }
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f*d;c.drawLine(cx,cy+7*d,cx,cy+12*d,paint);c.restoreToCount(chargerSave)
                } else {
                    c.drawRoundRect(RectF(cx-8*d,cy-12*d,cx+8*d,cy+12*d),3*d,3*d,paint)
                    c.drawLine(cx-3*d,cy-8*d,cx+3*d,cy-8*d,paint)
                    letters.reset();letters.moveTo(cx+2*d,cy-4*d);letters.lineTo(cx-3*d,cy+2*d);letters.lineTo(cx+2*d,cy+2*d);letters.lineTo(cx-1*d,cy+7*d);c.drawPath(letters,paint)
                }
            } else if(icon in setOf(HeaderIcon.PLAY,HeaderIcon.RESUME)) {
                letters.reset();letters.moveTo(cx-7*d,cy-11*d);letters.lineTo(cx+11*d,cy);letters.lineTo(cx-7*d,cy+11*d);letters.close();c.drawPath(letters,paint)
            } else if(icon==HeaderIcon.STOP) {
                c.drawRoundRect(RectF(cx-9*d,cy-9*d,cx+9*d,cy+9*d),2*d,2*d,paint)
            } else if(icon==HeaderIcon.SCREEN_MODE) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-11*d,cy-8*d,cx+11*d,cy+6*d),2*d,2*d,paint)
                c.drawLine(cx,cy+6*d,cx,cy+11*d,paint);c.drawLine(cx-6*d,cy+11*d,cx+6*d,cy+11*d,paint)
            } else if(icon in setOf(HeaderIcon.VIDEO_MODE,HeaderIcon.PHOTO_MODE)) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawRoundRect(RectF(cx-11*d,cy-7*d,cx+8*d,cy+8*d),2*d,2*d,paint)
                if(icon==HeaderIcon.PHOTO_MODE) { c.drawCircle(cx-1*d,cy,4*d,paint);c.drawLine(cx-6*d,cy-10*d,cx+3*d,cy-10*d,paint) }
                else { letters.reset();letters.moveTo(cx+8*d,cy-3*d);letters.lineTo(cx+13*d,cy-6*d);letters.lineTo(cx+13*d,cy+6*d);letters.lineTo(cx+8*d,cy+3*d);c.drawPath(letters,paint) }
            } else if(icon in setOf(HeaderIcon.PREVIOUS,HeaderIcon.NEXT,HeaderIcon.PREVIOUS_ALL,HeaderIcon.NEXT_ALL)) {
                val saved=c.save();if(icon in setOf(HeaderIcon.NEXT,HeaderIcon.NEXT_ALL))c.scale(-1f,1f,cx,cy)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2.2f*d;paint.strokeJoin=Paint.Join.ROUND
                if(icon in setOf(HeaderIcon.PREVIOUS_ALL,HeaderIcon.NEXT_ALL)) {
                    c.drawLine(cx-12*d,cy-9*d,cx-12*d,cy+9*d,paint)
                    for(offset in 0..1) {
                        val x=cx+(-8+offset*10)*d
                        letters.reset();letters.moveTo(x+7*d,cy-8*d);letters.lineTo(x,cy);letters.lineTo(x+7*d,cy+8*d);letters.close();c.drawPath(letters,paint)
                    }
                } else {
                    c.drawLine(cx-10*d,cy-10*d,cx-10*d,cy+10*d,paint)
                    letters.reset();letters.moveTo(cx+8*d,cy-10*d);letters.lineTo(cx-6*d,cy);letters.lineTo(cx+8*d,cy+10*d);letters.close();c.drawPath(letters,paint)
                }
                c.restoreToCount(saved);paint.strokeJoin=Paint.Join.MITER
            } else if(icon==HeaderIcon.STATISTICS) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
                c.drawLine(cx-11*d,cy+11*d,cx+12*d,cy+11*d,paint)
                for(i in 0..2)c.drawRoundRect(RectF(cx-9*d+i*8*d,cy+(4-i*6)*d,cx-5*d+i*8*d,cy+7*d),d,d,paint)
            } else if(icon==HeaderIcon.ABOUT || icon==HeaderIcon.INFO_TOOLS) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;c.drawCircle(cx,cy,11*d,paint)
                paint.style=Paint.Style.FILL;c.drawCircle(cx,cy-6*d,1.5f*d,paint)
                c.drawRoundRect(RectF(cx-1.4f*d,cy-2*d,cx+1.4f*d,cy+7*d),d,d,paint)
            } else {
                letters.reset()
                for(i in 0..63) {
                    val angle=i*Math.PI/32;val radius=if(i%8 in 2..5)11*d else 9*d
                    val x=cx+kotlin.math.cos(angle).toFloat()*radius;val y=cy+kotlin.math.sin(angle).toFloat()*radius
                    if(i==0)letters.moveTo(x,y) else letters.lineTo(x,y)
                }
                letters.close();letters.addCircle(cx,cy,4*d,Path.Direction.CCW);letters.fillType=Path.FillType.EVEN_ODD;c.drawPath(letters,paint)
            }
            if(icon==HeaderIcon.BOOKMARK && badgeCount>0) { paint.style=Paint.Style.FILL;paint.color=Palette.mint;paint.textSize=10*d;paint.textAlign=Paint.Align.RIGHT;c.drawText(badgeCount.toString(),face.right-2*d,face.top+10*d,paint);paint.textAlign=Paint.Align.LEFT }
            paint.clearShadowLayer();c.restoreToCount(glyphSave);return
        }
        val text=if(icon==HeaderIcon.GUIDE)"?" else text.toString().uppercase(Locale.US)
        paint.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)
        var size=(if(compactHeader)24 else if(icon!=null)22 else 13)*d;paint.textSize=size
        while(paint.measureText(text)>face.width()-(if(icon!=null)2 else 8)*d && size>9*d) { size-=.5f*d;paint.textSize=size }
        val x=(width-paint.measureText(text))/2;val y=face.centerY()-(paint.ascent()+paint.descent())/2
        letters.reset();paint.getTextPath(text,0,text.length,x,y,letters)
        if(lit && !Palette.simple) {
            val glow=captureColor ?: if(active)0xff44ff80.toInt() else 0xffffd22c.toInt();paint.color=glow
            paint.setShadowLayer((if(active)8 else 6)*d,0f,0f,glow);c.drawPath(letters,paint);paint.setShadowLayer(2*d,0f,0f,glow);c.drawPath(letters,paint)
        }
        paint.clearShadowLayer();paint.color=captureColor ?: if(!lit)0xff70777b.toInt() else if(active)0xffaeffcb.toInt() else 0xffffe978.toInt();c.drawPath(letters,paint)
    }
}

internal class GlowOutline(private val d:Float):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c:Canvas) {
        val r=RectF(bounds.left+3*d,bounds.top+3*d,bounds.right-3*d,bounds.bottom-3*d)
        paint.style=Paint.Style.FILL;paint.color=0xff1e2429.toInt()
        if(!Palette.simple)paint.setShadowLayer(3*d,0f,0f,0xff65ff99.toInt())
        c.drawRoundRect(r,10*d,10*d,paint);paint.clearShadowLayer()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.color=0xff777b7e.toInt();c.drawRoundRect(r,10*d,10*d,paint)
    }
    override fun setAlpha(alpha:Int) { }
    override fun setColorFilter(filter:ColorFilter?) { }
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

internal class SourceTab(context:Context):Button(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { background=null;foreground=null;isAllCaps=false;setPadding(0,0,0,0);minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;stateListAnimator=null }
    override fun setSelected(selected:Boolean) { super.setSelected(selected);contentDescription="$text tab, ${if(selected)"selected" else "not selected"}";stateDescription=if(selected)"Selected" else "Not selected";invalidate() }
    override fun onDraw(c:Canvas) {
        paint.color=if(isSelected)0xff343d43.toInt() else 0xff050607.toInt();c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
        paint.color=if(isSelected)0xff65ff99.toInt() else 0xffcccccc.toInt();paint.typeface=Typeface.create(Typeface.DEFAULT,if(isSelected)Typeface.BOLD else Typeface.NORMAL)
        paint.textSize=16*resources.displayMetrics.scaledDensity
        val value=text.toString();c.drawText(value,(width-paint.measureText(value))/2,(height-paint.ascent()-paint.descent())/2,paint)
    }
}

internal class PulseStatusLabel(context:Context):TextView(context) {
    private var animator:ValueAnimator?=null
    init { gravity=Gravity.CENTER;includeFontPadding=false;textSize=14f;setTextColor(0xff42bfff.toInt());minHeight=context.dp(48) }
    override fun setText(text:CharSequence?,type:BufferType?) { super.setText(text,type);gravity=Gravity.CENTER;includeFontPadding=false;requestLayout() }
    fun show(message:String,error:Boolean,working:Boolean) {
        if(text.toString()!=message)text=message
        setTextColor(if(error)Palette.red else 0xff42bfff.toInt());contentDescription=message;working(working)
    }
    fun working(on:Boolean) {
        if(on && animator==null)animator=ValueAnimator.ofFloat(.35f,1f).apply {
            duration=900;repeatMode=ValueAnimator.REVERSE;repeatCount=ValueAnimator.INFINITE
            addUpdateListener { alpha=it.animatedValue as Float };start()
        } else if(!on) { animator?.cancel();animator=null;alpha=1f }
    }
    override fun onDetachedFromWindow() { working(false);super.onDetachedFromWindow() }
}

internal class NeonSpinner(context:Context):Spinner(context) {
    init { background=null;setPadding(0,0,0,0) }
    override fun performClick():Boolean {
        if(!isEnabled)return false
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        val popup=ListPopupWindow(context);popup.anchorView=parent as? View ?: this;popup.isModal=true
        popup.setBackgroundDrawable(rounded(0xff080a0b.toInt(),context.dp(12).toFloat()).apply { setStroke(context.dp(2),0xff777b7e.toInt()) })
        popup.width=maxOf((parent as? View)?.width ?: width,context.dp(160))
        popup.setAdapter(object:BaseAdapter() {
            override fun getCount()=this@NeonSpinner.adapter.count
            override fun getItem(n:Int)=this@NeonSpinner.adapter.getItem(n)
            override fun getItemId(n:Int)=n.toLong()
            override fun getView(n:Int,old:View?,parent:android.view.ViewGroup):View {
                val selected=n==selectedItemPosition
                val label=TextView(context).apply {
                    text=getItem(n).toString();setTextColor(if(selected)0xff65ff99.toInt() else 0xffffe978.toInt());textSize=17f;setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER
                    elevation=context.dp(2).toFloat();minHeight=context.dp(42);setPadding(context.dp(8),context.dp(5),context.dp(8),context.dp(5))
                    if(!Palette.simple)setShadowLayer(context.dp(3).toFloat(),0f,0f,if(selected)0xff65ff99.toInt() else 0xffffd22c.toInt())
                    background=android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed),rounded(0xff274f39.toInt(),context.dp(10).toFloat()).apply { setStroke(context.dp(1),0xff65ff99.toInt()) })
                        addState(intArrayOf(),rounded(0xff080a0b.toInt(),context.dp(10).toFloat()).apply { setStroke(context.dp(1),0xff777b7e.toInt()) })
                    }
                    setOnClickListener { setSelection(n);popup.dismiss() }
                }
                return FrameLayout(context).apply { setPadding(context.dp(8),context.dp(5),context.dp(8),context.dp(5));addView(label,FrameLayout.LayoutParams(-1,-2)) }
            }
        });popup.show();return true
    }
}
