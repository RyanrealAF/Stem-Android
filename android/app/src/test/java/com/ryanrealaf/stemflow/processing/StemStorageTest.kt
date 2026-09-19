package com.ryanrealaf.stemflow.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class StemStorageTest {
    @Test
    fun writesAndReadsFloatPcmWithoutWholeStemRequirement() {
        val root = Files.createTempDirectory("stemflow-storage").toFile()
        try {
            val storage = StemStorage(root)
            val expected = floatArrayOf(-1f, -0.25f, 0f, 0.5f, 1f)
            storage.appendFloatPcm(Stem.BASS, expected)

            storage.openReader(Stem.BASS).use { reader ->
                val first = reader.read(2)
                val second = reader.read(3)

                assertArrayEquals(floatArrayOf(-1f, -0.25f), first, 0f)
                assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), second, 0f)
                assertEquals(0, reader.read(1).size)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
