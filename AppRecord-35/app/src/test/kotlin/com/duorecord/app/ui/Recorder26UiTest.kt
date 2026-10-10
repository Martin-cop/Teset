package com.duorecord.app.ui

import android.content.*
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
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
import org.robolectric.shadows.ShadowCameraCharacteristics
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi",shadows=[Recorder26UiTest.RequestShadow::class])
class Recorder26UiTest {
    @Implements(CaptureRequest.Builder::class)
    class RequestShadow {
        val written=mutableMapOf<CaptureRequest.Key<*>,Any?>()
        @Implementation fun set(key:CaptureRequest.Key<*>,value:Any?) { written[key]=value }
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content)
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(a)
    private fun waitFor(ok:()->Boolean) { val until=System.nanoTime()+5000000000;while(!ok() && System.nanoTime()<until) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(ok()) }
    private fun render(v:View,name:String,w:Int=360,h:Int=800) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val f=java.io.File("build/ui-preview/$name.png");f.parentFile.mkdirs();f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    @Test fun cameraRequestsDisableAutomaticQuarterTurnsAndKeepPortraitSensorMapping() {
        val context=RuntimeEnvironment.getApplication();val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars=ShadowCameraCharacteristics.newCameraCharacteristics()
        shadowOf(chars).set(CameraCharacteristics.SENSOR_ORIENTATION,270)
        shadowOf(chars).set(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES,intArrayOf(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE,CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO))
        shadowOf(manager).addCamera("front",chars)
        var device:CameraDevice?=null
        manager.openCamera("front",object:CameraDevice.StateCallback() { override fun onOpened(c:CameraDevice) { device=c };override fun onDisconnected(c:CameraDevice) {};override fun onError(c:CameraDevice,e:Int) {} },Handler(Looper.getMainLooper()))
        shadowOf(Looper.getMainLooper()).idle();assertNotNull(device)
        val type=Class.forName("com.duorecord.capture.CameraCapabilities");val caps=type.getDeclaredConstructor(CameraManager::class.java).newInstance(manager)
        val request=device!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO)
        type.getDeclaredMethod("configureOrientation",CaptureRequest.Builder::class.java,String::class.java).invoke(caps,request,"front")
        val recorded=org.robolectric.shadow.api.Shadow.extract<RequestShadow>(request).written
        assertEquals(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE,recorded[CaptureRequest.SCALER_ROTATE_AND_CROP])
        val unsupported=ShadowCameraCharacteristics.newCameraCharacteristics();shadowOf(unsupported).set(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES,intArrayOf(CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO));shadowOf(manager).addCamera("unsupported",unsupported)
        request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO)
        type.getDeclaredMethod("configureOrientation",CaptureRequest.Builder::class.java,String::class.java).invoke(caps,request,"unsupported")
        assertEquals(CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO,recorded[CaptureRequest.SCALER_ROTATE_AND_CROP])
        val input=type.getDeclaredMethod("input",String::class.java,CaptureQuality::class.java).invoke(caps,"front",CaptureQuality(720,1280,2000000))
        val size=input.javaClass.getDeclaredMethod("getSize").invoke(input) as android.util.Size
        assertEquals(1280,size.width);assertEquals(720,size.height);assertEquals(270,input.javaClass.getDeclaredMethod("getRotation").invoke(input))
        device!!.close()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun thermometerTogglesUnitsAndBothNumberFramesAreCentered() {
        val context=RuntimeEnvironment.getApplication();val h=DeviceHealth(73,40.0,false,"Good",0,10000000000L,false,false)
        val battery=HealthReading(context,h,0);render(battery,"battery-centered-26",340,160)
        val temp=HealthReading(context,h,1);render(temp,"temperature-round-26",340,160)
        assertEquals(170,battery.value.left+battery.value.width/2);assertEquals(170,temp.value.left+temp.value.width/2)
        assertEquals("40.0°C",temp.value.text.toString());temp.value.performClick();assertEquals("104.0°F",temp.value.text.toString())
        temp.bind(h.copy(batteryTemperatureC=30.0));assertEquals("86.0°F",temp.value.text.toString());temp.value.performClick();assertEquals("30.0°C",temp.value.text.toString())
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun archiveHeaderSquaresAndClipboardContainEverySelectedFile() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();call(a,"showArchive");render(root(a),"archive-square-header-26")
        val header=views(root(a)).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.REFRESH }.parent as ViewGroup
        val icons=(0 until header.childCount).map { header.getChildAt(it) }.filterIsInstance<SignButton>().filter { it.icon!=null }
        assertEquals(6,icons.size);assertTrue(icons.all { it.width==40 && it.height==40 });assertTrue(icons.any { it.icon==HeaderIcon.SELECT })
        fun clip(id:String)=Clip(id,"content://media/external/video/media/$id",id,0,60000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.FRONT)
        val files=listOf(clip("1"),clip("2"))
        MainActivity::class.java.getDeclaredMethod("copyFiles",List::class.java).apply { isAccessible=true }.invoke(a,files)
        val data=(a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
        assertEquals(2,data.itemCount);assertEquals(files[1].uri,data.getItemAt(1).uri.toString())
        MainActivity::class.java.getDeclaredMethod("shareFiles",List::class.java).apply { isAccessible=true }.invoke(a,files)
        val chooser=shadowOf(a).nextStartedActivity;assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE,send.action);assertEquals(2,send.clipData!!.itemCount);assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun batteryPowerMetersPrecedeFormulaInsideOnePanelAndTitlesHaveColons() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("showDeviceStatus",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,0)
        val body=root(a);render(body,"battery-combined-power-26")
        val formula=views(body).single { it.tag=="power-formula-strip" };val panel=formula.parent as ViewGroup
        val meters=views(panel).filterIsInstance<AnalogPowerView>();assertEquals(3,meters.size);assertTrue(meters.none { it.showUnitInValue })
        val row=meters.first().parent as View;assertTrue(row.bottom<=formula.top);assertFalse(formula.isClickable)
        views(body).single { it.contentDescription=="Battery electrical details" }.performClick()
        assertTrue(views(root(a)).filterIsInstance<TextView>().any { it.text=="Battery current:" })
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun seekPausesAtFifteenSecondsAndPlayPauseAndNormalRateRemainSeparate() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val clip=Clip("front","content://media/external/video/media/1","front.mp4",0,60000,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.FRONT)
        val repo=Proxy.newProxyInstance(MediaRepository::class.java.classLoader,arrayOf(MediaRepository::class.java)) { _,m,_ -> when(m.name) { "config" -> RecordingConfig();"list" -> listOf(clip);"pin" -> AutoCloseable {};"folderReady" -> true;"storagePath" -> "Movies/Record";"freeBytes" -> 10000000000L;else -> Unit } } as MediaRepository
        var playing=true;var position=0L;var speed=1f
        val factory=object:PlayerFactory { override fun create(context:Context,onError:(String)->Unit)=object:PlayerComponent {
            override val view=FrameLayout(context);override fun open(clip:Clip,companion:Clip?) {}
            override fun positionMs()=position;override fun durationMs()=60000L;override fun seekTo(positionMs:Long) { position=positionMs }
            override fun isPlaying()=playing;override fun play() { playing=true };override fun pause() { playing=false };override fun release() {}
            override fun setSpeed(v:Float) { speed=v };override fun speed()=speed
        } }
        fun inject(name:String,v:Any) { DuoApp::class.java.getDeclaredField(name+"\$delegate").apply { isAccessible=true }.set(app,lazy { v }) }
        inject("repository",repo);inject("player",factory)
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        MainActivity::class.java.getDeclaredMethod("openPlayer",Clip::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,clip,false)
        waitFor { views(root(a)).any { it.contentDescription=="Playback position" } };val body=root(a);render(body,"playback-unified-26")
        val seek=views(body).filterIsInstance<SeekBar>().single();val listener=shadowOf(seek).onSeekBarChangeListener
        listener.onStartTrackingTouch(seek);listener.onProgressChanged(seek,250,true);seek.progress=250;listener.onStopTrackingTouch(seek)
        assertFalse(playing);assertEquals(15000L,position)
        views(body).single { it.contentDescription=="Play playback" }.performClick();assertTrue(playing);assertEquals(15000L,position)
        views(body).single { it.contentDescription=="Pause playback" }.performClick();assertFalse(playing)
        val nav=views(body).single { it.contentDescription=="Media navigation" } as ViewGroup
        assertEquals(listOf(HeaderIcon.PREVIOUS_ALL,HeaderIcon.PREVIOUS,HeaderIcon.PLAY,HeaderIcon.STOP,HeaderIcon.NEXT,HeaderIcon.NEXT_ALL),(0 until nav.childCount).map { (nav.getChildAt(it) as SignButton).icon })
        val fileRow=views(body).single { it.contentDescription=="Media file actions" } as ViewGroup;assertEquals(7,fileRow.childCount)
        views(body).filterIsInstance<QualityRuler>().single().changed!!.invoke(0,true);assertEquals(0f,speed,0f)
        views(body).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.NORMAL_SPEED }.performClick();assertEquals(1f,speed,0f)
        position=clip.durationMs;playing=false
        views(body).single { it.contentDescription=="Play playback" }.performClick()
        assertEquals(0L,position);assertTrue(playing)
        c.pause().stop().destroy()
    }
}
