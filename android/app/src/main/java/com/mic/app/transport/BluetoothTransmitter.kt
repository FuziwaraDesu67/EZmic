package com.mic.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.mic.app.protocol.AudioPacket
import com.mic.app.protocol.PacketConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class BluetoothTransmitter(
    var targetDevice: BluetoothDevice? = null
) : IAudioTransmitter {

    override val name: String = "Bluetooth"
    override var isConnected: Boolean = false
        private set

    override var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null
    override var onLogMessage: ((String) -> Unit)? = null

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        val dev = targetDevice
        if (dev == null) {
            onLogMessage?.invoke("Error: No target Bluetooth device selected.")
            return@withContext false
        }

        try {
            onLogMessage?.invoke("Connecting to ${dev.name ?: dev.address} via Bluetooth RFCOMM...")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.cancelDiscovery()

            var sock: BluetoothSocket? = null

            // 1. Try secure RFCOMM
            try {
                sock = dev.createRfcommSocketToServiceRecord(PacketConstants.BLUETOOTH_SERVICE_UUID)
                sock.connect()
            } catch (e1: Exception) {
                // 2. Try insecure RFCOMM (bypasses PIN / pairing handshake locks)
                try {
                    try { sock?.close() } catch (_: Exception) {}
                    sock = dev.createInsecureRfcommSocketToServiceRecord(PacketConstants.BLUETOOTH_SERVICE_UUID)
                    sock.connect()
                } catch (e2: Exception) {
                    // 3. Fallback to reflection on channel 1
                    try { sock?.close() } catch (_: Exception) {}
                    val m = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    sock = m.invoke(dev, 1) as BluetoothSocket
                    sock.connect()
                }
            }

            val activeSock = sock ?: throw java.io.IOException("Unable to create Bluetooth socket")
            socket = activeSock
            outputStream = activeSock.outputStream
            isConnected = true
            onConnectionStateChanged?.invoke(true, "Connected to ${dev.name ?: dev.address}")
            onLogMessage?.invoke("Bluetooth RFCOMM link active.")
            true
        } catch (e: Exception) {
            isConnected = false
            onConnectionStateChanged?.invoke(false, "Bluetooth failed: ${e.message}")
            onLogMessage?.invoke("Failed to connect: ${e.message}")
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
            onLogMessage?.invoke("Bluetooth transmitter disconnected.")
        }
    }
}
