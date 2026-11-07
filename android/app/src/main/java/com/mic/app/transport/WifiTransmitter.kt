package com.mic.app.transport

import com.mic.app.protocol.AudioPacket
import com.mic.app.protocol.PacketConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WifiTransmitter(
    var targetIp: String = "192.168.1.100",
    var targetPort: Int = PacketConstants.DEFAULT_WIFI_AUDIO_PORT
) : IAudioTransmitter {

    override val name: String = "Wi-Fi"
    override var isConnected: Boolean = false
        private set

    override var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null
    override var onLogMessage: ((String) -> Unit)? = null

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        try {
            targetAddress = InetAddress.getByName(targetIp)
            socket = DatagramSocket()
            isConnected = true
            onConnectionStateChanged?.invoke(true, "Streaming to $targetIp:$targetPort")
            onLogMessage?.invoke("Wi-Fi UDP transmitter connected to $targetIp:$targetPort")
            true
        } catch (e: Exception) {
            isConnected = false
            onConnectionStateChanged?.invoke(false, "Connection error: ${e.message}")
            onLogMessage?.invoke("Wi-Fi error: ${e.message}")
            false
        }
    }

    override fun sendPacket(packet: AudioPacket) {
        val sock = socket ?: return
        val addr = targetAddress ?: return
        try {
            val bytes = packet.toByteArray()
            val datagram = DatagramPacket(bytes, bytes.size, addr, targetPort)
            sock.send(datagram)
        } catch (e: Exception) {
            // Drop packet on temporary network glitch
        }
    }

    override fun disconnect() {
        socket?.close()
        socket = null
        targetAddress = null
        if (isConnected) {
            isConnected = false
            onConnectionStateChanged?.invoke(false, "Disconnected")
            onLogMessage?.invoke("Wi-Fi transmitter stopped.")
        }
    }
}
