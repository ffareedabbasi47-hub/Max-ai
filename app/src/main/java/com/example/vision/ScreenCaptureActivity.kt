package com.example.vision

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Android requires an interactive system consent dialog for screen capture every session — it
 * cannot be silently pre-authorized, by design (a deliberate privacy protection). This shows
 * that dialog and relays the result to [MaxLiveService] via [ScreenShareBridge]. Ported from
 * the Iris project's audited vision pipeline.
 */
class ScreenCaptureActivity : ComponentActivity() {

    private val requestProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ScreenShareBridge.deliverGrant(result.resultCode, result.data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        runCatching {
            requestProjection.launch(projectionManager.createScreenCaptureIntent())
        }.onFailure {
            ScreenShareBridge.deliverGrant(Activity.RESULT_CANCELED, null)
            finish()
        }
    }
}
