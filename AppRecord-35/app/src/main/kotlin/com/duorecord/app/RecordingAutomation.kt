package com.duorecord.app

import android.app.*
import android.content.*
import android.service.quicksettings.TileService
import com.duorecord.app.ui.MainActivity

internal object RecordingAutomation {
    fun alarm(context:Context,time:Long,action:String) {
        val pi=PendingIntent.getBroadcast(context,if(action=="start")201 else 202,Intent(context,ScheduleReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager=context.getSystemService(AlarmManager::class.java)
        manager.cancel(pi)
        if(time>System.currentTimeMillis()) {
            if(android.os.Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi)
        }
    }
    fun cancel(context:Context) {
        context.getSharedPreferences("automation",0).edit().putLong("start",0).putLong("end",0).putBoolean("armed",false).apply()
        alarm(context,0,"start");alarm(context,0,"end")
    }
}
class ScheduleReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val p=context.getSharedPreferences("automation",0)
        if(intent.action=="end") {
            if(p.getLong("activeEnd",0)>0 && (context.applicationContext as DuoApp).activeEngine!=null)context.startService(Intent(context,RecordingService::class.java).setAction(RecordingService.STOP).putExtra("userRequested",false))
            p.edit().putBoolean("armed",false).apply();return
        }
        if(!p.getBoolean("armed",false) || System.currentTimeMillis()>=p.getLong("end",0))return
        Notifications.channel(context,"schedule","Scheduled recording")
        val open=PendingIntent.getActivity(context,203,Intent(context,MainActivity::class.java).putExtra("scheduled",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(context,"schedule").setSmallIcon(R.drawable.ic_record).setContentTitle("Scheduled recording ready")
            .setContentText(if(p.getBoolean("audioOnly",false))"Tap to open the app and start audio recording." else "Tap to open the camera and start recording.").setContentIntent(open).setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(203,notification)
    }
}
class RecordTile:TileService() {
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is used only below Android 14.
    override fun onClick() {
        super.onClick()
        val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("quickRecord",true)
        val launch={ if(android.os.Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,204,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)) else @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        if(isLocked)unlockAndRun(launch) else launch()
    }
}
