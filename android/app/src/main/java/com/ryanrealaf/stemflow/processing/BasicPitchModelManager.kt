package com.ryanrealaf.stemflow.processing

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class BasicPitchModelManager(private val modelDirectory: File) {
    companion object {
        const val MODEL_ID = "spotify-basic-pitch-icassp-2022"
        const val SAMPLE_RATE = 22_050
        const val INPUT_SAMPLES = 43_844
        const val FFT_HOP = 256
        const val OUTPUT_FRAMES = 172
        const val PITCHES = 88
        private const val MODEL_URL =
            "https://raw.githubusercontent.com/spotify/basic-pitch/main/basic_pitch/saved_models/icassp_2022/nmp.onnx"
        private const val MIN_MODEL_BYTES = 1_000_000L
    }

    fun modelFile(): File {
        modelDirectory.mkdirs()
        val target = File(modelDirectory, "nmp.onnx")
        if (target.isFile && target.length() >= MIN_MODEL_BYTES) return target
        download(target)
        return target
    }

    private fun download(target: File) {
        val partial = File(target.parentFile, target.name + ".part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            require(connection.responseCode in 200..299) {
                "Basic Pitch model download failed: HTTP " + connection.responseCode
            }
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered(64 * 1024).use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            require(partial.length() >= MIN_MODEL_BYTES) { "Downloaded Basic Pitch model is incomplete" }
            check(partial.renameTo(target)) { "Could not install Basic Pitch model" }
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }
}
