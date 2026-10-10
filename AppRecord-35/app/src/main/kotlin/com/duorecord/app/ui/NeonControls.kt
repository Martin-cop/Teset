package com.duorecord.app.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

/** Clip scrolling content, while allowing the surrounding panel to draw its underlight. */
internal open class ViewportScrollView(context:Context):ScrollView(context) {
    override fun dispatchDraw(canvas:android.graphics.Canvas) {
        val saved=canvas.save()
        canvas.clipRect(scrollX,scrollY,scrollX+width,scrollY+height)
        super.dispatchDraw(canvas);canvas.restoreToCount(saved)
    }
}

internal fun Context.iconButton(icon:HeaderIcon,action:()->Unit)=SignButton(this).apply {
    this.icon=icon;text=when(icon) {
        HeaderIcon.COPY -> "Copy files";HeaderIcon.SELECT -> "Select files";HeaderIcon.NORMAL_SPEED -> "Normal speed · 1×";HeaderIcon.SHARE -> "Share latest capture";HeaderIcon.TRIM -> "Trim video";HeaderIcon.RECORD -> "Record";HeaderIcon.PAUSE -> "Pause recording";HeaderIcon.RESUME -> "Resume recording"
        HeaderIcon.BOOKMARK -> "Mark important moment";HeaderIcon.CHARGER -> "Wall charger";HeaderIcon.POWER_BANK -> "Power bank"
        HeaderIcon.GUIDE -> "Guide";HeaderIcon.ABOUT -> "About";HeaderIcon.SETTINGS -> "Settings"
        HeaderIcon.PREVIOUS -> "Previous";HeaderIcon.NEXT -> "Next"
        HeaderIcon.INFO_TOOLS -> "Info & tools";HeaderIcon.STATISTICS -> "Selection statistics"
        HeaderIcon.VIDEO_MODE -> "Video mode";HeaderIcon.PHOTO_MODE -> "Photo mode";HeaderIcon.AUDIO_MODE -> "Audio mode";HeaderIcon.SCREEN_MODE -> "Screen mode"
        HeaderIcon.MIXED_MODE -> "Mixed recording";HeaderIcon.INTERNAL_AUDIO -> "Record internal playback audio";HeaderIcon.PLAY -> "Play latest capture";HeaderIcon.STOP -> "Stop capture"
        HeaderIcon.MICROPHONE -> "Record ambient audio"
        HeaderIcon.PREVIOUS_ALL -> "Previous file, all types";HeaderIcon.NEXT_ALL -> "Next file, all types"
        HeaderIcon.SCHEDULE -> "Schedule recording"
        HeaderIcon.DELETE -> "Delete this file"
        HeaderIcon.SCREEN_LIGHT -> "Front screen light"
        HeaderIcon.CLOSE -> "Close";HeaderIcon.REFRESH -> "Refresh files";HeaderIcon.LIGHT -> "Recording light"
        HeaderIcon.RECORDINGS -> "Recordings";HeaderIcon.FOLDER -> "Open recording folder";HeaderIcon.EXPORT -> "Export by time"
    }
    contentDescription=text;tooltipText=text;setOnClickListener { action() }
}

/** Same 48 dp frame / 30 dp inset input as the reference, adapted to recording values. */
internal fun Context.inputRow(title:String,input:EditText):LinearLayout=LinearLayout(this).apply {
    gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false
    setPadding(dp(7),dp(8),dp(12),dp(8))
    background=rounded(0xff1e2429.toInt(),dp(12).toFloat()).apply { setStroke(dp(2),0xff777b7e.toInt()) }
    val caption=label(title,15f).apply { gravity=Gravity.CENTER;includeFontPadding=false;setLineSpacing(0f,1f);labelFor=input.id }
    addView(caption,LinearLayout.LayoutParams(dp(110),-1).apply { marginEnd=dp(8) })
    val entry=FrameLayout(context).apply {
        background=rounded(Color.BLACK).apply { val r=dp(10).toFloat();cornerRadii=floatArrayOf(0f,0f,r,r,r,r,0f,0f) }
        input.background=null;input.textSize=16f;input.setTextColor(Palette.mint);input.setHintTextColor(0xffa0a0a0.toInt())
        input.gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;input.layoutDirection=View.LAYOUT_DIRECTION_LTR;input.isSingleLine=true
        input.setPadding(dp(6),0,dp(32),0)
        addView(input,FrameLayout.LayoutParams(-1,-1))
        addView(label("×",24f,Palette.mint).apply {
            gravity=Gravity.CENTER;includeFontPadding=false;contentDescription="Clear $title";isFocusable=true;isEnabled=input.isEnabled
            if(!isEnabled)setTextColor(0xff70777b.toInt())
            setOnClickListener { if(input.isEnabled) { input.setText("");input.requestFocus() } }
        },FrameLayout.LayoutParams(dp(28),-1,Gravity.RIGHT))
    }
    addView(entry,LinearLayout.LayoutParams(0,dp(30),1f).apply { marginEnd=dp(2) })
}

internal fun Context.durationInputRow(input:EditText,unit:NeonSpinner,selected:Int):LinearLayout=LinearLayout(this).apply {
    gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8))
    background=rounded(0xff1e2429.toInt(),dp(12).toFloat()).apply { setStroke(dp(2),0xff777b7e.toInt()) }
    addView(label("File duration",14f).apply { gravity=Gravity.CENTER;includeFontPadding=false;setLineSpacing(0f,1f) },LinearLayout.LayoutParams(dp(94),-1))
    input.gravity=Gravity.CENTER;input.setPadding(dp(4),0,dp(4),0);input.background=rounded(android.graphics.Color.BLACK,dp(6).toFloat())
    addView(input,LinearLayout.LayoutParams(dp(56),dp(30)).apply { marginStart=dp(4);marginEnd=dp(8) })
    val choices=listOf("Seconds","Minutes","Hours")
    unit.adapter=object:ArrayAdapter<String>(this@durationInputRow,android.R.layout.simple_spinner_item,choices) {
        override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=label(getItem(position).orEmpty(),14f,Palette.mint).apply { gravity=Gravity.CENTER;includeFontPadding=false;setLineSpacing(0f,1f) }
    }
    unit.setSelection(selected);unit.contentDescription="File duration unit"
    addView(unit,LinearLayout.LayoutParams(0,dp(30),1f))
    addView(label("▾",16f).apply { gravity=Gravity.CENTER;includeFontPadding=false;setOnClickListener { if(unit.isEnabled)unit.performClick() } },LinearLayout.LayoutParams(dp(16),-1))
}

/** Keep the platform Switch thumb/track and geometry; only add a discreet template underlight. */
internal class NativeSaveSwitch(context:Context):Switch(context) {
    var onUserChange:((Boolean)->Unit)?=null
    private var binding=false
    private val glow=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    fun bindChecked(value:Boolean) { binding=true;isChecked=value;binding=false;stateDescription=if(value)"Protected" else "Not protected" }
    init {
        text="";showText=false
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        setOnCheckedChangeListener { _,value -> stateDescription=if(value)"Protected" else "Not protected";invalidate();if(!binding)onUserChange?.invoke(value) }
    }
    override fun onDraw(canvas:android.graphics.Canvas) {
        val bounds=trackDrawable?.bounds
        if(isEnabled && !Palette.simple && bounds!=null && !bounds.isEmpty) {
            val radius=bounds.height()/2f
            glow.color=0xff1e2429.toInt();glow.setShadowLayer(context.dp(3).toFloat(),0f,0f,if(isChecked)0xff44ff80.toInt() else 0xffffd22c.toInt())
            canvas.drawRoundRect(android.graphics.RectF(bounds),radius,radius,glow);glow.clearShadowLayer()
        }
        super.onDraw(canvas)
    }
}

/** Android transforms child touch/accessibility bounds together with its rotation. */
internal class VerticalSaveControl(context:Context,val toggle:NativeSaveSwitch):FrameLayout(context) {
    init {
        clipChildren=false;clipToPadding=false
        toggle.apply {
            rotation=-90f;gravity=Gravity.CENTER_VERTICAL
            minimumHeight=0;minHeight=0;minimumWidth=0;minWidth=0
            setPadding(0,0,0,0)
        }
        addView(toggle,LayoutParams(LayoutParams.WRAP_CONTENT,context.dp(28),Gravity.CENTER))
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        // Measure along the switch's natural horizontal axis before rotating it.
        // A normal WRAP_CONTENT pass would constrain it to the narrow vertical slot
        // and clip the platform track/thumbnail at its unrotated edge.
        toggle.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),MeasureSpec.makeMeasureSpec(context.dp(28),MeasureSpec.EXACTLY))
        setMeasuredDimension(resolveSize(context.dp(28),widthMeasureSpec),resolveSize(maxOf(context.dp(48),toggle.measuredWidth),heightMeasureSpec))
    }
}

internal class NeonSwitch(context:Context):Switch(context) {
    var onUserChange:((Boolean)->Unit)?=null
    private var binding=false
    fun bindChecked(value:Boolean) { binding=true;isChecked=value;binding=false;updateFrame() }
    init {
        setTextColor(Palette.text);textSize=15f;showText=false;textOn="";textOff=""
        setPadding(context.dp(8),context.dp(6),context.dp(8),context.dp(6));switchPadding=context.dp(8)
        thumbDrawable=GradientDrawable().apply { shape=GradientDrawable.OVAL;setSize(context.dp(22),context.dp(22));setColor(Color.WHITE) }
        trackDrawable=rounded(Color.WHITE,context.dp(6).toFloat()).apply { setSize(context.dp(38),context.dp(12)) }
        val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
        thumbTintList=ColorStateList(states,intArrayOf(0xff65ff99.toInt(),0xff9a9a9a.toInt()))
        trackTintList=ColorStateList(states,intArrayOf(0xff277a49.toInt(),0xff555555.toInt()))
        setOnCheckedChangeListener { _,value -> updateFrame();if(!binding)onUserChange?.invoke(value) };updateFrame()
    }
    private fun updateFrame() {
        background=rounded(0xff1e2429.toInt(),context.dp(10).toFloat()).apply { setStroke(context.dp(2),if(isChecked)0xff65ff99.toInt() else 0xff777b7e.toInt()) }
        stateDescription=if(isChecked)"On" else "Off"
    }
    override fun setEnabled(enabled:Boolean) { super.setEnabled(enabled);alpha=if(enabled)1f else .5f }
}

internal fun Context.spinnerRow(title:String,spinner:NeonSpinner,values:List<String>,selected:Int):LinearLayout=LinearLayout(this).apply {
    gravity=Gravity.CENTER_VERTICAL;setPadding(dp(7),dp(5),dp(7),dp(5));clipChildren=false;clipToPadding=false
    setLayerType(View.LAYER_TYPE_SOFTWARE,null);background=GlowOutline(resources.displayMetrics.density)
    addView(label(title,15f).apply { gravity=Gravity.CENTER;includeFontPadding=false },LinearLayout.LayoutParams(dp(110),-1))
    spinner.adapter=object:ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,values) {
        override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=label(getItem(position).orEmpty(),15f,Palette.mint).apply {
            gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;includeFontPadding=false;setPadding(dp(6),0,dp(6),0);isSingleLine=true;background=null
        }
    }
    spinner.contentDescription="$title selection";spinner.setSelection(selected.coerceAtLeast(0))
    addView(spinner,LinearLayout.LayoutParams(0,-1,1f))
    addView(label("▾",17f,Palette.text).apply { gravity=Gravity.CENTER;contentDescription="Choose $title";isFocusable=true;setOnClickListener { spinner.performClick() } },LinearLayout.LayoutParams(dp(20),-1))
}

/** A title, scrollable content and SignButton actions; no platform dialog chrome leaks into the theme. */
internal class NeonDialog private constructor(context:Context,title:String,body:View,actions:List<Action>,private val showHeader:Boolean=true):Dialog(context) {
    private val ownerContext=context
    private val buttons=mutableMapOf<Int,SignButton>()
    private val errorLabel=context.label("",14f,Palette.red).apply { visibility=View.GONE;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private lateinit var contentScroll:ScrollView
    companion object {
        private var current=java.lang.ref.WeakReference<NeonDialog>(null)
        fun showing(context:Context):NeonDialog?=current.get()?.takeIf { it.ownerContext===context && it.isShowing }
    }
    fun showError(message:String) { errorLabel.text=message;errorLabel.visibility=View.VISIBLE;errorLabel.post { contentScroll.smoothScrollTo(0,0) } }
    data class Action(val id:Int,val title:String,val listener:((DialogInterface,Int)->Unit)?)
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val outer=context.column(0).apply { setBackgroundColor(Palette.background) }
        val panel=GlowPanel(context).apply { setPadding(context.dp(18),context.dp(18),context.dp(18),context.dp(18)) }
        val closeAction=actions.firstOrNull { it.id==DialogInterface.BUTTON_NEGATIVE }
        val header=LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false;tag="panel-title-strip"
            setPadding(context.dp(8),context.dp(2),context.dp(2),context.dp(2))
            background=rounded(0xff050607.toInt()).apply { val r=context.dp(14).toFloat();cornerRadii=floatArrayOf(r,r,r,r,0f,0f,0f,0f) }
        }
        header.addView(context.label(title,16f,Palette.text,true).apply { includeFontPadding=false;setLineSpacing(0f,1f) },LinearLayout.LayoutParams(0,-2,1f))
        val close=context.iconButton(HeaderIcon.CLOSE) { closeAction?.listener?.invoke(this,DialogInterface.BUTTON_NEGATIVE);dismiss() }.apply { squareFace=true }
        if(closeAction?.title!="Close")header.addView(close,LinearLayout.LayoutParams(context.dp(36),context.dp(36)))
        if(showHeader)panel.addView(header,0,LinearLayout.LayoutParams(-1,context.dp(40)))
        if(showHeader)panel.addView(View(context).apply { setBackgroundColor(0xff343638.toInt()) },LinearLayout.LayoutParams(-1,context.dp(2)))
        panel.addGap(12)
        if(closeAction?.title in setOf("Back","Cancel"))buttons[closeAction!!.id]=close
        contentScroll=object:ViewportScrollView(context) {
            override fun onMeasure(w:Int,h:Int) {
                val cap=(resources.displayMetrics.heightPixels*.66f).toInt().coerceAtLeast(context.dp(180))
                val available=MeasureSpec.getSize(h).takeIf { it>0 } ?: cap
                super.onMeasure(w,MeasureSpec.makeMeasureSpec(minOf(cap,available),MeasureSpec.AT_MOST))
            }
        }.apply {
            // A scroll viewport must clip its contents above the pinned action buttons.
            clipChildren=true;clipToPadding=true
            addView(context.column().apply { addWide(errorLabel);addWide(body) })
        }
        panel.addContent(contentScroll)
        if(actions.any { it.id!=DialogInterface.BUTTON_NEGATIVE || it.title !in setOf("Back","Cancel") })panel.addGap(12)
        actions.forEachIndexed { index,action ->
            if(action.id==DialogInterface.BUTTON_NEGATIVE && action.title in setOf("Back","Cancel"))return@forEachIndexed
            val button=context.button(action.title) { action.listener?.invoke(this,action.id);dismiss() }
            buttons[action.id]=button;panel.addContent(button,48)
            if(index<actions.lastIndex)panel.addGap(2)
        }
        outer.addWide(panel);setContentView(outer)
        if(showHeader)PageGuides.dialog(title)?.let { help -> addGuide { Builder(context).setTitle("$title guide").setMessage(help).setNegativeButton("Back",null).show() } }
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    fun addGuide(action:()->Unit) {
        val content=findViewById<ViewGroup>(android.R.id.content)
        fun locate(v:View):LinearLayout? {
            if(v is LinearLayout && v.tag=="panel-title-strip")return v
            if(v is ViewGroup)for(i in 0 until v.childCount)locate(v.getChildAt(i))?.let { return it }
            return null
        }
        locate(content)?.let { header ->
            val existing=(0 until header.childCount).map { header.getChildAt(it) }.filterIsInstance<SignButton>().firstOrNull { it.icon==HeaderIcon.GUIDE }
            if(existing!=null)existing.setOnClickListener { action() } else header.addView(context.iconButton(HeaderIcon.GUIDE,action).apply { squareFace=true },if((header.getChildAt(header.childCount-1) as? SignButton)?.icon==HeaderIcon.CLOSE)header.childCount-1 else header.childCount,LinearLayout.LayoutParams(context.dp(36),context.dp(36)))
        }
    }
    fun getButton(which:Int):SignButton=buttons.getValue(which)
    override fun show() { super.show();current=java.lang.ref.WeakReference(this);window?.setLayout(context.resources.displayMetrics.widthPixels-context.dp(12),WindowManager.LayoutParams.WRAP_CONTENT) }
    class Builder(private val context:Context) {
        private var showHeader=true
        fun withoutHeader()=apply { showHeader=false }
        private var title="";private var message:String?=null;private var view:View?=null
        private val actions=mutableListOf<Action>()
        fun setTitle(value:String)=apply { title=value }
        fun setMessage(value:String)=apply { message=value }
        fun setView(value:View)=apply { view=value }
        fun setPositiveButton(text:String,listener:((DialogInterface,Int)->Unit)?)=apply { actions+=Action(DialogInterface.BUTTON_POSITIVE,text,listener) }
        fun setNegativeButton(text:String,listener:((DialogInterface,Int)->Unit)?)=apply { actions+=Action(DialogInterface.BUTTON_NEGATIVE,text,listener) }
        fun setNeutralButton(text:String,listener:((DialogInterface,Int)->Unit)?)=apply { actions+=Action(DialogInterface.BUTTON_NEUTRAL,text,listener) }
        fun create():NeonDialog {
            val content=view ?: context.label(message.orEmpty(),17f).apply { setPadding(context.dp(16),context.dp(12),context.dp(16),context.dp(12)) }
            return NeonDialog(context,title,content,actions,showHeader)
        }
        fun show():NeonDialog=create().also { it.show() }
    }
}
