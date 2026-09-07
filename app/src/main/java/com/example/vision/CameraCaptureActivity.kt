package com.example.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Delegates to the system Camera app (standard ACTION_IMAGE_CAPTURE contract, so MAX itself
 * needs no CAMERA permission) to take one photo, JPEG-encodes it, hands the bytes to
 * [VisionBridge], and closes. Ported from the Iris project's audited vision pipeline.
 */
class CameraCaptureActivity : ComponentActivity() {

    private lateinit var photoUri: Uri

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val bytes = if (success) readCapturedJpeg() else null
        VisionBridge.deliverPhoto(bytes)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val photoFile = runCatching { File.createTempFile("max_capture_", ".jpg", cacheDir) }.getOrNull()
        if (photoFile == null) {
            VisionBridge.deliverPhoto(null)
            finish()
            return
        }

        photoUri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", photoFile)
        runCatching { takePicture.launch(photoUri) }.onFailure {
            VisionBridge.deliverPhoto(null)
            finish()
        }
    }

    private fun readCapturedJpeg(): ByteArray? = runCatching {
        contentResolver.openInputStream(photoUri)?.use { input ->
            val bitmap: Bitmap = BitmapFactory.decodeStream(input) ?: return null
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        }
    }.getOrNull()
}
