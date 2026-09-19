package com.ryanrealaf.stemflow.processing

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class MidiNote(val pitch: Int, val startUs: Long, val durationUs: Long, val velocity: Int = 100)

class MidiWriter(private val ticksPerQuarter: Int = 480, private val tempoBpm: Double = 120.0) {
    fun write(file: File, notes: List<MidiNote>) {
        val track = ByteArrayOutputStream()
        val usPerQuarter = 60_000_000.0 / tempoBpm
        writeVlq(track, 0)
        track.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03))
        val tempo = usPerQuarter.toInt()
        track.write(byteArrayOf((tempo shr 16).toByte(), (tempo shr 8).toByte(), tempo.toByte()))

        val events = notes.flatMap { n ->
            val start = (n.startUs / usPerQuarter * ticksPerQuarter).toLong()
            val end = ((n.startUs + n.durationUs) / usPerQuarter * ticksPerQuarter).toLong()
            listOf(start to byteArrayOf(0x90.toByte(), n.pitch.coerceIn(0,127).toByte(), n.velocity.coerceIn(1,127).toByte()),
                   end to byteArrayOf(0x80.toByte(), n.pitch.coerceIn(0,127).toByte(), 0))
        }.sortedBy { it.first }

        var previous = 0L
        for ((tick, message) in events) {
            writeVlq(track, (tick - previous).coerceAtLeast(0))
            track.write(message)
            previous = tick
        }
        writeVlq(track, 0)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        FileOutputStream(file).use { out ->
            out.write("MThd".toByteArray()); writeBeInt(out, 6); writeBeShort(out, 0); writeBeShort(out, 1); writeBeShort(out, ticksPerQuarter)
            out.write("MTrk".toByteArray()); writeBeInt(out, track.size()); track.writeTo(out)
        }
    }

    private fun writeVlq(out: ByteArrayOutputStream, value: Long) {
        var buffer = value and 0x7F
        var v = value ushr 7
        while (v != 0L) { buffer = (buffer shl 8) or ((v and 0x7F) or 0x80); v = v ushr 7 }
        while (true) { out.write((buffer and 0xFF).toInt()); if ((buffer and 0x80) != 0L) buffer = buffer ushr 8 else return }
    }

    private fun writeBeInt(out: java.io.OutputStream, v: Int) = out.write(byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))
    private fun writeBeShort(out: java.io.OutputStream, v: Int) = out.write(byteArrayOf((v shr 8).toByte(), v.toByte()))
}
