package com.duorecord.app.ui

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import com.duorecord.app.*
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
class MixedFloatingControlsTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun floatingControlsUseSmallNonModalWindowsAndLiveCameraFramesAndDisappearInHome() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        ShadowSettings.setCanDrawOverlays(true);app.uiVisible.value=false
        var config=MixedSetup.presentation(RecordingConfig())
        app.repository!!.saveConfig(config)
        val floating=MixedFloatingControls(app,{ config },{ config=it },{})
        val wm=app.getSystemService(WindowManager::class.java);val windows=Shadow.extract<ShadowWindowManagerImpl>(wm)
        floating.show();shadowOf(Looper.getMainLooper()).idle()
        var attached=windows.views.toList()
        assertEquals(4,attached.size)
        val inset=attached.single { it.tag=="live-inset-FRONT" }
        assertEquals(1,views(inset).filterIsInstance<TextureView>().size)
        assertTrue(views(inset).filterIsInstance<SignButton>().isEmpty())
        assertEquals(0,(inset.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE)
        assertTrue(attached.filter { it!==inset }.all { (it.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE!=0 })
        assertTrue(attached.all { v -> val p=v.layoutParams as WindowManager.LayoutParams;p.width>0 && p.height>0 && p.width<360 && p.height<800 && p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL!=0 && p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0 })
        val toolbar=attached.single { it.tag=="floating-toolbar" }
        assertEquals(248,toolbar.layoutParams.width);assertEquals(56,toolbar.layoutParams.height)
        assertEquals(2,views(toolbar).filterIsInstance<SignButton>().size)
        assertTrue(views(toolbar).any { it.tag=="floating-counter" })
        config=MixedSetup.background(config,CameraSource.REAR);floating.sync(config)
        assertEquals(CameraSource.REAR,config.mixedBackground);assertTrue(config.mixedRear)
        val now=windows.views.toList();assertTrue(now.any { it.tag=="live-inset-SCREEN" })
        assertEquals(0,views(now.single { it.tag=="live-inset-SCREEN" }).filterIsInstance<TextureView>().size)
        floating.setAppVisible(true);shadowOf(Looper.getMainLooper()).idle();assertTrue(windows.views.isEmpty())
        floating.setAppVisible(false);shadowOf(Looper.getMainLooper()).idle();assertTrue(windows.views.any { it.tag=="live-inset-FRONT" })
        val front=windows.views.single { it.tag=="live-inset-FRONT" }
        views(windows.views.single { it.tag=="inset-controls-FRONT" }).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.CLOSE }.performClick()
        assertTrue(config.mixedFront);assertFalse(MixedLayout.read(config.mixedLayout).getValue(CameraSource.FRONT).visible)
        assertFalse(windows.views.any { it.tag=="live-inset-FRONT" })
        floating.hide();assertTrue(windows.views.isEmpty())
    }
    @Test fun screenBackgroundIsDirectlySelectableFromHomeWhenScreenWasOff() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(RecordingConfig(mixedMode=true,mixedScreen=false,mixedRear=true,mixedBackground=CameraSource.REAR))
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        val root=a.findViewById<ViewGroup>(android.R.id.content)
        views(root).filterIsInstance<SignButton>().single { it.contentDescription=="Use SCREEN as background" }.performClick()
        val end=System.nanoTime()+5000000000
        while(app.repository!!.config().mixedBackground!=CameraSource.SCREEN && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
        assertTrue(app.repository!!.config().mixedScreen);assertEquals(CameraSource.SCREEN,app.repository!!.config().mixedBackground)
        assertTrue(views(root).filterIsInstance<SignButton>().single { it.contentDescription=="Include SCREEN source" }.isSelected)
        assertTrue(views(root).filterIsInstance<SignButton>().single { it.contentDescription=="Use SCREEN as background" }.isSelected)
        c.pause().stop().destroy()
    }
}
