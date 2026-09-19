package com.ryanrealaf.stemflow.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JobRepositoryTest {
    @Test
    fun persistsAndLoadsJobState() {
        val root = Files.createTempDirectory("stemflow-jobs").toFile()
        try {
            val repository = JobRepository(root)
            val created = repository.create("content://test/audio")
            val updated = created.copy(
                phase = JobPhase.TRANSCRIBING_BASS,
                progress = 0.62f,
                message = "Transcribing bass"
            )
            repository.save(updated)

            val loaded = repository.load(created.jobId)

            assertNotNull(loaded)
            assertEquals(updated.jobId, loaded!!.jobId)
            assertEquals(updated.inputUri, loaded.inputUri)
            assertEquals(updated.phase, loaded.phase)
            assertEquals(updated.progress, loaded.progress, 0.0001f)
            assertEquals(updated.message, loaded.message)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun incompleteExcludesTerminalJobs() {
        val root = Files.createTempDirectory("stemflow-jobs").toFile()
        try {
            val repository = JobRepository(root)
            val active = repository.create("content://active/audio")
            repository.save(active.copy(phase = JobPhase.SEPARATING))
            val complete = repository.create("content://complete/audio")
            repository.save(complete.copy(phase = JobPhase.COMPLETE))
            val failed = repository.create("content://failed/audio")
            repository.save(failed.copy(phase = JobPhase.FAILED))

            val incomplete = repository.incomplete()

            assertEquals(1, incomplete.size)
            assertEquals(active.jobId, incomplete.single().jobId)
            assertTrue(incomplete.single().phase == JobPhase.SEPARATING)
        } finally {
            root.deleteRecursively()
        }
    }
}
