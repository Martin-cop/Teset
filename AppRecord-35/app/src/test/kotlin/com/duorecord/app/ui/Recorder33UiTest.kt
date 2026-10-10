package com.duorecord.app.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import com.duorecord.app.DuoApp
import com.duorecord.app.DeviceHealth
import com.duorecord.contracts.CameraCaptureStatus
import com.duorecord.app.Phase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class Recorder33UiTest {
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 @Test fun choosingCameraDoesNotStartCaptureAndBorderBelongsOnlyToMonitor() {
  val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
  val panels=views(a.findViewById(android.R.id.content)).filterIsInstance<CameraPanel>()
  val front=panels.first();front.record.performClick()
  assertEquals(Phase.IDLE,(a.application as DuoApp).status.value.phase)
  assertEquals(1,front.record.captureState);assertNull(front.foreground);assertNotNull(front.monitor.foreground)
  front.render(CameraCaptureStatus(recording=true),true);assertEquals(2,front.record.captureState)
  c.pause().stop().destroy()
 }
 @Test fun energyIsOneLineNumberAndItsFrameHasRoomWithoutChangingFont() {
  val h=DeviceHealth(73,37.5,true,"Good",0,10000000000L,false,false,batteryVoltageV=3.8,estimatedCapacityMah=5000.0)
  val reading=HealthReading(RuntimeEnvironment.getApplication(),h,0)
  reading.value.performClick();assertEquals("13.9",reading.value.text.toString());assertEquals(1,reading.value.maxLines)
  reading.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(252,View.MeasureSpec.EXACTLY));reading.layout(0,0,340,252)
  assertTrue(reading.value.width>reading.value.paint.measureText("13.9"));assertEquals(reading.value.width-reading.value.paddingLeft-reading.value.paddingRight,reading.value.layout.width)
 }
}
