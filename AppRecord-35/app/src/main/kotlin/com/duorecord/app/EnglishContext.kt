package com.duorecord.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Keep app dialogs, playback controls and layout English on any phone language. */
internal fun Context.inEnglish():Context {
    val configuration=Configuration(resources.configuration).apply {
        setLocale(Locale.ENGLISH)
        setLayoutDirection(Locale.ENGLISH)
    }
    return createConfigurationContext(configuration)
}
