package com.duorecord.app.ui

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.duorecord.app.*
import com.duorecord.components.*
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder24UiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content)
    private fun render(v:View,name:String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,360,800)
        val bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));val out=java.io.File("build/ui-preview/$name.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun audioCounterAndSpectrumFollowTransportEdgesAndComponentsHaveNoApplicationDependency() {
        val app=RuntimeEnvironment.getApplication() as DuoApp;app.repository!!.saveConfig(RecordingConfig(audioOnly=true))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();val root=root(a)
        render(root,"audio-counter-aligned")
        val meter=views(root).filterIsInstance<AudioSpectrumView>().single()
        val row=views(root).single { it.tag=="capture-transport" } as LinearLayout
        val first=row.getChildAt(0);val last=row.getChildAt(row.childCount-1)
        assertEquals(row.left+first.left+4,meter.left+2);assertEquals(row.left+last.right-4,meter.right-2)
        val counter=views(root).filterIsInstance<ElapsedCounterView>().single();counter.elapsedMs=90_000
        assertEquals("00:01:30",counter.text.toString());assertEquals(meter.left+2,counter.left);assertEquals(meter.right-2,counter.right)
        val recording=views(row).filterIsInstance<SignButton>().first();assertEquals(HeaderIcon.RECORD,recording.icon);assertTrue(recording.semanticTransport)
        assertEquals("com.duorecord.components",meter.javaClass.packageName)
        val standalone=GaugeView(a).apply { kind=GaugeView.Kind.BATTERY;fraction=2f };assertEquals(1f,standalone.fraction,0f)
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun batteryHeaderOpensDedicatedMetricsAndFormulaExpandsInline() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("showDeviceStatus",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,0)
        var root=root(a);render(root,"battery-compact-details")
        val formula=views(root).filterIsInstance<TextView>().single { it.text.toString().contains(" V × ") }
        assertFalse(formula.isClickable);assertEquals(22f,formula.textSize,.1f)
        assertEquals(3,views(root).filterIsInstance<AnalogPowerView>().size)
        views(root).single { it.contentDescription=="Battery electrical details" }.performClick()
        root=root(a);assertTrue(views(root).filterIsInstance<TextView>().any { it.text=="Battery current:" })
        assertFalse(views(root).any { it is HealthGauge });assertTrue(views(root).filterIsInstance<SignButton>().any { it.icon==HeaderIcon.GUIDE })
        a.onBackPressed();assertTrue(views(root(a)).filterIsInstance<SignButton>().any { it.contentDescription=="Battery electrical details" })
        c.pause().stop().destroy()
    }
    @Test fun archiveHeaderDeleteBeginsEmptySelectionAndHasFolderShortcut() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("showArchive").apply { isAccessible=true }.invoke(a)
        val root=root(a)
        assertTrue(views(root).filterIsInstance<SignButton>().any { it.icon==HeaderIcon.FOLDER })
        views(root).single { it.contentDescription=="Select files" }.performClick()
        assertTrue(views(root).filterIsInstance<TextView>().any { it.text=="0 files selected" && it.parent is View && (it.parent as View).visibility==View.VISIBLE })
        c.pause().stop().destroy()
    }
    @Test fun decodedStereoMeterIsPassThroughAndShowsActualFrequencyAndSilence() {
        val meters=mutableListOf<AudioMeter>()
        val type=Class.forName("com.duorecord.player.PlaybackMeterProcessor")
        val processor=type.declaredConstructors.single().apply { isAccessible=true }.newInstance({ m:AudioMeter -> meters+=m;Unit }) as AudioProcessor
        processor.configure(AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));processor.flush()
        val input=ByteBuffer.allocateDirect(4096).order(ByteOrder.LITTLE_ENDIAN)
        repeat(1024) { i -> val v=(16000*kotlin.math.sin(2*Math.PI*1000*i/48000)).toInt().toShort();input.putShort(v);input.putShort(v) };input.flip()
        val expected=ByteArray(input.remaining());input.duplicate().get(expected)
        processor.queueInput(input);assertEquals(0,input.remaining())
        val output=processor.output;val actual=ByteArray(output.remaining());output.get(actual);assertArrayEquals(expected,actual)
        assertTrue(meters.last().rmsDb in -10f..-8f);assertTrue(meters.last().bands.max()> .5f)
        processor.flush();assertEquals(-90f,meters.last().rmsDb,.0001f)
        val silent=ByteBuffer.allocateDirect(4096);processor.queueInput(silent);assertEquals(-90f,meters.last().rmsDb,.0001f)
    }
    @Test fun screenStopOpensOnlyTheNewCompletedScreenFile() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val start=System.currentTimeMillis()
        val cfg=RecordingConfig(screenMode=true)
        val old=Clip("old","content://video/old","old_S.mp4",start-100000,1000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.SCREEN)
        var fresh=old.copy(id="new",uri="content://video/new",name="new_S.mp4",startMs=start)
        var files=listOf(old);val opened=mutableListOf<Clip>()
        val repo=java.lang.reflect.Proxy.newProxyInstance(com.duorecord.contracts.MediaRepository::class.java.classLoader,arrayOf(com.duorecord.contracts.MediaRepository::class.java)) { _,method,_ ->
            when(method.name) { "config" -> cfg;"list" -> files;"pin" -> AutoCloseable {};"folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10000000000L;else -> Unit }
        } as com.duorecord.contracts.MediaRepository
        val factory=object:com.duorecord.contracts.PlayerFactory {
            override fun create(context:android.content.Context,onError:(String)->Unit)=object:com.duorecord.contracts.PlayerComponent {
                override val view=FrameLayout(context)
                override fun open(clip:Clip,companion:Clip?) { opened+=clip }
                override fun positionMs()=0L;override fun durationMs()=1000L;override fun seekTo(positionMs:Long) {};
                override fun setSpeed(speed:Float) {};override fun speed()=1f;override fun play() {};override fun pause() {};override fun release() {}
            }
        }
        fun inject(name:String,value:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { value }) }
        inject("repository",repo);inject("player",factory)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val a=controller.get()
        val saved=CaptureRecovery(a).begin(cfg,false,0);fresh=fresh.copy(startMs=saved.startWall)
        app.status.value=RuntimeStatus(phase=Phase.RECORDING)
        MainActivity::class.java.getDeclaredMethod("stopRecording").apply { isAccessible=true }.invoke(a)
        val auto=MainActivity::class.java.getDeclaredMethod("maybeAutoPlayScreen").apply { isAccessible=true }
        auto.invoke(a);shadowOf(Looper.getMainLooper()).idle();assertTrue(opened.isEmpty())
        files=listOf(old,fresh);app.status.value=RuntimeStatus()
        auto.invoke(a)
        val end=System.nanoTime()+5000000000
        while(opened.isEmpty() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertEquals(listOf(fresh),opened)
        controller.pause().stop().destroy()
    }

}
