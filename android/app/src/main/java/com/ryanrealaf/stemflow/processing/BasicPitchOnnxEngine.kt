package com.ryanrealaf.stemflow.processing

import java.io.File

data class BasicPitchModelInfo(
    val modelId: String = "spotify-basic-pitch-icassp-2022",
    val sampleRate: Int = 22_050,
    val channels: Int = 1
)

class BasicPitchOnnxEngine(
    private val modelFile: File
) : StemTranscriber {
    val metadata = BasicPitchModelInfo()

    override fun transcribe(
        stem: Stem,
        wavFile: File,
        outputMidi: File,
        progress: (Float, String) -> Unit
    ) {
        require(modelFile.isFile) { "Basic Pitch ONNX model not found: " + modelFile.absolutePath }
        require(wavFile.isFile) { "Stem WAV not found: " + wavFile.absolutePath }
        error(
            "Basic Pitch ONNX model is present, but feature extraction and note decoding " +
            "are not installed yet. Refusing to emit synthetic MIDI."
        )
    }
}
