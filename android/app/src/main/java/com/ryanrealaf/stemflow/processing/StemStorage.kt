package com.ryanrealaf.stemflow.processing

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Stem(val fileName: String) {
    VOCALS("vocals.pcm"),
    DRUMS("drums.pcm"),
    BASS("bass.pcm"),
    GUITAR("guitar.pcm"),
    PIANO("piano.pcm"),
    OTHER("other.pcm")
}

class StemStorage(private val jobDirectory: File) {
    private val stemsDirectory = File(jobDirectory, "stems").apply { mkdirs() }

    fun file(stem: Stem): File = File(stemsDirectory, stem.fileName)

    fun appendFloatPcm(stem: Stem, samples: FloatArray) {
        if (samples.isEmpty()) return
        BufferedOutputStream(FileOutputStream(file(stem), true), 64 * 1024).use { output ->
            val bytes = ByteArray(samples.size * 4)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach(buffer::putFloat)
            output.write(bytes)
        }
    }

    fun openReader(stem: Stem): FloatPcmReader =
        FloatPcmReader(file(stem))
}

class FloatPcmReader(private val file: File) : AutoCloseable {
    private val input = BufferedInputStream(FileInputStream(file), 64 * 1024)
    private val bytes = ByteArray(64 * 1024)

    fun read(maxSamples: Int): FloatArray {
        require(maxSamples > 0)
        val requestedBytes = minOf(bytes.size, maxSamples * 4)
        var offset = 0
        while (offset < requestedBytes) {
            val count = input.read(bytes, offset, requestedBytes - offset)
            if (count < 0) break
            offset += count
        }
        if (offset == 0) return FloatArray(0)
        val sampleCount = offset / 4
        val buffer = ByteBuffer.wrap(bytes, 0, sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(sampleCount) { buffer.float }
    }

    override fun close() {
        input.close()
    }
}
