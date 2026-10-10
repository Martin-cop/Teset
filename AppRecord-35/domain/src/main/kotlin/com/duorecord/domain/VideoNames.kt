package com.duorecord.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoNames {
    fun base(time:Long,camera:CameraSource):String {
        val date=SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(Date(time))
        return date+when(camera) { CameraSource.REAR -> "_R";CameraSource.FRONT -> "_L";CameraSource.LEGACY -> "";CameraSource.AUDIO -> "_V";CameraSource.SCREEN -> "_S";CameraSource.MIXED -> "_M" }
    }
    fun unique(time:Long,camera:CameraSource,existing:Set<String>,extension:String="mp4"):String {
        val stem=base(time,camera);val used=existing.map { it.lowercase(Locale.US) }.toHashSet()
        var name="$stem.$extension";var index=0
        while(name.lowercase(Locale.US) in used)name="${stem}_${++index}.$extension"
        return name
    }
    fun camera(name:String):CameraSource?=when {
        Regex("_M(?:_\\d+)?\\.mp4$",RegexOption.IGNORE_CASE).containsMatchIn(name) -> CameraSource.MIXED
        Regex("_S(?:_\\d+)?\\.mp4$",RegexOption.IGNORE_CASE).containsMatchIn(name) -> CameraSource.SCREEN
        Regex("_R(?:_\\d+)?\\.(?:mp4|jpg)$",RegexOption.IGNORE_CASE).containsMatchIn(name) -> CameraSource.REAR
        Regex("_L(?:_\\d+)?\\.(?:mp4|jpg)$",RegexOption.IGNORE_CASE).containsMatchIn(name) -> CameraSource.FRONT
        Regex("_[AV](?:_\\d+)?\\.(?:m4a|aac)$",RegexOption.IGNORE_CASE).containsMatchIn(name) -> CameraSource.AUDIO
        else -> null
    }
    fun time(name:String):Long?=runCatching {
        val stamp=Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}").find(name)?.value ?: return null
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).apply { isLenient=false }.parse(stamp)?.time
    }.getOrNull()
}

object PlaybackNavigation {
    fun neighbors(clips:List<Clip>,current:Clip,sameType:Boolean=true):Pair<Clip?,Clip?> {
        val same=clips.filter { (!sameType || (it.isPhoto==current.isPhoto && it.isAudio==current.isAudio && it.camera==current.camera)) && it.status==ClipStatus.READY }.distinctBy { it.id }
            .sortedWith(compareBy<Clip> { it.startMs }.thenBy { it.id })
        val index=same.indexOfFirst { it.id==current.id }
        return if(index<0)null to null else same.getOrNull(index-1) to same.getOrNull(index+1)
    }
}
