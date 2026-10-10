package com.duorecord.app

import android.content.Context
import android.view.KeyEvent
import com.duorecord.domain.*

/** Only key events delivered by Android are eligible; system-reserved keys aren't offered. */
internal object RecordingShortcut {
    val keys=listOf(
        KeyEvent.KEYCODE_VOLUME_UP to "Volume up",
        KeyEvent.KEYCODE_VOLUME_DOWN to "Volume down",
        KeyEvent.KEYCODE_VOLUME_MUTE to "Volume mute",
        KeyEvent.KEYCODE_CAMERA to "Camera shutter",
        KeyEvent.KEYCODE_FOCUS to "Camera focus",
        KeyEvent.KEYCODE_HEADSETHOOK to "Headset button",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Media play / pause",
        KeyEvent.KEYCODE_MEDIA_PLAY to "Media play",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "Media pause",
        KeyEvent.KEYCODE_MEDIA_RECORD to "Media record"
    )
    fun read(context:Context):ShortcutConfig {
        val p=context.getSharedPreferences("shortcut",0)
        val legacy=p.getInt("key",0)
        val defaults=setOf(if(legacy==1)KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN)
        val selected=p.getString("keys",null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.filter { code -> keys.any { it.first==code } }?.toSet() ?: defaults
        val gesture=runCatching { ShortcutGesture.valueOf(p.getString("gesture","PRESSES")!!) }.getOrDefault(ShortcutGesture.PRESSES)
        return ShortcutConfig(p.getBoolean("enabled",legacy!=2),selected,gesture,p.getInt("count",3).coerceIn(2,5),p.getLong("holdMs",800).coerceIn(500,5000))
    }
    fun save(context:Context,config:ShortcutConfig) {
        config.validate()
        check(context.getSharedPreferences("shortcut",0).edit().putBoolean("enabled",config.enabled)
            .putString("keys",config.keys.sorted().joinToString(",")).putString("gesture",config.gesture.name)
            .putInt("count",config.count).putLong("holdMs",config.holdMs).commit()) { "Could not save shortcut." }
    }
}
