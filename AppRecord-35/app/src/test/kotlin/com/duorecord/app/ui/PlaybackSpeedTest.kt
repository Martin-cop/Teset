package com.duorecord.app.ui

import com.duorecord.player.PlaybackFeature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PlaybackSpeedTest {
    @Test fun nativePlaybackEngineSupportsThirtyTimesPauseAndAudioTrackRestoration() {
        val player=PlaybackFeature().create(RuntimeEnvironment.getApplication()) { fail(it) }
        try {
            val master=player.javaClass.getDeclaredField("master").apply { isAccessible=true }.get(player) as androidx.media3.exoplayer.ExoPlayer
            player.setSpeed(30f);assertEquals(30f,player.speed(),0f)
            assertEquals(30f,master.playbackParameters.speed,0f)
            assertTrue(master.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_AUDIO))
            player.setSpeed(0f);assertEquals(0f,player.speed(),0f);assertFalse(master.playWhenReady)
            player.play();assertEquals(30f,player.speed(),0f);assertTrue(master.playWhenReady)
            player.setSpeed(1f);assertEquals(1f,player.speed(),0f)
            assertFalse(master.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_AUDIO))
        } finally { player.release() }
    }
}
