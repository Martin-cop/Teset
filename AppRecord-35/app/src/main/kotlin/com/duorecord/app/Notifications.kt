package com.duorecord.app

import android.app.*
import android.content.Context
import android.content.Intent
import com.duorecord.app.ui.MainActivity

internal object Notifications {
    const val RECORD=101;const val EXPORT=102
    fun channel(context:Context,id:String,title:String) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,title,NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null);enableVibration(false) })
    }
    fun open(context:Context,clipId:String?=null):PendingIntent=PendingIntent.getActivity(context,if(clipId==null)0 else clipId.hashCode(),Intent(context,MainActivity::class.java).apply { if(clipId!=null)putExtra("clip",clipId) },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun record(context:Context,message:String):Notification {
        channel(context,"recording","Recording")
        val app=context.applicationContext as DuoApp
        val stop=PendingIntent.getService(context,1,Intent(context,RecordingService::class.java).setAction(RecordingService.STOP).putExtra("openPlayback",app.repository?.config()?.requiresProjection==true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paused=app.status.value.phase==Phase.USER_PAUSED
        val pause=PendingIntent.getService(context,3,Intent(context,RecordingService::class.java).setAction(if(paused)RecordingService.RESUME else RecordingService.PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val recording=app.status.value.phase==Phase.RECORDING
        val elapsed=app.captureClock.elapsedMs();val seconds=elapsed/1000
        val frozen=String.format(java.util.Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
        return Notification.Builder(context,"recording").setSmallIcon(R.drawable.ic_record).setContentTitle(if(paused || app.status.value.phase==Phase.PAUSED)"AppRecord • Paused" else if(app.status.value.phase==Phase.STARTING)"AppRecord • Starting" else "AppRecord • Recording")
            .setWhen(System.currentTimeMillis()-elapsed).setUsesChronometer(recording).setShowWhen(recording)
            .setContentText(if(recording)message else "$frozen · $message").setContentIntent(open(context)).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).addAction(Notification.Action.Builder(null,if(paused)"Resume" else "Pause",pause).build()).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
    }
    fun export(context:Context,message:String,progress:Int=0,busy:Boolean=true,clipId:String?=null):Notification {
        channel(context,"exports","Video exports")
        val builder=Notification.Builder(context,"exports").setSmallIcon(R.drawable.ic_record).setContentTitle("DuoRecord • Export")
            .setContentText(message).setContentIntent(open(context,clipId)).setOnlyAlertOnce(true).setOngoing(busy).setAutoCancel(!busy)
        if(busy) {
            val cancel=PendingIntent.getService(context,2,Intent(context,ExportService::class.java).setAction(ExportService.CANCEL),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setProgress(100,progress,progress==0).addAction(Notification.Action.Builder(null,"Cancel",cancel).build())
        }
        return builder.build()
    }
}
