package com.duorecord.domain

import kotlin.math.sqrt

data class MixedFrame(val x:Float,val y:Float,val width:Float,val height:Float,val visible:Boolean=true) {
    fun bounded():MixedFrame {
        val w=width.coerceIn(.12f,.90f);val h=height.coerceIn(.12f,.90f)
        return copy(x=x.coerceIn(0f,1f-w),y=y.coerceIn(0f,1f-h),width=w,height=h)
    }
}
object MixedLayout {
    fun read(value:String):Map<CameraSource,MixedFrame> {
        val result=linkedMapOf(CameraSource.FRONT to MixedFrame(0f,0f,.38f,.30f),CameraSource.REAR to MixedFrame(.60f,.02f,.38f,.30f),CameraSource.SCREEN to MixedFrame(.60f,.64f,.38f,.34f))
        value.split(';').forEach { item -> runCatching {
            val parts=item.split(':');val source=CameraSource.valueOf(parts[0]);val n=parts[1].split(',')
            require(source in result && n.size==5)
            val v=n.take(4).map { it.toFloat().also { f -> require(f.isFinite()) } }
            result[source]=MixedFrame(v[0],v[1],v[2],v[3],n[4]=="1").bounded()
        } }
        return result
    }
    fun write(frames:Map<CameraSource,MixedFrame>):String=frames.entries.joinToString(";") { (s,f) -> "${s.name}:${f.x},${f.y},${f.width},${f.height},${if(f.visible)1 else 0}" }
}
object AudioLevels {
    /** Twelve adjacent PCM windows: a genuine input level meter, not an invented spectrum. */
    fun measure(pcm:ByteArray,length:Int):FloatArray {
        val samples=length.coerceAtMost(pcm.size)/2
        return FloatArray(12) { band ->
            val from=samples*band/12;val to=samples*(band+1)/12
            var sum=0.0
            for(i in from until to) { val n=((pcm[i*2].toInt() and 255) or (pcm[i*2+1].toInt() shl 8)).toShort().toDouble()/32768;sum+=n*n }
            (sqrt(sum/(to-from).coerceAtLeast(1))*5).toFloat().coerceIn(0f,1f)
        }
    }
    fun mix(ambient:ByteArray,internal:ByteArray,size:Int,ambientOn:Boolean,internalOn:Boolean):ByteArray {
        val result=ByteArray(size)
        for(i in 0 until size/2) {
            fun sample(a:ByteArray)=((a[i*2].toInt() and 255) or (a[i*2+1].toInt() shl 8)).toShort().toInt()
            val n=((if(ambientOn)sample(ambient) else 0)+(if(internalOn)sample(internal) else 0)).coerceIn(-32768,32767)
            result[i*2]=n.toByte();result[i*2+1]=(n shr 8).toByte()
        }
        return result
    }
}
