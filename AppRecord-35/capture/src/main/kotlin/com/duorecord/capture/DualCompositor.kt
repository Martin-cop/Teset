package com.duorecord.capture

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.duorecord.domain.RecordingConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal data class CameraInput(val id:String,val size:Size,val rotation:Int)

/** One EGL context feeds an independent hardware encoder for each selected camera. */
internal class DualCompositor(
    private val encoderSurfaces:List<Surface>,private val inputs:List<CameraInput>,
    private val config:RecordingConfig,private val originNs:Long,
    private val onReady:(List<Surface>)->Unit,private val onFirstFrame:()->Unit,private val onError:(String)->Unit,
    private val onPhoto:((ByteArray,Long,Int)->Unit)?=null,
    private val preparePhoto:((()->Unit)->Unit)={ it() },private val finishPhoto:()->Unit={}
) {
    private val thread=HandlerThread("DuoRecord-GPU").apply { start() }
    private val handler=Handler(thread.looper)
    private var display:EGLDisplay=EGL14.EGL_NO_DISPLAY
    private var context:EGLContext=EGL14.EGL_NO_CONTEXT
    private val windows=mutableListOf<EGLSurface>()
    private val textures=IntArray(inputs.size)
    private val streams=mutableListOf<SurfaceTexture>()
    private val surfaces=mutableListOf<Surface>()
    private val dirty=BooleanArray(inputs.size)
    private val seen=BooleanArray(inputs.size)
    private val lastFrame=LongArray(inputs.size)
    private val matrices=Array(inputs.size) { FloatArray(16) }
    private var program=0;private var running=false;private var started=false
    private val photoWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private val photoBusy=java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var capturePaused=false
    fun setCapturePaused(value:Boolean) {
        capturePaused=value
        handler.post { if(value && exposure.cancel()) { photoBusy.set(false);finishPhoto() } }
    }
    private val exposure=com.duorecord.domain.PhotoExposureWindow()
    private var nextPhotoNs=if(config.photoEpochMs>0) {
        val interval=config.photoIntervalSeconds*1000L;val now=System.currentTimeMillis()
        val next=config.photoEpochMs+((now-config.photoEpochMs).coerceAtLeast(0)/interval+1)*interval
        System.nanoTime()+(next-now)*1_000_000L
    } else 0L;private var photoIndex=0
    private var createdNs=System.nanoTime()
    private val positions=floatBuffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))

    fun prepare() { handler.post { try { initGl();onReady(surfaces.toList()) } catch(t:Throwable) { onError("Video setup failed: ${t.message}") } } }
    fun start() { handler.post { if(!running) { running=true;createdNs=System.nanoTime();lastFrame.fill(createdNs);tick.run() } } }

    fun pause() { handler.post {
        running=false;handler.removeCallbacks(tick)
        if(exposure.cancel()) { photoBusy.set(false);finishPhoto() }
    } }

    private val tick=object:Runnable {
        override fun run() {
            if(!running)return
            try {
                for(i in inputs.indices) if(dirty[i]) {
                    streams[i].updateTexImage();streams[i].getTransformMatrix(matrices[i])
                    dirty[i]=false;seen[i]=true;lastFrame[i]=System.nanoTime()
                }
                val now=System.nanoTime()
                if(seen.all { it }) {
                    check(lastFrame.all { now-it<4_000_000_000L }) { "One camera stream stopped." }
                    for(i in inputs.indices) {
                        check(EGL14.eglMakeCurrent(display,windows[i],windows[i],context))
                        GLES20.glViewport(0,0,config.width,config.height)
                        GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                        draw(i)
                        if(!capturePaused && onPhoto!=null && now>=nextPhotoNs && photoBusy.compareAndSet(false,true)) {
                            val interval=config.photoIntervalSeconds*1_000_000_000L
                            nextPhotoNs=if(nextPhotoNs==0L)now+interval else nextPhotoNs+((now-nextPhotoNs)/interval+1)*interval
                            val token=exposure.begin()
                            preparePhoto { handler.post { if(running)exposure.ready(token,System.nanoTime()) } }
                        }
                        if(!capturePaused && exposure.takeFreshFrame(lastFrame[i])) {
                            try { takePhoto(System.currentTimeMillis(),photoIndex++) } finally { finishPhoto() }
                        }
                        EGLExt.eglPresentationTimeANDROID(display,windows[i],now-originNs)
                        check(EGL14.eglSwapBuffers(display,windows[i])) { "Could not send frame to encoder." }
                    }
                    if(!started) { started=true;onFirstFrame() }
                } else check(now-createdNs<12_000_000_000L) { "Camera frames unavailable." }
            } catch(t:Throwable) { running=false;finishPhoto();onError(t.message ?: "Video composition error.");return }
            handler.postDelayed(this,1000L/config.fps)
        }
    }
    private fun takePhoto(time:Long,index:Int) {
        val w=config.width;val h=config.height
        val buffer=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR) { "Photo capture failed." }
        buffer.rewind()
        photoWorker.execute {
            var bitmap:android.graphics.Bitmap?=null
            try {
                // Flip the OpenGL rows in place, avoiding a second full-size bitmap.
                val row=ByteArray(w*4);val opposite=ByteArray(w*4)
                for(y in 0 until h/2) {
                    val top=y*w*4;val bottom=(h-1-y)*w*4
                    buffer.position(top);buffer.get(row);buffer.position(bottom);buffer.get(opposite)
                    buffer.position(top);buffer.put(opposite);buffer.position(bottom);buffer.put(row)
                }
                buffer.rewind()
                bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val bytes=java.io.ByteArrayOutputStream()
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,bytes)) { "Could not encode photo." }
                onPhoto!!.invoke(bytes.toByteArray(),time,index)
            } catch(t:Throwable) { onError(t.message ?: "Could not save photo.") }
            finally { bitmap?.recycle();photoBusy.set(false) }
        }
    }
    private fun initGl() {
        display=EglDisplayPool.acquire()
        val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE)
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        encoderSurfaces.forEach { windows+=EGL14.eglCreateWindowSurface(display,configs[0],it,intArrayOf(EGL14.EGL_NONE),0) }
        check(context!=EGL14.EGL_NO_CONTEXT && windows.all { it!=EGL14.EGL_NO_SURFACE })
        check(EGL14.eglMakeCurrent(display,windows.first(),windows.first(),context))
        val vertex="attribute vec4 aPosition; attribute vec2 aTexture; uniform mat4 uTexture; varying vec2 vTexture; void main(){ gl_Position=aPosition; vTexture=(uTexture*vec4(aTexture,0.0,1.0)).xy; }"
        val fragment="#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES sTexture; varying vec2 vTexture; void main(){ gl_FragColor=texture2D(sTexture,vTexture); }"
        val vs=shader(GLES20.GL_VERTEX_SHADER,vertex);val fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0]!=0) { GLES20.glGetProgramInfoLog(program) };GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
        GLES20.glGenTextures(inputs.size,textures,0)
        for(i in inputs.indices) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textures[i])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            val stream=SurfaceTexture(textures[i]);stream.setDefaultBufferSize(inputs[i].size.width,inputs[i].size.height)
            stream.setOnFrameAvailableListener({ dirty[i]=true },handler)
            streams+=stream;surfaces+=Surface(stream)
        }
    }
    private fun draw(i:Int) {
        val input=inputs[i]
        val rotated=input.rotation%180!=0
        val sourceW=if(rotated)input.size.height else input.size.width
        val sourceH=if(rotated)input.size.width else input.size.height
        val panelW=config.width;val panelH=config.height
        val scale=minOf(panelW.toFloat()/sourceW,panelH.toFloat()/sourceH)
        val w=(sourceW*scale).toInt();val h=(sourceH*scale).toInt()
        GLES20.glViewport((panelW-w)/2,(panelH-h)/2,w,h)
        GLES20.glUseProgram(program)
        val aPosition=GLES20.glGetAttribLocation(program,"aPosition")
        val aTexture=GLES20.glGetAttribLocation(program,"aTexture")
        GLES20.glEnableVertexAttribArray(aPosition);GLES20.glEnableVertexAttribArray(aTexture)
        positions.position(0);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,positions)
        val coords=rotatedCoords(com.duorecord.domain.CameraTextureOrientation.remainingRotation(input.rotation,matrices[i]))
        GLES20.glVertexAttribPointer(aTexture,2,GLES20.GL_FLOAT,false,0,coords)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTexture"),1,false,matrices[i],0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textures[i])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"sTexture"),0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glDisableVertexAttribArray(aPosition);GLES20.glDisableVertexAttribArray(aTexture)
    }
    private val coordCache=mutableMapOf<Int,FloatBuffer>()
    private fun rotatedCoords(degrees:Int):FloatBuffer=coordCache.getOrPut(degrees) {
        val coords=floatArrayOf(0f,0f,1f,0f,0f,1f,1f,1f)
        for(i in 0..3) {
            val x=coords[i*2];val y=coords[i*2+1]
            val pair=when((degrees%360+360)%360) { 90 -> 1f-y to x;180 -> 1f-x to 1f-y;270 -> y to 1f-x;else -> x to y }
            coords[i*2]=pair.first;coords[i*2+1]=pair.second
        }
        floatBuffer(coords)
    }.apply { position(0) }
    private fun shader(type:Int,source:String):Int {
        val shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
        val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0) { GLES20.glGetShaderInfoLog(shader) };return shader
    }
    fun release(done:()->Unit) { handler.post {
        running=false;handler.removeCallbacks(tick);exposure.cancel();finishPhoto()
        surfaces.forEach { runCatching { it.release() } };streams.forEach { runCatching { it.release() } }
        if(display!=EGL14.EGL_NO_DISPLAY) {
            if(program!=0)GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(inputs.size,textures,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            windows.filter { it!=EGL14.EGL_NO_SURFACE }.forEach { EGL14.eglDestroySurface(display,it) };windows.clear()
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread();EglDisplayPool.release();display=EGL14.EGL_NO_DISPLAY
        }
        photoWorker.shutdown()
        // Complete a JPEG already being saved before reporting a finished camera.
        kotlin.concurrent.thread(name="DuoRecord-photo-drain") {
            if(!photoWorker.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS))onError("Photo saving did not finish in time.")
            done()
        }
        thread.quitSafely()
    } }
    private fun floatBuffer(values:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values);position(0) }
}
