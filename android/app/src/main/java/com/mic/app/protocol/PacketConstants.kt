package com.mic.app.protocol

import java.util.UUID

object PacketConstants {
    const val MAGIC: Int = 0x4D494331 // "MIC1" (Legacy V1)
    const val MAGIC_V2: Int = 0x4D494332 // "MIC2" (Authenticated V2)
    const val HEADER_SIZE: Int = 12
    const val HEADER_SIZE_V2: Int = 16

    const val CODEC_OPUS: Byte = 0x01
    const val CODEC_PCM: Byte = 0x02

    const val FLAG_MUTED: Byte = 0x01
    const val FLAG_STEREO: Byte = 0x02

    const val SAMPLE_RATE: Int = 48000
    const val FRAME_DURATION_MS: Int = 20
    const val SAMPLES_PER_FRAME: Int = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 960 samples

    const val DEFAULT_WIFI_AUDIO_PORT: Int = 49152
    const val DEFAULT_WIFI_DISCOVERY_PORT: Int = 49150
    const val DEFAULT_USB_PORT: Int = 49153

    // Dedicated 128-bit GUID for MicCompanion RFCOMM
    val BLUETOOTH_SERVICE_UUID: UUID = UUID.fromString("6A7D94D1-4E87-4C09-8C7F-85121B6628A4")
}
