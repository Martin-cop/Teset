package com.duorecord.app.ui

import android.content.Intent
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder29UiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content)
    private fun render(v:View,name:String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,360,800)
        val b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=java.io.File("build/ui-preview/$name.png");f.parentFile!!.mkdirs();f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    @Test fun capacityEstimateRejectsUnsupportedLowLevelAndImplausibleReadings() {
        assertEquals(5000.0,DeviceHealthReader.estimateCapacityMah(3650000,73)!!,.001)
        assertNull(DeviceHealthReader.estimateCapacityMah(Int.MIN_VALUE,70));assertNull(DeviceHealthReader.estimateCapacityMah(10000,0))
        assertNull(DeviceHealthReader.estimateCapacityMah(10000,4));assertNull(DeviceHealthReader.estimateCapacityMah(Int.MAX_VALUE,50))
        assertNull(DeviceHealthReader.estimateCapacityMah(null,50))
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun statusKeepsOneLineHeightAndBatteryTitleTogglesBack() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.repository!!.saveConfig(RecordingConfig(audioOnly=true))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        render(root(a),"audio-fixed-message-29")
        val label=views(root(a)).filterIsInstance<TextView>().single { it.tag=="capture-status-strip" };assertEquals(40,label.height);assertEquals(1,label.maxLines)
        label.text="First\nSecond\nThird line should not grow this strip";render(root(a),"audio-long-message-29");assertEquals(40,label.height)
        MainActivity::class.java.getDeclaredMethod("showDeviceStatus",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,0)
        views(root(a)).single { it.contentDescription=="Battery electrical details" }.performClick()
        views(root(a)).filterIsInstance<SignButton>().single { it.text=="Battery details" }.performClick()
        assertTrue(views(root(a)).any { it is HealthGauge });render(root(a),"battery-gauges-29")
        c.pause().stop().destroy()
    }
    @Test fun screenToolbarIsProtectedAndHasPauseResumeStopWithoutCameraInsets() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;ShadowSettings.setCanDrawOverlays(true);app.uiVisible.value=false
        val config=RecordingConfig(screenMode=true);app.repository!!.saveConfig(config);app.status.value=RuntimeStatus(phase=Phase.RECORDING)
        var stopped=false
        val floating=MixedFloatingControls(app,{config},{},{stopped=true});floating.show()
        val windows=Shadow.extract<ShadowWindowManagerImpl>(app.getSystemService(WindowManager::class.java))
        val bar=windows.views.single();assertEquals("floating-toolbar",bar.tag)
        assertTrue((bar.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertTrue(views(bar).none { it is TextureView })
        views(bar).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.RECORD }.performClick()
        assertEquals(RecordingService.PAUSE,shadowOf(app).nextStartedService.action)
        app.status.value=RuntimeStatus(phase=Phase.USER_PAUSED);floating.sync(config)
        views(windows.views.single()).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.PAUSE }.performClick()
        assertEquals(RecordingService.RESUME,shadowOf(app).nextStartedService.action)
        views(windows.views.single()).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.STOP }.performClick();assertTrue(stopped)
        floating.hide();assertTrue(windows.views.isEmpty());app.status.value=RuntimeStatus()
    }
    @Test fun finalizedScreenStopReturnsTheScreenFileEvenWhenAnotherSourceFinishesLast() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.uiVisible.value=false
        val controller=Robolectric.buildService(RecordingService::class.java).create();val service=controller.get()
        fun set(name:String,value:Any) { RecordingService::class.java.getDeclaredField(name).apply { isAccessible=true }.set(service,value) }
        set("preferredPlaybackSource",CameraSource.SCREEN);set("returnToPlayback",true)
        val clip=Clip("screen","content://record/screen","screen.mp4",1000,1000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.SCREEN)
        service.onSegment(clip);service.onSegment(clip.copy(id="rear",camera=CameraSource.REAR));service.onStopped("Stopped")
        shadowOf(Looper.getMainLooper()).idle()
        val launched=shadowOf(app).nextStartedActivity;assertNotNull(launched);assertEquals("screen",launched.getStringExtra("clip"));assertEquals(Phase.IDLE,app.status.value.phase)
        controller.destroy()
    }
    @Test fun activeScreenProtectsMainWindowAndRequestsMinimizeAfterStart() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.repository!!.saveConfig(RecordingConfig(screenMode=true))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredField("leaveForMixedCapture").apply { isAccessible=true }.setBoolean(a,true)
        app.status.value=RuntimeStatus(phase=Phase.RECORDING,cameras=mapOf(CameraSource.SCREEN to CameraCaptureStatus(recording=true)))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertFalse(MainActivity::class.java.getDeclaredField("leaveForMixedCapture").apply { isAccessible=true }.getBoolean(a))
        app.status.value=RuntimeStatus();shadowOf(Looper.getMainLooper()).idle();assertEquals(0,a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        c.pause().stop().destroy()
    }
}
