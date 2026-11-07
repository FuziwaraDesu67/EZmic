package com.mic.app.transport

import com.mic.app.protocol.AudioPacket
import com.mic.app.protocol.PacketConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class UsbTransmitter(
    var port: Int = PacketConstants.DEFAULT_USB_PORT
) : IAudioTransmitter {

    override val name: String = "USB (ADB)"
    override var isConnected: Boolean = false
        private set

    override var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null
    override var onLogMessage: ((String) -> Unit)? = null

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        try {
            onLogMessage?.invoke("Connecting to PC via USB ADB port $port...")
            val sock = Socket()
            // Connect to localhost:port which is routed across the USB cable via ADB forward/reverse
            sock.connect(InetSocketAddress("127.0.0.1", port), 3000)
            sock.tcpNoDelay = true

            socket = sock
            outputStream = sock.getOutputStream()
            isConnected = true
            onConnectionStateChanged?.invoke(true, "Connected to PC via USB")
            onLogMessage?.invoke("USB connection established successfully.")
            true
        } catch (e: Exception) {
            isConnected = false
            onConnectionStateChanged?.invoke(false, "USB connect failed: ${e.message}")
            onLogMessage?.invoke("Ensure USB Debugging is ON and PC Companion is started.")
            false
        }
    }

    override fun sendPacket(packet: AudioPacket) {
        val out = outputStream ?: return
        try {
            val bytes = packet.toByteArray()
            synchronized(out) {
                out.write(bytes)
                out.flush()
            }
        } catch (e: Exception) {
            disconnect()
        }
    }

    override fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}

        outputStream = null
        socket = null

        if (isConnected) {
            isConnected = false
            onConnectionStateChanged?.invoke(false, "Disconnected")
            onLogMessage?.invoke("USB transmitter disconnected.")
        }
    }
}
