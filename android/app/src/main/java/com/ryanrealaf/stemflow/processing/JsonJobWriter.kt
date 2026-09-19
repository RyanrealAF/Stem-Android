package com.ryanrealaf.stemflow.processing

import java.io.File

object JsonJobWriter {
    fun write(file: File, state: JobState, input: AudioInfo? = null) {
        val json = buildString {
            append("{")
            append("\"jobId\":\"").append(escape(state.jobId)).append("\",")
            append("\"inputUri\":\"").append(escape(state.inputUri)).append("\",")
            append("\"phase\":\"").append(state.phase.name).append("\",")
            append("\"progress\":").append(state.progress).append(",")
            append("\"message\":\"").append(escape(state.message)).append("\"")
            if (input != null) {
                append(",\"audio\":{")
                append("\"mimeType\":\"").append(escape(input.mimeType)).append("\",")
                append("\"sampleRate\":").append(input.sampleRate).append(",")
                append("\"channels\":").append(input.channelCount).append(",")
                append("\"durationUs\":").append(input.durationUs)
                append("}")
            }
            state.error?.let { append(",\"error\":\"").append(escape(it)).append("\"") }
            append("}")
        }
        file.writeText(json)
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
