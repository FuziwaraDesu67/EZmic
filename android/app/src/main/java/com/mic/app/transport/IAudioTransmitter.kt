package com.mic.app.transport

import com.mic.app.protocol.AudioPacket

interface IAudioTransmitter {
    val name: String
    val isConnected: Boolean

    var onConnectionStateChanged: ((Boolean, String) -> Unit)?
    var onLogMessage: ((String) -> Unit)?

    suspend fun connect(): Boolean
    fun sendPacket(packet: AudioPacket)
    fun disconnect()
}
