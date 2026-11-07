package com.mic.app.transport

import android.content.Context
import android.net.wifi.WifiManager
import com.mic.app.protocol.PacketConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class DiscoveryBeacon(private val context: Context) {
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    suspend fun discoverPc(timeoutMs: Int = 5000): DiscoveredPc? = withContext(Dispatchers.IO) {
        try {
            // Android drops UDP broadcasts by default unless MulticastLock is acquired
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("MicDiscoveryLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PacketConstants.DEFAULT_WIFI_DISCOVERY_PORT))
                soTimeout = timeoutMs
            }
            socket = sock

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
                    sock.soTimeout = remaining
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)

                    sock.receive(packet)
                    val jsonString = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (!jsonString.startsWith("{")) continue

                    val json = JSONObject(jsonString)

                    if (json.optString("service") == "mic-to-pc") {
                        val deviceName = json.optString("device", "Windows PC")
                        val port = json.optInt("port", PacketConstants.DEFAULT_WIFI_AUDIO_PORT)
                        val payloadIp = json.optString("ip")
                        val senderIp = if (payloadIp.isNotEmpty() && payloadIp != "127.0.0.1") {
                            payloadIp
                        } else {
                            packet.address?.hostAddress ?: "127.0.0.1"
                        }

                        return@withContext DiscoveredPc(
                            ip = senderIp,
                            port = port,
                            name = deviceName
                        )
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                    // Ignore non-matching packet and wait for next beacon
                }
            }
        } catch (_: Exception) {
            // Timeout or network unreachable
        } finally {
            close()
        }
        return@withContext null
    }

    fun close() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null

        socket?.close()
        socket = null
    }
}

data class DiscoveredPc(
    val ip: String,
    val port: Int,
    val name: String
)
