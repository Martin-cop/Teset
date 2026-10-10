package com.duorecord.capture

import android.opengl.EGL14
import android.opengl.EGLDisplay

/** eglTerminate is display-wide: stopping one camera must not invalidate the other's EGL surfaces. */
internal object EglDisplayPool {
    private var display:EGLDisplay=EGL14.EGL_NO_DISPLAY
    private var users=0
    @Synchronized fun acquire():EGLDisplay {
        if(users==0) {
            val next=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version=IntArray(2)
            check(EGL14.eglInitialize(next,version,0,version,1)) { "Could not initialize video graphics." }
            display=next
        }
        users++
        return display
    }
    @Synchronized fun release() {
        check(users>0)
        if(--users==0) { EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY }
    }
}
