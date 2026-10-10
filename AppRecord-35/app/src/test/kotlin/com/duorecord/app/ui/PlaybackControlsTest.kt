package com.duorecord.app.ui

import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import com.duorecord.app.DuoApp
import com.duorecord.contracts.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class PlaybackControlsTest {
    private fun descendants(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList())
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(condition())
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun draggingRulerChangesSpeedAndNextArrowStaysOnFrontCamera() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        fun clip(id:String,source:CameraSource,time:Long)=Clip(id,"content://video/$id",id,time,1000,100,ClipKind.ROLLING,ClipStatus.READY,camera=source)
        val first=clip("front-1",CameraSource.FRONT,1000);val second=clip("front-2",CameraSource.FRONT,3000)
        val files=listOf(first,clip("voice",CameraSource.AUDIO,2000).copy(name="voice.aac"),second)
        val repo=Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,method,_ ->
            when(method.name) { "config" -> RecordingConfig();"list" -> files;"pin" -> AutoCloseable {};"folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10_000_000_000L;else -> Unit }
        } as MediaRepository
        val opened=mutableListOf<Clip>();var currentSpeed=1f;var position=0L;val seeks=mutableListOf<Long>()
        val factory=object:PlayerFactory { override fun create(context:android.content.Context,onError:(String)->Unit)=object:PlayerComponent {
            override val view=FrameLayout(context)
            override fun open(clip:Clip,companion:Clip?) { opened+=clip }
            override fun positionMs()=position;override fun durationMs()=1000L
            override fun seekTo(positionMs:Long) { position=positionMs;seeks+=positionMs };override fun play()=Unit;override fun pause()=Unit;override fun release()=Unit
            override fun setSpeed(speed:Float) { currentSpeed=speed };override fun speed()=currentSpeed
        } }
        fun inject(name:String,value:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { value }) }
        inject("repository",repo);inject("player",factory)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val open=MainActivity::class.java.getDeclaredMethod("openPlayer",Clip::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        open.invoke(activity,first,false);waitUntil { opened.size==1 }
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)
        val ruler=descendants(root).filterIsInstance<QualityRuler>().single()
        val zero=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,10f,20f,0)
        ruler.dispatchTouchEvent(zero);zero.recycle();assertEquals(0f,currentSpeed,0f)
        val event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,ruler.width-10f,20f,0)
        ruler.dispatchTouchEvent(event);event.recycle();assertEquals(30f,currentSpeed,0f)
        assertFalse(descendants(root).filterIsInstance<android.widget.Button>().any { it.text.startsWith("Speed:") })
        val nav=descendants(root).single { it.contentDescription=="Media navigation" } as ViewGroup
        assertEquals(6,nav.childCount);assertEquals("Stop playback",nav.getChildAt(3).contentDescription)
        assertEquals(HeaderIcon.NEXT,(nav.getChildAt(4) as SignButton).icon)
        assertTrue((nav.getChildAt(4) as SignButton).semanticTransport)
        waitUntil { descendants(root).any { it.contentDescription=="Next file, all types" && it.isEnabled } }
        val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val output=java.io.File("build/ui-preview/playback-five-controls.png");output.parentFile!!.mkdirs();output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        waitUntil { descendants(root).any { it.contentDescription=="Next file, all types" && it.isEnabled } }
        descendants(root).single { it.contentDescription=="Next file, all types" }.performClick()
        waitUntil { opened.size==2 };assertTrue(opened.last().isAudio);assertEquals(8f,currentSpeed,0f)
        var current=activity.findViewById<ViewGroup>(android.R.id.content)
        position=1000L
        MainActivity::class.java.getDeclaredField("pendingSeekMs").apply { isAccessible=true }.set(activity,600L)
        MainActivity::class.java.getDeclaredField("pendingSeekUntil").apply { isAccessible=true }.setLong(activity,Long.MAX_VALUE)
        descendants(current).single { it.contentDescription=="Play playback" }.performClick()
        assertEquals(600L,seeks.last());assertEquals(600L,position)
        waitUntil { descendants(current).any { it.contentDescription=="Next file, all types" && it.isEnabled } }
        descendants(current).single { it.contentDescription=="Next file, all types" }.performClick()
        waitUntil { opened.size==3 };assertEquals(second,opened.last());assertEquals(30f,currentSpeed,0f)
        current=activity.findViewById(android.R.id.content)
        waitUntil { descendants(current).any { it.contentDescription=="Previous same type and camera" && it.isEnabled } }
        descendants(current).single { it.contentDescription=="Previous same type and camera" }.performClick()
        waitUntil { opened.size==4 };assertEquals(first,opened.last());assertEquals(30f,currentSpeed,0f)
        controller.pause().stop().destroy()
    }
}
