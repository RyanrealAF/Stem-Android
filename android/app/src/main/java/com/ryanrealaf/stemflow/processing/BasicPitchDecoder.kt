package com.ryanrealaf.stemflow.processing

import kotlin.math.max

data class BasicPitchNote(
    val startFrame: Int,
    val endFrame: Int,
    val midiPitch: Int,
    val amplitude: Float
)

object BasicPitchDecoder {
    const val AUDIO_SAMPLE_RATE = 22_050
    const val FFT_HOP = 256
    const val AUDIO_WINDOW_LENGTH_SECONDS = 2
    const val AUDIO_N_SAMPLES = AUDIO_SAMPLE_RATE * AUDIO_WINDOW_LENGTH_SECONDS - FFT_HOP
    const val ANNOT_N_FRAMES = 172
    const val MIDI_OFFSET = 21
    const val MAX_FREQ_IDX = 87
    const val DEFAULT_ONSET_THRESHOLD = 0.5f
    const val DEFAULT_FRAME_THRESHOLD = 0.3f
    const val DEFAULT_MIN_NOTE_LENGTH_FRAMES = 11
    const val ENERGY_TOLERANCE = 11

    fun inferOnsets(onsets: Array<FloatArray>, frames: Array<FloatArray>): Array<FloatArray> {
        require(onsets.size == frames.size)
        if (onsets.isEmpty()) return onsets
        val result = Array(onsets.size) { onsets[it].clone() }
        val maxOnset = onsets.maxOfOrNull { row -> row.maxOrNull() ?: 0f } ?: 0f
        if (maxOnset <= 0f) return result
        val diff = Array(onsets.size) { FloatArray(onsets[it].size) }
        for (n in 1..2) {
            for (t in n until frames.size) {
                val current = frames[t]
                val previous = frames[t - n]
                for (f in current.indices) diff[t][f] = max(diff[t][f], current[f] - previous[f])
            }
        }
        val diffMax = diff.maxOfOrNull { it.maxOrNull() ?: 0f } ?: 0f
        if (diffMax > 0f) {
            for (t in diff.indices) for (f in diff[t].indices) {
                result[t][f] = max(result[t][f], maxOnset * diff[t][f] / diffMax)
            }
        }
        return result
    }

    fun decode(
        frames: Array<FloatArray>,
        onsets: Array<FloatArray>,
        onsetThreshold: Float = DEFAULT_ONSET_THRESHOLD,
        frameThreshold: Float = DEFAULT_FRAME_THRESHOLD,
        minNoteLength: Int = DEFAULT_MIN_NOTE_LENGTH_FRAMES,
        inferOnsets: Boolean = true
    ): List<BasicPitchNote> {
        require(frames.size == onsets.size)
        if (frames.isEmpty()) return emptyList()
        val effectiveOnsets = if (inferOnsets) inferOnsets(onsets, frames) else onsets
        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        for (f in effectiveOnsets.indices) {
            for (pitch in effectiveOnsets[f].indices.take(88)) {
                if (effectiveOnsets[f][pitch] >= onsetThreshold &&
                    (f == 0 || effectiveOnsets[f - 1][pitch] <= effectiveOnsets[f][pitch]) &&
                    (f + 1 >= effectiveOnsets.size || effectiveOnsets[f + 1][pitch] <= effectiveOnsets[f][pitch])) {
                    candidates += Triple(f, pitch, effectiveOnsets[f][pitch])
                }
            }
        }
        candidates.sortByDescending { it.third }
        val remaining = Array(frames.size) { t -> frames[t].clone() }
        val notes = mutableListOf<BasicPitchNote>()
        for ((start, pitch, _) in candidates) {
            if (start >= frames.lastIndex) continue
            var end = start + 1
            var below = 0
            while (end < frames.size) {
                if (remaining[end][pitch] < frameThreshold) below++ else below = 0
                if (below >= ENERGY_TOLERANCE) break
                end++
            }
            end -= below
            if (end - start <= minNoteLength) continue
            val amp = remaining.subList(start, end).map { it[pitch] }.average().toFloat()
            notes += BasicPitchNote(start, end, pitch + MIDI_OFFSET, amp)
            for (t in start until end) {
                remaining[t][pitch] = 0f
                if (pitch > 0) remaining[t][pitch - 1] = 0f
                if (pitch + 1 < remaining[t].size) remaining[t][pitch + 1] = 0f
            }
        }
        return notes.sortedBy { it.startFrame }
    }
}
