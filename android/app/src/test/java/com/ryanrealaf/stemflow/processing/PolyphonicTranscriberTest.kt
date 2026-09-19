package com.ryanrealaf.stemflow.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream

class PolyphonicTranscriberTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDecoderCreatesNotesFromActivations() {
        val decoder = PolyphonicNoteDecoder(frameThreshold = 0.5f, minNoteDurationSec = 0.05f)
        val activations = Array(10) { FloatArray(88) }

        // Set pitch index 39 (MIDI pitch 60 - C4) active for frames 2..6
        for (f in 2..6) {
            activations[f][39] = 0.8f
        }

        val notes = decoder.decodeFrameActivations(activations, frameDurationSec = 0.02f)

        assertEquals(1, notes.size)
        val note = notes[0]
        assertEquals(60, note.pitch)
        assertEquals(0.04f, note.startTimeSec, 0.001f)
        assertEquals(0.10f, note.durationSec, 0.001f)
        assertTrue(note.velocity > 0)
    }

    @Test
    fun testMidiWriterProducesValidHeaderAndData() {
        val writer = StandardMidiWriter(ppq = 480, bpm = 120)
        val file = tempFolder.newFile("test.mid")

        val notes = listOf(
            PitchNote(pitch = 60, startTimeSec = 0.0f, durationSec = 0.5f, velocity = 100),
            PitchNote(pitch = 64, startTimeSec = 0.5f, durationSec = 0.5f, velocity = 90)
        )

        writer.writeMidi(notes, file)

        assertTrue(file.exists())
        assertTrue(file.length() > 14) // Header is at least 14 bytes

        val header = ByteArray(4)
        FileInputStream(file).use { it.read(header) }
        assertEquals("MThd", String(header, Charsets.US_ASCII))
    }
}
