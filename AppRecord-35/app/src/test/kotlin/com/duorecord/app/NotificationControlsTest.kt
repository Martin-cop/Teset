package com.duorecord.app

import android.Manifest
import android.app.*
import com.duorecord.domain.RecordingConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class NotificationControlsTest {
    @Test fun notificationChoiceMigratesOldFloatingDefaultOnceAndKeepsLaterManualChoices() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        val p=app.getSharedPreferences("screen_controls",0)
        p.edit().clear().putBoolean("floating",true).commit()
        ScreenControls.migrate(app);assertFalse(ScreenControls.floating(app))
        ScreenControls.setFloating(app,true);ScreenControls.migrate(app);assertTrue(ScreenControls.floating(app))
        ScreenControls.setFloating(app,false);assertFalse(ScreenControls.floating(app))
    }
    @Test fun notificationPauseResumeStopAndChronometerFollowActualCaptureState() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        app.repository!!.saveConfig(RecordingConfig(screenMode=true))
        app.status.value=RuntimeStatus(phase=Phase.RECORDING)
        val running=Notifications.record(app,"Recording screen")
        assertEquals("AppRecord • Recording",running.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(running.extras.getBoolean("android.showChronometer"))
        assertEquals(listOf("Pause","Stop"),running.actions.map { it.title.toString() })
        assertEquals(RecordingService.PAUSE,shadowOf(running.actions[0].actionIntent).savedIntent.action)
        assertTrue(shadowOf(running.actions[1].actionIntent).savedIntent.getBooleanExtra("openPlayback",false))
        app.status.value=RuntimeStatus(phase=Phase.USER_PAUSED)
        val paused=Notifications.record(app,"Paused")
        assertEquals("AppRecord • Paused",paused.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertFalse(paused.extras.getBoolean("android.showChronometer"))
        assertEquals("Resume",paused.actions[0].title.toString())
        assertEquals(RecordingService.RESUME,shadowOf(paused.actions[0].actionIntent).savedIntent.action)
    }
    @Test fun disabledNotificationPermissionOrChannelCannotLeaveScreenWithoutControls() {
        val app=RuntimeEnvironment.getApplication() as DuoApp
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ScreenControls.notificationsAllowed(app))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(ScreenControls.notificationsAllowed(app))
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("recording","Recording",NotificationManager.IMPORTANCE_NONE))
        assertFalse(ScreenControls.notificationsAllowed(app))
    }
}
