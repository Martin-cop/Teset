package com.duorecord.app.ui

import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.DuoApp
import com.duorecord.contracts.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class AudioModeUiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(root:View,text:String)=views(root).filterIsInstance<Button>().single { it.text==text }
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(condition())
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun audioModeSaveHidesCameraControlsAndItsScheduleRetainsTheCaptureType() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val app=activity.application as DuoApp
        button(activity.findViewById(android.R.id.content),"Audio mode").performClick()
        waitUntil { app.repository!!.config().audioOnly && views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text=="Record" } }
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)
        val choices=views(root).filterIsInstance<SignButton>().filter { it.persistentSelectionColors }
        assertEquals(5,choices.size);assertTrue(button(root,"Audio mode").isSelected)
        assertEquals(1,choices.count { it.isSelected })
        assertTrue(views(root).none { it is CameraPanel });assertFalse(views(root).filterIsInstance<Button>().any { it.text=="Recording light" })
        val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val out=java.io.File("build/ui-preview/audio-home.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val factory=object:CaptureFactory { override fun create(context:android.content.Context)=object:CaptureEngine {
            override fun support(config:RecordingConfig)=CaptureSupport(config.audioOnly,"Audio")
            override fun qualities(source:CameraSource,config:RecordingConfig):List<CaptureQuality> { fail("Audio schedules must not query cameras");return emptyList() }
            override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {}
            override fun stop(reason:String) {};override fun split() {};override fun update(config:RecordingConfig,sources:Set<CameraSource>) {};override fun setPreview(source:CameraSource,surface:Surface?) {}
        } }
        DuoApp::class.java.getDeclaredField("capture\$delegate").apply { isAccessible=true }.set(app,lazy { factory })
        button(root,"Capture timing").performLongClick()
        val page=activity.findViewById<ViewGroup>(android.R.id.content)
        assertFalse(button(page,"Rear").isShown);assertFalse(button(page,"Front").isShown)
        button(page,"Save schedule").performClick()
        val prefs=activity.getSharedPreferences("automation",0);waitUntil { prefs.getBoolean("armed",false) }
        assertTrue(prefs.getBoolean("audioOnly",false));assertFalse(prefs.getBoolean("photos",true))
        assertFalse(prefs.getBoolean("light",true));assertFalse(prefs.getBoolean("screenLight",true))
        controller.pause().stop().destroy()
    }
    @Test fun recyclingAudioRowsRestoresCameraPairsAndAudioPlaybackOpensTheAudioFile() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val audio=Clip("a","content://audio/a","a.m4a",1000,1000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.AUDIO)
        var opened:Clip?=null
        val adapter=ClipListAdapter({ opened=it },{})
        val holder=adapter.onCreateViewHolder(LinearLayout(context),0)
        adapter.clips=listOf(audio);adapter.onBindViewHolder(holder,0)
        assertEquals("V",holder.rear.text);assertEquals(View.VISIBLE,holder.microphone.visibility);assertTrue(holder.rear.isSelected);assertEquals(View.GONE,(holder.front.parent as View).visibility)
        holder.rear.performClick();assertEquals(audio,opened)
        val rear=audio.copy(id="r",name="r.mp4",camera=CameraSource.REAR)
        adapter.clips=listOf(rear);adapter.onBindViewHolder(holder,0)
        assertEquals("R",holder.rear.text);assertEquals(View.VISIBLE,(holder.front.parent as View).visibility)
    }
}
