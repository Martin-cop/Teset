package com.duorecord.app

import android.Manifest
import android.content.pm.ServiceInfo
import com.duorecord.domain.RecordingConfig

internal object CapturePermissions {
    fun runtime(config:RecordingConfig):List<String> = when {
        // Reserve camera/microphone foreground access before the user changes to another app.
        config.mixedMode -> listOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
        config.screenMode -> listOf(Manifest.permission.RECORD_AUDIO)
        config.audioOnly -> listOf(Manifest.permission.RECORD_AUDIO)
        config.photoMode -> listOf(Manifest.permission.CAMERA)
        else -> listOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
    }
    fun foregroundTypes(config:RecordingConfig):Int = when {
        config.mixedMode -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or (if(config.requiresProjection)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
        config.screenMode -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        config.audioOnly -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        config.photoMode -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    }
}
