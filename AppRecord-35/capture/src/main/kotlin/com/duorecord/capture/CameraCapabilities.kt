package com.duorecord.capture

import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.*
import android.util.Size
import com.duorecord.domain.*
import java.util.concurrent.Executor

internal class CameraCapabilities(private val manager:CameraManager) {
    fun ids(mode:CameraMode):Map<CameraSource,String> {
        fun facing(id:String)=manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        if(mode==CameraMode.BOTH) {
            for(group in manager.concurrentCameraIds) {
                val front=group.firstOrNull { facing(it)==CameraCharacteristics.LENS_FACING_FRONT }
                val rear=group.firstOrNull { facing(it)==CameraCharacteristics.LENS_FACING_BACK }
                if(front!=null && rear!=null)return mapOf(CameraSource.FRONT to front,CameraSource.REAR to rear)
            }
            error("This phone does not support simultaneous front and rear recording.")
        }
        val source=if(mode==CameraMode.FRONT)CameraSource.FRONT else CameraSource.REAR
        val wanted=if(source==CameraSource.FRONT)CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return mapOf(source to (manager.cameraIdList.firstOrNull { facing(it)==wanted } ?: error("${source.name} camera unavailable.")))
    }
    fun configureOrientation(request:CaptureRequest.Builder,id:String) {
        // Sensor rotation is applied by EGL for both preview and saved frames.
        val chars=manager.getCameraCharacteristics(id)
        if(android.os.Build.VERSION.SDK_INT>=31 && chars.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)?.contains(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)==true)
            request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
    }
    fun input(id:String,quality:CaptureQuality):CameraInput {
        val chars=manager.getCameraCharacteristics(id)
        val sensor=chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val angle=(sensor+if(chars.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT)180 else 0)%360
        val size=if(angle%180!=0)Size(quality.height,quality.width) else Size(quality.width,quality.height)
        return CameraInput(id,size,angle)
    }
    fun qualities(source:CameraSource,config:RecordingConfig):List<CaptureQuality> {
        val mode=if(config.cameraMode==CameraMode.BOTH)CameraMode.BOTH else if(source==CameraSource.FRONT)CameraMode.FRONT else CameraMode.REAR
        val ids=ids(mode)
        val own=single(ids.getValue(source),config)
        if(mode!=CameraMode.BOTH)return own
        val other=if(source==CameraSource.FRONT)CameraSource.REAR else CameraSource.FRONT
        val key=if(other==CameraSource.FRONT)config.frontQuality else config.rearQuality
        val otherQuality=CapturePolicy.choose(single(ids.getValue(other),config),key)
        return own.filter { quality -> concurrent(mapOf(source to input(ids.getValue(source),quality),other to input(ids.getValue(other),otherQuality))) }
    }
    fun concurrent(inputs:Map<CameraSource,CameraInput>):Boolean {
        if(inputs.size<2)return true
        val callback=object:CameraCaptureSession.StateCallback() {
            override fun onConfigured(session:CameraCaptureSession)=Unit
            override fun onConfigureFailed(session:CameraCaptureSession)=Unit
        }
        val configurations=inputs.values.associate { input -> input.id to SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,listOf(OutputConfiguration(input.size,SurfaceTexture::class.java)),Executor { it.run() },callback) }
        return runCatching { manager.isConcurrentSessionConfigurationSupported(configurations) }.getOrDefault(false)
    }
    internal fun single(id:String,config:RecordingConfig):List<CaptureQuality> {
        val chars=manager.getCameraCharacteristics(id)
        val map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
        if(chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.none { it.contains(config.fps) }!=false)return emptyList()
        val rotated=(chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0)%180!=0
        val codecs=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val types=if(config.preferHevc)listOf(MediaFormat.MIMETYPE_VIDEO_HEVC,MediaFormat.MIMETYPE_VIDEO_AVC) else listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
        return map.getOutputSizes(SurfaceTexture::class.java).mapNotNull { size ->
            val duration=map.getOutputMinFrameDuration(SurfaceTexture::class.java,size)
            if(duration>0 && 1_000_000_000.0/duration+0.1<config.fps)return@mapNotNull null
            val w=if(rotated)size.height else size.width;val h=if(rotated)size.width else size.height
            val type=types.firstOrNull { mime ->
                fun supported(encoder:Boolean)=codecs.any { codec ->
                    codec.isEncoder==encoder && (!encoder || codec.isHardwareAccelerated) && codec.supportedTypes.any { it.equals(mime,true) } &&
                        runCatching { codec.getCapabilitiesForType(mime).videoCapabilities.areSizeAndRateSupported(w,h,config.fps.toDouble()) }.getOrDefault(false)
                }
                supported(true) && supported(false)
            } ?: return@mapNotNull null
            val target=(w.toLong()*h*config.fps*(if(type==MediaFormat.MIMETYPE_VIDEO_HEVC)0.10 else 0.15)).toLong().coerceIn(128_000,120_000_000).toInt()
            CaptureQuality(w,h,target)
        }.distinctBy { it.key }.sortedWith(compareBy<CaptureQuality> { it.pixels }.thenBy { it.width })
    }
}
