package com.example.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.core.MaxLiveConfig
import com.example.system.MaxLiveService

/**
 * Shown when the user long-presses home (or uses the assist gesture) with MAX set as the
 * default assistant. Kept deliberately minimal — a full custom overlay matching Google
 * Assistant's card UI is a much larger undertaking; this version shows a brief "MAX active"
 * confirmation and immediately hands off to either a Live Mode conversation (if enabled in
 * Settings) or the main app UI, rather than trying to replicate a whole custom in-place UI.
 */
class MaxVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        val layout = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#0D0D1A"))
        }
        val label = TextView(context).apply {
            text = "MAX ACTIVE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 18f
            gravity = Gravity.CENTER
        }
        layout.addView(
            label,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        return layout
    }

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        if (MaxLiveConfig.isLiveModeEnabled(context)) {
            // Live Mode is on — jump straight into a live conversation rather than opening the
            // full app UI, closer to how Google Assistant responds immediately when invoked.
            val serviceIntent = Intent(context, MaxLiveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(activityIntent)
        }

        // Auto-dismiss this minimal session view quickly — MainActivity/MaxLiveService now
        // owns the actual interaction, not this assist overlay.
        android.os.Handler(context.mainLooper).postDelayed({ hide() }, 800)
    }
}
