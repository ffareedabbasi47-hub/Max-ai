package com.example.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class MaxVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession =
        MaxVoiceInteractionSession(this)
}
