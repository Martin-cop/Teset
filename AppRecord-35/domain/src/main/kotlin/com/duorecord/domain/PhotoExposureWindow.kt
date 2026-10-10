package com.duorecord.domain

/** An exposure must use a frame received after lighting settles. Cancelling invalidates callbacks. */
class PhotoExposureWindow {
    private var generation=0
    private var token:Int?=null
    private var readyNs=Long.MAX_VALUE
    fun begin():Int {
        check(token==null) { "Photo exposure already pending." }
        token=++generation;readyNs=Long.MAX_VALUE
        return generation
    }
    fun ready(expected:Int,timeNs:Long) { if(token==expected)readyNs=timeNs }
    fun takeFreshFrame(frameNs:Long):Boolean {
        if(token==null || readyNs==Long.MAX_VALUE || frameNs<readyNs)return false
        token=null;readyNs=Long.MAX_VALUE;return true
    }
    fun cancel():Boolean {
        val pending=token!=null
        generation++;token=null;readyNs=Long.MAX_VALUE
        return pending
    }
}
