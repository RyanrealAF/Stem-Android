package com.ryanrealaf.stemflow.processing

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JobArchiveExporter {
    fun export(jobDirectory: File, outputZip: File) {
        require(jobDirectory.isDirectory) { "Job directory does not exist" }
        outputZip.parentFile?.mkdirs()
        ZipOutputStream(outputZip.outputStream().buffered()).use { zip ->
            jobDirectory.walkTopDown()
                .filter { it.isFile && it != outputZip }
                .forEach { file ->
                    val relative = file.relativeTo(jobDirectory).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { input -> input.copyTo(zip, DEFAULT_BUFFER) }
                    zip.closeEntry()
                }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
    }
}
