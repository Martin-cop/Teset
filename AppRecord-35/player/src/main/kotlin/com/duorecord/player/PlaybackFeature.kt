package com.duorecord.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.duorecord.contracts.*
import com.duorecord.domain.*
import kotlin.math.abs

@UnstableApi
class PlaybackFeature : PlayerFactory {
    override fun create(context:Context,onError:(String)->Unit):PlayerComponent = NativePlayer(context,onError)
}

/** The master owns the controller and audio; the second decoder follows its absolute time. */
@UnstableApi
private class NativePlayer(private val context:Context,private val onError:(String)->Unit) : PlayerComponent {
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var meter=AudioMeter()
    private var compatibility=false
    private var master:ExoPlayer=makePlayer(true)
    private var follower:ExoPlayer?=null
    private var masterClip:Clip?=null
    private var followerClip:Clip?=null
    private var released=false
    private var adjusting=false
    private var viewingRotation=0
    private var requestedSpeed=1f
    private var lastPositiveSpeed=1f
    override val view:View=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_LTR;setBackgroundColor(android.graphics.Color.BLACK) }
    private fun makePlayer(audio:Boolean):ExoPlayer=ExoPlayer.Builder(context,if(compatibility)androidx.media3.exoplayer.DefaultRenderersFactory(context) else object:androidx.media3.exoplayer.DefaultRenderersFactory(context) {
        override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):androidx.media3.exoplayer.audio.AudioSink =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(PlaybackMeterProcessor { if(audio)meter=it })).build()
    }).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
        if(audio)setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true) else {
            volume=0f
            trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build()
        }
        addListener(object:Player.Listener {
            override fun onPlayerError(error:PlaybackException) {
                onError("Playback failed: ${error.errorCodeName}"+"\n"+error.stackTraceToString())
                if(audio && !compatibility && error.errorCode==PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK) {
                    val position=master.currentPosition;val clip=masterClip;val companion=followerClip
                    handler.post { if(!released && clip!=null) {
                        compatibility=true;master.release();master=makePlayer(true)
                        open(clip,companion);master.seekTo(position);setSpeed(requestedSpeed)
                        onError("Playback retried in standard compatibility mode. Audio spectrum unavailable for this file.")
                    } }
                } else pause()
            }
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int) { if(audio)sync(true) }
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int) {
                if(audio) {
                    if(playWhenReady && requestedSpeed==0f)applyPositiveRate(lastPositiveSpeed)
                    sync(false)
                }
            }
        })
    }
    private fun panel(player:ExoPlayer,title:String,controls:Boolean) {
        val container=view as LinearLayout
        container.addView(TextView(context).apply { text=title;setTextColor(0xff65ff99.toInt());textSize=13f;setPadding(12,4,12,4) },LinearLayout.LayoutParams(-1,-2))
        val frame=android.widget.FrameLayout(context)
        val display=PlayerView(context).apply {
            this.player=player;useController=false;controllerShowTimeoutMs=0
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowNextButton(false);setShowPreviousButton(false);setShowRewindButton(true);setShowFastForwardButton(true)
            setKeepContentOnPlayerReset(false);layoutDirection=View.LAYOUT_DIRECTION_LTR
        }
        frame.addView(display,android.widget.FrameLayout.LayoutParams(-1,-1))
        frame.addView(object:View(context) {
            private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=0xff65ff99.toInt();style=android.graphics.Paint.Style.STROKE;strokeWidth=2*resources.displayMetrics.density }
            override fun onDraw(c:android.graphics.Canvas) {
                val surface=display.videoSurfaceView
                if(surface!=null && surface.width>0 && master.videoSize.width>0) {
                    val quarter=viewingRotation%180!=0
                    val scale=if(quarter)minOf(width.toFloat()/surface.height,height.toFloat()/surface.width) else 1f
                    surface.rotation=viewingRotation.toFloat();surface.scaleX=scale;surface.scaleY=scale
                    val w=(if(quarter)surface.height else surface.width)*scale;val h=(if(quarter)surface.width else surface.height)*scale
                    c.drawRect((width-w)/2+1,(height-h)/2+1,(width+w)/2-1,(height+h)/2-1,p)
                }
                if(isShown)postInvalidateDelayed(250)
            }
        },android.widget.FrameLayout.LayoutParams(-1,-1))
        container.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
    }
    override fun open(clip:Clip,companion:Clip?) {
        handler.removeCallbacks(tick);follower?.release();follower=null
        viewingRotation=0
        (view as LinearLayout).removeAllViews()
        val clips=listOfNotNull(clip,companion).distinctBy { it.id }.sortedBy { if(it.camera==CameraSource.REAR)0 else 1 }
        masterClip=clips.first();followerClip=clips.getOrNull(1)
        panel(master,when(masterClip!!.camera) { CameraSource.REAR -> "Rear camera";CameraSource.FRONT -> "Front camera";CameraSource.AUDIO -> "Audio recording · AAC";CameraSource.SCREEN -> "Screen recording";CameraSource.MIXED -> "Mixed recording";else -> "Legacy combined recording" },true)
        master.setMediaItem(MediaItem.fromUri(masterClip!!.uri));master.prepare()
        followerClip?.let { second ->
            follower=makePlayer(false).also { other ->
                panel(other,"Front camera • shared controls above",false)
                other.setPlaybackSpeed(master.playbackParameters.speed)
                other.setMediaItem(MediaItem.fromUri(second.uri));other.prepare()
            }
        }
        master.playWhenReady=true;handler.post(tick)
    }
    private fun sync(force:Boolean) {
        if(released || adjusting)return
        val other=follower ?: return
        val first=masterClip ?: return;val second=followerClip ?: return
        adjusting=true
        try {
            val target=first.startMs+master.currentPosition-second.startMs
            val inRange=target>=0 && target<second.durationMs
            if(force || (other.playbackState==Player.STATE_READY && abs(other.currentPosition-target)>120))other.seekTo(target.coerceIn(0,second.durationMs))
            other.playWhenReady=master.playWhenReady && master.playbackState==Player.STATE_READY && inRange
        } finally { adjusting=false }
    }
    private val tick=object:Runnable { override fun run() { if(!released) { sync(false);handler.postDelayed(this,100) } } }
    override fun setViewingRotation(degrees:Int) { require(degrees in listOf(0,90,180,270));viewingRotation=degrees;(view as LinearLayout).invalidate() }
    override fun isPlaying()=master.isPlaying
    override fun audioMeter()=if(master.isPlaying)meter else AudioMeter()
    override fun positionMs():Long=master.currentPosition.coerceAtLeast(0)
    override fun durationMs():Long=master.duration.takeIf { it!=C.TIME_UNSET }?.coerceAtLeast(0) ?: masterClip?.durationMs ?: 0
    private var explicitSeek:Long?=null
    override fun seekTo(positionMs:Long) { val target=positionMs.coerceIn(0,durationMs());explicitSeek=if(!master.playWhenReady)target else null;master.seekTo(target);sync(true) }
    private fun applyPositiveRate(speed:Float) {
        requestedSpeed=speed;lastPositiveSpeed=speed
        // Android audio processing commonly caps at 8×. Disable the video player's
        // audio track above that so its clock can actually run at the requested 30×.
        master.trackSelectionParameters=master.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,speed>8f).build()
        master.setPlaybackSpeed(speed);follower?.setPlaybackSpeed(speed)
    }
    override fun setSpeed(speed:Float) {
        PlaybackRates.validate(speed)
        require(masterClip?.isAudio!=true || speed<=8f) { "Audio-only playback supports up to 8×." }
        val wasPausedRate=requestedSpeed==0f
        if(speed==0f) { requestedSpeed=0f;pause() }
        else { applyPositiveRate(speed);if(wasPausedRate)play() }
    }
    override fun speed():Float=requestedSpeed
    override fun play() {
        if(requestedSpeed==0f)applyPositiveRate(lastPositiveSpeed)
        val end=master.duration
        val selected=explicitSeek;explicitSeek=null
        if(selected!=null && (end<=0 || selected<end)) { master.seekTo(selected);sync(true) }
        else if(master.playbackState==Player.STATE_ENDED || end>0 && master.currentPosition>=end) { master.seekTo(0);sync(true) }
        master.play();sync(false)
    }
    override fun pause() { master.pause();follower?.pause() }
    override fun release() {
        released=true;handler.removeCallbacksAndMessages(null)
        val container=view as LinearLayout
        fun detach(v:View) { if(v is PlayerView)v.player=null; if(v is android.view.ViewGroup)for(i in 0 until v.childCount)detach(v.getChildAt(i)) };detach(container)
        follower?.release();master.release()
    }
}
