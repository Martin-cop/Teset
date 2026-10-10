package com.duorecord.app.ui

import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorecord.app.DuoApp
import com.duorecord.app.StorageActionsTest
import com.duorecord.domain.CameraSource
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowStatFs
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
class ArchiveRefreshTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    private fun waitUntil(condition:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!condition() && System.nanoTime()<end) { Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle() }
        assertTrue("Background refresh did not settle",condition())
    }
    @Test fun autoRefreshTracksExternalChangesPreservesSelectionAndStopsWithPageLifecycle() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(),4_000_000,3_000_000,3_000_000)
        val provider=StorageActionsTest.Provider().apply { attachInfo(app,ProviderInfo().apply { authority="media" }) }
        ShadowContentResolver.registerProviderInternal("media",provider)
        val repo=app.repository!!
        fun file(time:Long)=repo.createSegment(time,CameraSource.REAR).also { it.descriptor.close();repo.completeSegment(it.clip.id,1000) }.clip
        val first=file(1791372000000);val second=file(1791372010000)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        fun value(name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
        fun settled()=(value("archiveScanJob") as? Job)?.isActive!=true && value("refreshing")==false
        MainActivity::class.java.getDeclaredMethod("showArchive").apply { isAccessible=true }.invoke(activity)
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        val adapter=views(root).filterIsInstance<RecyclerView>().single().adapter as ClipListAdapter
        waitUntil { adapter.itemCount==2 && settled() }
        adapter.selectAll()
        provider.delete(Uri.parse(first.uri),null,null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(2,adapter.itemCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        waitUntil { adapter.itemCount==1 && settled() }
        assertTrue(adapter.selecting);assertEquals(setOf(second.id),adapter.selected)
        provider.update(Uri.parse(second.uri),ContentValues().apply { put("duration",2500) },null,null)
        provider.files.getValue(Uri.parse(second.uri).lastPathSegment!!.toLong()).writeBytes(ByteArray(100))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        waitUntil { adapter.clips.single().durationMs==2500L && settled() }
        assertEquals(100L,adapter.clips.single().bytes)
        // Manual refresh shares the same scan guard and updates immediately.
        provider.delete(Uri.parse(second.uri),null,null)
        views(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.REFRESH }.performClick()
        waitUntil { adapter.itemCount==0 && settled() };assertTrue(adapter.selected.isEmpty())
        controller.pause().stop()
        val third=file(1791372020000)
        val stoppedQueries=provider.queries
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(stoppedQueries,provider.queries);assertEquals(0,adapter.itemCount)
        controller.start().resume().visible()
        waitUntil { adapter.clips.any { it.id==third.id } && settled() }
        views(root).filterIsInstance<SignButton>().single { it.icon==HeaderIcon.CLOSE && it.text=="Close" }.performClick()
        waitUntil { settled() }
        assertEquals("home",value("screen"))
        assertTrue(views(activity.findViewById(android.R.id.content)).none { it is RecyclerView })
        val closedQueries=provider.queries
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals(closedQueries,provider.queries)
        controller.pause().stop().destroy()
    }
}
