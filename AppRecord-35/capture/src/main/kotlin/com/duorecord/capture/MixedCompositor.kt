package com.duorecord.capture

import android.graphics.SurfaceTexture
import android.graphics.RectF
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.duorecord.domain.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal data class MixedTarget(val surface:Surface,val source:CameraSource,val width:Int,val height:Int)
/** Raw outputs and the composite are drawn from the same original textures and clock. */
internal class MixedCompositor(private val targets:List<MixedTarget>,private val inputs:List<Pair<CameraSource,CameraInput>>,private var config:RecordingConfig,private val originNs:Long,private val onReady:(Map<CameraSource,Surface>)->Unit,private val onFirstFrame:()->Unit,private val onError:(String)->Unit) {
    private val thread=HandlerThread("AppRecord-mixed-GPU").apply { start() }
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
    private var createdNs=System.nanoTime()
    private val positions=floatBuffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    fun prepare() { handler.post { try { initGl();onReady(inputs.indices.associate { inputs[it].first to surfaces[it] }) } catch(t:Throwable) { onError("Mixed video setup failed: ${t.message}") } } }
    fun update(next:RecordingConfig) { handler.post { config=next } }
    fun start() { handler.post { running=true;createdNs=System.nanoTime();tick.run() } }
    private val tick=object:Runnable {
        override fun run() {
            if(!running)return
            try {
                for(i in inputs.indices)if(dirty[i]) { streams[i].updateTexImage();streams[i].getTransformMatrix(matrices[i]);dirty[i]=false;seen[i]=true;lastFrame[i]=System.nanoTime() }
                val now=System.nanoTime()
                if(seen.all { it }) {
                    check(inputs.indices.all { inputs[it].first==CameraSource.SCREEN || now-lastFrame[it]<8000000000L }) { "Camera input interrupted." }
                    val frames=MixedLayout.read(config.mixedLayout)
                    targets.forEachIndexed { index,target ->
                        check(EGL14.eglMakeCurrent(display,windows[index],windows[index],context))
                        GLES20.glViewport(0,0,target.width,target.height);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                        if(target.source!=CameraSource.MIXED)draw(inputs.indexOfFirst { it.first==target.source },RectF(0f,0f,1f,1f),target.width,target.height)
                        else {
                            draw(inputs.indexOfFirst { it.first==config.mixedBackground },RectF(0f,0f,1f,1f),target.width,target.height,true)
                            inputs.forEachIndexed { i,(source,_) -> if(source!=config.mixedBackground)frames[source]?.takeIf { it.visible }?.let { f -> draw(i,RectF(f.x,f.y,f.x+f.width,f.y+f.height),target.width,target.height) } }
                        }
                        EGLExt.eglPresentationTimeANDROID(display,windows[index],now-originNs)
                        check(EGL14.eglSwapBuffers(display,windows[index])) { "Mixed encoder rejected a frame." }
                    }
                    if(!started) { started=true;onFirstFrame() }
                } else check(now-createdNs<20000000000L) { "A selected capture source did not deliver frames." }
            } catch(t:Throwable) { running=false;onError(t.message ?: "Mixed composition failed.");return }
            handler.postDelayed(this,1000L/config.fps.coerceIn(1,30))
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
        targets.map { it.surface }.forEach { windows+=EGL14.eglCreateWindowSurface(display,configs[0],it,intArrayOf(EGL14.EGL_NONE),0) }
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
            val stream=SurfaceTexture(textures[i]);stream.setDefaultBufferSize(inputs[i].second.size.width,inputs[i].second.size.height)
            stream.setOnFrameAvailableListener({ dirty[i]=true },handler)
            streams+=stream;surfaces+=Surface(stream)
        }
    }
    private fun draw(i:Int,box:android.graphics.RectF,wOut:Int,hOut:Int,cover:Boolean=false) {
        val input=inputs[i].second
        val rotated=input.rotation%180!=0
        val sourceW=if(rotated)input.size.height else input.size.width
        val sourceH=if(rotated)input.size.width else input.size.height
        val panelW=(box.width()*wOut).toInt();val panelH=(box.height()*hOut).toInt()
        val scale=if(cover)maxOf(panelW.toFloat()/sourceW,panelH.toFloat()/sourceH) else minOf(panelW.toFloat()/sourceW,panelH.toFloat()/sourceH)
        val w=(sourceW*scale).toInt();val h=(sourceH*scale).toInt()
        GLES20.glViewport((box.left*wOut).toInt()+(panelW-w)/2,((1-box.bottom)*hOut).toInt()+(panelH-h)/2,w,h)
        GLES20.glUseProgram(program)
        val aPosition=GLES20.glGetAttribLocation(program,"aPosition")
        val aTexture=GLES20.glGetAttribLocation(program,"aTexture")
        GLES20.glEnableVertexAttribArray(aPosition);GLES20.glEnableVertexAttribArray(aTexture)
        positions.position(0);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,positions)
        val coords=rotatedCoords(if(inputs[i].first==CameraSource.SCREEN)input.rotation else com.duorecord.domain.CameraTextureOrientation.remainingRotation(input.rotation,matrices[i]))
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
        running=false;handler.removeCallbacksAndMessages(null)
        surfaces.forEach { runCatching { it.release() } };streams.forEach { runCatching { it.release() } }
        if(display!=EGL14.EGL_NO_DISPLAY) {
            if(program!=0)GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(inputs.size,textures,0)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            windows.forEach { EGL14.eglDestroySurface(display,it) }
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread();EglDisplayPool.release();display=EGL14.EGL_NO_DISPLAY
        }
        done();thread.quitSafely()
    } }
    private fun floatBuffer(values:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values);position(0) }
}
