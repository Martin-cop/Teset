package com.duorecord.domain

enum class MicrophoneState { OFF, STARTING, UNKNOWN, ACTIVE, SILENCED }
data class MicrophoneDecision(val state:MicrophoneState,val changed:Boolean,val stop:Boolean,val zeroInput:Boolean)

/** System-silenced video may keep its picture and encode silence, without reopening cameras. */
class MicrophoneContinuity(private val continueSilencedVideo:Boolean) {
    var state=MicrophoneState.STARTING;private set
    fun observe(clientSilenced:Boolean?):MicrophoneDecision {
        val next=when(clientSilenced) { true -> MicrophoneState.SILENCED;false -> MicrophoneState.ACTIVE;null -> if(state==MicrophoneState.SILENCED)MicrophoneState.SILENCED else MicrophoneState.UNKNOWN }
        val changed=next!=state;state=next
        return MicrophoneDecision(next,changed,next==MicrophoneState.SILENCED && !continueSilencedVideo,next==MicrophoneState.SILENCED && continueSilencedVideo)
    }
}
