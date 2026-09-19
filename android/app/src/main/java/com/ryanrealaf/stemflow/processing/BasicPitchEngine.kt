package com.ryanrealaf.stemflow.processing

import java.io.File

interface StemMidiTranscriber {
    fun transcribe(stem: Stem, wavFile: File, midiFile: File, progress: (Float, String) -> Unit)
}

class UnsupportedBasicPitchEngine : StemMidiTranscriber {
    override fun transcribe(stem: Stem, wavFile: File, midiFile: File, progress: (Float, String) -> Unit) {
        error("Basic Pitch Android model runtime is not installed")
    }
}
