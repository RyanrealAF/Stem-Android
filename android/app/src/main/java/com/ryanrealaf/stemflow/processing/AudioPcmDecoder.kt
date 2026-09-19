package com.ryanrealaf.stemflow.processing

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

data class PcmChunk(
    val sampleRate: Int,
    val channelCount: Int,
    val presentationTimeUs: Long,
    val samples: FloatArray
)

interface PcmChunkConsumer {
    fun onChunk(chunk: PcmChunk)
}

class AudioPcmDecoder(
    private val resolver: ContentResolver,
    private val chunkFrames: Int = 22050 * 12
) {
    fun decode(uri: Uri, consumer: PcmChunkConsumer) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(resolver.openFileDescriptor(uri, "r")!!.fileDescriptor)

            var track = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = index
                    format = candidate
                    break
                }
            }
            require(track >= 0 && format != null) { "No audio track found" }

            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME type missing")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = FloatChunkAccumulator(chunkFrames, channels, sampleRate, consumer)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime,
                                extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && bufferInfo.size > 0) {
                            output.position(bufferInfo.offset)
                            output.limit(bufferInfo.offset + bufferInfo.size)
                            pcm.accept(output.slice(), bufferInfo.presentationTimeUs)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decodedFormat = codec.outputFormat
                        require(decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, 2) == 2) {
                            "Decoder did not produce PCM 16-bit output"
                        }
                    }
                }
            }
            pcm.finish()
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private class FloatChunkAccumulator(
        private val chunkFrames: Int,
        private val channels: Int,
        private val sampleRate: Int,
        private val consumer: PcmChunkConsumer
    ) {
        private var buffer = FloatArray(chunkFrames * channels)
        private var size = 0
        private var timestampUs = 0L

        fun accept(bytes: ByteBuffer, presentationTimeUs: Long) {
            if (size == 0) timestampUs = presentationTimeUs
            while (bytes.remaining() >= 2) {
                if (size == buffer.size) flush()
                val value = bytes.getShort().toInt() / 32768f
                buffer[size++] = value
            }
        }

        fun finish() {
            if (size > 0) flush()
        }

        private fun flush() {
            val chunk = buffer.copyOf(size)
            consumer.onChunk(PcmChunk(sampleRate, channels, timestampUs, chunk))
            size = 0
            timestampUs += ((chunk.size / channels).toLong() * 1_000_000L) / sampleRate
        }
    }
}
