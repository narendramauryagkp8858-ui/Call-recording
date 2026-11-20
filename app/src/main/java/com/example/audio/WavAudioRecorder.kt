package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WavAudioRecorder(
    private val outputFile: File,
    private val sampleRateHz: Int,
    private val audioSource: Int,
    private val onAmplitude: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var writeJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var totalAudioBytes: Long = 0L

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
            .coerceAtLeast(4096)

        val recorder = AudioRecord(
            audioSource,
            sampleRateHz,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize for source $audioSource")
        }

        audioRecord = recorder
        totalAudioBytes = 0L
        isPaused.set(false)
        isRecording.set(true)

        FileOutputStream(outputFile).use { fos ->
            fos.write(createWavHeader(sampleRateHz, 1, 16, 0L))
        }

        recorder.startRecording()

        writeJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufferSize)
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(44L)
                while (isActive && isRecording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        if (!isPaused.get()) {
                            raf.write(buffer, 0, read)
                            totalAudioBytes += read
                            onAmplitude(computeNormalizedAmplitude(buffer, read))
                        } else {
                            onAmplitude(0f)
                        }
                    }
                }
                // Update RIFF and data chunk sizes in header
                writeWavHeaderSizes(raf, totalAudioBytes)
            }
        }
    }

    fun pause() {
        isPaused.set(true)
        onAmplitude(0f)
    }

    fun resume() {
        isPaused.set(false)
    }

    suspend fun stop() {
        isRecording.set(false)
        isPaused.set(false)
        runCatching { audioRecord?.stop() }
        writeJob?.join()
        runCatching { audioRecord?.release() }
        audioRecord = null
        writeJob = null
        onAmplitude(0f)
    }

    fun releaseImmediate() {
        isRecording.set(false)
        isPaused.set(false)
        writeJob?.cancel()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        writeJob = null
    }

    private fun computeNormalizedAmplitude(pcmBytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = (high shl 8) or low
            val norm = abs(sample) / 32768.0
            sumSquares += norm * norm
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = sqrt(sumSquares / samples)
        return (rms * 3.2).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        fun createWavHeader(
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int,
            dataLength: Long
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val totalDataLen = dataLength + 36L
            return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(totalDataLen.toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM subchunk size
                putShort(1) // AudioFormat = 1 (PCM)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign)
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataLength.toInt())
            }.array()
        }

        private fun writeWavHeaderSizes(raf: RandomAccessFile, dataLength: Long) {
            val riffSize = (dataLength + 36L).toInt()
            val dataSize = dataLength.toInt()
            val le4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            raf.seek(4L)
            le4.clear()
            le4.putInt(riffSize)
            raf.write(le4.array())

            raf.seek(40L)
            le4.clear()
            le4.putInt(dataSize)
            raf.write(le4.array())
        }
    }
}
