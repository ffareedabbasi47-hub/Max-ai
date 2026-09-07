package com.example.assistant

import android.service.voice.VoiceInteractionService

/**
 * This is the actual Android mechanism behind "default assistant app" — the same
 * `VoiceInteractionService` API Google Assistant itself is built on. Registering this here
 * does NOT make MAX the default automatically — Android deliberately never lets an app
 * silently grab that role (same principle as Accessibility Service, notification listener,
 * etc.). The user has to go to:
 *
 *   Settings → Apps → Default apps → Digital assistant app → MAX
 *
 * and pick it manually, once. There's no way to skip that step, and no honest way to claim
 * otherwise. Once selected, long-press-home / the assist gesture launches
 * [MaxVoiceInteractionSessionService] → [MaxVoiceInteractionSession] instead of Google
 * Assistant.
 */
class MaxVoiceInteractionService : VoiceInteractionService()
