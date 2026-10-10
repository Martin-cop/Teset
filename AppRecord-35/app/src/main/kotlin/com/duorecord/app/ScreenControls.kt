package com.duorecord.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** User-selected notification controls; later manual changes survive restart. */
internal object ScreenControls {
    private fun preferences(context:Context)=context.getSharedPreferences("screen_controls",0)
    fun floating(context:Context)=preferences(context).getBoolean("floating",false)
    fun migrate(context:Context) {
        val p=preferences(context)
        if(p.getInt("choice_revision",0)<31)p.edit().putBoolean("floating",false).putInt("choice_revision",31).apply()
    }
    fun setFloating(context:Context,enabled:Boolean) { preferences(context).edit().putBoolean("floating",enabled).putInt("choice_revision",31).apply() }
    fun notificationsAllowed(context:Context):Boolean {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        val manager=context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel("recording")?.importance!=NotificationManager.IMPORTANCE_NONE
    }
}
