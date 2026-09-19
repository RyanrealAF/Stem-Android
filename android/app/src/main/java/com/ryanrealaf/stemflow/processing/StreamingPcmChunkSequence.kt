package com.ryanrealaf.stemflow.processing

import android.content.ContentResolver
import android.net.Uri
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class StreamingPcmChunkSequence(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val chunkFrames: Int = 22050 * 12
) : PcmChunkSequence {
    override fun iterator(): Iterator<PcmChunk> {
        val queue = ArrayBlockingQueue<Any>(2)
        val end = Any()
        val worker = Thread {
            try {
                AudioPcmDecoder(resolver, chunkFrames).decode(uri, object : PcmChunkConsumer {
                    override fun onChunk(chunk: PcmChunk) {
                        queue.put(chunk)
                    }
                })
                queue.put(end)
            } catch (t: Throwable) {
                queue.put(DecodeFailure(t))
            }
        }.apply {
            name = "StemFlow-PcmDecoder"
            isDaemon = true
            start()
        }

        return object : Iterator<PcmChunk> {
            private var next: Any? = null
            private var finished = false

            private fun fill() {
                if (next != null || finished) return
                next = queue.take()
                if (next === end) {
                    finished = true
                    next = null
                }
                if (next is DecodeFailure) {
                    val error = (next as DecodeFailure).cause
                    finished = true
                    next = null
                    throw IllegalStateException("PCM decoding failed", error)
                }
            }

            override fun hasNext(): Boolean {
                fill()
                return !finished
            }

            override fun next(): PcmChunk {
                fill()
                if (finished) throw NoSuchElementException()
                @Suppress("UNCHECKED_CAST")
                return next.also { next = null } as PcmChunk
            }
        }
    }

    private data class DecodeFailure(val cause: Throwable)
}
