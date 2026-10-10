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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class ScheduleEditingTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun button(v:View,text:String)=views(v).filterIsInstance<Button>().single { it.text==text }
    private fun waitFor(test:()->Boolean) { val end=System.nanoTime()+5_000_000_000;while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    @Test fun clearedDateCanBeTypedWithoutSeparatorsAndNativePickersSetDateAndTime() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val root=a.findViewById<View>(android.R.id.content)
        val from=views(root).filterIsInstance<EditText>().first { it.contentDescription?.toString()?.startsWith("Local date")==true }
        val before=java.time.LocalDateTime.now().withSecond(0).withNano(0)
        views(root).single { it.contentDescription=="Start · now + 3 minutes" }.performClick()
        val fmt=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        assertEquals(before.plusMinutes(3).format(fmt),from.text.toString())
        views(root).single { it.contentDescription=="End · now + 10 minutes" }.performClick()
        assertTrue(views(root).filterIsInstance<EditText>().any { it.text.toString()==before.plusMinutes(10).format(fmt) })
        from.setText("");from.append("202810071230");assertEquals("2028-10-07 12:30",from.text.toString())
        from.setText("")
        views(root).single { it.contentDescription=="Choose Start date" }.performClick()
        var dialog=ShadowDialog.getLatestDialog() as NeonDialog
        views(dialog.window!!.decorView).filterIsInstance<DatePicker>().single().updateDate(2029,10,4)
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("11 · November · 2029") })
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(from.text.startsWith("2029-11-04 "))
        views(root).single { it.contentDescription=="Choose Start time" }.performClick()
        dialog=ShadowDialog.getLatestDialog() as NeonDialog
        views(dialog.window!!.decorView).filterIsInstance<TimePicker>().single().apply { hour=15;minute=37 }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("2029-11-04 15:37",from.text.toString())
        c.pause().stop().destroy()
    }
    @Test fun scheduleSavesPhotoAndAudioChoicesAndShowsThreeLineSummaryWithHourMinuteDuration() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val factory=object:CaptureFactory { override fun create(context:android.content.Context)=object:CaptureEngine {
            override fun support(config:RecordingConfig)=CaptureSupport(true,"Supported")
            override fun qualities(source:CameraSource,config:RecordingConfig)=listOf(CaptureQuality(480,640,1_000_000))
            override fun start(config:RecordingConfig,repository:MediaRepository,listener:CaptureListener) {}
            override fun stop(reason:String) {};override fun split() {};override fun update(config:RecordingConfig,sources:Set<CameraSource>) {};override fun setPreview(source:CameraSource,surface:Surface?) {}
        } }
        DuoApp::class.java.getDeclaredField("capture\$delegate").apply { isAccessible=true }.set(app,lazy { factory })
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val root=a.findViewById<View>(android.R.id.content)
        waitFor { views(root).filterIsInstance<QualityRuler>().count { it.steps==1 }==2 }
        val duration=views(root).filterIsInstance<QualityRuler>().single { it.steps==600 }
        duration.selected=69;duration.changed?.invoke(69,true)
        assertTrue(views(root).filterIsInstance<TextView>().any { it.text=="Duration · 1 h 10 min" })
        button(root,"Photos").performClick()
        views(root).filterIsInstance<EditText>().single { it.contentDescription=="Scheduled photo interval in seconds" }.setText("1")
        button(root,"Save schedule").performClick()
        val p=a.getSharedPreferences("automation",0);waitFor { p.getBoolean("armed",false) }
        assertTrue(p.getBoolean("photos",false));assertEquals(1,p.getInt("interval",0));assertFalse(p.getBoolean("audioOnly",true))
        val summary=views(root).filterIsInstance<TextView>().single { it.contentDescription=="Saved schedule" }.text.toString()
        assertEquals(3,summary.lines().size);assertTrue(summary.endsWith("Duration: 1 h 10 min"))
        button(root,"Audio").performClick();button(root,"Save schedule").performClick();waitFor { p.getBoolean("audioOnly",false) }
        assertFalse(p.getBoolean("photos",true));assertFalse(button(root,"Front").isShown)
        button(root,"Close").performClick()
        assertFalse(views(a.findViewById(android.R.id.content)).filterIsInstance<TextView>().any { it.contentDescription=="Saved schedule" })
        c.pause().stop().destroy()
    }
}
