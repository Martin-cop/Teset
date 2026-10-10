package com.duorecord.app.ui

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.*
import com.duorecord.contracts.CameraCaptureStatus
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class TransportControlsTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(test:()->Boolean) { val end=System.nanoTime()+5000000000;while(!test() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    private fun row(a:MainActivity)=views(a.findViewById(android.R.id.content)).single { it.tag=="capture-transport" } as LinearLayout
    private fun render(view:View,name:String,w:Int=360,h:Int=800) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val out=java.io.File("build/ui-preview/$name.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun cameraQualityIsUnderMonitorAndToolsAreLastWithActiveModePulse() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.repository!!.saveConfig(RecordingConfig())
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val root=a.findViewById<ViewGroup>(android.R.id.content)
        render(root,"home-rearranged-28")
        val card=views(root).single { it.tag=="capture-card" } as ViewGroup
        assertSame(card,views(root).single { it.tag=="capture-status-strip" }.parent)
        assertTrue(views(card).filterIsInstance<SignButton>().none { it.icon in setOf(HeaderIcon.SCHEDULE,HeaderIcon.FOLDER,HeaderIcon.BOOKMARK,HeaderIcon.SETTINGS) })
        val panels=views(card).filterIsInstance<CameraPanel>();assertEquals(2,panels.size)
        panels.forEach { panel ->
            val q=views(panel).single { it.tag=="camera-quality-control" }
            assertTrue(q.top>=panel.monitor.bottom+8);assertEquals(panel.monitor.left,q.left);assertEquals(panel.monitor.right,q.right)
            assertSame(q,views(panel).single { it.tag=="camera-quality-value" }.parent)
        }
        val body=card.parent as ViewGroup
        val tools=views(root).single { it.contentDescription=="Tools" };assertSame(body,tools.parent.parent)
        assertEquals(body.childCount-1,body.indexOfChild(tools.parent as View))
        val mode=views(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.VIDEO_MODE }
        app.status.value=RuntimeStatus(phase=Phase.RECORDING);shadowOf(Looper.getMainLooper()).idle();assertTrue(mode.pulseGlyph)
        app.status.value=RuntimeStatus(phase=Phase.USER_PAUSED);shadowOf(Looper.getMainLooper()).idle();assertFalse(mode.pulseGlyph)
        app.status.value=RuntimeStatus();c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun allFiveModesShareOrderedControlRowAndTimingLivesOnItsOwnPage() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val configs=listOf(RecordingConfig(),RecordingConfig(photoMode=true),RecordingConfig(audioOnly=true),RecordingConfig(screenMode=true),RecordingConfig(mixedMode=true))
        for((index,config) in configs.withIndex()) {
            app.status.value=RuntimeStatus();app.repository!!.saveConfig(config)
            val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get();val root=a.findViewById<ViewGroup>(android.R.id.content)
            val expected=listOf(HeaderIcon.RECORD,HeaderIcon.STOP,HeaderIcon.PLAY)
            val controls=views(row(a)).filterIsInstance<SignButton>()
            assertEquals(expected,controls.map { it.icon });assertEquals(0,views(root).filterIsInstance<EditText>().size)
            assertTrue(controls[0].isEnabled);assertFalse(controls[1].isEnabled);assertFalse(controls[2].isEnabled)
            render(root,"transport-mode-$index")
            assertTrue(controls.all { it.width>=40 && it.height==48 });assertEquals(1,controls.map { it.top }.distinct().size)
            val tools=views(root).single { it.contentDescription=="Tools" } as LinearLayout
            val toolButtons=(0 until tools.childCount).map { tools.getChildAt(it) };assertEquals(52,tools.height);assertNull(tools.background)
            assertTrue(toolButtons.zipWithNext().all { (l,r) -> r.left-l.right>=2 })
            views(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.SCHEDULE }.performClick()
            val page=a.findViewById<ViewGroup>(android.R.id.content)
            val entry=views(page).filterIsInstance<EditText>().single();assertEquals("120",entry.text.toString())
            val buttons=views(page).filterIsInstance<SignButton>()
            assertTrue(buttons.any { it.icon==HeaderIcon.GUIDE });assertTrue(buttons.any { it.icon==HeaderIcon.CLOSE })
            if(index==0) {
                render(page,"capture-timing")
                entry.setText("9999");buttons.single { it.icon==HeaderIcon.CLOSE }.performClick();shadowOf(Looper.getMainLooper()).idle()
                assertEquals(120,app.repository!!.config().maxRecordingMinutes);assertNotNull(entry.error)
            }
            entry.setText("45");buttons.single { it.icon==HeaderIcon.CLOSE }.performClick()
            waitFor { app.repository!!.config().maxRecordingMinutes==45 && views(a.findViewById(android.R.id.content)).any { it.tag=="capture-transport" } }
            app.status.value=RuntimeStatus(phase=Phase.RECORDING,cameras=mapOf(CameraSource.REAR to CameraCaptureStatus(recording=true)))
            shadowOf(Looper.getMainLooper()).idle()
            var updated=views(row(a)).filterIsInstance<SignButton>();assertTrue(updated[0].isEnabled);assertTrue(updated[1].isEnabled);assertFalse(updated[2].isEnabled);assertFalse(views(a.findViewById(android.R.id.content)).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.SCHEDULE }.isEnabled)
            updated[0].performClick();assertEquals(RecordingService.PAUSE,shadowOf(a).nextStartedService.action)
            app.status.value=app.status.value.copy(phase=Phase.USER_PAUSED);shadowOf(Looper.getMainLooper()).idle()
            updated=views(row(a)).filterIsInstance<SignButton>();assertEquals(HeaderIcon.PAUSE,updated[0].icon);assertTrue(updated[1].isEnabled);assertTrue(updated[0].isEnabled)
            updated[0].performClick();assertEquals(RecordingService.RESUME,shadowOf(a).nextStartedService.action)
            app.status.value=RuntimeStatus();controller.pause().stop().destroy()
        }
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun sourceIconsRemainHonestAndSitRightOfNarrowBatteryReading() {
        val context=RuntimeEnvironment.getApplication()
        val health=DeviceHealth(40,37.5,false,"Good",0,10000000000L,false,false)
        ChargingSourceChoice.select(context,ChargingSourceChoice.POWER_BANK) // Legacy preference must not suppress the plug.
        val reading=HealthReading(context,health,0);render(reading,"power-source-unplugged",340,184)
        val controls=views(reading).filterIsInstance<SignButton>();assertEquals(1,controls.size)
        val plug=controls.single();assertEquals(HeaderIcon.CHARGER,plug.icon);assertFalse(plug.horizontalCharger);assertFalse(plug.isClickable)
        assertEquals(reading.value.height,plug.height);assertFalse(plug.isSelected)
        reading.bind(health.copy(charging=true));assertTrue(plug.isSelected);assertTrue(plug.pulseGlyph)
        render(reading,"power-source-charging-29",340,184)
        reading.bind(health);assertFalse(plug.isSelected);assertFalse(plug.pulseGlyph)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun professionalMeterShowsActualPcmSpectrumAndDigitalReadings() {
        val context=RuntimeEnvironment.getApplication()
        val pcm=ByteArray(2048);repeat(1024) { i -> val n=(22000*kotlin.math.sin(2*Math.PI*1000*i/48000)).toInt();pcm[2*i]=n.toByte();pcm[2*i+1]=(n shr 8).toByte() }
        val meter=AudioLevelView(context);meter.meter=PcmMeter.analyze(pcm,pcm.size)
        assertTrue(meter.contentDescription.toString().contains("dBFS"));assertTrue(meter.meter.rmsDb> -10f)
        render(meter,"professional-audio-meter",300,176)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun seventyThreePercentUsesSevenFullCellsAndOnlyThreeTenthsOfTheEighth() {
        val context=RuntimeEnvironment.getApplication()
        val health=DeviceHealth(73,37.5,false,"Good",0,10000000000L,false,false)
        assertEquals(.73f,HealthGauge.fill(health,0),.0001f)
        val bitmap=Bitmap.createBitmap(500,100,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val b=RectF(10f,10f,480f,90f);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        HealthGauge.draw(canvas,paint,b,0,HealthGauge.bars(health,0),HealthGauge.fill(health,0))
        val unit=b.height()/14f;val right=b.right-2*unit;val step=(right-b.left-4*unit)/10
        val cellLeft=b.left+2*unit+7*step;val cellWidth=step-unit*.6f
        fun color(fraction:Float)=bitmap.getPixel((cellLeft+cellWidth*fraction).toInt(),50)
        assertEquals(Palette.mint,color(.15f));assertNotEquals(Palette.mint,color(.50f))
        assertEquals(Palette.mint,bitmap.getPixel((b.left+2*unit+6.5f*step).toInt(),50))
        assertNotEquals(Palette.mint,bitmap.getPixel((b.left+2*unit+8.5f*step).toInt(),50))
        val out=java.io.File("build/ui-preview/battery-73-percent.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun marksSurviveReopeningAndKeepTheirSessionAndSourceIdentity() {
        val context=RuntimeEnvironment.getApplication();val marks=CaptureMarkers(context)
        marks.add(1000,setOf(CameraSource.REAR,CameraSource.MIXED),2000)
        val restored=CaptureMarkers(context).list().single()
        assertEquals(2000L,restored.at);assertEquals(1000L,restored.sessionStart);assertEquals(setOf(CameraSource.REAR,CameraSource.MIXED),restored.sources)
    }
}
