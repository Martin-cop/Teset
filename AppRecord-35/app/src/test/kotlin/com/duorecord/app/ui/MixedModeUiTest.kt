package com.duorecord.app.ui

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class MixedModeUiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(test:()->Boolean) { val end=System.nanoTime()+5000000000;while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun fifthModeHasIndependentAudioAndLayoutControlsAndUnavailableModesTurnRed() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(RecordingConfig(mixedMode=true,mixedRear=true,mixedBackground=CameraSource.REAR))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val root=a.findViewById<ViewGroup>(android.R.id.content)
        val choices=views(root).filterIsInstance<SignButton>().filter { it.persistentSelectionColors }
        assertEquals(5,choices.size);assertEquals(HeaderIcon.MIXED_MODE,choices.single { it.isSelected }.icon)
        assertEquals(1,views(root).filterIsInstance<MixedLayoutEditor>().size)
        assertEquals(1,views(root).filterIsInstance<SignButton>().count { it.icon==HeaderIcon.INTERNAL_AUDIO })
        assertEquals(1,views(root).filterIsInstance<SignButton>().count { it.icon==HeaderIcon.MICROPHONE })
        app.status.value=RuntimeStatus(phase=Phase.RECORDING,startedMs=System.currentTimeMillis(),cameras=mapOf(CameraSource.MIXED to com.duorecord.contracts.CameraCaptureStatus(recording=true)))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(choices.all { !it.isEnabled && !it.unavailableRed });assertTrue(choices.single { it.isSelected }.persistentSelectionColors)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));root.layout(0,0,360,1000)
        val texture=views(root).filterIsInstance<TextureView>().single()
        val outputBounds=a.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val outputWidth=outputBounds.width().takeIf { it>0 } ?: a.resources.displayMetrics.widthPixels
        val outputHeight=outputBounds.height().takeIf { it>0 } ?: a.resources.displayMetrics.heightPixels
        assertEquals(outputWidth.toFloat()/outputHeight,texture.width.toFloat()/texture.height,.01f)
        val bitmap=Bitmap.createBitmap(360,1000,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        val out=java.io.File("build/ui-preview/mixed-home.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        c.pause().stop().destroy()
    }
    @Test fun dragResizeAndCloseChangeOnlyTheInsetLayout() {
        val context=RuntimeEnvironment.getApplication();val config=RecordingConfig(mixedMode=true,mixedRear=true,mixedBackground=CameraSource.REAR)
        var changed=config;val editor=MixedLayoutEditor(context,config) { changed=it };editor.layout(0,0,400,400)
        fun event(action:Int,x:Float,y:Float) { val e=MotionEvent.obtain(0,1,action,x,y,0);editor.onTouchEvent(e);e.recycle() }
        // Front starts at x=0..152, y=0..120; its header is draggable.
        event(MotionEvent.ACTION_DOWN,30f,30f);event(MotionEvent.ACTION_MOVE,70f,70f);event(MotionEvent.ACTION_UP,70f,70f)
        var frame=MixedLayout.read(changed.mixedLayout).getValue(CameraSource.FRONT)
        assertEquals(.10f,frame.x,.001f);assertEquals(.10f,frame.y,.001f)
        assertEquals(config.mixedSources,changed.mixedSources);assertEquals(CameraSource.REAR,changed.mixedBackground)
        val right=(frame.x+frame.width)*400;val bottom=(frame.y+frame.height)*400
        event(MotionEvent.ACTION_DOWN,right-5,bottom-5);event(MotionEvent.ACTION_MOVE,right+35,bottom+35);event(MotionEvent.ACTION_UP,right+35,bottom+35)
        frame=MixedLayout.read(changed.mixedLayout).getValue(CameraSource.FRONT);assertEquals(.48f,frame.width,.001f)
        event(MotionEvent.ACTION_DOWN,(frame.x+frame.width)*400-8,frame.y*400+8)
        assertFalse(MixedLayout.read(changed.mixedLayout).getValue(CameraSource.FRONT).visible);assertEquals(config.mixedSources,changed.mixedSources)
    }
    @Test fun blankDurationWithFutureStartArmsAnUnlimitedScheduleWithoutStartingAService() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(RecordingConfig(audioOnly=true))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val root=a.findViewById<ViewGroup>(android.R.id.content)
        views(root).filterIsInstance<Button>().single { it.text=="Capture timing" }.performClick()
        views(a.findViewById(android.R.id.content)).filterIsInstance<EditText>().single { it.contentDescription.toString().startsWith("Capture duration") }.setText("")
        views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text=="Close" }.performClick()
        waitFor { app.repository!!.config().maxRecordingMinutes==0 && views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text=="Record" } }
        val start=System.currentTimeMillis()+600000;a.getSharedPreferences("home_start",0).edit().putLong("at",start).commit()
        views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text=="Record" }.performClick()
        waitFor { a.getSharedPreferences("automation",0).getBoolean("armed",false) }
        val p=a.getSharedPreferences("automation",0);assertEquals(start,p.getLong("start",0));assertEquals(Long.MAX_VALUE,p.getLong("end",0))
        assertTrue(p.getBoolean("audioOnly",false));assertEquals(0,app.repository!!.config().maxRecordingMinutes);assertEquals(Phase.IDLE,app.status.value.phase)
        views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().single { it.text=="Capture timing" }.performLongClick()
        val dates=views(a.findViewById(android.R.id.content)).filterIsInstance<EditText>().map { it.text.toString() }.filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) }
        assertEquals(2,dates.size);assertTrue(dates[1]>dates[0]);assertEquals(Long.MAX_VALUE,p.getLong("end",0))
        c.pause().stop().destroy()
    }
}
