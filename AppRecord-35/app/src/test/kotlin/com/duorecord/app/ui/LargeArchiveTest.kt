package com.duorecord.app.ui

import android.os.Looper
import android.view.*
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.duorecord.app.DuoApp
import com.duorecord.contracts.MediaRepository
import com.duorecord.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class LargeArchiveTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(test:()->Boolean) { val until=System.nanoTime()+10_000_000_000;while(!test() && System.nanoTime()<until) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() };assertTrue(test()) }
    @Test fun firstOpenUsesConfiguredLimitAndExplicitRefreshLoadsAllWhileBackRemainsUsable() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val files=(0 until 2444).map { n -> Clip("$n","content://files/$n","$n.mp4",n*1000L,500,100,ClipKind.ROLLING,ClipStatus.READY,camera=CameraSource.REAR) }
        val real=app.repository!!;real.saveConfig(real.config().copy(archiveInitialLimit=40,archiveRefreshSeconds=5))
        val repo=object:MediaRepository by real { override fun list()=files;override fun reconcile(activeRecording:Boolean) {};override fun reconcileRecent(activeRecording:Boolean) {} }
        DuoApp::class.java.getDeclaredField("repository\$delegate").apply { isAccessible=true }.set(app,lazy { repo })
        val c=Robolectric.buildActivity(MainActivity::class.java).setup();val a=c.get()
        fun root()=a.findViewById<View>(android.R.id.content)
        views(root()).filterIsInstance<Button>().single { it.text=="Recordings" }.performClick()
        val adapter=views(root()).filterIsInstance<RecyclerView>().single().adapter as ClipListAdapter
        waitFor { adapter.clips.size==40 };assertEquals("2443",adapter.clips.first().id)
        views(root()).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.REFRESH }.performClick()
        waitFor { adapter.clips.size==2444 }
        a.onBackPressed();assertFalse(a.isFinishing);assertFalse(views(root()).any { it is RecyclerView })
        c.pause().stop().destroy()
    }
}
