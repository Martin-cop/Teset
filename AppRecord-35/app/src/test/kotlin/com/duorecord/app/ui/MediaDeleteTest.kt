package com.duorecord.app.ui
import android.app.AlertDialog
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.DuoApp
import com.duorecord.contracts.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.lang.reflect.Proxy
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MediaDeleteTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(test:()->Boolean) { val end=System.nanoTime()+5_000_000_000;while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    @Test fun photoVideoAndAudioHaveDeleteWithoutKeepAndReleaseTheirPinsBeforeDeletion() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val photo=java.io.File(app.cacheDir,"delete-test.jpg")
        android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888).apply { photo.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it) };recycle() }
        val files=java.util.concurrent.CopyOnWriteArrayList(listOf(
            Clip("v","content://video/v","v.mp4",1000,1000,10,ClipKind.SELECTED,ClipStatus.READY,camera=CameraSource.FRONT),
            Clip("a","content://audio/a","a.aac",2000,1000,10,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.AUDIO),
            Clip("p",android.net.Uri.fromFile(photo).toString(),"p.jpg",3000,1,photo.length(),ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.FRONT)))
        val pins=java.util.concurrent.atomic.AtomicInteger()
        val repo=Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,method,args -> when(method.name) {
            "config" -> RecordingConfig();"list" -> files.toList();"get" -> files.firstOrNull { it.id==args!![0] }
            "pin" -> { pins.incrementAndGet();AutoCloseable { pins.decrementAndGet() } }
            "delete" -> { assertEquals(0,pins.get());val ids=args!![0] as Set<*>;files.removeAll { it.id in ids };Unit }
            "folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10_000_000_000L;else -> Unit
        } } as MediaRepository
        val factory=object:PlayerFactory { override fun create(context:android.content.Context,onError:(String)->Unit)=object:PlayerComponent {
            override val view=FrameLayout(context);override fun open(clip:Clip,companion:Clip?) {}
            override fun positionMs()=0L;override fun durationMs()=1000L;override fun seekTo(positionMs:Long) {}
            override fun setSpeed(speed:Float) {};override fun speed()=1f;override fun play() {};override fun pause() {};override fun release() {}
        } }
        fun inject(name:String,value:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { value }) }
        inject("repository",repo);inject("player",factory)
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val open=MainActivity::class.java.getDeclaredMethod("openPlayer",Clip::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        for(clip in files.toList()) {
            open.invoke(a,clip,false)
            waitFor { pins.get()==1 && views(a.findViewById(android.R.id.content)).any { it.contentDescription=="Delete this file" } }
            val root=a.findViewById<View>(android.R.id.content)
            assertFalse(views(root).filterIsInstance<Button>().any { it.text.startsWith("Keep") || it.text.startsWith("Unselect") })
            views(root).single { it.contentDescription=="Delete this file" }.performClick()
            var dialog=ShadowDialog.getLatestDialog() as NeonDialog
            views(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text=="Close" }.performClick();assertTrue(files.any { it.id==clip.id })
            views(root).single { it.contentDescription=="Delete this file" }.performClick();dialog=ShadowDialog.getLatestDialog() as NeonDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();waitFor { files.none { it.id==clip.id } };assertEquals(0,pins.get())
        }
        c.pause().stop().destroy();photo.delete()
    }
}
