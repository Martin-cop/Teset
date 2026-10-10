package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class MicrophoneContinuityTest {
    @Test fun silencedVideoEncodesSilenceAndRestoresWithoutRequestingAStop() {
        val policy=MicrophoneContinuity(true)
        assertEquals(MicrophoneState.ACTIVE,policy.observe(false).state)
        val muted=policy.observe(true)
        assertTrue(muted.changed);assertTrue(muted.zeroInput);assertFalse(muted.stop)
        assertFalse(policy.observe(true).changed)
        val restored=policy.observe(false)
        assertTrue(restored.changed);assertEquals(MicrophoneState.ACTIVE,restored.state)
        assertFalse(restored.zeroInput);assertFalse(restored.stop)
    }
    @Test fun aMissingConfigurationCannotFalselyConfirmMicrophoneRecovery() {
        val policy=MicrophoneContinuity(true)
        assertEquals(MicrophoneState.UNKNOWN,policy.observe(null).state)
        policy.observe(true)
        val unknown=policy.observe(null)
        assertEquals(MicrophoneState.SILENCED,unknown.state);assertTrue(unknown.zeroInput);assertFalse(unknown.changed)
        assertEquals(MicrophoneState.ACTIVE,policy.observe(false).state)
    }
    @Test fun audioOnlyOrStrictVideoRequestsPauseWhenAndroidSilencesInput() {
        val muted=MicrophoneContinuity(false).observe(true)
        assertTrue(muted.stop);assertFalse(muted.zeroInput)
    }
}
