package com.ryanrealaf.stemflow.processing

import java.io.File

data class SeparatorMetadata(
    val modelId: String,
    val sampleRate: Int,
    val channelCount: Int,
    val stems: List<Stem>
)

interface StemSeparator {
    val metadata: SeparatorMetadata

    fun separate(
        pcm: PcmChunkSequence,
        outputDirectory: File,
        progress: (Float, String) -> Unit
    )
}

interface PcmChunkSequence : Sequence<PcmChunk>

class UnsupportedSeparator(
    override val metadata: SeparatorMetadata = SeparatorMetadata(
        modelId = "none",
        sampleRate = 44_100,
        channelCount = 2,
        stems = Stem.entries.toList()
    )
) : StemSeparator {
    override fun separate(
        pcm: PcmChunkSequence,
        outputDirectory: File,
        progress: (Float, String) -> Unit
    ) {
        error("No mobile neural separator is installed")
    }
}
