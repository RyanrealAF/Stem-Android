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
        if (useXnnpack) runCatching { options.addXnnpack(mapOf("intra_op_num_threads" to "2")) }
        session = environment.createSession(modelFile.absolutePath, options)
    }

    fun inputNames(): Set<String> = session.inputNames
    fun outputNames(): Set<String> = session.outputNames

    fun runDemucs(values: FloatArray, samples: Int): Array<FloatArray> {
        require(values.size == 2 * samples)
        val input = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(values),
            longArrayOf(1, 2, samples.toLong())
        )
        try {
            session.run(mapOf("mix" to input)).use { result ->
                val value = result[0].value ?: error("Demucs returned no output")
                val batch = value as? Array<*> ?: error("Unexpected Demucs output type")
                val sources = batch.firstOrNull() as? Array<*>
                    ?: error("Unexpected Demucs batch shape")
                val output = Array(sources.size) { sourceIndex ->
                    val source = sources[sourceIndex] as? Array<*>
                        ?: error("Unexpected Demucs source shape")
                    require(source.size == 2) { "Demucs output is not stereo" }
                    val left = source[0] as? FloatArray ?: error("Unexpected left channel type")
                    val right = source[1] as? FloatArray ?: error("Unexpected right channel type")
                    require(left.size == samples && right.size == samples) {
                        "Demucs output sample count mismatch"
                    }
                    FloatArray(samples * 2).also { interleaved ->
                        var j = 0
                        for (i in 0 until samples) {
                            interleaved[j++] = left[i]
                            interleaved[j++] = right[i]
                        }
                    }
                }
                return output
            }
        } finally {
            input.close()
        }
    }

    fun runFloatTensor(name: String, shape: LongArray, values: FloatArray): Map<String, Any> {
        require(shape.fold(1L) { a, b -> a * b } == values.size.toLong()) {
            "Tensor shape does not match value count"
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape).use { input ->
            session.run(mapOf(name to input)).use { result ->
                return result.associate { value ->
                    value.name to (value.value ?: error("ONNX output has no value"))
                }
            }
        }
    }

    override fun close() {
        session.close()
    }
}
