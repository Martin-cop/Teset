package com.duorecord.app.ui

import android.graphics.*
import android.view.*
import android.widget.*
import com.duorecord.app.*
import com.duorecord.domain.*
import com.duorecord.components.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder30UiTest {
    private fun render(v:View,name:String,w:Int,h:Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=java.io.File("build/ui-preview/$name.png");f.parentFile!!.mkdirs();f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    @Test fun activeClockExcludesStartupPauseRecoveryAndStopAndResetsForNewCapture() {
        var now=1000L;val clock=CaptureClock(now={now})
        clock.transition(100,Phase.STARTING);now+=4000;assertEquals(0L,clock.elapsedMs())
        clock.transition(100,Phase.RECORDING);now+=3000;assertEquals(3000L,clock.elapsedMs())
        clock.transition(100,Phase.USER_PAUSED);now+=30000;assertEquals(3000L,clock.elapsedMs())
        clock.transition(100,Phase.RECORDING);now+=2000;clock.transition(100,Phase.PAUSED)
        now+=10000;assertEquals(5000L,clock.elapsedMs());clock.transition(100,Phase.RECORDING);now+=1000
        clock.transition(0,Phase.IDLE);now+=10000;assertEquals(6000L,clock.elapsedMs())
        clock.transition(200,Phase.STARTING);assertEquals(0L,clock.elapsedMs())
    }
    @Test fun allSelectIncludesFilesOutsideVisibleLimitAndSurvivesLimitedRefresh() {
        val files=(1..2444).map { Clip("$it","content://record/$it","$it.mp4",it.toLong(),1000,10,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.REAR) }
        val adapter=ClipListAdapter({},{})
        adapter.allFiles=files;adapter.clips=files.take(100);adapter.selectAll()
        assertEquals(2444,adapter.selectedClips().size);assertTrue(adapter.allSelected)
        adapter.clips=files.takeLast(100);assertEquals(2444,adapter.selectedClips().size)
        adapter.toggleAll();assertTrue(adapter.selectedClips().isEmpty());assertFalse(adapter.allSelected)
        assertEquals(100,RecordingConfig().archiveInitialLimit)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun numericCapacityUsesMahAndEnergyToggleUsesWhWithMatchedFramesAndCable() {
        val context=RuntimeEnvironment.getApplication();val health=DeviceHealth(73,37.5,true,"Good",0,10000000000L,false,false,batteryCurrentMa=1200.0,batteryVoltageV=3.8,chargingActive=true,estimatedCapacityMah=5000.0)
        val body=context.column().apply { addWide(HealthReading(context,health,0),236);addGap(12);addWide(HealthReading(context,health,1),236) }
        render(body,"charging-cable-30",340,484)
        val b=body.getChildAt(0) as HealthReading;val t=body.getChildAt(2) as HealthReading
        assertEquals(b.value.width,t.value.width);assertNotNull(b.gauge.chargerRect)
        val capacity=(0 until b.childCount).map { b.getChildAt(it) }.filterIsInstance<TextView>().single { it.tag=="battery-capacity" }
        assertEquals("5000",capacity.text.toString());assertEquals(b.value.textSize,capacity.textSize,.01f)
        b.value.performClick();assertEquals("13.9",b.value.text.toString())
        b.value.performClick();assertEquals("73%",b.value.text.toString())
        t.value.performClick();assertEquals("99.5°F",t.value.text.toString())
    }
    @Test fun powerHistoryBoundsAndMissingValuesStayHonest() {
        val at=100000000L
        val data=listOf(PowerSample(at-PowerHistory.WINDOW_MS-1,4.0,1.0,4.0,true),PowerSample(at-10000,4.0,null,null,true),PowerSample(at,4.1,1.2,4.92,true),PowerSample(at+1,4.0,1.0,4.0,true))
        val kept=PowerHistory.retain(data,at);assertEquals(2,kept.size);assertNull(kept.first().amps);assertEquals(4.92,kept.last().watts!!,.001)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun quieterSpectrumChangesVisualColorWithoutChangingActualDecibelValues() {
        val context=RuntimeEnvironment.getApplication();val meter=AudioMeter(bands=FloatArray(20){.25f},rmsDb=-40f,peakDb=-30f)
        val spectrum=AudioSpectrumView(context).apply { this.meter=meter }
        assertTrue(AudioSpectrumView.displayAmplitude(.25f)>.65f);assertEquals(0f,AudioSpectrumView.displayAmplitude(0f),0f)
        assertEquals(-40f,spectrum.meter.rmsDb,0f);assertFalse(spectrum.meter.clipped)
        render(spectrum,"quiet-spectrum-30",340,220)
        val chart=HistoryChartView(context).apply { points=listOf(1000L to 2.0,2000L to 4.0,3000L to null,4000L to 3.0);unit="W" };render(chart,"power-history-30",340,240)
    }
    @Test fun temperatureAlarmRearmsOnlyAfterCooling() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.observeBatteryTemperature(44.9);assertFalse(app.batteryHeatAlerted)
        app.observeBatteryTemperature(45.0);assertTrue(app.batteryHeatAlerted);assertTrue(app.pendingTemperaturePage)
        app.pendingTemperaturePage=false;app.observeBatteryTemperature(46.0);assertFalse(app.pendingTemperaturePage)
        app.observeBatteryTemperature(42.1);assertTrue(app.batteryHeatAlerted)
        app.observeBatteryTemperature(42.0);assertFalse(app.batteryHeatAlerted)
        app.observeBatteryTemperature(45.5);assertTrue(app.pendingTemperaturePage)
    }
    @Test fun homeMonitorSwipesChangeModeAndOpenFilesWithoutUsingRulerTouches() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.repository!!.saveConfig(RecordingConfig())
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
        fun root()=a.findViewById<ViewGroup>(android.R.id.content)
        fun layout() { root().measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root().layout(0,0,360,800) }
        layout()
        fun swipe(x:Float,y:Float,dx:Float,dy:Float) {
            val at=android.os.SystemClock.uptimeMillis()
            val down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0);a.dispatchTouchEvent(down);down.recycle()
            val up=MotionEvent.obtain(at,at+300,MotionEvent.ACTION_UP,x+dx,y+dy,0);a.dispatchTouchEvent(up);up.recycle()
        }
        val monitor=views(root()).filterIsInstance<CameraPanel>().first().monitor;val pos=IntArray(2);monitor.getLocationOnScreen(pos)
        swipe(280f,pos[1]+80f,-180f,0f)
        val end=System.nanoTime()+5000000000
        while(!app.repository!!.config().audioOnly && System.nanoTime()<end) { Thread.sleep(10);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        assertTrue(app.repository!!.config().audioOnly)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();layout()
        root().getLocationOnScreen(pos)
        swipe(280f,pos[1]+400f,0f,-150f)
        assertTrue(views(root()).any { it is androidx.recyclerview.widget.RecyclerView })
        c.pause().stop().destroy()
    }

    @Test fun activeCounterCheckpointRestoresWithoutAddingProcessDowntime() {
        val context=RuntimeEnvironment.getApplication();var now=1000L
        val clock=CaptureClock(context,now={now});clock.transition(500,Phase.RECORDING)
        now+=6000;assertEquals(6000L,clock.elapsedMs())
        now+=30000
        val restored=CaptureClock(context,now={now});restored.transition(500,Phase.STARTING)
        assertEquals(6000L,restored.elapsedMs());restored.transition(500,Phase.RECORDING)
        now+=3000;assertEquals(9000L,restored.elapsedMs())
    }

}
