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
    var targetPort: Int = PacketConstants.DEFAULT_WIFI_AUDIO_PORT,
    var sessionToken: Long = 0L
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

            // Security (EZMIC-VULN-WIFI-SPOOFED-LOCKOUT): If token not yet discovered, attempt direct unicast handshake
            if (sessionToken == 0L) {
                try {
                    val queryBytes = """{"service":"mic-query"}""".toByteArray(Charsets.UTF_8)
                    val queryPacket = DatagramPacket(queryBytes, queryBytes.size, targetAddress, PacketConstants.DEFAULT_WIFI_DISCOVERY_PORT)
                    socket?.soTimeout = 600
                    socket?.send(queryPacket)
                    val respBuf = ByteArray(1024)
                    val respPacket = DatagramPacket(respBuf, respBuf.size)
                    socket?.receive(respPacket)
                    val respStr = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                    val json = org.json.JSONObject(respStr)
                    if (json.optString("service") == "mic-to-pc") {
                        val token = json.optLong("token", 0L)
                        if (token != 0L) {
                            sessionToken = token
                            onLogMessage?.invoke("Negotiated session token ($token) with $targetIp")
                        }
                    }
                } catch (_: Exception) {
                    // Fall back if PC does not answer query or discovery port unreachable
                } finally {
                    socket?.soTimeout = 0
                }
            }

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
            val outPacket = if (sessionToken != 0L && packet.sessionToken != sessionToken) {
                packet.copy(sessionToken = sessionToken)
            } else {
                packet
            }
            val bytes = outPacket.toByteArray()
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
