package com.mic.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mic.app.MainActivity
import com.mic.app.R
import com.mic.app.audio.AudioCaptureManager
import com.mic.app.transport.IAudioTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MicForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private var audioCaptureManager: AudioCaptureManager? = null
    var activeTransmitter: IAudioTransmitter? = null
        private set

    private val _streamingState = MutableStateFlow(StreamingState())
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): MicForegroundService = this@MicForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MicApp::StreamingWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    fun startStreaming(transmitter: IAudioTransmitter, useOpus: Boolean = true, noiseSuppression: Boolean = true, echoCancellation: Boolean = true) {
        activeTransmitter = transmitter

        val notification = buildNotification("Connecting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        wakeLock?.acquire(8 * 60 * 60 * 1000L) // Max 8 hours safety timeout

        transmitter.onConnectionStateChanged = { connected, msg ->
            _streamingState.value = _streamingState.value.copy(
                isConnected = connected,
                statusMessage = msg
            )
            updateNotification(msg)
        }

        serviceScope.launch {
            val connected = transmitter.connect()
            if (connected) {
                audioCaptureManager = AudioCaptureManager(
                    onPacketReady = { packet ->
                        transmitter.sendPacket(packet)
                    },
                    onLevelChanged = { level ->
                        _streamingState.value = _streamingState.value.copy(audioLevel = level)
                    }
                ).apply {
                    this.enableNoiseSuppression = noiseSuppression
                    this.enableEchoCancellation = echoCancellation
                    start()
                }

                _streamingState.value = _streamingState.value.copy(
                    isStreaming = true,
                    statusMessage = "Streaming audio via ${transmitter.name}"
                )
            } else {
                val lastErr = _streamingState.value.statusMessage
                stopStreaming(reason = if (lastErr.isNotEmpty() && lastErr != "Idle") lastErr else "Connection failed")
            }
        }
    }

    fun setMute(muted: Boolean) {
        audioCaptureManager?.isMuted = muted
        _streamingState.value = _streamingState.value.copy(isMuted = muted)
    }

    fun setGain(gain: Float) {
        audioCaptureManager?.gain = gain
        _streamingState.value = _streamingState.value.copy(gain = gain)
    }

    fun stopStreaming(reason: String? = null) {
        audioCaptureManager?.stop()
        audioCaptureManager = null

        activeTransmitter?.disconnect()
        activeTransmitter = null

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        _streamingState.value = StreamingState(
            isStreaming = false,
            isConnected = false,
            statusMessage = reason ?: _streamingState.value.statusMessage.takeIf { it != "Idle" } ?: "Idle",
            audioLevel = 0f
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Microphone Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active mic transmission status to PC"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mic to PC")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "mic_stream_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

data class StreamingState(
    val isStreaming: Boolean = false,
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val gain: Float = 1.0f,
    val audioLevel: Float = 0f,
    val statusMessage: String = "Idle"
)
