package com.ryanrealaf.stemflow.processing

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavPcmWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int
) : AutoCloseable {
    private val output = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    private var dataBytes = 0L
    private var closed = false

    init {
        require(sampleRate > 0)
        require(channels > 0)
        writeHeader(0)
    }

    fun write(samples: FloatArray) {
        check(!closed)
        require(samples.size % channels == 0)
        val bytes = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach {
            val v = it.coerceIn(-1f, 1f)
            buffer.putShort(if (v < 0f) (v * 32768f).toInt().toShort() else (v * 32767f).toInt().toShort())
        }
        output.write(bytes)
        dataBytes += bytes.size
    }

    override fun close() {
        if (closed) return
        closed = true
        output.flush()
        output.close()
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4); raf.writeLeInt((36 + dataBytes).toInt())
            raf.seek(40); raf.writeLeInt(dataBytes.toInt())
        }
    }

    private fun writeHeader(dataSize: Long) {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt((36 + dataSize).toInt())
        h.put("WAVEfmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
        h.putInt(sampleRate); h.putInt(sampleRate * channels * 2); h.putShort((channels * 2).toShort()); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataSize.toInt())
        output.write(h.array())
    }
}

private fun java.io.RandomAccessFile.writeLeInt(v: Int) {
    write(byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte()))
}
