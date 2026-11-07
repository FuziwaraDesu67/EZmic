# Mic-to-PC Streaming Protocol Specification (v1.0)

## 1. Overview
This protocol defines the binary frame structure, transport semantics, and discovery mechanisms used between the Android Mic App and the Windows Companion Application.

---

## 2. Binary Packet Structure

Every audio frame transmitted over Wi-Fi (UDP/TCP), USB (TCP tunnel), or Bluetooth (RFCOMM) conforms to the following fixed-header binary layout:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Magic: "MIC1" (0x4D494331)              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Sequence Number (uint32)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   Codec (u8)  |   Flags (u8)  |      Payload Length (uint16)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                        Audio Payload                          |
|                       (Variable bytes)                        |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 2.1 Header Field Definitions

| Field | Size | Data Type | Description |
| :--- | :--- | :--- | :--- |
| **Magic** | 4 bytes | `uint32` (BE) | Fixed signature: `0x4D494331` (ASCII `"MIC1"`). Packets missing this magic byte sequence are discarded. |
| **Sequence Number** | 4 bytes | `uint32` (BE) | Monotonically increasing counter per frame. Used by the receiver for jitter buffer ordering and packet loss detection. |
| **Codec Type** | 1 byte | `uint8` | `0x01` = Opus (compressed voice frame)<br>`0x02` = Raw PCM (16-bit signed, little-endian) |
| **Flags** | 1 byte | `uint8` | Bitfield:<br>- Bit 0 (`0x01`): `1` if Muted on transmitter<br>- Bit 1 (`0x02`): `1` if Stereo, `0` if Mono<br>- Bits 2-7: Reserved (0) |
| **Payload Length** | 2 bytes | `uint16` (BE) | Number of audio bytes following the 12-byte header. |
| **Audio Payload** | `N` bytes | Byte array | Encoded Opus frame or raw PCM samples. |

---

## 3. Audio Specifications

- **Sample Rate**: 48,000 Hz (Standard studio/broadcast rate).
- **Channel Configuration**: 1 Channel (Mono) by default, optional 2 Channel (Stereo).
- **Bit Depth**: 16-bit Signed Integer (Little-Endian).
- **Frame Duration**: 20 milliseconds per packet.
  - At 48,000 Hz: $48000 \times 0.02 = 960$ samples per frame.
  - Raw PCM payload size: $960 \times 2 = 1920$ bytes.
  - Opus payload size: typically 40 to 160 bytes at 32–64 kbps.

---

## 4. Transports & Port Allocations

### 4.1 Wi-Fi Transport
- **Audio Port**: UDP `49152`
- **Control / Handshake Port**: TCP `49152`
- **Auto-Discovery Beacon**:
  - Broadcast target: `255.255.255.255:49150`
  - Beacon payload (JSON):
    ```json
    {
      "service": "mic-to-pc",
      "version": "1.0",
      "device": "Android Phone / Windows PC",
      "ip": "192.168.1.X",
      "port": 49152
    }
    ```

### 4.2 USB Transport (ADB Port Forwarding)
- **Host Port (PC)**: `tcp:49153`
- **Device Port (Android)**: `tcp:49153`
- **ADB Command**:
  ```bash
  adb forward tcp:49153 tcp:49153
  ```
- Windows companion automatically monitors ADB device connectivity and establishes the forward tunnel.

### 4.3 Bluetooth Transport (RFCOMM / SPP)
- **Standard SPP UUID**: `00001101-0000-1000-8000-00805F9B34FB` (Serial Port Profile)
- **Service Name**: `"MicToPcStream"`
- Windows Companion acts as RFCOMM server / client listening or connecting to paired Android device.

---

## 5. Control Messages (TCP / Bluetooth Out-of-Band)
A simple JSON control protocol runs over the stream control channel:
```json
{
  "type": "HELLO",
  "client": "Android-Mic-v1",
  "preferred_codec": "OPUS",
  "sample_rate": 48000,
  "channels": 1
}
```
Response:
```json
{
  "type": "WELCOME",
  "status": "READY",
  "buffer_ms": 40
}
```
Mute State Notification:
```json
{
  "type": "SET_MUTE",
  "muted": true
}
```
