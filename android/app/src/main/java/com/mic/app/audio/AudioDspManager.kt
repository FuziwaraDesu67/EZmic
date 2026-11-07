package com.mic.app.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

class AudioDspManager {
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null

    companion object {
        private const val TAG = "AudioDspManager"

        val isNoiseSuppressionAvailable: Boolean
            get() = NoiseSuppressor.isAvailable()

        val isEchoCancellationAvailable: Boolean
            get() = AcousticEchoCanceler.isAvailable()

        val isAgcAvailable: Boolean
            get() = AutomaticGainControl.isAvailable()
    }

    fun attachToSession(audioSessionId: Int, enableNoiseSuppression: Boolean, enableEchoCancellation: Boolean) {
        release()

        try {
            if (enableNoiseSuppression && isNoiseSuppressionAvailable) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware NoiseSuppressor enabled on session $audioSessionId")
                }
            }

            if (enableEchoCancellation && isEchoCancellationAvailable) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AcousticEchoCanceler enabled on session $audioSessionId")
                }
            }

            if (isAgcAvailable) {
                agc = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AutomaticGainControl enabled on session $audioSessionId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio DSP effects", e)
        }
    }

    fun release() {
        noiseSuppressor?.release()
        noiseSuppressor = null

        echoCanceler?.release()
        echoCanceler = null

        agc?.release()
        agc = null
    }
}
