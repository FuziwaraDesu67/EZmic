# Phone-to-PC Virtual Microphone (MicCompanion)

Turn your Android smartphone into a studio-grade, low-latency microphone for your Windows PC over **Wi-Fi**, **USB (via ADB)**, or **Bluetooth**.

---

## Features
- **Tri-Mode Connectivity**:
  - **Wi-Fi**: Automatic UDP broadcast discovery on the local network.
  - **USB Cable**: Automated ADB port forwarding (`adb forward`) for lowest possible jitter (~5-12ms).
  - **Bluetooth**: Serial Port Profile (RFCOMM) wireless streaming without needing a local Wi-Fi router.
- **Audio Processing**:
  - Hardware **Noise Suppression** & **Acoustic Echo Cancellation** (AEC) on Android.
  - Software gain boost (1x to 5x) and instant mute synchronization.
  - Real-time stereo/mono **VU Meter** and level indicators on both phone and desktop.
- **Background Execution**:
  - Android foreground service with persistent status notification; continues streaming while screen is off.
- **Windows Integration**:
  - Routes directly into virtual audio devices such as **VB-Audio Virtual Cable**.
  - System tray minimization with quick mute toggle and connection status.
  - Automatic Windows startup support.

---

## Directory Structure
- [`android/`](file:///d:/games/mic/android) : Native Kotlin + Jetpack Compose Android application.
- [`windows/`](file:///d:/games/mic/windows) : C# .NET 8 WPF Companion application.
- [`docs/`](file:///d:/games/mic/docs) : Protocol specification and setup guides.

---

## Quick Start

### 1. Windows Companion Setup
1. Install [VB-Audio Virtual Cable](file:///d:/games/mic/docs/SETUP_VB_CABLE.md).
2. Open `windows/MicCompanion.sln` in Visual Studio or build with `dotnet build`.
3. Launch `MicCompanion.exe`, pick your preferred connection type (Wi-Fi, USB, or Bluetooth), and choose `CABLE Input` as your output device.

### 2. Android App Setup
1. Open `android/` in Android Studio.
2. Build and install the APK onto your device.
3. Grant Microphone and Nearby Devices / Bluetooth permissions.
4. Tap **Connect**!
