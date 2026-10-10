package com.duorecord.domain

/** Small thumbnails can still require full-resolution decoder resources. */
object TrimResourcePolicy {
    fun decodeThumbnails(width:Int?,height:Int?,lowRam:Boolean):Boolean {
        if(width==null || height==null || width<=0 || height<=0)return false
        return width.toLong()*height <= if(lowRam)1280L*720 else 1920L*1080
    }
}
