package com.example.vision

/**
 * A background [android.app.Service] can't directly launch the camera and get a result back —
 * only an [android.app.Activity] can. [CameraCaptureActivity] does the capture and hands the
 * JPEG back through here. Ported from the Iris project's audited vision pipeline.
 */
object VisionBridge {
    @Volatile
    private var pendingCallback: ((ByteArray?) -> Unit)? = null

    fun awaitNextPhoto(callback: (ByteArray?) -> Unit) {
        pendingCallback = callback
    }

    fun deliverPhoto(bytes: ByteArray?) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(bytes)
    }
}
