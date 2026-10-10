package com.duorecord.app.ui

import android.os.Looper
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.duorecord.app.DuoApp
import com.duorecord.contracts.MediaRepository
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class SelectionInfoTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(v:View,t:String)=views(v).filterIsInstance<Button>().single { it.text==t }
    private fun waitFor(test:()->Boolean) { val end=System.nanoTime()+5_000_000_000;while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    private fun render(v:View,name:String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,360,800)
        val b=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b))
        val f=java.io.File("build/ui-preview/$name.png");f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun selectionStatisticsCountFilesAcrossPairedRowsAndExcludeWritingWithoutChangingSelection() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        fun clip(id:String,ext:String,camera:CameraSource,mb:Int)=Clip(id,"content://files/$id","$id.$ext",1000,1000,mb*1048576L,ClipKind.ROLLING,ClipStatus.READY,camera=camera,sessionId="session")
        val files=listOf(clip("pr","jpg",CameraSource.REAR,1),clip("pf","jpg",CameraSource.FRONT,2),clip("voice","aac",CameraSource.AUDIO,3),clip("vr","mp4",CameraSource.REAR,4),clip("vf","mp4",CameraSource.FRONT,5),clip("writing","mp4",CameraSource.REAR,7).copy(status=ClipStatus.WRITING,startMs=10000))
        val real=app.repository!!
        val repo=object:MediaRepository by real { override fun list()=files;override fun reconcile(activeRecording:Boolean) {} }
        DuoApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible=true }.set(app,lazy { repo })
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Recordings").performClick()
        val root=a.findViewById<View>(android.R.id.content)
        val adapter=views(root).filterIsInstance<RecyclerView>().single().adapter as ClipListAdapter
        waitFor { adapter.clips.size==6 }
        button(root,"All files statistics").performClick()
        val allDialog=ShadowDialog.getLatestDialog() as NeonDialog
        assertTrue(views(allDialog.window!!.decorView).filterIsInstance<TextView>().any { it.text=="6 files · 22.0 MB" })
        button(allDialog.window!!.decorView,"Close").performClick()
        assertTrue(adapter.selected.isEmpty());adapter.selectAll()
        assertEquals(5,adapter.selected.size)
        val selected=adapter.selected.toSet();button(root,"Selection statistics").performClick()
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        val texts=views(dialog.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.contains("5 files · 15.0 MB"));assertTrue(texts.contains("2 files · 3.0 MB"));assertTrue(texts.contains("1 file · 3.0 MB"));assertTrue(texts.contains("2 files · 9.0 MB"))
        assertTrue(texts.contains("Rear: 1 · 1.0 MB"));assertTrue(texts.contains("Front: 1 · 2.0 MB"))
        render(dialog.window!!.decorView,"selection-statistics")
        button(dialog.window!!.decorView,"Close").performClick();assertEquals(selected,adapter.selected)
        adapter.toggleAll();assertTrue(adapter.selected.isEmpty());assertFalse(button(root,"Selection statistics").isEnabled)
        adapter.selectAll();render(root,"recording-selection-info")
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun infoToolsSeparatesInformationalActionsAndCaptureModeKeepsAutoSaveWithoutFooter() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Info & tools").performClick()
        var root=a.findViewById<View>(android.R.id.content);assertNotNull(button(root,"Recording details"));assertNotNull(button(root,"Interruption history"))
        render(root,"info-tools")
        button(root,"About").performClick()
        var dialog=ShadowDialog.getLatestDialog() as NeonDialog
        val text=views(dialog.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text }
        assertTrue(text.contains("Programming language: Kotlin"));assertTrue(text.contains("Android SDK: 35"))
        for(title in listOf("Application","Development","Author"))assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text==title && it.tag=="panel-title-strip" })
        button(dialog.window!!.decorView,"Close").performClick();button(root,"Close").performClick()
        root=a.findViewById(android.R.id.content);button(root,"Settings").performClick()
        waitFor { ShadowDialog.getLatestDialog().isShowing };dialog=ShadowDialog.getLatestDialog() as NeonDialog
        val settings=views(dialog.window!!.decorView).filterIsInstance<Button>().map { it.text.toString() }
        assertFalse(settings.contains("Recording details"));assertFalse(settings.contains("Interruption history"));assertTrue(settings.contains("Recording shortcut"))
        button(dialog.window!!.decorView,"Close").performClick()
        button(root,"Photo mode").performClick()
        waitFor { views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text=="Photo · 10s" } }
        render(a.findViewById(android.R.id.content),"photo-home")
        button(a.findViewById(android.R.id.content),"Screen mode").performClick()
        waitFor { (a.application as DuoApp).repository!!.config().screenMode && views(a.findViewById(android.R.id.content)).filterIsInstance<SignButton>().any { it.icon==HeaderIcon.SCREEN_MODE && it.isSelected } }
        assertTrue((a.application as DuoApp).repository!!.config().screenMode)
        assertFalse((a.application as DuoApp).repository!!.config().photoMode)
        render(a.findViewById(android.R.id.content),"screen-home")
        c.pause().stop().destroy()
    }
}
