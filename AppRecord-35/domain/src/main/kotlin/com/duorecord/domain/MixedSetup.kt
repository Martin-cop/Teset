package com.duorecord.domain

/** Atomic source/background changes prevent a renderer pointing at an absent texture. */
object MixedSetup {
    val sources=listOf(CameraSource.FRONT,CameraSource.REAR,CameraSource.SCREEN)
    fun background(config:RecordingConfig,source:CameraSource):RecordingConfig {
        require(source in sources)
        val frames=MixedLayout.read(config.mixedLayout).mapValues { it.value.copy(visible=true) }
        return config.copy(mixedMode=true,screenMode=false,audioOnly=false,photoMode=false,
            mixedFront=config.mixedFront || source==CameraSource.FRONT,
            mixedRear=config.mixedRear || source==CameraSource.REAR,
            mixedScreen=config.mixedScreen || source==CameraSource.SCREEN,
            mixedBackground=source,mixedLayout=MixedLayout.write(frames))
    }
    fun toggle(config:RecordingConfig,source:CameraSource):RecordingConfig {
        require(source in sources)
        var result=when(source) {
            CameraSource.FRONT -> config.copy(mixedFront=!config.mixedFront)
            CameraSource.REAR -> config.copy(mixedRear=!config.mixedRear)
            else -> config.copy(mixedScreen=!config.mixedScreen)
        }
        require(result.mixedSources.isNotEmpty()) { "Keep at least one source selected." }
        if(result.mixedBackground !in result.mixedSources)result=result.copy(mixedBackground=if(CameraSource.SCREEN in result.mixedSources)CameraSource.SCREEN else result.mixedSources.first())
        val frames=MixedLayout.read(result.mixedLayout).toMutableMap();frames[source]=frames.getValue(source).copy(visible=true)
        return result.copy(mixedLayout=MixedLayout.write(frames))
    }
    fun presentation(config:RecordingConfig,both:Boolean=false)=background(config.copy(mixedFront=true,mixedRear=both,mixedScreen=true,mixedLayout=""),CameraSource.SCREEN)
}
