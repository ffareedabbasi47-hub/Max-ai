package com.example.wake

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads the small offline speech model used by [VoskWakeWordEngine] ONCE (about 40 MB, free,
 * no account or API key), then everything runs offline. The model is stored in app-private storage.
 */
class WakeModelManager(private val context: Context) {

    private val root = File(context.filesDir, "wake")
    val modelDir = File(root, MODEL_NAME)

    fun isReady(): Boolean = File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory

    /** Blocking. Returns null on success or a short user-readable error. */
    fun download(onProgress: (Int) -> Unit = {}): String? {
        if (isReady()) return null
        root.mkdirs()
        val zip = File(context.cacheDir, "$MODEL_NAME.zip")
        val tmp = File(root, "$MODEL_NAME.tmp")
        try {
            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) return "Model download fail (HTTP ${conn.responseCode})"
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            tmp.deleteRecursively()
            tmp.mkdirs()
            unzipSafely(zip, tmp)
            val extracted = File(tmp, MODEL_NAME)
            if (!File(extracted, "am").isDirectory) return "Model file kharab hai, dobara try karo"
            modelDir.deleteRecursively()
            if (!extracted.renameTo(modelDir)) return "Model save nahi ho paaya"
            return null
        } catch (e: Exception) {
            return "Model download fail: internet check karo"
        } finally {
            zip.delete()
            tmp.deleteRecursively()
        }
    }

    private fun unzipSafely(zip: File, dest: File) {
        val destPath = dest.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(dest, entry.name)
                // Zip-slip guard: never write outside the destination folder.
                if (!out.canonicalPath.startsWith(destPath)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
            }
        }
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    }
}
