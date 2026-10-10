package com.duorecord.app.ui

import android.content.Context
import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.*
import com.duorecord.contracts.*
import com.duorecord.components.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder27UiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content)
    private fun waitFor(ok:()->Boolean) { val until=System.nanoTime()+5000000000;while(!ok() && System.nanoTime()<until) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(ok()) }
    private fun render(v:View,name:String,w:Int=360,h:Int=800):Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=java.io.File("build/ui-preview/$name.png");f.parentFile.mkdirs();f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };return b
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun homeStopIsDisabledGrayBeforeCaptureAndArmedClockKeepsCoolHands() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val pause=views(root(a)).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.STOP }
        assertFalse(pause.isEnabled);assertFalse(pause.showUnavailablePause)
        val bitmap=render(pause,"visible-pause-27",48,48)
        val pixels=IntArray(48*48);bitmap.getPixels(pixels,0,48,0,0,48,48);assertTrue(pixels.any { Color.red(it) in 80..140 && Color.green(it) in 80..150 && Color.blue(it) in 80..150 });bitmap.recycle()
        val clock=a.iconButton(HeaderIcon.SCHEDULE) {}.apply { scheduleArmed=true;squareFace=true }
        val b=render(clock,"orange-cool-clock-27",56,56);val colors=IntArray(56*56);b.getPixels(colors,0,56,0,0,56,56)
        assertFalse(colors.any { Color.red(it)>220 && Color.green(it) in 100..190 && Color.blue(it)<100 })
        assertTrue(colors.any { Color.blue(it)>200 && Color.green(it)>180 && Color.red(it)<180 });b.recycle()
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun chargeGlyphsHaveFullValueHeightAndBothNumberFramesAreNarrowAndGreen() {
        val context=RuntimeEnvironment.getApplication();val h=DeviceHealth(73,37.5,false,"Good",0,10000000000L,false,false)
        val battery=HealthReading(context,h,0);val temperature=HealthReading(context,h,1)
        render(battery,"full-charge-icons-27",340,160).recycle();render(temperature,"narrow-green-temperature-27",340,160).recycle()
        assertEquals(104,battery.value.width);assertEquals(battery.value.width,temperature.value.width)
        assertEquals(170,battery.value.left+battery.value.width/2);assertEquals(170,temperature.value.left+temperature.value.width/2)
        val buttons=views(battery).filterIsInstance<SignButton>();assertEquals(1,buttons.size);assertTrue(buttons.all { it.height==battery.value.height })
        battery.bind(h.copy(charging=true));assertTrue(buttons.any { it.isSelected });battery.bind(h);assertFalse(buttons.any { it.isSelected })
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun powerMetersUseEqualVisibleGapsAndFormulaMeetsPanelEdge() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("showDeviceStatus",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,0)
        val body=root(a);render(body,"spaced-power-meters-27").recycle()
        val meters=views(body).filterIsInstance<AnalogPowerView>();assertEquals(3,meters.size)
        val row=meters.first().parent as LinearLayout
        assertEquals(12,row.paddingLeft);assertEquals(row.paddingLeft,row.paddingRight)
        assertEquals(18,meters[1].left+6-(meters[0].right-6));assertEquals(18,meters[2].left+6-(meters[1].right-6))
        assertEquals(18,meters.first().left+6);assertEquals(18,row.width-(meters.last().right-6))
        val formula=views(body).single { it.tag=="power-formula-strip" };assertEquals(11,formula.left)
        assertTrue(row.bottom<formula.top);c.pause().stop().destroy()
    }
    private fun inject(app:DuoApp,name:String,v:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { v }) }
    private fun repository(clip:Clip)=Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,m,_ -> when(m.name) { "config" -> RecordingConfig();"list" -> listOf(clip);"pin" -> AutoCloseable {};"folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10000000000L;else -> Unit } } as MediaRepository
    private class FakePlayer(context:Context):PlayerComponent {
        override val view=FrameLayout(context);var opened=false;var released=false;var playing=false;var position=15000L
        override fun open(clip:Clip,companion:Clip?) { opened=true;playing=true };override fun positionMs()=position;override fun durationMs()=60000L
        override fun seekTo(positionMs:Long) { position=positionMs };override fun setSpeed(speed:Float) {};override fun speed()=1f
        override fun isPlaying()=playing;override fun play() { playing=true };override fun pause() { playing=false };override fun release() { released=true;playing=false }
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun audioViewerAddsTwoAlignedGlossyRailsWithoutScrollingControls() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;val clip=Clip("audio","content://audio/1","audio.aac",0,60000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.AUDIO)
        inject(app,"repository",repository(clip));inject(app,"player",object:PlayerFactory { override fun create(context:Context,onError:(String)->Unit)=FakePlayer(context) })
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("openPlayer",Clip::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,clip,false)
        waitFor { views(root(a)).filterIsInstance<GlossyControlRail>().size==2 };val body=root(a);render(body,"audio-glossy-rails-27").recycle()
        val rails=views(body).filterIsInstance<GlossyControlRail>();val rows=views(body).filter { it.contentDescription in listOf("Media navigation","Media file actions") }
        rails.forEachIndexed { i,rail -> assertEquals(rows[i].left,rail.left);assertEquals(rows[i].width,rail.width);assertTrue(rail.top>=rows[i].bottom) }
        assertFalse(views(body).any { it is HorizontalScrollView });c.pause().stop().destroy()
    }
    @Test fun trimReleasesMainDecoderAndCreatesPreviewOnlyOnDemandThenRestoresPaused() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;val clip=Clip("video","content://video/1","large.mp4",0,60000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.REAR)
        val players=mutableListOf<FakePlayer>();inject(app,"repository",repository(clip))
        inject(app,"player",object:PlayerFactory { override fun create(context:Context,onError:(String)->Unit)=FakePlayer(context).also { players+=it } })
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("openPlayer",Clip::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,clip,false)
        waitFor { players.size==1 && players.first().opened }
        MainActivity::class.java.getDeclaredMethod("trimDialog",Clip::class.java).apply { isAccessible=true }.invoke(a,clip)
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        assertTrue(players.first().released);assertEquals(1,players.size)
        views(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text=="Preview selection" }.performClick()
        waitFor { players.size==2 && players.last().opened };assertEquals(1,players.count { !it.released })
        dialog.dismiss();waitFor { players.size==3 && players.last().opened };assertEquals(1,players.count { !it.released });assertFalse(players.last().playing)
        c.pause().stop().destroy()
    }
}
