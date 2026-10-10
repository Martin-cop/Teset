package com.duorecord.app.ui

import android.os.Looper
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
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class RequestedControlsTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(root:View,text:String)=views(root).filterIsInstance<Button>().single { it.text==text }
    private fun waitUntil(test:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue("Timed out",test())
    }
    private fun layout(v:View) { v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,360,800) }
    @Test @Config(sdk=[30]) fun scheduleOnAndroid11DoesNotCallTheNewerExactAlarmPermissionApi() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        assertTrue(views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.text=="Exact timing · allowed" })
        c.pause().stop().destroy()
    }
    @Test fun dateTypingAndPastingRejectLettersInvalidCalendarAndOutOfRangeTime() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val dates=views(a.findViewById(android.R.id.content)).filterIsInstance<EditText>().filter { it.contentDescription?.toString()?.startsWith("Local date and time")==true }
        val input=dates.first();val original=input.text.toString()
        listOf("2027-10-25 24:00","2027-10-25 10:60","2027-02-29 10:00","2027-13-25 10:00","2027-10-25 ab:00").forEach { bad -> input.text.replace(0,input.text.length,bad);assertEquals(bad,original,input.text.toString()) }
        input.setText("۲۰۲۸-۰۲-۲۹ ۲۳:۵۵");assertEquals("2028-02-29 23:55",input.text.toString())
        input.text.replace(11,13,"xx");assertEquals("2028-02-29 23:55",input.text.toString())
        c.pause().stop().destroy()
    }
    @Test fun minuteRulerUpdatesEndAcrossMidnightAndEndEditingUpdatesDuration() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val root=a.findViewById<ViewGroup>(android.R.id.content);layout(root)
        val dates=views(root).filterIsInstance<EditText>().filter { it.contentDescription?.toString()?.startsWith("Local date and time")==true };val ruler=views(root).filterIsInstance<QualityRuler>().single { it.steps==600 }
        dates.first().setText("2027-10-25 23:55")
        val x=10f+(ruler.width-20f)*9/599
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach { action -> val e=MotionEvent.obtain(0,10,action,x,18f,0);ruler.dispatchTouchEvent(e);e.recycle() }
        assertEquals(9,ruler.selected);assertEquals("2027-10-26 00:05",dates.last().text.toString())
        dates.last().setText("2027-10-26 00:15");assertEquals(19,ruler.selected)
        dates.first().setText("2027-10-26 12:00");assertEquals("2027-10-26 12:20",dates.last().text.toString())
        c.pause().stop().destroy()
    }
    @Test fun schedulePersistsIndependentCameraQualityAndLightChoicesWithoutIlluminating() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val app=a.application as DuoApp
        val accepted=mutableListOf<RecordingConfig>()
        val factory=object:CaptureFactory { override fun create(context:android.content.Context)=object:CaptureEngine {
            override fun support(config:RecordingConfig)=CaptureSupport(true,"Supported").also { accepted+=config }
            override fun qualities(source:CameraSource,config:RecordingConfig)=listOf(CaptureQuality(480,640,1_000_000),CaptureQuality(720,1280,2_000_000))
            override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {}
            override fun stop(reason:String) {};override fun split() {};override fun update(config:RecordingConfig,sources:Set<CameraSource>) {};override fun setPreview(source:CameraSource,surface:android.view.Surface?) {}
        } }
        DuoApp::class.java.getDeclaredField("capture\$delegate").apply { isAccessible=true }.set(app,lazy { factory })
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val root=a.findViewById<ViewGroup>(android.R.id.content)
        waitUntil { views(root).filterIsInstance<QualityRuler>().count { it.steps==2 }==2 }
        val front=button(root,"Front");val rear=button(root,"Rear")
        assertTrue(front.isSelected);assertTrue(rear.isSelected);front.performClick();assertFalse(front.isSelected);assertTrue(rear.isSelected)
        val brightness=a.window.attributes.screenBrightness
        button(root,"Front screen light").performClick();button(root,"Recording light").performClick()
        assertEquals(brightness,a.window.attributes.screenBrightness,0f)
        button(root,"Save schedule").performClick()
        val prefs=a.getSharedPreferences("automation",0);waitUntil { prefs.getBoolean("armed",false) }
        assertEquals("REAR",prefs.getString("mode",null));assertTrue(prefs.getBoolean("light",false));assertTrue(prefs.getBoolean("screenLight",false))
        assertEquals(CameraMode.REAR,accepted.last().cameraMode)
        assertEquals(prefs.getString("rear",null),accepted.last().rearQuality)
        button(root,"Close").performClick();button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val reopened=a.findViewById<ViewGroup>(android.R.id.content)
        assertFalse(button(reopened,"Front").isSelected);assertTrue(button(reopened,"Rear").isSelected)
        assertTrue(button(reopened,"Recording light").isSelected);assertTrue(button(reopened,"Front screen light").isSelected)
        button(reopened,"Rear").performClick();button(reopened,"Save schedule").performClick()
        waitUntil { views(reopened).filterIsInstance<TextView>().any { it.text=="Select at least one camera." } }
        c.pause().stop().destroy()
    }
    @Test fun archiveAndScheduleGuidesSitImmediatelyBeforeCloseAndAreGrouped() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        listOf("Recordings" to listOf("SELECT & DELETE","KEEP / SELECTED"),"Schedule recording" to listOf("START, DURATION & END","EXACT TIMING PERMISSION")).forEach { (entry,headings) ->
            if(entry=="Schedule recording")button(a.findViewById(android.R.id.content),"Capture timing").performLongClick() else button(a.findViewById(android.R.id.content),entry).performClick()
            val root=a.findViewById<ViewGroup>(android.R.id.content)
            val guide=button(root,"Guide");val close=button(root,"Close");val header=guide.parent as ViewGroup
            assertSame(header,close.parent);assertEquals(header.indexOfChild(guide)+1,header.indexOfChild(close))
            guide.performClick();val dialog=ShadowDialog.getLatestDialog()
            val text=views(dialog.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text }
            headings.forEach { assertTrue(text.contains(it)) };dialog.dismiss();close.performClick()
        }
        c.pause().stop().destroy()
    }
    @Test fun quotaIncludesBytesAlreadyWrittenToAnOpenFileAndExcludesKeptFiles() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val app=a.application as DuoApp
        val file=java.io.File(a.cacheDir,"quota-live.mp4")
        java.io.RandomAccessFile(file,"rw").use { it.setLength(20L*1048576) }
        val ordinary=Clip("one","content://media/one","one.mp4",1,1000,20L*1048576,ClipKind.ROLLING,ClipStatus.READY)
        val files=listOf(ordinary,ordinary.copy(id="kept",kind=ClipKind.SELECTED,bytes=512L*1048576),ordinary.copy(id="live",uri=android.net.Uri.fromFile(file).toString(),status=ClipStatus.WRITING,bytes=0))
        val repository=java.lang.reflect.Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,method,_ -> when(method.name) {
            "list" -> files;"config" -> RecordingConfig(quotaBytes=80L*1048576);"folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10L*1073741824;else -> Unit
        } } as MediaRepository
        DuoApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible=true }.set(app,lazy { repository })
        MainActivity::class.java.getDeclaredMethod("refreshData").apply { isAccessible=true }.invoke(a)
        val badge=views(a.findViewById(android.R.id.content)).filterIsInstance<HealthBadges>().single()
        waitUntil { badge.health?.rollingBytes==40L*1048576 }
        assertEquals("50% used\n40.0 MB / 80.0 MB",HealthGauge.value(badge.health,5))
        file.delete();c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun screenLightShowsActiveCameraAndRearLedRemainsControllableForEveryCameraMode() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val app=a.application as DuoApp
        val root=a.findViewById<ViewGroup>(android.R.id.content);layout(root)
        val panels=views(root).filterIsInstance<CameraPanel>()
        val original=panels.map { it.monitor.parent };val brightness=a.window.attributes.screenBrightness
        app.activeEngine=java.lang.reflect.Proxy.newProxyInstance(CaptureEngine::class.java.classLoader,arrayOf(CaptureEngine::class.java)) { _,_,_ -> Unit } as CaptureEngine
        for(sources in listOf(setOf(CameraSource.FRONT),setOf(CameraSource.REAR),setOf(CameraSource.FRONT,CameraSource.REAR))) {
            app.status.value=RuntimeStatus(phase=Phase.RECORDING,cameras=sources.associateWith { CameraCaptureStatus(recording=true) })
            app.torch.value=TorchStatus(available=true);shadowOf(Looper.getMainLooper()).idle()
            button(root,"Front screen light").performClick();shadowOf(Looper.getMainLooper()).idle()
            val chosen=if(CameraSource.FRONT in sources)0 else 1
            val overlay=panels[chosen].monitor.parent as ViewGroup
            assertNotSame(original[chosen],overlay);assertEquals(1f,a.window.attributes.screenBrightness,0f)
            assertFalse(views(overlay).filterIsInstance<Button>().any { it.text=="Guide" || it.text=="Recording light" })
            assertEquals((a.resources.displayMetrics.widthPixels*.25f).toInt(),panels[chosen].monitor.layoutParams.width)
            assertSame(overlay,panels[chosen].monitor.parent);assertEquals(Phase.RECORDING,app.status.value.phase)
            views(overlay).single { it.contentDescription=="Turn off screen light" }.performClick()
            panels.forEachIndexed { i,panel -> assertSame(original[i],panel.monitor.parent) }
            assertEquals(brightness,a.window.attributes.screenBrightness,0f)
        }
        app.activeEngine=null;app.status.value=RuntimeStatus();c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun photoScreenLightOnlyIlluminatesDuringExposureAndAlwaysRestoresOnStop() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val app=a.application as DuoApp
        app.repository!!.saveConfig(app.repository!!.config().copy(photoMode=true))
        val root=a.findViewById<ViewGroup>(android.R.id.content);val front=views(root).filterIsInstance<CameraPanel>().first();val monitor=front.monitor;val original=monitor.parent
        layout(root);val brightness=a.window.attributes.screenBrightness
        button(root,"Front screen light").performClick();assertSame(original,monitor.parent)
        app.status.value=RuntimeStatus(phase=Phase.RECORDING,cameras=mapOf(CameraSource.FRONT to CameraCaptureStatus(recording=true)))
        shadowOf(Looper.getMainLooper()).idle();assertNotSame(original,monitor.parent)
        val stableOverlay=monitor.parent
        app.photoLighting.value=setOf(CameraSource.FRONT);shadowOf(Looper.getMainLooper()).idle()
        assertNotSame(original,monitor.parent);assertEquals(1f,a.window.attributes.screenBrightness,0f)
        app.photoLighting.value=setOf(CameraSource.FRONT,CameraSource.REAR);shadowOf(Looper.getMainLooper()).idle()
        app.photoLighting.value=setOf(CameraSource.REAR);shadowOf(Looper.getMainLooper()).idle();assertNotSame(original,monitor.parent)
        app.photoLighting.value=emptySet();shadowOf(Looper.getMainLooper()).idle();assertSame(stableOverlay,monitor.parent);assertEquals(brightness,a.window.attributes.screenBrightness,0f)
        val close=views(stableOverlay as View).single { it.contentDescription=="Turn off screen light" }
        close.performClick();assertFalse(app.screenLightWanted);assertSame(original,monitor.parent)
        app.photoLighting.value=setOf(CameraSource.FRONT);shadowOf(Looper.getMainLooper()).idle()
        app.status.value=RuntimeStatus();shadowOf(Looper.getMainLooper()).idle();assertSame(original,monitor.parent);assertEquals(brightness,a.window.attributes.screenBrightness,0f)
        c.pause().stop().destroy()
    }
}
