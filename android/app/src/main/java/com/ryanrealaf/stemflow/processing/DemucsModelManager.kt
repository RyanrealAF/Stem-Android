package com.ryanrealaf.stemflow.processing

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DemucsModelManager(private val modelDirectory: File) {
    companion object {
        const val MODEL_ID = "htdemucs_6s"
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val SEGMENT_SAMPLES = 343_980
        const val STRIDE_SAMPLES = SEGMENT_SAMPLES * 3 / 4
        private const val MODEL_URL =
            "https://huggingface.co/StemSplitio/htdemucs-6s-onnx/resolve/main/htdemucs_6s_fp16weights.onnx"
    }

    fun modelFile(): File {
        modelDirectory.mkdirs()
        val target = File(modelDirectory, "htdemucs_6s_fp16weights.onnx")
        if (target.isFile && target.length() > 100_000_000L) return target
        download(target)
        return target
    }

    private fun download(target: File) {
        val partial = File(target.parentFile, target.name + ".part")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.connect()
        require(connection.responseCode in 200..299) {
            "Demucs model download failed: HTTP " + connection.responseCode
        }
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(partial.length() > 100_000_000L) { "Downloaded Demucs model is incomplete" }
        check(partial.renameTo(target)) { "Unable to finalize Demucs model file" }
    }
}
