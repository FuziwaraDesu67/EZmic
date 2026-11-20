package com.mic.app.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioPacket(
    val magic: Int = PacketConstants.MAGIC_V2,
    val sessionToken: Long = 0L,
    val sequenceNumber: Long,
    val codec: Byte = PacketConstants.CODEC_OPUS,
    val flags: Byte = 0,
    val payloadLength: Int,
    val payload: ByteArray
) {
    val isMuted: Boolean get() = (flags.toInt() and PacketConstants.FLAG_MUTED.toInt()) != 0
    val isStereo: Boolean get() = (flags.toInt() and PacketConstants.FLAG_STEREO.toInt()) != 0

    fun toByteArray(): ByteArray {
        val headerSize = if (magic == PacketConstants.MAGIC_V2) PacketConstants.HEADER_SIZE_V2 else PacketConstants.HEADER_SIZE
        val buffer = ByteBuffer.allocate(headerSize + payload.size)
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(magic)
        if (magic == PacketConstants.MAGIC_V2) {
            buffer.putInt(sessionToken.toInt())
        }
        buffer.putInt(sequenceNumber.toInt())
        buffer.put(codec)
        buffer.put(flags)
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)

        return buffer.array()
    }

    companion object {
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): AudioPacket? {
            if (length < PacketConstants.HEADER_SIZE) return null

            val buffer = ByteBuffer.wrap(data, offset, length)
            buffer.order(ByteOrder.BIG_ENDIAN)

            val magic = buffer.getInt()
            if (magic == PacketConstants.MAGIC_V2) {
                if (length < PacketConstants.HEADER_SIZE_V2) return null
                val token = buffer.getInt().toLong() and 0xFFFFFFFFL
                val seq = buffer.getInt().toLong() and 0xFFFFFFFFL
                val codec = buffer.get()
                val flags = buffer.get()
                val payloadLen = buffer.getShort().toInt() and 0xFFFF

                if (length < PacketConstants.HEADER_SIZE_V2 + payloadLen) return null

                val payload = ByteArray(payloadLen)
                buffer.get(payload)

                return AudioPacket(
                    magic = magic,
                    sessionToken = token,
                    sequenceNumber = seq,
                    codec = codec,
                    flags = flags,
                    payloadLength = payloadLen,
                    payload = payload
                )
            } else if (magic == PacketConstants.MAGIC) {
                val seq = buffer.getInt().toLong() and 0xFFFFFFFFL
                val codec = buffer.get()
                val flags = buffer.get()
                val payloadLen = buffer.getShort().toInt() and 0xFFFF

                if (length < PacketConstants.HEADER_SIZE + payloadLen) return null

                val payload = ByteArray(payloadLen)
                buffer.get(payload)

                return AudioPacket(
                    magic = magic,
                    sessionToken = 0L,
                    sequenceNumber = seq,
                    codec = codec,
                    flags = flags,
                    payloadLength = payloadLen,
                    payload = payload
                )
            }

            return null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPacket

        if (magic != other.magic) return false
        if (sessionToken != other.sessionToken) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (codec != other.codec) return false
        if (flags != other.flags) return false
        if (payloadLength != other.payloadLength) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = magic
        result = 31 * result + sessionToken.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + codec
        result = 31 * result + flags
        result = 31 * result + payloadLength
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
