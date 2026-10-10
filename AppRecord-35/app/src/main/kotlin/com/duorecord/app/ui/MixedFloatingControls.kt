package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.DuoApp
import com.duorecord.domain.*
import kotlin.math.abs

/** Small independent touch windows: no full-screen blocker and no floating configuration page. */
internal class MixedFloatingControls(private val context:Context,private val config:()->RecordingConfig,private val change:(RecordingConfig)->Unit,private val stop:()->Unit) {
    private val app get()=context.applicationContext as DuoApp
    private val manager=context.getSystemService(WindowManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val d=context.resources.displayMetrics.density
    private var current=config()
    private var enabled=false;private var appVisible=app.uiVisible.value
    private var expanded=false
    private var bar:View?=null
    private var barX=8;private var barY=360
    private val insets=linkedMapOf<CameraSource,Inset>()
    private val collapse=Runnable { {} }
    private data class Inset(val view:FrameLayout,val params:WindowManager.LayoutParams,val owner:Any,val chrome:List<Pair<View,WindowManager.LayoutParams>>)
    private fun pixels(dp:Int)=(dp*d).toInt()
    private fun bounds():Rect=manager.maximumWindowMetrics.bounds.let { if(it.width()>0 && it.height()>0)it else Rect(0,0,context.resources.displayMetrics.widthPixels,context.resources.displayMetrics.heightPixels) }
    private fun params(w:Int,h:Int,protected:Boolean=true)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or (if(protected)WindowManager.LayoutParams.FLAG_SECURE else 0),
        PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT }
    fun show() { enabled=true;sync(config());setAppVisible(app.uiVisible.value) }
    fun setAppVisible(visible:Boolean) {
        appVisible=visible
        if(visible)removeWindows() else if(enabled && android.provider.Settings.canDrawOverlays(context)) { if(current.screenMode)expanded=true;sync(config());rebuildBar();refreshInsets() }
    }
    fun sync(next:RecordingConfig) {
        current=next
        if(enabled && !appVisible && android.provider.Settings.canDrawOverlays(context)) { refreshInsets();rebuildBar() }
    }
    private fun apply(next:RecordingConfig,updateBar:Boolean=true) {
        current=next;change(next);refreshInsets();if(updateBar && expanded)rebuildBar()
    }
    private fun add(view:View,p:WindowManager.LayoutParams):Boolean=runCatching { manager.addView(view,p);true }.getOrElse {
        app.diagnostics.event("Floating control unavailable",it.message ?: it.javaClass.simpleName)
        Toast.makeText(context,"Floating preview unavailable. Return to AppRecord for controls.",Toast.LENGTH_LONG).show();false
    }
    private var counter:com.duorecord.components.ElapsedCounterView?=null
    private var toggle:SignButton?=null
    private var stopButton:SignButton?=null
    private val counterTicker=object:Runnable { override fun run() {
        counter?.elapsedMs=app.captureClock.elapsedMs()
        val paused=app.status.value.phase==com.duorecord.app.Phase.USER_PAUSED
        val ready=app.status.value.phase in setOf(com.duorecord.app.Phase.RECORDING,com.duorecord.app.Phase.USER_PAUSED)
        toggle?.apply { icon=if(paused)HeaderIcon.PAUSE else HeaderIcon.RECORD;captureState=if(paused)1 else 2;isEnabled=ready;contentDescription=if(paused)"Resume same file" else "Pause recording";invalidate() }
        stopButton?.isEnabled=ready
        if(enabled && !appVisible)handler.postDelayed(this,250)
    } }
    private fun rebuildBar() {
        handler.removeCallbacks(counterTicker)
        bar?.let { runCatching { manager.removeView(it) } };bar=null;counter=null
        if(!enabled || appVisible || !android.provider.Settings.canDrawOverlays(context))return
        val box=bounds();val width=pixels(248).coerceAtMost(box.width())
        val p=params(width,pixels(56)).apply { x=barX.coerceIn(0,(box.width()-width).coerceAtLeast(0));y=barY.coerceIn(pixels(24),(box.height()-pixels(56)).coerceAtLeast(pixels(24))) }
        val body=LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL;tag="floating-toolbar"
            setPadding(pixels(6),pixels(6),pixels(6),pixels(6))
            background=rounded(Color.TRANSPARENT,pixels(8).toFloat()).apply { setStroke(pixels(1),Color.WHITE) }
        }
        counter=com.duorecord.components.ElapsedCounterView(context).apply { background=null;textSize=20f;tag="floating-counter" }
        drag(counter!!,p,body) {}
        body.addView(counter,LinearLayout.LayoutParams(0,-1,1f))
        toggle=context.iconButton(HeaderIcon.PAUSE) {
            val paused=app.status.value.phase==com.duorecord.app.Phase.USER_PAUSED
            context.startService(android.content.Intent(context,com.duorecord.app.RecordingService::class.java).setAction(if(paused)com.duorecord.app.RecordingService.RESUME else com.duorecord.app.RecordingService.PAUSE))
        }.apply { squareFace=true;semanticTransport=true }
        stopButton=context.iconButton(HeaderIcon.STOP) { stop() }.apply { squareFace=true;semanticTransport=true;contentDescription="Stop and open playback" }
        body.addView(toggle,LinearLayout.LayoutParams(pixels(44),pixels(44)))
        body.addView(stopButton,LinearLayout.LayoutParams(pixels(44),pixels(44)))
        if(add(body,p)) { bar=body;counterTicker.run() }
    }
    private fun drag(grip:View,p:WindowManager.LayoutParams,window:View,click:()->Unit) {
        var startX=0f;var startY=0f;var oldX=0;var oldY=0;var moved=false
        grip.setOnTouchListener { _,e -> when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=e.rawX;startY=e.rawY;oldX=p.x;oldY=p.y;moved=false;handler.removeCallbacks(collapse);true }
            MotionEvent.ACTION_MOVE -> {
                moved=moved || abs(e.rawX-startX)+abs(e.rawY-startY)>pixels(6)
                val b=bounds();p.x=(oldX+e.rawX-startX).toInt().coerceIn(0,(b.width()-window.width).coerceAtLeast(0));p.y=(oldY+e.rawY-startY).toInt().coerceIn(0,(b.height()-window.height).coerceAtLeast(0));barX=p.x;barY=p.y
                runCatching { manager.updateViewLayout(window,p) };true
            }
            MotionEvent.ACTION_UP -> { if(!moved)click();if(expanded && current.mixedMode)handler.postDelayed(collapse,5000);true }
            else -> true
        } }
    }
    private fun refreshInsets() {
        if(!enabled || appVisible)return
        if(!current.mixedMode) { insets.keys.toList().forEach(::removeInset);return }
        val frames=MixedLayout.read(current.mixedLayout)
        val wanted=current.mixedSources.filter { it!=current.mixedBackground && frames.getValue(it).visible }.toSet()
        (insets.keys-wanted).forEach(::removeInset)
        val b=bounds()
        wanted.forEach { s ->
            val f=frames.getValue(s)
            val inset=insets[s] ?: createInset(s)?.also { insets[s]=it } ?: return@forEach
            inset.params.apply { width=(b.width()*f.width).toInt();height=(b.height()*f.height).toInt();x=(b.width()*f.x).toInt();y=(b.height()*f.y).toInt() }
            runCatching { manager.updateViewLayout(inset.view,inset.params) }
            inset.chrome.forEachIndexed { index,(view,p) ->
                p.width=if(index==0)inset.params.width else pixels(32);p.height=pixels(32)
                p.x=inset.params.x+if(index==0)0 else (inset.params.width-pixels(32)).coerceAtLeast(0)
                p.y=inset.params.y+if(index==0)0 else (inset.params.height-pixels(32)).coerceAtLeast(0)
                runCatching { manager.updateViewLayout(view,p) }
            }
        }
    }
    private fun createInset(source:CameraSource):Inset? {
        val owner=Any();val box=FrameLayout(context).apply { clipChildren=true;tag="live-inset-${source.name}" }
        val p=params(pixels(120),pixels(160),protected=source==CameraSource.SCREEN)
        if(source!=CameraSource.SCREEN) {
            val quality=app.status.value.cameras[source]?.quality
            val aspect=quality?.let { it.width.toFloat()/it.height } ?: .75f
            val fit=MixedPreviewFrame(context,aspect).apply { setBackgroundColor(Color.BLACK) }
            val preview=TextureView(context).apply { contentDescription="Live ${source.name} camera";surfaceTextureListener=object:TextureView.SurfaceTextureListener {
                private var surface:Surface?=null
                override fun onSurfaceTextureAvailable(texture:SurfaceTexture,w:Int,h:Int) { surface=Surface(texture);app.preview(source,owner,surface) }
                override fun onSurfaceTextureSizeChanged(t:SurfaceTexture,w:Int,h:Int)=Unit
                override fun onSurfaceTextureUpdated(t:SurfaceTexture)=Unit
                override fun onSurfaceTextureDestroyed(t:SurfaceTexture):Boolean { app.preview(source,owner,null);surface?.release();surface=null;return true }
            } }
            fit.addView(preview,FrameLayout.LayoutParams(-1,-1));box.addView(fit,FrameLayout.LayoutParams(-1,-1))
        } else {
            // Re-displaying a live screen texture on its own source creates infinite feedback.
            box.background=rounded(0x22151b20,0f).apply { setStroke(pixels(1),Palette.mint) }
        }
        val header=LinearLayout(context).apply { tag="inset-controls-${source.name}";gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(0xaa000000.toInt()) }
        val name=context.label(if(source==CameraSource.SCREEN)"Screen frame" else source.name.lowercase().replaceFirstChar { it.uppercase() },11f).apply { setPadding(pixels(4),0,0,0);gravity=Gravity.CENTER_VERTICAL }
        header.addView(name,LinearLayout.LayoutParams(0,pixels(32),1f))
        header.addView(context.iconButton(HeaderIcon.CLOSE) {
            val frames=MixedLayout.read(current.mixedLayout).toMutableMap();frames[source]=frames.getValue(source).copy(visible=false);apply(current.copy(mixedLayout=MixedLayout.write(frames)))
        }.apply { squareFace=true;contentDescription="Hide ${source.name} inset; raw capture continues" },LinearLayout.LayoutParams(pixels(32),pixels(32)))
        val resize=object:View(context) {
            private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Palette.mint;strokeWidth=2*d }
            override fun onDraw(c:Canvas) { c.drawLine(8*d,height-5*d,width-5*d,8*d,pen);c.drawLine(17*d,height-5*d,width-5*d,17*d,pen) }
        }.apply { contentDescription="Resize ${source.name} inset";setBackgroundColor(0x88000000.toInt()) }
        resize.tag="inset-resize-${source.name}"
        fun gestures(target:View,resizing:Boolean) {
            var x=0f;var y=0f;var original=MixedLayout.read(current.mixedLayout).getValue(source)
            target.setOnTouchListener { _,e -> when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=e.rawX;y=e.rawY;original=MixedLayout.read(current.mixedLayout).getValue(source);true }
                MotionEvent.ACTION_MOVE -> {
                    val b=bounds();val dx=(e.rawX-x)/b.width();val dy=(e.rawY-y)/b.height()
                    val next=(if(resizing)original.copy(width=original.width+dx,height=original.height+dy) else original.copy(x=original.x+dx,y=original.y+dy)).bounded()
                    val frames=MixedLayout.read(current.mixedLayout).toMutableMap();frames[source]=next;apply(current.copy(mixedLayout=MixedLayout.write(frames)),false);true
                }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> true
                else -> false
            } }
        }
        gestures(name,false);gestures(box,false);gestures(resize,true)
        if(!add(box,p))return null
        val chrome=listOf(header to params(p.width,pixels(32)),resize to params(pixels(32),pixels(32)))
        val added=mutableListOf<View>()
        for((v,cp) in chrome) {
            if(!add(v,cp)) { added.forEach { runCatching { manager.removeView(it) } };runCatching { manager.removeView(box) };app.preview(source,owner,null);return null }
            added+=v
        }
        return Inset(box,p,owner,chrome)
    }
    private fun removeInset(source:CameraSource) { insets.remove(source)?.let { app.preview(source,it.owner,null);it.chrome.forEach { (v,_) -> runCatching { manager.removeView(v) } };runCatching { manager.removeView(it.view) } } }
    private fun removeWindows() { handler.removeCallbacks(collapse);handler.removeCallbacks(counterTicker);bar?.let { runCatching { manager.removeView(it) } };bar=null;insets.keys.toList().forEach(::removeInset);expanded=false }
    fun hide() { enabled=false;removeWindows() }
}
