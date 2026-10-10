package com.duorecord.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.DocumentsContract

/** Resolve a concrete browser so Android's application chooser is never launched. */
internal object RecordingFolderBrowser {
    fun open(activity:Activity,path:String="Movies/Record"):Boolean {
        val directory=DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:$path")
        val intents=listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(directory,"vnd.android.document/directory")
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI,directory),
            Intent(Intent.ACTION_VIEW).setDataAndType(DocumentsContract.buildRootUri("com.android.externalstorage.documents","primary"),"vnd.android.document/root")
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI,directory)
        )
        val pm=activity.packageManager
        for(intent in intents) {
            val handlers=pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo?.let { a -> a.exported && a.enabled }==true }
            val preferred=pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            val ordered=handlers.sortedWith(compareBy(
                { if(it.activityInfo.packageName==preferred?.packageName && it.activityInfo.name==preferred?.name)0 else 1 },
                { if(it.activityInfo.packageName in setOf("com.google.android.documentsui","com.android.documentsui"))0 else 1 },
                { if(it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM!=0)0 else 1 },
                { it.activityInfo.packageName },{ it.activityInfo.name }
            ))
            for(handler in ordered)try {
                activity.startActivity(Intent(intent).setComponent(ComponentName(handler.activityInfo.packageName,handler.activityInfo.name)))
                return true
            } catch(_:Exception) { /* Try the next verified handler / directory form. */ }
        }
        return false
    }
}
