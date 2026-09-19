package com.ryanrealaf.stemflow.processing

import android.content.Context
import java.io.File
import kotlin.math.max

class HtDemucs6sSeparator(
    context: Context,
    private val modelManager: DemucsModelManager = DemucsModelManager(File(context.filesDir, "stemflow/models"))
) : StemSeparator {
    override val metadata = SeparatorMetadata(
        modelId = DemucsModelManager.MODEL_ID,
        sampleRate = DemucsModelManager.SAMPLE_RATE,
        channelCount = 2,
        stems = Stem.entries.toList()
    )

    override fun separate(
        pcm: PcmChunkSequence,
        outputDirectory: File,
        progress: (Float, String) -> Unit
    ) {
        outputDirectory.mkdirs()
        val writers = Stem.entries.associateWith {
            WavPcmWriter(
                File(outputDirectory, it.name.lowercase() + ".wav"),
                DemucsModelManager.SAMPLE_RATE,
                2
            )
        }
        val overlap = DemucsModelManager.SEGMENT_SAMPLES - DemucsModelManager.STRIDE_SAMPLES
        val window = transitionWindow(DemucsModelManager.SEGMENT_SAMPLES)
        val tails = Stem.entries.associateWith { FloatArray(overlap * 2) }
        val runner = OnnxModelRunner(modelManager.modelFile())
        val rolling = RollingStereoBuffer(DemucsModelManager.SEGMENT_SAMPLES * 2)
        var processedFrames = 0L
        var totalFramesHint = 0L

        try {
            pcm.forEach { chunk ->
                require(chunk.sampleRate == DemucsModelManager.SAMPLE_RATE) {
                    "HTDemucs prototype currently requires 44.1 kHz input; got " + chunk.sampleRate + " Hz"
                }
                require(chunk.channelCount in 1..2) { "HTDemucs supports mono or stereo input only" }
                rolling.append(toStereo(chunk))
                while (rolling.size >= DemucsModelManager.SEGMENT_SAMPLES * 2) {
                    val segment = rolling.peek(DemucsModelManager.SEGMENT_SAMPLES * 2)
                    val outputs = runner.runDemucs(segment, DemucsModelManager.SEGMENT_SAMPLES)
                    emit(outputs, writers, tails, window, overlap)
                    rolling.discard(DemucsModelManager.STRIDE_SAMPLES * 2)
                    processedFrames += DemucsModelManager.STRIDE_SAMPLES
                    totalFramesHint = max(totalFramesHint, processedFrames + rolling.size / 2L)
                    progress(0.0f, "Separated " + processedFrames + " frames")
                }
            }

            if (rolling.size > 0) {
                val actualFrames = rolling.size / 2
                val padded = FloatArray(DemucsModelManager.SEGMENT_SAMPLES * 2)
                rolling.copyInto(padded)
                val outputs = runner.runDemucs(padded, DemucsModelManager.SEGMENT_SAMPLES)
                emit(outputs, writers, tails, window, overlap, actualFrames)
            }

            tails.forEach { (stem, tail) -> writers[stem]!!.write(tail) }
            val finalProgress = if (totalFramesHint > 0) 1f else 0f
            progress(finalProgress, "Six stems written")
        } finally {
            runner.close()
            writers.values.forEach { runCatching { it.close() } }
        }
    }

    private fun emit(
        outputs: Array<FloatArray>,
        writers: Map<Stem, WavPcmWriter>,
        tails: Map<Stem, FloatArray>,
        window: FloatArray,
        overlap: Int,
        actualFrames: Int = DemucsModelManager.SEGMENT_SAMPLES
    ) {
        Stem.entries.forEachIndexed { index, stem ->
            val source = outputs[index]
            val tail = tails[stem]!!
            val validFrames = actualFrames.coerceIn(1, DemucsModelManager.SEGMENT_SAMPLES)
            val first = FloatArray(DemucsModelManager.STRIDE_SAMPLES * 2)
            for (frame in 0 until DemucsModelManager.STRIDE_SAMPLES.coerceAtMost(validFrames)) {
                val w = window[frame]
                val src = frame * 2
                first[src] = source[src] * w
                first[src + 1] = source[src + 1] * w
            }
            if (tail.any { it != 0f }) {
                for (frame in 0 until overlap) {
                    val src = frame * 2
                    val w = window[DemucsModelManager.STRIDE_SAMPLES + frame]
                    val denom = max(1e-6f, 1f - w + w)
                    first[src] = (tail[src] + source[src] * w) / denom
                    first[src + 1] = (tail[src + 1] + source[src + 1] * w) / denom
                }
            }
            writers[stem]!!.write(first)
            val tailStart = DemucsModelManager.STRIDE_SAMPLES * 2
            for (frame in 0 until overlap) {
                val src = (tailStart + frame * 2)
                val w = window[DemucsModelManager.STRIDE_SAMPLES + frame]
                tail[frame * 2] = source[src] * w
                tail[frame * 2 + 1] = source[src + 1] * w
            }
        }
    }

    private fun transitionWindow(size: Int): FloatArray {
        val overlap = size / 4
        return FloatArray(size) { i ->
            when {
                i < overlap -> i.toFloat() / overlap
                i >= size - overlap -> (size - i).toFloat() / overlap
                else -> 1f
            }
        }
    }

    private fun toStereo(chunk: PcmChunk): FloatArray {
        if (chunk.channelCount == 2) return chunk.samples
        val out = FloatArray(chunk.samples.size * 2)
        chunk.samples.forEachIndexed { i, value ->
            out[i * 2] = value
            out[i * 2 + 1] = value
        }
        return out
    }

    private class RollingStereoBuffer(private val capacity: Int) {
        private var data = FloatArray(capacity * 2)
        var size: Int = 0
            private set

        fun append(values: FloatArray) {
            if (values.isEmpty()) return
            if (size + values.size > data.size) {
                var newSize = data.size
                while (newSize < size + values.size) newSize *= 2
                data = data.copyOf(newSize)
            }
            values.copyInto(data, size)
            size += values.size
        }

        fun peek(count: Int): FloatArray = data.copyOfRange(0, count)

        fun copyInto(target: FloatArray) {
            data.copyInto(target, 0, 0, minOf(size, target.size))
        }

        fun discard(count: Int) {
            require(count <= size)
            data.copyInto(data, 0, count, size)
            size -= count
        }
    }
}
