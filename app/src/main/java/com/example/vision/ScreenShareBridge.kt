package com.example.vision

import android.content.Intent

/** Same pattern as [VisionBridge] — a Service can't show the MediaProjection consent dialog and
 * receive the result directly, so [ScreenCaptureActivity] does that and hands the
 * (resultCode, data) pair back here. Ported from the Iris project. */
object ScreenShareBridge {
    @Volatile
    private var pendingCallback: ((Int, Intent?) -> Unit)? = null

    fun awaitNextGrant(callback: (Int, Intent?) -> Unit) {
        pendingCallback = callback
    }

    fun deliverGrant(resultCode: Int, data: Intent?) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(resultCode, data)
    }
}
