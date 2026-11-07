package com.mic.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mic.app.protocol.AudioPacket
import com.mic.app.protocol.PacketConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class AudioCaptureManager(
    private val onPacketReady: (AudioPacket) -> Unit,
    private val onLevelChanged: (Float) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private val dspManager = AudioDspManager()

    private var isRecording = false
    private var recordingThread: Thread? = null
    private var sequenceNumber = 0L

    var gain: Float = 1.0f
    var isMuted: Boolean = false
    var enableNoiseSuppression: Boolean = true
    var enableEchoCancellation: Boolean = true

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            PacketConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = max(minBufferSize, PacketConstants.SAMPLES_PER_FRAME * 4)

        // Use VOICE_RECOGNITION for clean, studio-quality microphone capture without
        // destructive cellular phone-call DSP / phase distortion on Samsung devices.
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            PacketConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord = record

        if (enableNoiseSuppression || enableEchoCancellation) {
            dspManager.attachToSession(
                record.audioSessionId,
                enableNoiseSuppression,
                enableEchoCancellation
            )
        }

        record.startRecording()
        isRecording = true
        sequenceNumber = 0L

        recordingThread = Thread({ recordLoop() }, "AudioCaptureThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun recordLoop() {
        val record = audioRecord ?: return
        val pcmBuffer = ShortArray(PacketConstants.SAMPLES_PER_FRAME)

        while (isRecording) {
            // Read exact full frame of 960 samples (20ms at 48kHz) to eliminate audio tearing
            var totalRead = 0
            while (totalRead < PacketConstants.SAMPLES_PER_FRAME && isRecording) {
                val read = record.read(pcmBuffer, totalRead, PacketConstants.SAMPLES_PER_FRAME - totalRead)
                if (read > 0) {
                    totalRead += read
                } else if (read < 0) {
                    break
                }
            }

            if (totalRead < PacketConstants.SAMPLES_PER_FRAME) continue

            // 1. Calculate Peak Level & Apply Gain / Mute
            var maxMagnitude = 0
            val gainFactor = gain

            for (i in 0 until totalRead) {
                var sample = pcmBuffer[i].toInt()
                if (isMuted) {
                    pcmBuffer[i] = 0
                } else {
                    if (abs(gainFactor - 1.0f) > 0.01f) {
                        sample = (sample * gainFactor).toInt()
                        sample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        pcmBuffer[i] = sample.toShort()
                    }
                    val mag = abs(sample)
                    if (mag > maxMagnitude) {
                        maxMagnitude = mag
                    }
                }
            }

            val peakLevel = if (isMuted) 0f else (maxMagnitude.toFloat() / Short.MAX_VALUE)
            onLevelChanged(peakLevel)

            // 2. Format as Raw 16-bit PCM (Little-Endian)
            val byteBuffer = ByteBuffer.allocate(totalRead * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until totalRead) {
                byteBuffer.putShort(pcmBuffer[i])
            }
            val payload = byteBuffer.array()

            if (payload.isNotEmpty()) {
                var flags: Byte = 0
                if (isMuted) {
                    flags = (flags.toInt() or PacketConstants.FLAG_MUTED.toInt()).toByte()
                }

                val packet = AudioPacket(
                    sequenceNumber = sequenceNumber++,
                    codec = PacketConstants.CODEC_PCM,
                    flags = flags,
                    payloadLength = payload.size,
                    payload = payload
                )

                onPacketReady(packet)
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) {}
        }
        audioRecord = null

        dspManager.release()
        onLevelChanged(0f)
    }
}
