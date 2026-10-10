package com.duorecord.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorecord.app.DuoApp
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class RecordingListTest {
    private val start=java.time.Instant.parse("2026-10-25T10:02:00Z").toEpochMilli()
    private fun clip(id:String,camera:CameraSource,kind:ClipKind=ClipKind.ROLLING,status:ClipStatus=ClipStatus.READY)=Clip(id,"content://video/$id",id,start,45_000,2_254_438,kind,status,camera=camera,sessionId="session")
    @Test fun buttonsRouteToTheirOwnCameraAndKeepBindingHasNoSideEffects() {
        val context=RuntimeEnvironment.getApplication() as DuoApp
        val opened=mutableListOf<String>();val protected=mutableListOf<String>()
        val adapter=ClipListAdapter({ opened+=it.id },{ protected+=it.id })
        val rear=clip("rear",CameraSource.REAR);val front=clip("front",CameraSource.FRONT,ClipKind.SELECTED)
        adapter.clips=listOf(front,rear)
        val holder=adapter.onCreateViewHolder(LinearLayout(context),0);adapter.onBindViewHolder(holder,0)
        assertEquals(1,adapter.itemCount);assertEquals("R",holder.rear.text.toString());assertEquals("F",holder.front.text.toString())
        assertTrue(holder.rear.isSelected);assertTrue(holder.front.isSelected)
        holder.rear.performClick();holder.front.performClick();assertEquals(listOf("rear","front"),opened)
        assertFalse(holder.keepRear.isChecked);assertTrue(holder.keepFront.isChecked);assertTrue(protected.isEmpty())
        holder.keepRear.performClick();assertEquals(listOf("rear"),protected);assertFalse(holder.keepRear.isEnabled)
        adapter.protectionFinished("rear")
        adapter.clips=listOf(rear.copy(kind=ClipKind.SELECTED),front);adapter.onBindViewHolder(holder,0)
        assertTrue(holder.keepRear.isChecked);assertEquals(listOf("rear"),protected)
        holder.keepFront.performClick();assertEquals(listOf("rear","front"),protected)
    }
    @Test fun unavailableOrUnfinishedCameraIsDisabledAndGray() {
        val context=RuntimeEnvironment.getApplication() as DuoApp
        var opened=false;var protected=false
        val adapter=ClipListAdapter({ opened=true },{ protected=true })
        adapter.clips=listOf(clip("rear",CameraSource.REAR, status=ClipStatus.WRITING))
        val holder=adapter.onCreateViewHolder(LinearLayout(context),0);adapter.onBindViewHolder(holder,0)
        assertFalse(holder.rear.isEnabled);assertFalse(holder.front.isEnabled)
        assertFalse(holder.rear.isSelected);assertFalse(holder.front.isSelected)
        assertFalse(holder.keepRear.isEnabled);assertFalse(holder.keepFront.isEnabled)
        assertTrue(holder.front.contentDescription.toString().contains("unavailable"))
        holder.front.performClick();holder.rear.performClick()
        assertFalse(opened);assertFalse(protected)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun archiveHasCompactSeparateCardsEqualSpacingAndScrollsToBothEnds() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val method=MainActivity::class.java.getDeclaredMethod("showArchive");method.isAccessible=true;method.invoke(activity)
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        fun descendants(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList())
        val list=descendants(root).filterIsInstance<RecyclerView>().single()
        assertFalse(list.parent is GlowPanel)
        val close=descendants(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.CLOSE && it.text=="Close" }
        val refresh=descendants(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.REFRESH }
        val adapter=list.adapter as ClipListAdapter
        val fixtures=(0 until 60).flatMap { i ->
            val time=start-i*120_000L
            val rear=clip("rear-$i",CameraSource.REAR).copy(startMs=time,sessionId="session-$i")
            if(i%3==0)listOf(rear) else listOf(rear,clip("front-$i",CameraSource.FRONT,if(i%2==0)ClipKind.SELECTED else ClipKind.ROLLING).copy(startMs=time,sessionId="session-$i"))
        }
        adapter.clips=fixtures
        fun layout(){root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)}
        layout()
        val first=list.findViewHolderForAdapterPosition(0) as ClipListAdapter.Holder
        assertTrue(first.title.text.toString().matches(Regex("\\d{4}/\\d{2}/\\d{2}  \\d{2}:\\d{2}")))
        assertTrue(first.details.text.toString().matches(Regex("[0-9]+\\.[0-9] MB - \\d{2}:\\d{2}:\\d{2}")))
        assertFalse(first.front.isEnabled);assertTrue(first.rear.isEnabled)
        assertTrue(first.row is GlowPanel)
        assertEquals(40,close.width);assertEquals(close.width,close.height)
        val guide=descendants(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.GUIDE }
        assertEquals(refresh.right+4,guide.left);assertEquals(guide.right+4,close.left);assertEquals(close.top,refresh.top)
        assertEquals(80,first.row.height)
        assertEquals(12,first.row.paddingLeft);assertEquals(first.row.paddingLeft,first.row.paddingTop)
        assertEquals(first.row.paddingLeft,first.row.paddingRight);assertEquals(first.row.paddingLeft,first.row.paddingBottom)
        assertEquals(-90f,first.keepRear.rotation,0f);assertEquals(-90f,first.keepFront.rotation,0f)
        val rearSwitch=first.keepRear.parent as VerticalSaveControl;val frontSwitch=first.keepFront.parent as VerticalSaveControl
        assertSame(first.rear.parent,rearSwitch.parent);assertSame(first.front.parent,frontSwitch.parent)
        assertTrue(rearSwitch.left>=first.rear.right);assertTrue(frontSwitch.right<=first.front.left)
        assertNotNull((first.rear.parent as View).background);assertNotNull((first.front.parent as View).background)
        val text=first.title.parent as View;val rearPair=first.rear.parent as View
        assertEquals(rearPair.top+first.rear.top+4,text.top)
        assertEquals(rearPair.top+first.rear.bottom-4,text.bottom)
        val second=list.findViewHolderForAdapterPosition(1) as ClipListAdapter.Holder
        // Visible card edges are 4 dp inside the View's shadow reserve.
        val sideGap=(root.getChildAt(0) as View).paddingLeft+4
        assertEquals(sideGap,second.row.top-first.row.bottom+8)
        val listRect=android.graphics.Rect();list.getGlobalVisibleRect(listRect)
        val headerRect=android.graphics.Rect();(close.parent as View).getGlobalVisibleRect(headerRect)
        assertEquals(sideGap,listRect.top-headerRect.bottom+4)
        first.row.performLongClick();assertTrue(adapter.selecting);assertEquals(setOf("rear-0"),adapter.selected)
        adapter.selectAll();assertEquals(fixtures.size,adapter.selectedClips().size)
        adapter.clearSelection();assertFalse(adapter.selecting);assertTrue(adapter.selected.isEmpty())
        val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val output=java.io.File("build/ui-preview/recording-list.png");output.parentFile!!.mkdirs();output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        // Exercise the actual fixed-size handle with touch events.
        list.scrollBy(0,200)
        val handle=descendants(root).filterIsInstance<ArchiveScrollHandle>().single()
        assertEquals(32f,handle.thumbBounds().height(),.001f)
        assertEquals(10f,handle.thumbBounds().width(),.001f)
        val handleRect=android.graphics.Rect();handle.getGlobalVisibleRect(handleRect)
        assertTrue("Scrollbar overlaps recording cards",listRect.right<=handleRect.left)
        val center=handle.thumbBounds().centerY()
        fun drag(action:Int,y:Float) {
            val event=android.view.MotionEvent.obtain(0,100,action,handle.width-4f,y,0)
            handle.dispatchTouchEvent(event);event.recycle()
        }
        drag(android.view.MotionEvent.ACTION_DOWN,center)
        drag(android.view.MotionEvent.ACTION_MOVE,handle.height-12f)
        drag(android.view.MotionEvent.ACTION_UP,handle.height-12f)
        layout();assertTrue((list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()>40)
        list.scrollToPosition(adapter.itemCount-1);layout();assertEquals(adapter.itemCount-1,(list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition())
        list.scrollToPosition(0);layout();assertEquals(0,(list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
        controller.pause().stop().destroy()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun selectionToolbarStaysAboveClippedRowsAndSelectAllTogglesBothWays() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        MainActivity::class.java.getDeclaredMethod("showArchive").apply { isAccessible=true }.invoke(activity)
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
        val list=views(root).filterIsInstance<RecyclerView>().single();val adapter=list.adapter as ClipListAdapter
        adapter.clips=(0 until 18).map { clip("r$it",CameraSource.REAR).copy(sessionId="s$it",startMs=start-it*1000) }
        fun layout() { root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800) }
        layout();(list.findViewHolderForAdapterPosition(0) as ClipListAdapter.Holder).row.performLongClick();layout()
        val check=views(root).filterIsInstance<SelectAllCheckBox>().single().apply { setOnClickListener { adapter.toggleAll() } }
        assertEquals("Partly selected",check.stateDescription)
        check.performClick();layout();assertEquals(18,adapter.selected.size);assertTrue(check.isChecked)
        val count=views(root).filterIsInstance<TextView>().single { it.text=="18 files selected" }
        val toolbar=check.parent as View
        val countRect=android.graphics.Rect();count.getGlobalVisibleRect(countRect)
        val toolsRect=android.graphics.Rect();toolbar.getGlobalVisibleRect(toolsRect)
        val listRect=android.graphics.Rect();list.getGlobalVisibleRect(listRect)
        assertTrue(countRect.bottom<=toolsRect.top);assertTrue(toolsRect.bottom+12<=listRect.top)
        assertTrue(list.parent is ClippedListFrame)
        val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val out=java.io.File("build/ui-preview/recording-18-selected.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        check.performClick();layout();assertTrue(adapter.selected.isEmpty());assertFalse(check.isChecked);assertEquals("None selected",check.stateDescription)
        check.performClick();layout();assertEquals(18,adapter.selected.size)
        val first=list.findViewHolderForAdapterPosition(0) as ClipListAdapter.Holder
        assertTrue(first.title.paint.measureText(first.title.text.toString())<=first.title.width)
        assertTrue(first.details.paint.measureText(first.details.text.toString())<=first.details.width)
        fun headerPixels():IntArray {
            val bmp=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bmp));val pixels=IntArray(360*listRect.top)
            bmp.getPixels(pixels,0,360,0,0,360,listRect.top);bmp.recycle();return pixels
        }
        val before=headerPixels()
        list.scrollBy(0,170);layout()
        assertArrayEquals("Scrolling rows drew over the selection toolbar",before,headerPixels())
        assertEquals(32f,views(root).filterIsInstance<ArchiveScrollHandle>().single().thumbBounds().height(),.001f)
        controller.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun verticalNativeSwitchReceivesTransformedTouchAndDoesNotOpenVideo() {
        val context=RuntimeEnvironment.getApplication() as DuoApp
        var opened=false;var saves=0
        val adapter=ClipListAdapter({ opened=true },{ saves++ })
        adapter.clips=listOf(clip("rear",CameraSource.REAR),clip("front",CameraSource.FRONT))
        val holder=adapter.onCreateViewHolder(LinearLayout(context),0);adapter.onBindViewHolder(holder,0)
        val controller=Robolectric.buildActivity(android.app.Activity::class.java).setup()
        controller.get().setContentView(holder.row)
        holder.row.measure(View.MeasureSpec.makeMeasureSpec(336,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));holder.row.layout(0,0,336,80)
        val bitmap=android.graphics.Bitmap.createBitmap(336,80,android.graphics.Bitmap.Config.ARGB_8888)
        holder.row.draw(android.graphics.Canvas(bitmap));bitmap.recycle()
        val toggle=holder.keepRear;val wrapper=toggle.parent as View;val pair=wrapper.parent as View
        val x=pair.left+wrapper.left+wrapper.width/2f;val y=pair.top+wrapper.top+wrapper.height/2f
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(0,20,action,x,y,0)
            holder.row.dispatchTouchEvent(event);event.recycle()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1,saves);assertTrue(toggle.isChecked);assertFalse(opened)
        controller.pause().stop().destroy()
    }

    @Test fun unchangedRefreshDoesNotRebindAndDeletionPreservesSurvivingSelection() {
        val adapter=ClipListAdapter({},{})
        val rear=clip("rear",CameraSource.REAR);val front=clip("front",CameraSource.FRONT)
        adapter.clips=listOf(rear,front);adapter.selectAll()
        var fullRebinds=0;var changes=0
        adapter.registerAdapterDataObserver(object:RecyclerView.AdapterDataObserver() {
            override fun onChanged() { fullRebinds++ }
            override fun onItemRangeChanged(start:Int,count:Int,payload:Any?) { changes++ }
            override fun onItemRangeRemoved(start:Int,count:Int) { changes++ }
            override fun onItemRangeInserted(start:Int,count:Int) { changes++ }
        })
        adapter.clips=listOf(rear,front)
        assertEquals(0,fullRebinds);assertEquals(0,changes)
        adapter.clips=listOf(front.copy(bytes=1000))
        assertEquals(0,fullRebinds);assertTrue(changes>0);assertEquals(setOf("front"),adapter.selected)
        assertTrue(adapter.selecting);assertEquals(1000L,adapter.rows.single().bytes)
    }
}
