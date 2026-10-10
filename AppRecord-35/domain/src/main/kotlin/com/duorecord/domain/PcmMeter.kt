package com.duorecord.domain

import kotlin.math.*

data class AudioMeter(val bands:FloatArray=FloatArray(20),val rmsDb:Float=-90f,val peakDb:Float=-90f,val clipped:Boolean=false)
/** Mono PCM16 dBFS and a Hann-windowed, logarithmically grouped real input spectrum. */
object PcmMeter {
    const val SIZE=1024
    fun analyze(pcm:ByteArray,length:Int,rate:Int=48000):AudioMeter {
        val count=(length.coerceIn(0,pcm.size)/2).coerceAtMost(SIZE)
        if(count==0)return AudioMeter()
        val real=DoubleArray(SIZE);val imag=DoubleArray(SIZE)
        var sum=0.0;var peak=0.0;var clipped=false
        for(i in 0 until count) {
            val sample=((pcm[i*2].toInt() and 255) or (pcm[i*2+1].toInt() shl 8)).toShort().toInt()
            val x=sample/32768.0;sum+=x*x;peak=max(peak,abs(x));clipped=clipped || abs(sample)>=32760
            real[i]=x*(.5-.5*cos(2*PI*i/(SIZE-1)))
        }
        var j=0
        for(i in 1 until SIZE) {
            var bit=SIZE shr 1
            while(j and bit!=0) { j=j xor bit;bit=bit shr 1 }
            j=j xor bit
            if(i<j) { val v=real[i];real[i]=real[j];real[j]=v }
        }
        var size=2
        while(size<=SIZE) {
            val half=size/2
            for(from in 0 until SIZE step size)for(k in 0 until half) {
                val angle=-2*PI*k/size;val cosine=cos(angle);val sine=sin(angle)
                val a=from+k;val b=a+half;val re=real[b]*cosine-imag[b]*sine;val im=real[b]*sine+imag[b]*cosine
                real[b]=real[a]-re;imag[b]=imag[a]-im;real[a]+=re;imag[a]+=im
            }
            size*=2
        }
        fun db(value:Double)=(20*log10(value.coerceAtLeast(.0000316228))).toFloat().coerceIn(-90f,0f)
        val upper=minOf(20000.0,rate/2.0)
        val bands=FloatArray(20) { band ->
            val low=50*(upper/50).pow(band/20.0);val high=50*(upper/50).pow((band+1)/20.0)
            val first=(low*SIZE/rate).roundToInt().coerceIn(1,SIZE/2-1)
            val last=max(first,(high*SIZE/rate).toInt().coerceAtMost(SIZE/2-1))
            var power=0.0
            for(k in first..last)power=max(power,real[k]*real[k]+imag[k]*imag[k])
            ((db(sqrt(power)*4/SIZE)+60)/60).coerceIn(0f,1f)
        }
        return AudioMeter(bands,db(sqrt(sum/count)),db(peak),clipped)
    }
}
