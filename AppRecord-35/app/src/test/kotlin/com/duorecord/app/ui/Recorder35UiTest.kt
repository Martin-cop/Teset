package com.duorecord.app.ui

import android.graphics.*
import android.view.View
import android.widget.TextView
import com.duorecord.app.DeviceHealth
import com.duorecord.components.HistoryChartView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder35UiTest {
 private fun layout(v:View,h:Int=200) { v.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,340,h) }
 @GraphicsMode(GraphicsMode.Mode.NATIVE)
 @Test fun unitsSitBesideTheirNumbersAndBodyGapsMatchWithRightmostCable() {
  val c=RuntimeEnvironment.getApplication();val h=DeviceHealth(73,37.5,true,"Good",0,null,false,false,batteryVoltageV=3.8,estimatedCapacityMah=5000.0)
  val battery=HealthReading(c,h,0);val thermometer=HealthReading(c,h,1);layout(battery);layout(thermometer)
  assertEquals(24,battery.value.top);assertEquals(battery.value.top,thermometer.value.top)
  val bb=HealthGauge.bounds(c,340,200,true,0);val tb=HealthGauge.bounds(c,340,200,true,1)
  assertEquals(bb.top-battery.value.bottom,HealthGauge.lineTop(tb,1)-thermometer.value.bottom,.1f)
  val u=bb.height()/14;val step=(bb.width()-6*u)/10
  assertEquals(bb.left+2*u+9*step+(step-.6f*u)/2,battery.gauge.chargerRect!!.centerX(),1f)
  val texts=(0 until battery.childCount).map { battery.getChildAt(it) }.filterIsInstance<TextView>()
  val cap=texts.single { it.tag=="battery-capacity" };val capUnit=texts.single { it.tag=="capacity-unit" }
  assertEquals(cap.right,capUnit.left);assertTrue(capUnit.top>cap.top);assertTrue(capUnit.bottom<cap.bottom)
  assertTrue(kotlin.math.abs((cap.left-12)-((battery.value.left-6)-capUnit.right))<=1)
  battery.value.performClick();layout(battery);val energyUnit=texts.single { it.tag=="energy-unit" }
  assertEquals("13.9",battery.value.text.toString());assertEquals(View.VISIBLE,energyUnit.visibility);assertEquals(capUnit.top,energyUnit.top)
  val bitmap=Bitmap.createBitmap(340,420,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);battery.draw(canvas);canvas.translate(0f,220f);thermometer.draw(canvas)
  val out=java.io.File("build/ui-preview/battery-thermometer-35.png");out.parentFile!!.mkdirs();out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
 }
 @GraphicsMode(GraphicsMode.Mode.NATIVE)
 @Test fun signedHistoryColumnsHavePositiveAndNegativeFillAndMissingSamplesLeaveGaps() {
  val c=RuntimeEnvironment.getApplication();val chart=HistoryChartView(c).apply { points=listOf(1000L to 2.0,2000L to null,3000L to -1.0,4000L to 3.0);unit="A" };layout(chart,240)
  val bitmap=Bitmap.createBitmap(340,240,Bitmap.Config.ARGB_8888);chart.draw(Canvas(bitmap))
  var green=0;var red=0
  for(x in 50..319)for(y in 20..199) { val color=bitmap.getPixel(x,y);if(color==0xff65ff99.toInt())green++;if(color==0xffff6565.toInt())red++ }
  assertTrue(green>30);assertTrue(red>30)
  assertEquals(0xff080c10.toInt(),bitmap.getPixel(142,100))
  val out=java.io.File("build/ui-preview/power-columns-35.png");out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
 }
}
