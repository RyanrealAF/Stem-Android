package com.ryanrealaf.stemflow.processing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

class OnnxModelRunner(
    modelFile: File,
    private val useXnnpack: Boolean = true
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        require(modelFile.isFile) { "ONNX model not found: " + modelFile.absolutePath }
        val options = OrtSession.SessionOptions()
        if (useXnnpack) runCatching {
            options.addXnnpack(mapOf("intra_op_num_threads" to "2"))
        }
        session = environment.createSession(modelFile.absolutePath, options)
    }

    fun inputNames(): Set<String> = session.inputNames
    fun outputNames(): Set<String> = session.outputNames

    fun runBasicPitch(values: FloatArray): Map<String, FloatArray> {
        require(values.size == BasicPitchModelManager.INPUT_SAMPLES)
        val inputName = session.inputNames.first()
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(values),
            longArrayOf(1, values.size.toLong(), 1L)
        ).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                return result.associate { value ->
                    value.name to flatten(value.value ?: error("Basic Pitch output has no value"))
                }
            }
        }
    }

    fun runDemucs(values: FloatArray, samples: Int): Array<FloatArray> {
        require(values.size == 2 * samples)
        val input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values),
            longArrayOf(1, 2, samples.toLong()))
        try {
            session.run(mapOf("mix" to input)).use { result ->
                val value = result[0].value ?: error("Demucs returned no output")
                val batch = value as? Array<*> ?: error("Unexpected Demucs output type")
                val sources = batch.firstOrNull() as? Array<*> ?: error("Unexpected Demucs batch shape")
                return Array(sources.size) { sourceIndex ->
                    val source = sources[sourceIndex] as? Array<*> ?: error("Unexpected Demucs source shape")
                    require(source.size == 2)
                    val left = source[0] as? FloatArray ?: error("Unexpected left channel type")
                    val right = source[1] as? FloatArray ?: error("Unexpected right channel type")
                    require(left.size == samples && right.size == samples)
                    FloatArray(samples * 2).also {
                        var j = 0
                        for (i in 0 until samples) { it[j++] = left[i]; it[j++] = right[i] }
                    }
                }
            }
        } finally { input.close() }
    }

    private fun flatten(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> {
            val parts = value.map { flatten(it ?: error("Null ONNX tensor element")) }
            FloatArray(parts.sumOf { it.size }).also { out ->
                var offset = 0
                parts.forEach { part -> part.copyInto(out, offset); offset += part.size }
            }
        }
        else -> error("Unsupported ONNX tensor output type: " + value::class.java.name)
    }

    override fun close() { session.close() }
}
