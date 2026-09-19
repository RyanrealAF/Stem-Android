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
