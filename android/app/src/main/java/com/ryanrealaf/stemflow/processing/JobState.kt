package com.ryanrealaf.stemflow.processing

enum class JobPhase {
    VALIDATING,
    SEPARATING,
    TRANSCRIBING_VOCALS,
    TRANSCRIBING_DRUMS,
    TRANSCRIBING_BASS,
    TRANSCRIBING_GUITAR,
    TRANSCRIBING_PIANO,
    TRANSCRIBING_OTHER,
    EXPORTING,
    COMPLETE,
    FAILED,
    CANCELLED
}

data class JobState(
    val jobId: String,
    val inputUri: String,
    val phase: JobPhase,
    val progress: Float,
    val message: String,
    val error: String? = null
)
