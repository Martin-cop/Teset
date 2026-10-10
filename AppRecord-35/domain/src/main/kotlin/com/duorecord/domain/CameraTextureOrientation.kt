package com.duorecord.domain

import kotlin.math.abs

/** Camera SurfaceTexture includes the producer transform: complete its quarter
 * turn rather than applying sensor rotation twice. Not for screen/decoder input. */
object CameraTextureOrientation {
    fun producerQuarterTurn(transform:FloatArray):Int {
        require(transform.size>=16)
        val crossed=abs(transform[1])+abs(transform[4])
        val diagonal=abs(transform[0])+abs(transform[5])
        if(crossed<=diagonal || crossed<.0001f)return if(transform[5]>0f)180 else 0
        // Standard camera sampling has a vertical flip. Crop scales and a
        // front-facing horizontal mirror do not change this Y-axis sign.
        return if(transform[1]<0f)90 else 270
    }
    fun remainingRotation(sensorDegrees:Int,transform:FloatArray):Int =
        ((sensorDegrees-producerQuarterTurn(transform))%360+360)%360
}
