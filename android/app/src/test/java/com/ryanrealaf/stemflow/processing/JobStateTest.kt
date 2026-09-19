package com.ryanrealaf.stemflow.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class JobStateTest {
    @Test
    fun processingPhasesAreStable() {
        assertEquals(JobPhase.VALIDATING, JobPhase.valueOf("VALIDATING"))
        assertEquals(JobPhase.TRANSCRIBING_DRUMS, JobPhase.valueOf("TRANSCRIBING_DRUMS"))
        assertEquals(JobPhase.EXPORTING, JobPhase.valueOf("EXPORTING"))
        assertEquals(JobPhase.COMPLETE, JobPhase.valueOf("COMPLETE"))
    }
}
