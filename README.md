<p align="center">
  <a href="https://fuziwaradesu67.github.io/EZmic/" target="_blank">
    <img src="assets/ezmic_hero_preview.gif" alt="EZmic Cinematic Motion Graphic Showcase" width="100%" style="border-radius: 14px; box-shadow: 0 16px 48px rgba(0, 0, 0, 0.6); max-width: 880px;" />
  </a>
</p>

<p align="center">
  <a href="https://fuziwaradesu67.github.io/EZmic/"><img src="https://img.shields.io/badge/%F0%9F%8C%90%20Live%20Showcase-GitHub%20Pages-10B981?style=for-the-badge&logoColor=white" alt="Live Showcase" /></a>
  <a href="https://github.com/FuziwaraDesu67/EZmic/releases/download/v1.1.6/EZmic-v1.1.6-win64.exe"><img src="https://img.shields.io/badge/Windows-v1.1.6%20.exe-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows Download" /></a>
  <a href="https://github.com/FuziwaraDesu67/EZmic/releases/download/v1.1.6/EZmic-v1.1.6.apk"><img src="https://img.shields.io/badge/Android-v1.1.6%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android APK" /></a>
  <a href="https://buymeacoffee.com/eaadarsh"><img src="https://img.shields.io/badge/Support-Buy%20Me%20a%20Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me a Coffee" /></a>
  <a href="https://github.com/FuziwaraDesu67/EZmic/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-gray?style=for-the-badge" alt="License" /></a>
</p>

# EZmic 🎙️⚡

> **Turn your Android smartphone into a studio-grade, ultra-low latency microphone for your Windows PC over Wi-Fi, USB, or Bluetooth.**  
> *Created with ❤️ by [@FuziwaraDesu67](https://github.com/FuziwaraDesu67) • [☕ Buy me a coffee](https://buymeacoffee.com/eaadarsh) • [🌐 Live Interactive Showcase](https://fuziwaradesu67.github.io/EZmic/)*

Works out of the box with **Discord**, **OBS Studio**, **Zoom**, **Audacity**, and **Steam Games** with zero complex setup.

---

## 📲 What's New in v1.1.6 (In-App Downloader & 1-Click Phone Installer) 🚀

- 📲 **Native In-App APK Downloader & Installer**: No more leaving the app or opening GitHub in a browser! When an update is detected, EZmic downloads the APK directly with live progress (0–100%) and launches the Android Package Installer automatically.
- ⚡ **1-Click ADB Phone Install from PC Companion**: Connect your Android phone via USB cable and click `[ 📲 Install App to Phone ]` on the PC companion — EZmic installs directly to your phone via ADB with zero hassle.
- 🛡️ **Full Security Hardening**: All v1.1.5 security audit protections (Client IP Pinning, Loopback USB binding, PE header verification, certified driver portal) are fully incorporated.

---

## 🛡️ What's New in v1.1.5 (Security Hardened Release) 🔒

- 🛡️ **Client IP Pinning & Wi-Fi Session Locking**: Protects against LAN audio injection (CVSS 8.5 / S-03). Locks active streaming exclusively to the verified sender IP; drops and alerts on unauthorized packets from third-party devices on shared networks.
- 🔒 **Loopback-Only USB Binding**: Bound USB TCP receiver strictly to `127.0.0.1` (S-02), eliminating external LAN exposure.
- 📡 **Discovery Beacon Spoof Protection**: Phone stream target is physically bound to UDP packet sender origin (S-01), preventing redirection to rogue receivers.
- 🛡️ **Safe Driver Portal Guidance**: Directs users to the official vendor portal for certified VB-Audio CABLE drivers (S-05), mitigating CVE-2026-124019 and removing unverified elevated execution.
- 🔐 **Binary Verified Auto-Updater**: Enforces PE binary header validation (`MZ`) and randomized temp staging paths; eliminates HTML fallback execution (S-04, S-10, S-12).
- 📱 **Android Stop State Integrity**: Cancels pending connection jobs on disconnect, preventing background capture starts after Stop is pressed (S-08).
- 🎛️ **Audio Buffer Parity & UI Flood Protection**: Enforces even-byte PCM buffer alignment and rate-limits error logging to protect against UI thread starvation (S-11).
- 🧹 **Network Stealth & Port Cleanup**: Disables background beacon broadcasts when receiver is idle (S-15), and removes ADB tunnels upon disconnection (S-16).

---

## ⚡ Why EZmic?

- **1 Single Executable, 1 Single APK**: No .NET installation required, no Android Studio or SDK required.
- **Embedded ADB**: USB low-latency mode works automatically without needing command-line tools.
- **1-Click Virtual Cable Setup**: Direct guidance to official signed vendor drivers right inside the desktop interface.
- **Hardware Rack Console UI**: Built with a 24-segment discrete LED ladder VU meter, ballistic decay, decibel readout, and live ON AIR tally.
- **Tri-Mode Connectivity**:
  - **Wi-Fi**: Automatic subnet beacon discovery (`UDP 49150/49152`) with Client IP Pinning.
  - **USB Cable**: Hardware ADB tunnel with ultra-low **~5ms latency** bound strictly to loopback.
  - **Bluetooth**: Direct RFCOMM streaming without needing a local Wi-Fi router.
- **WASAPI Shared Mode Engine**: Native Windows Audio Session API eliminates driver crashes and supports all multi-channel devices.
- **Studio Audio Quality**: 48,000 Hz, 16-bit PCM lossless stereo duplication with hardware noise suppression & echo cancellation.

---

## 🚀 60-Second Quick Start

### Step 1: Download the Files
Grab the latest release from the **[Releases Page](https://github.com/FuziwaraDesu67/EZmic/releases/latest)**:
- 💻 **Windows PC**: [**Download EZmic-v1.1.6-win64.exe**](https://github.com/FuziwaraDesu67/EZmic/releases/download/v1.1.6/EZmic-v1.1.6-win64.exe) *(Single-file standalone, zero setup)*
- 📱 **Android Phone**: [**Download EZmic-v1.1.6.apk**](https://github.com/FuziwaraDesu67/EZmic/releases/download/v1.1.6/EZmic-v1.1.6.apk) *(Native app, high quality)*

*(All versions available on the **[Releases](https://github.com/FuziwaraDesu67/EZmic/releases)** tab)*

### Step 2: Open EZmic PC
1. Double-click `EZmic-v1.1.6-win64.exe` (or the `EZmic` shortcut on your Desktop).
2. If you don't already have a virtual audio cable, the app will show a yellow banner:  
   👉 Click **"Get VB-CABLE"** (opens official portal to install certified driver).
3. Select your desired connection mode (**Wi-Fi**, **USB**, or **Bluetooth**) and click **Start receiver**.

### Step 3: Start Streaming on Phone
1. Open the **EZmic** app on your phone.
2. Choose matching mode:
   - **Wi-Fi**: Tap **Auto-detect PC** (finds your PC automatically), then tap **Start broadcast**.
   - **USB**: Plug in USB cable with USB Debugging enabled, then tap **Start broadcast**.
   - **Bluetooth**: Pick your paired PC from the dropdown, then tap **Start broadcast**.
3. Watch the 24-segment LED VU meter dance live on both screens!

### Step 4: Route to Discord, Games, or OBS
In Discord / OBS / Game Audio Settings:
- Set **Input Device (Microphone)** to: **`CABLE Output (VB-Audio Virtual Cable)`**.
- Speak into your phone — your PC hears you crystal clear!

---

## 🔌 Connection Modes Detailed

| Mode | Latency | Stability | Ideal Use Case |
|---|---|---|---|
| **USB (ADB)** | **~5 ms** | 100% Rock-Solid (Loopback isolated) | Gaming, competitive voice chat, live streaming |
| **Wi-Fi** | ~15–25 ms | Excellent (Client IP Pinned) | Wireless freedom across your room/desk |
| **Bluetooth** | ~35–50 ms | High (Secure RFCOMM Service) | On-the-go or when no Wi-Fi router is available |

---

## 🏗️ Technical Architecture

```
[ Android Microphone (Samsung/Pixel/etc.) ]
                │
         48kHz 16-bit PCM
     (VOICE_RECOGNITION DSP)
                │
  ┌─────────────┼─────────────┐
  ▼             ▼             ▼
Wi-Fi UDP    USB ADB TCP   Bluetooth RFCOMM
(Port 49152) (Port 49153)  (Dedicated UUID)
(IP Pinned)  (127.0.0.1)          │
  │             │                 │
  └─────────────┼─────────────────┘
                │
                ▼
[ Windows Mic Companion (WASAPI Shared) ]
                │
        48kHz Stereo Float/PCM
                │
                ▼
[ VB-Audio Virtual Cable (CABLE Input) ]
                │
                ▼
[ Discord / OBS / Steam Games / Zoom ]
```

---

## 🛠️ Building from Source

### Windows Companion
Requirements: .NET 8 / 9 SDK
```powershell
cd windows/MicCompanion
# Single-file standalone publish
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o "../../dist/pc"
```

### Android App
Requirements: Android Studio / JDK 17+
```bash
cd android
./gradlew assembleDebug
# APK is generated at: app/build/outputs/apk/debug/app-debug.apk
```

---

## ☕ Support & Donations

If EZmic has been useful to you, you can support further development by buying a coffee:

[![Buy Me A Coffee](https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png)](https://buymeacoffee.com/eaadarsh)

Direct link: **[buymeacoffee.com/eaadarsh](https://buymeacoffee.com/eaadarsh)**

---

## 👨‍💻 Author & Contributions

Created and maintained by **[@FuziwaraDesu67](https://github.com/FuziwaraDesu67)**.  
Contributions, bug reports, and feature requests are welcome on [GitHub Issues](https://github.com/FuziwaraDesu67/EZmic/issues)!

---

## 📜 License
MIT License. Free for personal and commercial use.
