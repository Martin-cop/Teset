package com.duorecord.app.ui

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.duorecord.app.*
import com.duorecord.contracts.CameraCaptureStatus
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class CameraUiTest {
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
    private fun button(root:View,text:String)=descendants(root).filterIsInstance<Button>().first { it.text.toString()==text }
    private fun render(view:View,name:String,width:Int=360,height:Int=800) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
        val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File("build/ui-preview/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun renderDialog(dialog:android.app.Dialog,name:String) {
        val content=dialog.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        content.measure(View.MeasureSpec.makeMeasureSpec(348,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(760,View.MeasureSpec.AT_MOST))
        render(content,name,348,content.measuredHeight.coerceAtLeast(1))
    }
    private fun waitFor(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue("Condition timed out",condition())
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun camerasStaySideBySideWithControlsAboveAndPlaybackBelow() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800)
        val front=button(root,"Select Front");val rear=button(root,"Select Rear")
        val fp=front.parent as CameraPanel;val rp=rear.parent as CameraPanel
        assertSame(fp.parent,rp.parent)
        assertEquals(fp.width,rp.width)
        assertTrue(fp.left<rp.left)
        val light=descendants(root).filterIsInstance<Button>().single { it.contentDescription?.toString()?.startsWith("Recording light,")==true }
        assertTrue(light.isEnabled);assertFalse(light.isSelected)
        light.performClick();assertTrue(light.isSelected);assertTrue((activity.application as DuoApp).lightForNextRecording)
        render(root,"light-preselected")
        light.performClick();assertFalse(light.isSelected);assertFalse((activity.application as DuoApp).lightForNextRecording)
        val lightBounds=android.graphics.Rect();light.getGlobalVisibleRect(lightBounds)
        val frontBounds=android.graphics.Rect();front.getGlobalVisibleRect(frontBounds)
        val rearBounds=android.graphics.Rect();rear.getGlobalVisibleRect(rearBounds)
        assertTrue(frontBounds.right<=lightBounds.left);assertTrue(lightBounds.right<=rearBounds.left)
        assertEquals(40,light.width);assertEquals(frontBounds.top,lightBounds.top)
        assertNotEquals(View.LAYER_TYPE_SOFTWARE,((fp.parent as View).parent as View).layerType)
        val both=button(root,"Record")
        val shortcuts=listOf("Recordings","Open recording folder","Export by time","Capture timing","Share latest capture","Mark important moment").map { button(root,it) as SignButton }
        assertEquals(6,shortcuts.size)
        assertEquals(1,shortcuts.map { it.parent }.distinct().size)
        assertTrue(shortcuts.all { it.width>=48 && it.height==56 && it.icon!=null && (it.contentDescription==it.text || it.icon==HeaderIcon.BOOKMARK) && (it.tooltipText==it.text || it.icon==HeaderIcon.SCHEDULE) })
        assertTrue(shortcuts.zipWithNext().all { (left,right) -> left.top==right.top && right.left-left.right==4 })
        assertSame(((fp.parent as View).parent as View).parent,(both.parent as View).parent)
        assertTrue((both.parent as View).width>fp.width)
        for(panel in listOf(fp,rp)) {
            val views=descendants(panel)
            val seek=views.filterIsInstance<QualityRuler>().single()
            val texture=views.filterIsInstance<android.view.TextureView>().single()
            assertTrue(panel.record.bottom<=panel.monitor.top)
            assertTrue((seek.parent as View).top>=panel.monitor.bottom)
            assertFalse(views.filterIsInstance<Button>().any { it.text.toString().startsWith("Play last") })
        }
        render(root,"home")
        both.isPressed=true;render(root,"pressed");both.isPressed=false
        val app=activity.application as DuoApp
        app.status.value=RuntimeStatus(message="Camera permission denied.\nOpen system settings to allow camera access.",error=true)
        shadowOf(Looper.getMainLooper()).idle();render(root,"error")
        assertEquals(Palette.red,descendants(root).filterIsInstance<PulseStatusLabel>().first().currentTextColor)
        app.status.value=RuntimeStatus(phase=Phase.STARTING,message="Starting cameras…\nChecking recording quality")
        shadowOf(Looper.getMainLooper()).idle();render(root,"processing")
        app.status.value=RuntimeStatus(phase=Phase.RECORDING)
        app.torch.value=com.duorecord.contracts.TorchStatus(enabled=true,available=true)
        shadowOf(Looper.getMainLooper()).idle();assertTrue(light.isEnabled);assertTrue(light.isSelected);render(root,"light-on")
        app.torch.value=com.duorecord.contracts.TorchStatus(enabled=false,available=true)
        shadowOf(Looper.getMainLooper()).idle();assertFalse(light.isSelected);assertTrue(light.isEnabled)
        app.torch.value=com.duorecord.contracts.TorchStatus(available=true,pending=true)
        shadowOf(Looper.getMainLooper()).idle();assertFalse(light.isEnabled)
        app.torch.value=com.duorecord.contracts.TorchStatus()
        app.status.value=RuntimeStatus();shadowOf(Looper.getMainLooper()).idle()
        button(root,"Guide").performClick()
        val guide=ShadowDialog.getLatestDialog()
        assertTrue(descendants(guide.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("Record/Pause/Resume, Stop") })
        renderDialog(guide,"guide");guide.dismiss()
        button(root,"Info & tools").performClick()
        button(controller.get().findViewById(android.R.id.content),"About").performClick()
        val about=ShadowDialog.getLatestDialog()
        val aboutText=descendants(about.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text }
        assertTrue(aboutText.contains("Developer: Morteza"));assertTrue(aboutText.contains("rassoly@gmail.com"))
        assertFalse(aboutText.contains("Update before"));renderDialog(about,"about");button(about.window!!.decorView,"Close").performClick()
        controller.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun whiteScreenLampKeepsOnlyFrontMonitorAndRestoresBrightnessOnBackAndStop() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        render(root,"screen-light-off")
        val front=descendants(root).filterIsInstance<CameraPanel>().first()
        val monitor=front.monitor;val originalParent=monitor.parent
        val brightness=activity.window.attributes.screenBrightness
        button(root,"Front screen light").performClick()
        assertSame(originalParent,monitor.parent);assertEquals(brightness,activity.window.attributes.screenBrightness,0f)
        val app=activity.application as DuoApp
        app.status.value=RuntimeStatus(phase=Phase.RECORDING,cameras=mapOf(CameraSource.FRONT to com.duorecord.contracts.CameraCaptureStatus(recording=true)))
        shadowOf(Looper.getMainLooper()).idle()
        render(root,"screen-light-on")
        assertNotSame(originalParent,monitor.parent);assertEquals(1f,activity.window.attributes.screenBrightness,0f)
        val overlay=monitor.parent as ViewGroup
        assertEquals(2,overlay.childCount)
        val off=descendants(overlay).single { it.contentDescription=="Turn off screen light" };off.performClick()
        assertSame(originalParent,monitor.parent);assertEquals(brightness,activity.window.attributes.screenBrightness,0f)
        assertFalse(app.screenLightWanted)
        button(root,"Front screen light").performClick()
        activity.onBackPressed();assertSame(originalParent,monitor.parent);assertEquals(brightness,activity.window.attributes.screenBrightness,0f)
        button(root,"Front screen light").performClick();controller.pause().stop()
        assertSame(originalParent,monitor.parent);assertEquals(brightness,activity.window.attributes.screenBrightness,0f)
        controller.destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun healthGaugesRepresentChargeTemperatureAndUsedStorageInTenSteps() {
        val context=RuntimeEnvironment.getApplication()
        val health=DeviceHealth(40,36.0,false,"Good",0,4L*1073741824,false,false,8L*1073741824)
        assertEquals(4,HealthGauge.bars(health,0));assertEquals(6,HealthGauge.bars(health,1));assertEquals(5,HealthGauge.bars(health,3))
        assertEquals(10,HealthGauge.bars(health.copy(batteryPercent=100),0));assertEquals(0,HealthGauge.bars(health.copy(batteryPercent=0),0))
        assertEquals(2,HealthGauge.bars(health.copy(freeBytes=8L*1073741824,totalBytes=10L*1073741824),3))
        assertTrue(HealthGauge.value(health.copy(freeBytes=8L*1073741824,totalBytes=10L*1073741824),3).startsWith("20% used"))
        assertEquals(0,HealthGauge.bars(health.copy(totalBytes=null),3));assertEquals("—",HealthGauge.value(health.copy(totalBytes=null),3))
        assertEquals(listOf(0,4,3),HealthBadges(context).apply { this.health=health.copy(cpuTemperatureC=41.5) }.badgeIndices)
        render(HealthBadges(context).apply { this.health=health },"health-colored-40",232,48)
        val reading=HealthReading(context,health,0)
        render(reading,"battery-framed-number",340,160)
        assertEquals(reading.value.width,reading.value.measuredWidth)
        assertEquals("40%",reading.value.text.toString())
        val thermometer=HealthReading(context,health,1)
        render(thermometer,"temperature-framed-number",340,160)
        assertEquals(reading.value.width,thermometer.value.width);assertEquals(reading.value.height,thermometer.value.height)
        render(HealthReading(context,health.copy(charging=true,chargingActive=true),0),"battery-charger-connected",340,160)
        render(HealthGauge(context,health,0),"battery-gauge-large",340,110)
        render(HealthGauge(context,health,1),"temperature-gauge-large",340,110)
        render(HealthGauge(context,health,3),"storage-barrel-large",340,110)
        render(HealthGauge(context,health.copy(rollingBytes=4L*1073741824),5),"quota-barrel-large",340,110)
        assertFalse(HealthGauge.value(health.copy(batteryTemperatureC=37.9),0,compact=true).contains('.'))
        assertEquals("25% used\n20.0 MB / 80.0 MB",HealthGauge.value(health.copy(rollingBytes=20L*1048576,rollingLimitBytes=80L*1048576),5))
        val image=android.graphics.BitmapFactory.decodeFile("build/ui-preview/battery-gauge-large.png")
        assertEquals(Palette.mint,image.getPixel(38,55))
        assertEquals(0xff492828.toInt(),image.getPixel(235,55))
        assertEquals(0xffffd52a.toInt(),image.getPixel(325,55));image.recycle()
    }
    @Test fun photoModeSavesTypedIntervalAndUpdatesHomeControl() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val app=controller.get().application as DuoApp
        button(controller.get().findViewById(android.R.id.content),"Photo mode").performClick()
        waitFor { descendants(controller.get().findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text=="Photo · 10s" } }
        button(controller.get().findViewById(android.R.id.content),"Photo · 10s").performClick()
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single { it.contentDescription=="Photo interval seconds" }.setText("7")
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        waitFor { app.repository!!.config().photoIntervalSeconds==7 && descendants(controller.get().findViewById(android.R.id.content)).filterIsInstance<Button>().any { it.text=="Photo · 7s" } }
        assertTrue(app.repository!!.config().photoMode)
        controller.pause().stop().destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun doubleTapExpandsEachLiveMonitorAndSecondDoubleTapRestoresIt() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        render(root,"home-compact-four-shortcuts")
        val panels=descendants(root).filterIsInstance<CameraPanel>()
        fun doubleTap(view:View,time:Long) {
            for((action,offset) in listOf(android.view.MotionEvent.ACTION_DOWN to 0L,android.view.MotionEvent.ACTION_UP to 20L,android.view.MotionEvent.ACTION_DOWN to 120L,android.view.MotionEvent.ACTION_UP to 140L)) {
                val event=android.view.MotionEvent.obtain(time+offset,time+offset,action,view.width/2f,view.height/2f,0)
                view.dispatchTouchEvent(event);event.recycle()
            }
        }
        for((index,panel) in panels.withIndex()) {
            val parent=panel.monitor.parent;val indexBefore=panel.indexOfChild(panel.monitor)
            doubleTap(panel.monitor,1000L+index*3000)
            render(root,"monitor-fullscreen-$index")
            assertNotSame(parent,panel.monitor.parent);assertEquals(360,panel.monitor.width);assertEquals(800,panel.monitor.height)
            doubleTap(panel.monitor,2000L+index*3000)
            render(root,"monitor-restored-$index")
            assertSame(parent,panel.monitor.parent);assertEquals(indexBefore,panel.indexOfChild(panel.monitor))
        }
        doubleTap(panels[1].monitor,8000)
        activity.onBackPressed();assertSame(panels[1],panels[1].monitor.parent)
        controller.pause().stop().destroy()
    }
    @Test fun settingsExplanationsAreAvailableThroughItsHeaderGuide() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        button(controller.get().findViewById(android.R.id.content),"Settings").performClick()
        waitFor { ShadowDialog.getLatestDialog()?.isShowing==true }
        val settings=ShadowDialog.getLatestDialog() as NeonDialog
        val text=descendants(settings.window!!.decorView).filterIsInstance<TextView>().joinToString { it.text }
        assertFalse(text.contains("Stop duration:"));assertFalse(text.contains("FPS means"))
        button(settings.window!!.decorView,"Guide").performClick()
        val guide=ShadowDialog.getLatestDialog()
        assertTrue(descendants(guide.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("FPS means video frames per second") && it.text.contains("RECOVERY & CLOSE") })
        guide.dismiss();settings.dismiss();controller.pause().stop().destroy()
    }
    @Test fun buttonAndQualityOverlayShowActualRecordingState() {
        val app=(RuntimeEnvironment.getApplication() as DuoApp)
        val panel=CameraPanel(app,"Front",{}, {}, {}, {})
        panel.qualities(listOf(CaptureQuality(240,320,128000),CaptureQuality(720,1280,2000000)),"720x1280")
        panel.render(CameraCaptureStatus(recording=true,quality=CaptureQuality(240,320,128000),fps=15,startedMs=System.currentTimeMillis()),true)
        assertTrue(panel.record.isSelected)
        assertEquals("Stop Front",panel.record.text.toString())
        val overlays=descendants(panel).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(overlays.any { it.contains("● REC") })
        assertEquals(1,overlays.count { it.contains("240 × 320") })
        panel.render(CameraCaptureStatus(),true)
        assertFalse(panel.record.isSelected)
        assertEquals("Select Front",panel.record.text.toString())
        panel.dispose()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun settingsUseEditableNumbersAndKeepDialogOpenForInvalidInput() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val root=controller.get().findViewById<ViewGroup>(android.R.id.content)
        button(root,"Settings").performClick()
        waitFor { ShadowDialog.getLatestDialog()?.isShowing==true }
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        val fields=descendants(dialog.window!!.decorView).filterIsInstance<EditText>()
        assertEquals(9,fields.size)
        renderDialog(dialog,"settings")
        assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>().all { it.clipChildren })
        val scroll=descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>().single()
        scroll.fullScroll(View.FOCUS_DOWN);renderDialog(dialog,"settings-bottom")
        scroll.scrollTo(0,0)
        assertTrue(fields.all { it.inputType and android.text.InputType.TYPE_MASK_CLASS==android.text.InputType.TYPE_CLASS_NUMBER })
        fields[0].setText("0")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("10 seconds") })
        val units=descendants(dialog.window!!.decorView).filterIsInstance<Spinner>().single { it.contentDescription=="File duration unit" }
        units.setSelection(0);shadowOf(Looper.getMainLooper()).idle()
        fields[0].setText("۷۳")
        fields[4].setText("15")
        val simple=descendants(dialog.window!!.decorView).filterIsInstance<Switch>().first { it.text=="Minimal appearance" }
        simple.isChecked=true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitFor { !dialog.isShowing }
        val config=(RuntimeEnvironment.getApplication() as DuoApp).repository!!.config()
        assertEquals(73,config.segmentSeconds)
        assertTrue(config.simpleTheme)
        assertNotNull(button(controller.get().findViewById(android.R.id.content),"Select Front"))
        controller.pause().stop().destroy()
    }
    @Test fun settingsStayOpenDuringRecordingAndOnlyAppearanceAndArchiveOptionsCanChange() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val app=controller.get().application as DuoApp
        val original=app.repository!!.config()
        app.status.value=RuntimeStatus(phase=Phase.RECORDING,message="Recording",startedMs=System.currentTimeMillis())
        shadowOf(Looper.getMainLooper()).idle()
        button(controller.get().findViewById(android.R.id.content),"Settings").performClick()
        waitFor { ShadowDialog.getLatestDialog()?.isShowing==true }
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        val views=descendants(dialog.window!!.decorView)
        assertTrue(views.filterIsInstance<EditText>().filter { it.contentDescription !in setOf("Refresh · sec","Initial files") }.all { !it.isEnabled })
        val archive=views.filterIsInstance<EditText>().filter { it.contentDescription in setOf("Refresh · sec","Initial files") };assertTrue(archive.all { it.isEnabled })
        archive.first { it.contentDescription=="Initial files" }.setText("70")
        val switches=views.filterIsInstance<NeonSwitch>()
        assertFalse(switches.first { it.text=="H.265 · Smaller files" }.isEnabled)
        val appearance=switches.first { it.text=="Minimal appearance" }
        assertTrue(appearance.isEnabled);appearance.isChecked=true
        // A capture preparation already in flight may have saved a newer quality after opening settings.
        app.repository!!.saveConfig(original.copy(frontQuality="720x1280"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitFor { !dialog.isShowing }
        assertEquals("720x1280",app.repository!!.config().frontQuality)
        assertEquals(original.fps,app.repository!!.config().fps)
        assertEquals(70,app.repository!!.config().archiveInitialLimit)
        assertTrue(app.repository!!.config().simpleTheme)
        assertEquals(Phase.RECORDING,app.status.value.phase)
        controller.pause().stop().destroy()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun cameraDropdownPreservesSelectionAndClosesAfterChoosing() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        val spinner=NeonSpinner(activity)
        val body=activity.column().apply { addWide(activity.spinnerRow("Camera",spinner,listOf("REAR","FRONT","BOTH"),1),48) }
        val dialog=NeonDialog.Builder(activity).setTitle("Export camera").setView(body).setNegativeButton("Back",null).show()
        renderDialog(dialog,"dropdown-closed")
        assertTrue(spinner.performClick());shadowOf(Looper.getMainLooper()).idle()
        val popup=shadowOf(activity.application).latestListPopupWindow
        assertTrue(popup.isShowing)
        val list=requireNotNull(popup.listView)
        assertEquals(3,list.adapter.count)
        val window=shadowOf(activity.application).latestPopupWindow
        val content=window.contentView
        val width=window.width.coerceAtLeast(160)
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST))
        render(content,"dropdown-open",width,content.measuredHeight.coerceAtLeast(1))
        val first=list.adapter.getView(0,null,list) as ViewGroup
        first.getChildAt(0).performClick()
        assertEquals(0,spinner.selectedItemPosition);assertFalse(popup.isShowing)
        dialog.dismiss();controller.pause().stop().destroy()
    }

    @Test fun rulerCommitsSupportedStepsAndTrimHandlesCannotCross() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val ruler=QualityRuler(app).apply { steps=3 }
        ruler.measure(View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(36,View.MeasureSpec.EXACTLY));ruler.layout(0,0,180,36)
        val commits=mutableListOf<Int>();ruler.changed={ index,commit -> if(commit)commits+=index }
        fun touch(view:View,action:Int,x:Float) { val event=android.view.MotionEvent.obtain(0,10,action,x,20f,0);view.onTouchEvent(event);event.recycle() }
        // Attach to a parent because drags reserve the gesture from the scrolling container.
        val parent=LinearLayout(app);parent.addView(ruler)
        touch(ruler,android.view.MotionEvent.ACTION_DOWN,170f);touch(ruler,android.view.MotionEvent.ACTION_UP,170f)
        assertEquals(2,ruler.selected);assertEquals(listOf(2),commits)
        val timeline=TrimTimeline(app,10_000);parent.addView(timeline)
        timeline.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(76,View.MeasureSpec.EXACTLY));timeline.layout(0,0,200,76)
        touch(timeline,android.view.MotionEvent.ACTION_DOWN,16f);touch(timeline,android.view.MotionEvent.ACTION_MOVE,190f);touch(timeline,android.view.MotionEvent.ACTION_UP,190f)
        assertTrue(timeline.from<timeline.to);assertTrue(timeline.to-timeline.from>=250)
        touch(timeline,android.view.MotionEvent.ACTION_DOWN,184f);touch(timeline,android.view.MotionEvent.ACTION_MOVE,0f);touch(timeline,android.view.MotionEvent.ACTION_UP,0f)
        assertTrue(timeline.from>=0);assertTrue(timeline.to<=10_000);assertTrue(timeline.from<timeline.to)
    }
    @Test fun resetRestoresDurationAndCancelsSchedule() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get();val app=activity.application as DuoApp
        app.repository!!.saveConfig(RecordingConfig(maxRecordingMinutes=7,segmentSeconds=33,simpleTheme=true))
        activity.getSharedPreferences("automation",0).edit().putBoolean("armed",true).putLong("start",System.currentTimeMillis()+3_600_000).putLong("end",System.currentTimeMillis()+7_200_000).apply()
        button(activity.findViewById(android.R.id.content),"Settings").performClick()
        waitFor { ShadowDialog.getLatestDialog()?.isShowing==true }
        val dialog=ShadowDialog.getLatestDialog() as NeonDialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();waitFor { !dialog.isShowing }
        assertEquals(RecordingConfig(),app.repository!!.config())
        assertFalse(activity.getSharedPreferences("automation",0).getBoolean("armed",true))
        controller.pause().stop().destroy()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun compactHeaderShowsDeviceReadingsAndDetailsAreInsideNeonPanel() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get();val root=activity.findViewById<ViewGroup>(android.R.id.content)
        render(root,"home-health")
        val guide=button(root,"Guide");val about=button(root,"Info & tools");val settings=button(root,"Settings")
        assertEquals(52,guide.width);assertEquals(52,guide.height)
        assertEquals(guide.right+2,about.left);assertEquals(about.right+2,settings.left)
        val health=descendants(root).filterIsInstance<TextView>().first { it.contentDescription?.toString()?.startsWith("Battery ")==true }
        assertTrue(health.text.contains("Battery"));assertTrue(health.text.contains("Heat"));assertTrue(health.text.contains("Free"))
        assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text=="Recording details" })
        assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text=="DuoRecord" })
        assertEquals(48,health.height)
        assertFalse(((guide.parent as View).parent as View).layerType==View.LAYER_TYPE_SOFTWARE)
        health.performClick()
        val text=descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>().joinToString { it.text }
        assertFalse(text.contains("Battery temperature"));assertFalse(text.contains("Battery health"))
        assertTrue(text.contains(" V × "))
        render(activity.findViewById(android.R.id.content),"device-status")
        controller.pause().stop().destroy()
    }

}
