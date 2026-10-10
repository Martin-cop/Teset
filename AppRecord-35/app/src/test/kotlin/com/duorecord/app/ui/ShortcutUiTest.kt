package com.duorecord.app.ui

import android.Manifest
import android.os.*
import android.view.*
import android.widget.*
import com.duorecord.app.*
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
import org.robolectric.shadows.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class ShortcutUiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(root:View,text:String)=views(root).filterIsInstance<Button>().single { it.text==text }
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(condition())
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun combinationEditorNamesKeysSavesHoldAndLeavesInvalidSettingsOpen() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        MainActivity::class.java.getDeclaredMethod("shortcutDialog").apply { isAccessible=true }.invoke(a)
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog;val root=dialog.window!!.decorView
        button(root,"Volume up").performClick();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,views(root).filterIsInstance<Spinner>().single().selectedItemPosition)
        assertTrue(RecordingShortcut.keys.all { (_,name) -> views(root).filterIsInstance<TextView>().any { it.text==name } })
        assertFalse(RecordingShortcut.keys.any { it.first==KeyEvent.KEYCODE_POWER || it.first==KeyEvent.KEYCODE_HOME })
        val hold=views(root).filterIsInstance<EditText>().single { it.contentDescription=="Shortcut hold milliseconds" }
        hold.setText("10");button(root,"Save").performClick();assertTrue(dialog.isShowing)
        hold.setText("1200");button(root,"Save").performClick();assertFalse(dialog.isShowing)
        val saved=RecordingShortcut.read(a)
        assertEquals(setOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN),saved.keys)
        assertEquals(ShortcutGesture.HOLD,saved.gesture);assertEquals(1200L,saved.holdMs)
        MainActivity::class.java.getDeclaredMethod("shortcutDialog").apply { isAccessible=true }.invoke(a)
        val reopened=ShadowDialog.getLatestDialog() as NeonDialog
        val guide=button(reopened.window!!.decorView,"Guide");val close=button(reopened.window!!.decorView,"Close")
        assertEquals((guide.parent as ViewGroup).indexOfChild(guide)+1,(close.parent as ViewGroup).indexOfChild(close))
        guide.performClick();val help=ShadowDialog.getLatestDialog()
        assertTrue(views(help.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("Hold together") && it.text.contains("last saved") })
        help.dismiss()
        val panel=reopened.findViewById<ViewGroup>(android.R.id.content)
        panel.measure(View.MeasureSpec.makeMeasureSpec(348,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));panel.layout(0,0,348,panel.measuredHeight)
        val bitmap=android.graphics.Bitmap.createBitmap(348,panel.height,android.graphics.Bitmap.Config.ARGB_8888);panel.draw(android.graphics.Canvas(bitmap))
        val out=java.io.File("build/ui-preview/shortcut-editor.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        reopened.dismiss();controller.pause().stop().destroy()
    }
    @Test fun legacySingleKeyPreferenceMigratesAndDisabledRemainsDisabled() {
        val app=org.robolectric.RuntimeEnvironment.getApplication()
        val p=app.getSharedPreferences("shortcut",0)
        p.edit().putInt("key",1).putInt("count",4).commit()
        assertEquals(setOf(KeyEvent.KEYCODE_VOLUME_UP),RecordingShortcut.read(app).keys)
        assertEquals(4,RecordingShortcut.read(app).count)
        p.edit().putInt("key",2).commit();assertFalse(RecordingShortcut.read(app).enabled)
    }
    @Test fun volumeCombinationStartsWithLastSavedCaptureSettingsAfterTheHold() {
        ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED)
        ShadowStatFs.registerStats(android.os.Environment.getExternalStorageDirectory(),4_000_000,3_000_000,3_000_000)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val app=a.application as DuoApp
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
        val config=app.repository!!.config().copy(cameraMode=CameraMode.FRONT,fps=24,frontQuality="720x1280",continueVideoWithoutMic=false)
        app.repository!!.saveConfig(config);app.lightForNextRecording=true
        val checked=mutableListOf<RecordingConfig>()
        val factory=object:CaptureFactory { override fun create(context:android.content.Context)=object:CaptureEngine {
            override fun support(config:RecordingConfig)=CaptureSupport(true,"Supported").also { checked+=config }
            override fun qualities(source:CameraSource,config:RecordingConfig)=listOf(CaptureQuality(720,1280,2_000_000))
            override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {}
            override fun stop(reason:String) {};override fun split() {};override fun update(config:RecordingConfig,sources:Set<CameraSource>) {};override fun setPreview(source:CameraSource,surface:Surface?) {}
        } }
        DuoApp::class.java.getDeclaredField("capture\$delegate").apply { isAccessible=true }.set(app,lazy { factory })
        RecordingShortcut.save(a,ShortcutConfig(keys=setOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN),gesture=ShortcutGesture.HOLD,holdMs=800))
        a.onWindowFocusChanged(true)
        fun down(key:Int)=a.onKeyDown(key,KeyEvent(KeyEvent.ACTION_DOWN,key))
        fun up(key:Int)=a.onKeyUp(key,KeyEvent(KeyEvent.ACTION_UP,key))
        down(KeyEvent.KEYCODE_VOLUME_UP);down(KeyEvent.KEYCODE_VOLUME_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));up(KeyEvent.KEYCODE_VOLUME_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));assertTrue(checked.isEmpty())
        down(KeyEvent.KEYCODE_VOLUME_DOWN);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        waitUntil { app.status.value.phase==Phase.STARTING && checked.isNotEmpty() }
        assertEquals(CameraMode.FRONT,checked.single().cameraMode);assertEquals(24,checked.single().fps)
        assertEquals("720x1280",checked.single().frontQuality);assertFalse(checked.single().continueVideoWithoutMic)
        assertTrue(app.lightForNextRecording)
        assertEquals(RecordingService.START,shadowOf(a).nextStartedService.action)
        up(KeyEvent.KEYCODE_VOLUME_UP);up(KeyEvent.KEYCODE_VOLUME_DOWN)
        controller.pause().stop().destroy()
    }
    @Test fun focusLossAndBackgroundingCancelAQueuedHold() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val app=a.application as DuoApp
        RecordingShortcut.save(a,ShortcutConfig(keys=setOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN),gesture=ShortcutGesture.HOLD))
        a.onWindowFocusChanged(true)
        fun down() { listOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN).forEach { a.onKeyDown(it,KeyEvent(KeyEvent.ACTION_DOWN,it)) } }
        down();a.onWindowFocusChanged(false);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(Phase.IDLE,app.status.value.phase)
        a.onWindowFocusChanged(true);down();controller.pause().stop();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(Phase.IDLE,app.status.value.phase);controller.destroy()
    }
}
