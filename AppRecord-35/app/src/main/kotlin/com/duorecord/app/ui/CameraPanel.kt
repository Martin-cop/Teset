package com.duorecord.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.*
import android.widget.*
import com.duorecord.contracts.CameraCaptureStatus
import com.duorecord.domain.CaptureQuality
import java.util.Locale

internal class CameraPanel(
    context:Context,private val cameraName:String,toggle:()->Unit,
    private val preview:(Surface?)->Unit,private val qualityChanged:(CaptureQuality)->Unit,play:()->Unit
):LinearLayout(context),TextureView.SurfaceTextureListener {
    val record=context.button("Start $cameraName",action=toggle).apply { toggleAppearance=true;captureState=0 }
    private val shutter=View(context).apply { setBackgroundColor(Color.WHITE);alpha=0f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    var initialSelection=false
    var playbackAvailable=false
    var photoIntervalSeconds:Int?=null
    private val selectedLabel=context.label("Detecting quality…",12f,0xffffdf58.toInt()).apply { gravity=Gravity.CENTER;isSingleLine=true }
    private val slider=QualityRuler(context)
    private val texture=TextureView(context).apply { surfaceTextureListener=this@CameraPanel;isOpaque=true }
    private val placeholder=context.label("$cameraName camera\nStopped",13f,Palette.muted).apply { gravity=Gravity.CENTER;setPadding(context.dp(5),0,context.dp(5),0) }
    private val overlay=context.label("",11f,Color.WHITE).apply { setPadding(context.dp(5),context.dp(4),context.dp(5),context.dp(4));setBackgroundColor(0xc0000000.toInt());gravity=Gravity.CENTER }
    val monitor=object:FrameLayout(context) {
        override fun onMeasure(w:Int,h:Int) { val width=MeasureSpec.getSize(w);super.onMeasure(w,if((parent as? android.view.ViewGroup)?.childCount==1 && parent !is LinearLayout)h else MeasureSpec.makeMeasureSpec(width*4/3,MeasureSpec.EXACTLY)) }
    }.apply { setBackgroundColor(Color.BLACK);clipChildren=true }
    private var surface:Surface?=null
    private var options=emptyList<CaptureQuality>()
    private var chosen:CaptureQuality?=null
    private var output:CaptureQuality?=null
    private var tracking=false
    private var enabled=true
    private var state=CameraCaptureStatus()
    init {
        orientation=VERTICAL;clipChildren=false;clipToPadding=false
        addWide(record,48);addGap(12)
        monitor.addView(texture,FrameLayout.LayoutParams(-1,-1))
        monitor.addView(placeholder,FrameLayout.LayoutParams(-1,-1))
        monitor.addView(overlay,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        monitor.addView(shutter,FrameLayout.LayoutParams(-1,-1))
        addWide(monitor);addGap(16)
        val quality=LinearLayout(context).apply { orientation=VERTICAL;tag="camera-quality-control";background=rounded(Color.BLACK,context.dp(10).toFloat());clipChildren=true;clipToOutline=true }
        selectedLabel.tag="camera-quality-value";selectedLabel.setBackgroundColor(Color.BLACK)
        quality.addWide(selectedLabel,24);quality.addWide(slider,36)
        addWide(quality)
        val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:MotionEvent)=true
            override fun onDoubleTap(e:MotionEvent):Boolean { play();return true }
        })
        monitor.setOnTouchListener { _,event -> gestures.onTouchEvent(event) }
        monitor.contentDescription="Double tap $cameraName monitor to toggle full screen"
        slider.changed={ index,commit -> tracking=!commit;options.getOrNull(index)?.let {
            chosen=it;selectedLabel.text="${it.width} × ${it.height}"
            if(commit)qualityChanged(it)
        } }
        render(state,true)
    }
    fun qualities(values:List<CaptureQuality>,key:String,error:String?=null) {
        options=values
        val index=values.indexOfFirst { it.key==key }.takeIf { it>=0 } ?: values.indices.minByOrNull { kotlin.math.abs(values[it].pixels-480L*640) } ?: 0
        slider.steps=values.size;slider.selected=index
        chosen=values.getOrNull(index)
        selectedLabel.text=chosen?.let { "${it.width} × ${it.height}" } ?: "Quality unavailable"
        slider.contentDescription="$cameraName resolution, minimum ${values.firstOrNull()?.key}, maximum ${values.lastOrNull()?.key}"
        slider.isEnabled=enabled && values.size>1
        if(!state.recording && error!=null)placeholder.text=error
    }
    fun render(value:CameraCaptureStatus,controlsEnabled:Boolean) {
        state=value;enabled=controlsEnabled
        foreground=null
        val frameColor=if(value.recording)Palette.mint else if(!controlsEnabled)Palette.muted else if(value.pending || initialSelection)0xffffdf58.toInt() else Color.WHITE
        monitor.foreground=rounded(Color.TRANSPARENT,context.dp(3).toFloat()).apply { setStroke(context.dp(2),frameColor) }
        record.isEnabled=controlsEnabled
        record.isSelected=value.recording
        record.captureState=if(value.recording)2 else if(value.pending || initialSelection)1 else 0
        record.text=if(value.pending)"Please wait…" else if(value.recording)"Stop $cameraName" else if(initialSelection)"Selected $cameraName" else "Select $cameraName"
        record.contentDescription="$cameraName camera, ${if(value.recording)"recording, stop" else "stopped, start"}"
        slider.isEnabled=controlsEnabled && options.size>1
        output=value.quality ?: chosen
        if(value.recording && !tracking)value.quality?.let { selectedLabel.text="${it.width} × ${it.height}" }
        val live=value.recording && value.previewMessage.isBlank()
        texture.alpha=if(live)1f else 0f
        placeholder.visibility=if(live)GONE else VISIBLE
        placeholder.text=if(value.previewMessage.isNotBlank())value.previewMessage else if(value.pending)"Starting…" else "$cameraName camera\nStopped"
        overlay.text=if(value.recording) {
            val q=value.quality
            if(photoIntervalSeconds!=null)"● PHOTO · every ${photoIntervalSeconds}s\n${context.clock(System.currentTimeMillis()-value.startedMs)}" else "● REC  ${context.clock(System.currentTimeMillis()-value.startedMs)}\n${value.fps} fps\n${String.format(Locale.US,"%.2f",(q?.bitRate ?: 0)/1_000_000.0)} Mbps"
        } else "${cameraName.uppercase(Locale.US)} · ${if(value.pending)"WAIT" else "OFF"}"
        updateTransform()
    }
    private fun updateTransform() {
        val q=output ?: chosen ?: return
        val w=texture.width.toFloat();val h=texture.height.toFloat()
        if(w<=0 || h<=0)return
        val scale=minOf(w/q.width,h/q.height)
        texture.setTransform(Matrix().apply { setScale(q.width*scale/w,q.height*scale/h,w/2,h/2) })
    }
    fun resumePreview() { surface?.let(preview) }
    fun pausePreview() { preview(null) }
    fun flashCaptured() { shutter.animate().cancel();shutter.alpha=1f;shutter.animate().alpha(0f).setStartDelay(90).setDuration(220).start() }
    fun dispose() { shutter.animate().cancel();preview(null);surface?.release();surface=null }
    override fun onSurfaceTextureAvailable(st:SurfaceTexture,width:Int,height:Int) { surface=Surface(st);preview(surface);updateTransform() }
    override fun onSurfaceTextureSizeChanged(st:SurfaceTexture,width:Int,height:Int) { updateTransform() }
    override fun onSurfaceTextureUpdated(st:SurfaceTexture)=Unit
    override fun onSurfaceTextureDestroyed(st:SurfaceTexture):Boolean { dispose();return true }
}
