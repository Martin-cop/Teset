package com.duorecord.app.ui

import android.content.Intent
import android.os.Looper
import android.view.*
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.duorecord.domain.ScheduleDefaults

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class NavigationPagesTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    private fun button(root:View,text:String)=views(root).filterIsInstance<Button>().single { it.text==text }
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(condition())
    }
    @Test fun folderUsesNormalBrowseViewInsteadOfTreePicker() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val browse=Intent(Intent.ACTION_VIEW).setDataAndType(android.provider.DocumentsContract.buildRootUri("com.android.externalstorage.documents","primary"),"vnd.android.document/root")
        val info=android.content.pm.ResolveInfo().apply { activityInfo=android.content.pm.ActivityInfo().apply {
            packageName="com.android.documentsui";name="FilesActivity";exported=true;enabled=true
            applicationInfo=android.content.pm.ApplicationInfo().apply { flags=android.content.pm.ApplicationInfo.FLAG_SYSTEM }
        } }
        shadowOf(a.packageManager).addResolveInfoForIntent(browse,info)
        button(a.findViewById(android.R.id.content),"Open recording folder").performClick()
        val intent=shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW,intent.action)
        assertEquals("vnd.android.document/root",intent.type)
        assertTrue(intent.getParcelableExtra<android.net.Uri>(android.provider.DocumentsContract.EXTRA_INITIAL_URI)!!.toString().contains("Movies%2FRecord"))
        assertNotEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,intent.action)
        assertEquals(android.content.ComponentName("com.android.documentsui","FilesActivity"),intent.component)
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun scheduleIsIndependentPageWithWholeHourDefaultsAndQualityRulers() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val expected=ScheduleDefaults.nextHour(LocalDateTime.now())
        button(a.findViewById(android.R.id.content),"Capture timing").performLongClick()
        val root=a.findViewById<ViewGroup>(android.R.id.content)
        val dates=views(root).filterIsInstance<EditText>().filter { it.contentDescription?.toString()?.startsWith("Local date and time")==true }
        assertEquals(2,dates.size)
        val format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        assertEquals(expected,LocalDateTime.parse(dates[0].text,format));assertFalse(dates.any { it.text.contains('.') })
        waitUntil { views(root).filterIsInstance<QualityRuler>().count { it.contentDescription?.toString()?.startsWith("Scheduled")==true }==2 }
        assertEquals(3,views(root).filterIsInstance<QualityRuler>().size)
        assertEquals(0,views(root).filterIsInstance<Spinner>().size)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)
        val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
        val out=java.io.File("build/ui-preview/schedule-page.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        button(root,"Close").performClick()
        assertNotNull(button(a.findViewById(android.R.id.content),"Record"))
        c.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun healthBadgesOpenGroupedReadingsAndDistinctConciseGuidesWithoutThermalIcon() {
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val titles=listOf("Battery","Storage")
        val guides=mutableSetOf<String>()
        for(index in titles.indices) {
            val root=a.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)
            val health=views(root).filterIsInstance<HealthBadges>().single()
            assertEquals(listOf(0,3),health.badgeIndices)
            fun event(action:Int) { val e=MotionEvent.obtain(0,0,action,health.width*(index+.5f)/health.badgeIndices.size,24f,0);health.dispatchTouchEvent(e);e.recycle() }
            event(MotionEvent.ACTION_DOWN);event(MotionEvent.ACTION_UP)
            val details=a.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(views(details).filterIsInstance<TextView>().any { it.text==titles[index] })
            val gauges=views(details).filterIsInstance<HealthGauge>()
            assertEquals(2,gauges.size)
            gauges.forEach { gauge ->
                val reading=gauge.parent as HealthReading
                val box=reading.parent as GlowPanel
                assertTrue(box.tag.toString().startsWith("health-reading-"))
                assertTrue(reading.value.text.isNotEmpty())
            }
            val panel=views(details).filterIsInstance<GlowPanel>().last()
            assertEquals(panel.paddingLeft,panel.paddingTop);assertEquals(panel.paddingLeft,panel.paddingRight);assertEquals(panel.paddingLeft,panel.paddingBottom)
            details.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));details.layout(0,0,360,800)
            val bitmap=android.graphics.Bitmap.createBitmap(360,800,android.graphics.Bitmap.Config.ARGB_8888);details.draw(android.graphics.Canvas(bitmap))
            val file=java.io.File("build/ui-preview/health-$index.png");file.parentFile!!.mkdirs();file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            button(details,"Guide").performClick()
            val guide=org.robolectric.shadows.ShadowDialog.getLatestDialog() as NeonDialog
            val guideText=views(guide.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text }
            assertTrue(guideText.length<800);assertTrue(guides.add(guideText))
            assertTrue(guideText.contains(titles[index]+" guide"))
            button(guide.window!!.decorView,"Close").performClick()
            button(details,"Close").performClick()
        }
        c.pause().stop().destroy()
    }
}
