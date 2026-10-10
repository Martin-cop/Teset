package com.duorecord.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.duorecord.domain.*
import java.text.SimpleDateFormat
import java.util.*

internal object Palette {
    var simple=false
    val background get()=Color.parseColor(if(simple)"#151A22" else "#4C4C4C");val card=Color.parseColor("#212121");val text=Color.parseColor("#EEEEEE")
    val muted=Color.parseColor("#CCCCCC");val mint=Color.parseColor("#65FF99");val red=Color.parseColor("#FF5555")
}
internal fun Context.dp(n:Int)=(resources.displayMetrics.density*n).toInt()
internal fun rounded(color:Int,radius:Float=16f)=GradientDrawable().apply { setColor(color);cornerRadius=radius }
internal fun Context.label(text:String,size:Float=16f,color:Int=Palette.text,bold:Boolean=false)=TextView(this).apply {
    this.text=text;textSize=size;setTextColor(color);gravity=Gravity.START
    if(bold)setTypeface(typeface,Typeface.BOLD)
    setLineSpacing(dp(4).toFloat(),1f)
}
internal fun Context.column(padding:Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;clipChildren=false;clipToPadding=false;setPadding(dp(padding),dp(padding),dp(padding),dp(padding)) }
internal fun Context.button(text:String,role:ButtonRole=ButtonRole.INSIDE,action:()->Unit)=SignButton(this).apply {
    this.text=text;contentDescription=text;blueUnderlight=role==ButtonRole.OUTSIDE
    setOnClickListener { action() }
}
internal fun LinearLayout.addGap(height:Int=12) { addView(View(context),LinearLayout.LayoutParams(1,context.dp(height))) }
internal fun LinearLayout.addWide(view:View,height:Int=ViewGroup.LayoutParams.WRAP_CONTENT) { addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,if(height>=0)context.dp(height) else height)) }
internal fun Context.clock(ms:Long):String {
    val total=ms.coerceAtLeast(0)/1000
    return String.format(Locale.US,"%02d:%02d:%02d",total/3600,(total/60)%60,total%60)
}
internal fun timestamp(ms:Long)=SimpleDateFormat("yyyy-MM-dd  HH:mm:ss",Locale.US).format(Date(ms))
internal fun fileSize(bytes:Long):String=when {
    bytes>=1024L*1024*1024 -> String.format(Locale.US,"%.1f GB",bytes/1073741824.0)
    bytes>=1024*1024 -> String.format(Locale.US,"%.1f MB",bytes/1048576.0)
    else -> "${bytes/1024} KB"
}

/** Fit the two metadata lines before drawing, including narrow selection-mode rows. */
internal class MetadataLabel(context:Context,private val maximumSp:Float):TextView(context) {
    init { setTextColor(Palette.text);isSingleLine=true;includeFontPadding=false;setLineSpacing(0f,1f) }
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val available=View.MeasureSpec.getSize(widthSpec)-compoundPaddingLeft-compoundPaddingRight
        if(available>0 && View.MeasureSpec.getMode(widthSpec)!=View.MeasureSpec.UNSPECIFIED) {
            val unit=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,1f,resources.displayMetrics)
            var size=maximumSp*unit
            val measure=android.text.TextPaint(paint)
            measure.textSize=size
            while(measure.measureText(text.toString())>available && size>8*unit) { size-=.5f*unit;measure.textSize=size }
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size)
        }
        super.onMeasure(widthSpec,heightSpec)
    }
}
internal class ClipListAdapter(private val open:(Clip)->Unit,private val protect:(Clip)->Unit,private val selectionChanged:()->Unit={}):RecyclerView.Adapter<ClipListAdapter.Holder>() {
    private var storedClips:List<Clip> = emptyList()
    private var submission=0
    var clips:List<Clip>
        get()=storedClips
        set(value) { submission++;if(storedClips!=value)publish(value,prepare(value,rows)) }
    private fun prepare(value:List<Clip>,before:List<RecordingRow>):Pair<List<RecordingRow>,DiffUtil.DiffResult> {
        val after=RecordingRows.group(value)
        val changes=DiffUtil.calculateDiff(object:DiffUtil.Callback() {
            override fun getOldListSize()=before.size
            override fun getNewListSize()=after.size
            override fun areItemsTheSame(old:Int,new:Int)=before[old].clips.map { it.id }==after[new].clips.map { it.id }
            override fun areContentsTheSame(old:Int,new:Int)=before[old]==after[new]
        },false)
        return after to changes
    }
    private fun publish(value:List<Clip>,prepared:Pair<List<RecordingRow>,DiffUtil.DiffResult>) {
        storedClips=value;rows=prepared.first;selected.retainAll(selectionUniverse.map { it.id }.toSet())
        prepared.second.dispatchUpdatesTo(this);selectionChanged()
    }
    suspend fun submit(value:List<Clip>) {
        if(storedClips==value)return
        val generation=++submission;val before=rows
        val prepared=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { prepare(value,before) }
        if(generation==submission)publish(value,prepared)
    }
    var rows:List<RecordingRow> = emptyList();private set
    private val pending=mutableSetOf<String>()
    val selected=mutableSetOf<String>()
    var selecting=false;private set
    var allFiles:List<Clip>?=null
    private val selectionUniverse get()=allFiles ?: clips
    fun selectedClips()=selectionUniverse.filter { it.id in selected }
    val allSelected:Boolean get()=selectionUniverse.filter { it.status!=ClipStatus.WRITING }.let { it.isNotEmpty() && it.all { clip -> clip.id in selected } }
    fun toggleAll() { if(allSelected) { selected.clear();selecting=true;notifyDataSetChanged();selectionChanged() } else selectAll() }
    fun selectAll() { selecting=true;selected.clear();selected.addAll(selectionUniverse.filter { it.status!=ClipStatus.WRITING }.map { it.id });notifyDataSetChanged();selectionChanged() }
    fun beginSelection() { selecting=true;selected.clear();notifyDataSetChanged();selectionChanged() }
    fun clearSelection() { selecting=false;selected.clear();notifyDataSetChanged();selectionChanged() }
    private fun toggle(row:RecordingRow) {
        selecting=true
        val ids=row.clips.filter { it.status!=ClipStatus.WRITING }.map { it.id }
        if(ids.isEmpty())return
        if(ids.all { it in selected })selected.removeAll(ids.toSet()) else selected.addAll(ids)
        notifyDataSetChanged();selectionChanged()
    }
    class Holder(val row:LinearLayout,val check:CheckBox,val title:TextView,val details:TextView,val rear:Button,val front:Button,val keepRear:NativeSaveSwitch,val keepFront:NativeSaveSwitch,val microphone:View):RecyclerView.ViewHolder(row)
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder {
        val c=parent.context
        val row=GlowPanel(c,compact=true).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_LTR;gravity=Gravity.CENTER_VERTICAL
            // Four equal 12 dp insets: 4 dp shadow reserve + 8 dp visible padding.
            setPadding(c.dp(12),c.dp(12),c.dp(12),c.dp(12))
            layoutParams=RecyclerView.LayoutParams(-1,c.dp(80))
        }
        val check=CheckBox(c).apply { text="";buttonTintList=ColorStateList.valueOf(Palette.mint) }
        row.addView(check,LinearLayout.LayoutParams(c.dp(30),c.dp(48)))
        val title=MetadataLabel(c,14f).apply { gravity=Gravity.TOP }
        val details=MetadataLabel(c,13f).apply { gravity=Gravity.BOTTOM;setTextColor(Palette.muted) }
        // Text spans the 40 dp visible square, not its transparent lighting reserve.
        val text=c.column().apply { addWide(title,20);addWide(details,20) }
        row.addView(text,LinearLayout.LayoutParams(0,c.dp(40),1f).apply { marginEnd=c.dp(6) })
        fun actions(letter:String):Pair<Button,NativeSaveSwitch> {
            val pair=LinearLayout(c).apply {
                gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false
                setPadding(c.dp(4),c.dp(4),c.dp(4),c.dp(4))
                background=rounded(0xff1e2429.toInt(),c.dp(10).toFloat()).apply { setStroke(c.dp(1),0xff626b73.toInt()) }
            }
            val play=c.button(letter) {}.apply { squareFace=true }
            val keep=NativeSaveSwitch(c)
            val vertical=VerticalSaveControl(c,keep)
            fun addSwitch()=pair.addView(vertical,LinearLayout.LayoutParams(c.dp(28),c.dp(48)))
            if(letter=="F")addSwitch()
            pair.addView(play,LinearLayout.LayoutParams(c.dp(48),c.dp(48)))
            if(letter=="R")addSwitch()
            row.addView(pair,LinearLayout.LayoutParams(c.dp(84),c.dp(56)).apply { if(letter=="F")marginStart=c.dp(8) })
            return play to keep
        }
        val microphone=object:View(c) {
            val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas:android.graphics.Canvas) {
                val d=resources.displayMetrics.density*1.6f;val x=width/2f;val y=height/2f
                p.color=Color.WHITE;p.style=android.graphics.Paint.Style.STROKE;p.strokeWidth=2*d
                if(tag=="screen") {
                    canvas.drawRoundRect(android.graphics.RectF(x-10*d,y-8*d,x+10*d,y+5*d),d,d,p)
                    canvas.drawLine(x,y+5*d,x,y+10*d,p);canvas.drawLine(x-6*d,y+10*d,x+6*d,y+10*d,p);return
                }
                canvas.drawRoundRect(android.graphics.RectF(x-4*d,y-10*d,x+4*d,y+3*d),4*d,4*d,p)
                canvas.drawArc(android.graphics.RectF(x-7*d,y-8*d,x+7*d,y+7*d),0f,180f,false,p)
                canvas.drawLine(x,y+7*d,x,y+11*d,p);canvas.drawLine(x-5*d,y+11*d,x+5*d,y+11*d,p)
            }
        }.apply { contentDescription="Audio file" }
        row.addView(microphone,LinearLayout.LayoutParams(c.dp(84),c.dp(56)))
        val rear=actions("R");val front=actions("F")
        return Holder(row,check,title,details,rear.first,front.first,rear.second,front.second,microphone)
    }
    override fun getItemCount()=rows.size
    override fun onBindViewHolder(holder:Holder,position:Int) {
        val row=rows[position];val c=holder.row.context
        holder.check.visibility=if(selecting)View.VISIBLE else View.GONE
        holder.check.isChecked=row.clips.filter { it.status!=ClipStatus.WRITING }.let { it.isNotEmpty() && it.all { file -> file.id in selected } }
        holder.check.isEnabled=row.clips.any { it.status!=ClipStatus.WRITING }
        holder.check.contentDescription="Select recording ${timestamp(row.startMs)}"
        holder.check.setOnClickListener { toggle(row) }
        holder.row.setOnLongClickListener { toggle(row);true }
        holder.row.setOnClickListener { if(selecting)toggle(row) }
        holder.title.setOnLongClickListener { toggle(row);true }
        holder.details.setOnLongClickListener { toggle(row);true }
        holder.title.text=SimpleDateFormat("yyyy/MM/dd  HH:mm",Locale.US).format(Date(row.startMs))
        holder.details.text=String.format(Locale.US,"%.1f MB - %s",row.bytes/1048576.0,if(row.clips.all { it.isPhoto })"PHOTO" else c.clock(row.durationMs))
        fun bind(clip:Clip?,name:String,play:Button,keep:NativeSaveSwitch) {
            val ready=clip?.status==ClipStatus.READY
            play.isEnabled=ready
            play.isSelected=ready
            (play.parent as LinearLayout).background=rounded(0xff1e2429.toInt(),c.dp(10).toFloat()).apply { setStroke(c.dp(1),if(ready)Palette.mint else 0xff626b73.toInt()) }
            play.stateDescription=if(ready)"File available" else "Unavailable"
            play.contentDescription=if(ready)"${if(clip?.isPhoto==true)"Open" else "Play"} $name ${if(clip?.isPhoto==true)"photo" else if(clip?.isAudio==true)"audio" else "video"}" else "$name file unavailable${if(clip?.status==ClipStatus.WRITING)": recording" else ""}"
            play.setOnLongClickListener { toggle(row);true }
            play.setOnClickListener { if(selecting)toggle(row) else if(clip!=null && clip.status==ClipStatus.READY)open(clip) }
            keep.bindChecked(clip?.protected==true)
            keep.isEnabled=ready && pending.isEmpty()
            keep.contentDescription="Keep $name file, ${if(clip?.protected==true)"on" else "off"}"
            keep.stateDescription=if(clip?.protected==true)"Protected from automatic deletion" else "Not protected"
            keep.onUserChange={ checked ->
                if(clip!=null && checked!=clip.protected && pending.add(clip.id)) {
                    keep.isEnabled=false;notifyDataSetChanged();protect(clip)
                }
            }
        }
        val rearPair=holder.rear.parent as LinearLayout
        val switchView=holder.keepRear.parent as View
        val standalone=row.audio!=null || row.screen!=null
        val expectedIndex=if(standalone)0 else 1
        if(rearPair.indexOfChild(switchView)!=expectedIndex) { rearPair.removeView(switchView);rearPair.addView(switchView,expectedIndex) }
        holder.rear.text=if(row.screen?.camera==CameraSource.MIXED)"M" else if(row.screen!=null)"S" else if(row.audio!=null)"V" else "R"
        holder.microphone.tag=if(row.screen!=null)"screen" else "audio";holder.microphone.contentDescription=if(row.screen?.camera==CameraSource.MIXED)"Mixed recording" else if(row.screen!=null)"Screen recording" else "Audio file";holder.microphone.invalidate()
        holder.microphone.visibility=if(row.audio!=null || row.screen!=null)View.VISIBLE else View.GONE
        ((holder.rear.parent as View).layoutParams as LinearLayout.LayoutParams).marginStart=if(row.audio!=null || row.screen!=null)c.dp(8) else 0
        holder.microphone.setOnLongClickListener { toggle(row);true }
        holder.microphone.setOnClickListener { if(selecting)toggle(row) }
        (holder.front.parent as View).visibility=if(row.audio!=null || row.screen!=null)View.GONE else View.VISIBLE
        bind(row.screen ?: row.audio ?: row.rear ?: row.legacy,if(row.screen!=null)"screen" else if(row.audio!=null)"audio" else if(row.legacy!=null)"legacy combined" else "rear",holder.rear,holder.keepRear)
        bind(row.front,"front",holder.front,holder.keepFront)
        holder.row.contentDescription="${holder.title.text}, ${holder.details.text}"
    }
    fun protectionFinished(id:String) { pending.remove(id);notifyDataSetChanged() }
}

/** All transport rows share seven equal square cells, including narrow devices. */
internal class SquareControlRow(context:Context):LinearLayout(context) {
    override fun onMeasure(widthSpec:Int,heightSpec:Int) {
        val cell=(View.MeasureSpec.getSize(widthSpec)-paddingLeft-paddingRight)/childCount.coerceAtLeast(1)
        for(i in 0 until childCount)getChildAt(i).layoutParams.height=cell
        super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(cell+paddingTop+paddingBottom,View.MeasureSpec.EXACTLY))
    }
}
