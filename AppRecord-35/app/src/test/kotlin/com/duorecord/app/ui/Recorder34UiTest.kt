package com.duorecord.app.ui

import android.view.View
import com.duorecord.app.DeviceHealth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder34UiTest {
 @GraphicsMode(GraphicsMode.Mode.NATIVE)
 @Test fun compactNumbersKeepTheirGreenColorAndCableIsAlignedToSecondCellFromRight() {
  val c=RuntimeEnvironment.getApplication();val h=DeviceHealth(73,37.5,true,"Good",0,10000000000L,false,false,batteryVoltageV=3.8,estimatedCapacityMah=5000.0)
  val row=HealthReading(c,h,0)
  fun layout() { row.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(172,View.MeasureSpec.EXACTLY));row.layout(0,0,340,172) }
  layout();assertEquals(24,row.value.top);val color=row.value.currentTextColor
  row.value.performClick();layout();assertEquals("13.9",row.value.text.toString());assertEquals(color,row.value.currentTextColor)
  val b=HealthGauge.bounds(c,340,172,true);val u=b.height()/14;val step=(b.width()-6*u)/10
  assertEquals(b.left+2*u+9*step+(step-.6f*u)/2,row.gauge.chargerRect!!.centerX(),1f)
  val unit=(0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<android.widget.TextView>().single { it.tag=="energy-unit" };assertEquals("Wh",unit.text.toString());assertEquals(row.value.textSize*.4f,unit.textSize,.01f)
  val t=HealthReading(c,h,1);val before=t.value.currentTextColor;t.value.performClick();assertEquals(before,t.value.currentTextColor)
  val bitmap=android.graphics.Bitmap.createBitmap(340,172,android.graphics.Bitmap.Config.ARGB_8888);row.draw(android.graphics.Canvas(bitmap))
  val out=java.io.File("build/ui-preview/compact-battery-34.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
 }
}
