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
class Recorder25UiTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(a:MainActivity)=a.findViewById<ViewGroup>(android.R.id.content)
    private fun call(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible=true }.invoke(a)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun storedStartLightsBothClocksAndExplicitClearReturnsToImmediate() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.getSharedPreferences("home_start",0).edit().putLong("at",System.currentTimeMillis()+3600000).commit()
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get();call(a,"updateScheduleIcons")
        val clocks=views(root(a)).filterIsInstance<SignButton>().filter { it.icon==HeaderIcon.SCHEDULE };assertEquals(1,clocks.size);assertTrue(clocks.all { it.scheduleArmed })
        assertFalse(views(root(a)).any { it.contentDescription=="Saved schedule" })
        assertTrue(views(root(a)).filterIsInstance<SignButton>().any { it.icon==HeaderIcon.SHARE })
        call(a,"showCaptureTiming")
        views(root(a)).single { it.contentDescription=="Clear scheduled start" }.performClick()
        assertEquals(0L,app.getSharedPreferences("home_start",0).getLong("at",0));assertFalse(app.getSharedPreferences("automation",0).getBoolean("armed",false))
        assertTrue(views(root(a)).filterIsInstance<TextView>().any { it.text=="Start time · Now" })
        c.pause().stop().destroy()
    }
    @Test fun observerFailureAndFloatPcmNeverAlterAudioBuffers() {
        val type=Class.forName("com.duorecord.player.PlaybackMeterProcessor")
        val processor=type.declaredConstructors.single().newInstance({ _:AudioMeter -> throw IllegalStateException("meter unavailable") }) as AudioProcessor
        processor.configure(AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_FLOAT));processor.flush()
        val input=ByteBuffer.allocateDirect(8192).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2048) { input.putFloat(.5f) };input.flip()
        val expected=ByteArray(8192);input.duplicate().get(expected)
        processor.queueInput(input)
        val first=processor.output;val actual=ByteArray(first.remaining());first.get(actual);assertArrayEquals(expected,actual)
        processor.flush()
        val again=ByteBuffer.allocateDirect(8192).order(ByteOrder.LITTLE_ENDIAN).apply { put(expected);flip() }
        processor.queueInput(again);val output=processor.output;val bytes=ByteArray(output.remaining());output.get(bytes)
        assertArrayEquals(expected,bytes);assertEquals(0,again.remaining())
        processor.queueEndOfStream();assertTrue(processor.isEnded)
    }
    @Test fun pausedIntervalsGiveBookmarksTheirActualSavedPlaybackOffset() {
        val context=RuntimeEnvironment.getApplication();val recovery=CaptureRecovery(context)
        recovery.begin(RecordingConfig(),false,0)
        recovery.noteUserPause(true,1000);recovery.noteUserPause(false,6000)
        assertEquals(5000L,recovery.pausedBetween(0,10000));assertEquals(2000L,recovery.pausedBetween(3000,5000))
        val markers=CaptureMarkers(context);val mark=markers.add(0,setOf(CameraSource.FRONT),9000,"same-file",4000)
        assertEquals(mark,CaptureMarkers(context).list().last());assertEquals(4000L,mark.offsetMs)
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun analogMetersAndResolutionRulerUseReadableActualValuesAndWhiteTicks() {
        val context=RuntimeEnvironment.getApplication()
        val body=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff484848.toInt()) }
        val row=LinearLayout(context)
        for((unit,v,scale) in listOf(Triple("V",3.8,5.0),Triple("A",1.2,8.0),Triple("W",4.56,40.0)))row.addView(AnalogPowerView(context).apply { this.unit=unit;value=v;fullScale=scale },LinearLayout.LayoutParams(0,140,1f))
        body.addView(row,LinearLayout.LayoutParams(360,140))
        body.addView(QualityRuler(context).apply { steps=24;selected=6 },LinearLayout.LayoutParams(360,42))
        body.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(182,View.MeasureSpec.EXACTLY));body.layout(0,0,360,182)
        val bitmap=Bitmap.createBitmap(360,182,Bitmap.Config.ARGB_8888);body.draw(Canvas(bitmap))
        assertEquals(Color.BLACK,bitmap.getPixel(180,145));assertEquals(Color.WHITE,bitmap.getPixel(10,161))
        val out=java.io.File("build/ui-preview/analog-power-white-ruler.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
