package com.ryanrealaf.stemflow.processing

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.floor

class BasicPitchWindowReader(
    private val wavFile: File,
    private val sourceSampleRate: Int = 44_100
) {
    companion object {
        const val TARGET_RATE = BasicPitchModelManager.SAMPLE_RATE
        const val WINDOW = BasicPitchModelManager.INPUT_SAMPLES
        const val OVERLAP_FRAMES = 30
        const val HOP = WINDOW - OVERLAP_FRAMES * BasicPitchModelManager.FFT_HOP
    }

    fun windows(): Sequence<FloatArray> = sequence {
        val mono = readMono16BitWav(wavFile)
        val resampled = linearResample(mono, sourceSampleRate, TARGET_RATE)
        var start = 0
        while (start < resampled.size) {
            val window = FloatArray(WINDOW)
            val available = minOf(WINDOW, resampled.size - start)
            resampled.copyInto(window, 0, start, start + available)
            yield(window)
            if (start + WINDOW >= resampled.size) break
            start += HOP
        }
    }

    private fun readMono16BitWav(file: File): FloatArray {
        RandomAccessFile(file, "r").use { raf ->
            require(String(ByteArray(4).also { raf.readFully(it) }) == "RIFF")
            raf.seek(22)
            val channels = readLeShort(raf)
            val rate = readLeInt(raf)
            require(rate == sourceSampleRate) { "Unexpected stem sample rate: $rate" }
            raf.seek(34)
            val bits = readLeShort(raf)
            require(bits == 16) { "Basic Pitch requires 16-bit PCM WAV stems" }
            raf.seek(12)
            var dataSize = -1L
            while (raf.filePointer + 8 <= raf.length()) {
                val id = String(ByteArray(4).also { raf.readFully(it) })
                val size = readLeInt(raf).toLong()
                if (id == "data") { dataSize = size; break }
                raf.seek(raf.filePointer + size + (size and 1L))
            }
            require(dataSize >= 0) { "WAV data chunk not found" }
            val frameBytes = channels * 2
            val frames = (dataSize / frameBytes).toInt()
            val result = FloatArray(frames)
            val sample = ByteArray(frameBytes)
            for (i in 0 until frames) {
                raf.readFully(sample)
                var sum = 0f
                for (c in 0 until channels) {
                    val lo = sample[c * 2].toInt() and 255
                    val hi = sample[c * 2 + 1].toInt()
                    val s = ((hi shl 8) or lo).toShort().toInt()
                    sum += s / 32768f
                }
                result[i] = sum / channels
            }
            return result
        }
    }

    private fun linearResample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return input
        val outSize = floor(input.size.toDouble() * to / from).toInt()
        val output = FloatArray(outSize)
        val scale = from.toDouble() / to
        for (i in output.indices) {
            val p = i * scale
            val a = p.toInt().coerceIn(0, input.lastIndex)
            val b = (a + 1).coerceIn(0, input.lastIndex)
            val frac = (p - a).toFloat()
            output[i] = input[a] * (1f - frac) + input[b] * frac
        }
        return output
    }

    private fun readLeShort(raf: RandomAccessFile): Int =
        (raf.read() or (raf.read() shl 8))

    private fun readLeInt(raf: RandomAccessFile): Int =
        raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)
}
