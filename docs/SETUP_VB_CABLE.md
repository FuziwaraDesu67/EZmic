# Setting Up VB-Audio Virtual Cable for Windows

## 1. What is VB-Audio Virtual Cable?
VB-Audio Virtual Cable is a free software virtual audio device for Windows. It acts as an audio "loopback bridge":
- Any application that plays sound into **CABLE Input** will have that sound come out of **CABLE Output** (which acts as a selectable microphone).
- By routing our PC companion app into **CABLE Input**, Windows, Discord, Zoom, Steam, and games can select **CABLE Output** as their primary microphone.

---

## 2. Installation Steps

1. **Download**:
   - Visit the official site: [https://vb-audio.com/Cable/](https://vb-audio.com/Cable/)
   - Download the free **VB-CABLE Driver** ZIP package.
2. **Extract & Install**:
   - Extract the downloaded ZIP archive.
   - Right-click `VBCABLE_Setup_x64.exe` and select **Run as administrator**.
   - Click **Install Driver**.
3. **Reboot / Audio Service Restart**:
   - A system reboot or Windows Audio service restart is recommended to finalize the installation.

---

## 3. Configuring the Mic Companion App

1. Launch **MicCompanion.exe** on Windows.
2. In the **Output Device** dropdown, select:
   `CABLE Input (VB-Audio Virtual Cable)`
3. Ensure the volume slider is set to 100% (or adjust gain boost as desired).

---

## 4. Configuring Applications (Discord, Games, Windows)

### 4.1 In Windows Default Audio Settings
1. Open Windows **Settings** > **System** > **Sound**.
2. Under **Input**, select **CABLE Output (VB-Audio Virtual Cable)** as your default input device.

### 4.2 In Discord / Teams / Zoom
1. Open Discord Settings > **Voice & Video**.
2. Set **Input Device** to: `CABLE Output (VB-Audio Virtual Cable)`.
3. Set **Output Device** to your normal headphones/speakers.
4. Test your voice: speak into your phone, and the Discord input sensitivity bar will light up!

---

## 5. Troubleshooting
- **No Sound Heard**:
  - Verify that the phone is showing a green "Connected" status and the VU meter responds to your voice.
  - Check that the Windows Companion is routed to **CABLE Input**, and Discord is listening to **CABLE Output**.
- **Robotic / Choppy Sound**:
  - Open Windows Sound Control Panel (`mmsys.cpl`), navigate to both `CABLE Input` and `CABLE Output` > Properties > Advanced, and ensure both are set to **2 channel, 16 bit, 48000 Hz (DVD Quality)**.
