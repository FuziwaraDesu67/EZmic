package com.mic.app.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mic.app.service.StreamingState
import com.mic.app.util.AppUpdateInfo
import com.mic.app.util.DownloadState
import com.mic.app.util.InAppUpdateManager
import com.mic.app.util.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.max

enum class TransportMode {
    WIFI, USB, BLUETOOTH
}

@Composable
fun MainScreen(
    state: StreamingState,
    pairedDevices: List<BluetoothDevice>,
    onStartWifi: (ip: String, port: Int, useOpus: Boolean, ns: Boolean, aec: Boolean, isStereo: Boolean) -> Unit,
    onStartUsb: (port: Int, useOpus: Boolean, ns: Boolean, aec: Boolean, isStereo: Boolean) -> Unit,
    onStartBluetooth: (device: BluetoothDevice, useOpus: Boolean, ns: Boolean, aec: Boolean, isStereo: Boolean) -> Unit,
    onStop: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onGainChanged: (Float) -> Unit,
    onDiscoverWifi: ((Discovered: (String, Int, String) -> Unit) -> Unit)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ezmic_prefs", Context.MODE_PRIVATE) }

    var showSettingsScreen by remember { mutableStateOf(false) }

    var selectedMode by remember {
        val saved = prefs.getString("selected_mode", TransportMode.USB.name)
        mutableStateOf(try { TransportMode.valueOf(saved ?: TransportMode.USB.name) } catch (_: Exception) { TransportMode.USB })
    }
    var wifiIp by remember { mutableStateOf(prefs.getString("wifi_ip", "192.168.1.100") ?: "192.168.1.100") }
    var wifiPort by remember { mutableStateOf(prefs.getString("wifi_port", "49152") ?: "49152") }
    var usbPort by remember { mutableStateOf(prefs.getString("usb_port", "49153") ?: "49153") }
    var selectedDevice by remember {
        mutableStateOf(
            pairedDevices.find {
                val n = it.name ?: ""
                n.contains("DESKTOP", true) || n.contains("PC", true) || n.contains("LAPTOP", true)
            } ?: pairedDevices.firstOrNull()
        )
    }
    var enableNs by remember { mutableStateOf(prefs.getBoolean("enable_ns", true)) }
    var enableAec by remember { mutableStateOf(prefs.getBoolean("enable_aec", true)) }
    var isStereoMode by remember { mutableStateOf(prefs.getBoolean("is_stereo_mode", false)) }
    var gainSlider by remember { mutableFloatStateOf(prefs.getFloat("gain_slider", 100f)) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<TransportMode?>(null) }

    // Settings screen preferences
    var autoStartBoot by remember { mutableStateOf(prefs.getBoolean("auto_start_boot", false)) }
    var runInBackground by remember { mutableStateOf(prefs.getBoolean("run_in_background", true)) }

    // Update Checker state
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateProgressPercent by remember { mutableIntStateOf(0) }
    var updateProgressText by remember { mutableStateOf("") }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var updateErrorMessage by remember { mutableStateOf<String?>(null) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }

    // Auto-save preferences
    LaunchedEffect(selectedMode) {
        prefs.edit().putString("selected_mode", selectedMode.name).apply()
    }
    LaunchedEffect(wifiIp) {
        prefs.edit().putString("wifi_ip", wifiIp).apply()
    }
    LaunchedEffect(wifiPort) {
        prefs.edit().putString("wifi_port", wifiPort).apply()
    }
    LaunchedEffect(usbPort) {
        prefs.edit().putString("usb_port", usbPort).apply()
    }
    LaunchedEffect(enableNs) {
        prefs.edit().putBoolean("enable_ns", enableNs).apply()
    }
    LaunchedEffect(enableAec) {
        prefs.edit().putBoolean("enable_aec", enableAec).apply()
    }
    LaunchedEffect(isStereoMode) {
        prefs.edit().putBoolean("is_stereo_mode", isStereoMode).apply()
    }
    LaunchedEffect(gainSlider) {
        prefs.edit().putFloat("gain_slider", gainSlider).apply()
    }
    LaunchedEffect(autoStartBoot) {
        prefs.edit().putBoolean("auto_start_boot", autoStartBoot).apply()
    }
    LaunchedEffect(runInBackground) {
        prefs.edit().putBoolean("run_in_background", runInBackground).apply()
    }

    // Auto-check for updates on launch
    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(context)
        if (info != null && info.hasUpdate) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    // Ballistic animated level
    val animatedLevel by animateFloatAsState(
        targetValue = state.audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh),
        label = "VuMeterLevel"
    )

    // Breathing pulse for ON AIR tally
    val infiniteTransition = rememberInfiniteTransition(label = "TallyPulse")
    val tallyAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TallyAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ChassisBackground
    ) {
        if (showSettingsScreen) {
            // ==========================================
            // SCREEN 2: EZmic / Settings View
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Settings Header with Back Arrow
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PanelSurface)
                            .border(1.dp, BorderHairline, CircleShape)
                            .clickable { showSettingsScreen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❮", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Preferences Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "General",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Run in background toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Run in background", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("Keep audio streaming when screen is off", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = runInBackground,
                                onCheckedChange = { runInBackground = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioMint,
                                    uncheckedThumbColor = Color(0xFF8E959E),
                                    uncheckedTrackColor = ControlWell,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = BorderHairline
                                )
                            )
                        }

                        Divider(color = BorderHairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                        // Launch on boot toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Launch on device boot", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("Start background service on phone startup", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = autoStartBoot,
                                onCheckedChange = { autoStartBoot = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioMint,
                                    uncheckedThumbColor = Color(0xFF8E959E),
                                    uncheckedTrackColor = ControlWell,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = BorderHairline
                                )
                            )
                        }
                    }
                }

                // Links & Actions Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Check for updates row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isCheckingUpdate) {
                                        isCheckingUpdate = true
                                        coroutineScope.launch {
                                            val info = UpdateChecker.check(context)
                                            isCheckingUpdate = false
                                            if (info != null && info.hasUpdate) {
                                                updateInfo = info
                                                showUpdateDialog = true
                                            } else if (info != null) {
                                                Toast.makeText(context, "EZmic is up to date (${UpdateChecker.getAppVersion(context)})", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Unable to check updates. Please check connection.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Check for updates", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                text = if (isCheckingUpdate) "Checking..." else "${UpdateChecker.getAppVersion(context)} ❯",
                                fontSize = 13.sp,
                                color = StudioMint
                            )
                        }

                        Divider(color = BorderHairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        // Support developer row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/eaadarsh"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Support the developer", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("Buy me a coffee ❯", fontSize = 13.sp, color = Color(0xFFFFDD00))
                        }

                        Divider(color = BorderHairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        // GitHub repository row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FuziwaraDesu67/EZmic"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GitHub repository", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("FuziwaraDesu67 ❯", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }

                // Battery optimization guidance tip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F151F))
                        .border(1.dp, Color(0xFF1E2D45), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("💡 Battery Optimization Tip", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To prevent Android from silencing the microphone in the background during long sessions, set battery usage for EZmic to 'Unrestricted' in App Info.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            // ==========================================
            // SCREEN 1: EZmic / Main Screen
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Header with Broadcast Tally + Settings Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "EZmic",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Studio microphone bridge",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tally Pill
                        val tallyText = when {
                            state.isMuted && state.isStreaming -> "MUTED"
                            state.isStreaming -> "ON AIR"
                            else -> "STANDBY"
                        }
                        val tallyColor = when {
                            state.isMuted && state.isStreaming -> StudioRed
                            state.isStreaming -> StudioMint
                            else -> TextMuted
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(PanelSurface)
                                .border(1.dp, tallyColor.copy(alpha = if (state.isStreaming) 0.5f else 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isStreaming) tallyColor.copy(alpha = tallyAlpha)
                                            else tallyColor
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tallyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isStreaming) tallyColor else TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Settings Icon Button (···)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PanelSurface)
                                .border(1.dp, BorderHairline, CircleShape)
                                .clickable { showSettingsScreen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("···", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                    }
                }

                // 2. Segmented Transport Dock (Wi-Fi, USB, Bluetooth)
                LiquidGlassDockSlider(
                    selectedMode = selectedMode,
                    onModeSelected = { mode ->
                        if (mode != selectedMode) {
                            if (state.isStreaming) {
                                pendingMode = mode
                                showSwitchDialog = true
                            } else {
                                selectedMode = mode
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Primary CTA: Hero Start/Stop Broadcast Button
                Button(
                    onClick = {
                        if (state.isStreaming) {
                            onStop()
                        } else {
                            when (selectedMode) {
                                TransportMode.WIFI -> onStartWifi(wifiIp, wifiPort.toIntOrNull() ?: 49152, true, enableNs, enableAec, isStereoMode)
                                TransportMode.USB -> onStartUsb(usbPort.toIntOrNull() ?: 49153, true, enableNs, enableAec, isStereoMode)
                                TransportMode.BLUETOOTH -> {
                                    val dev = selectedDevice
                                    if (dev != null) {
                                        onStartBluetooth(dev, true, enableNs, enableAec, isStereoMode)
                                    } else {
                                        Toast.makeText(context, "Select your paired PC first", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isStreaming) Color(0xFF1E222A) else StudioMint
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            width = if (state.isStreaming) 1.dp else 0.dp,
                            color = if (state.isStreaming) StudioRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = if (state.isStreaming) "Stop broadcast" else "Start broadcast",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (state.isStreaming) StudioRed else Color(0xFF091E16)
                    )
                }

                // Subtitle: Status Readout under button
                val isError = state.statusMessage.contains("failed", ignoreCase = true) ||
                              state.statusMessage.contains("error", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> StudioRed
                                    state.isStreaming -> StudioMint
                                    state.isConnected -> StudioMint
                                    else -> TextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.isStreaming -> "Broadcasting 48kHz Opus • ${if (isStereoMode) "Stereo" else "Mono"}"
                            selectedMode == TransportMode.WIFI -> "Ready · $wifiIp"
                            selectedMode == TransportMode.USB -> "Ready · USB low-latency"
                            else -> "Ready · Bluetooth"
                        },
                        fontSize = 12.sp,
                        color = when {
                            isError -> StudioRed
                            state.isStreaming -> TextPrimary
                            else -> TextSecondary
                        }
                    )
                }

                // 4. Contextual Transport Configuration Card
                when (selectedMode) {
                    TransportMode.WIFI -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PanelSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Wi-Fi Receiver Connection",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = wifiIp,
                                    onValueChange = { wifiIp = it },
                                    label = { Text("PC IP address", fontSize = 12.sp) },
                                    singleLine = true,
                                    enabled = !state.isStreaming,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = StudioMint,
                                        unfocusedBorderColor = BorderHairline,
                                        focusedLabelColor = StudioMint,
                                        unfocusedLabelColor = TextSecondary,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onDiscoverWifi { ip, port, _ ->
                                            wifiIp = ip
                                            wifiPort = port.toString()
                                        }
                                    },
                                    enabled = !state.isStreaming,
                                    colors = ButtonDefaults.buttonColors(containerColor = ControlWell),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Auto-detect PC", color = StudioMint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    TransportMode.USB -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PanelSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("USB Mode: Ultra-low 5ms latency", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("Connect phone to PC with USB debugging enabled.", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                    TransportMode.BLUETOOTH -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PanelSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Target Bluetooth PC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (pairedDevices.isEmpty()) {
                                    Text(
                                        text = "No paired PC found. Pair your PC in Android Bluetooth settings first.",
                                        fontSize = 12.sp,
                                        color = StudioRed
                                    )
                                } else {
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ControlWell)
                                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                            .clickable(enabled = !state.isStreaming) { expanded = true }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedDevice?.name ?: selectedDevice?.address ?: "Select paired PC...",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary
                                            )
                                            Text("▾", fontSize = 14.sp, color = TextSecondary)
                                        }

                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(PanelSurface)
                                        ) {
                                            pairedDevices.forEach { dev ->
                                                DropdownMenuItem(
                                                    text = { Text(dev.name ?: dev.address, color = TextPrimary, fontSize = 13.sp) },
                                                    onClick = {
                                                        selectedDevice = dev
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Input Level & Mute Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Input level",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )

                            val decibels = if (animatedLevel <= 0.008f) "-inf dB" else {
                                val db = 20 * log10(max(0.001f, animatedLevel))
                                "${"%.1f".format(db)} dB"
                            }
                            Text(
                                text = decibels,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Level Meter + Mute Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Level Meter Bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF22262E))
                            ) {
                                val meterBrush = if (state.isMuted) {
                                    Brush.linearGradient(listOf(StudioRed, StudioRed))
                                } else {
                                    Brush.horizontalGradient(listOf(StudioMint, Color(0xFF38EF7D)))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedLevel.coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(meterBrush)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Mute Pill Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (state.isMuted) Color(0xFF3B1212) else Color(0xFF22262E))
                                    .border(1.dp, if (state.isMuted) StudioRed else BorderHairline, RoundedCornerShape(16.dp))
                                    .clickable { onToggleMute(!state.isMuted) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎙️", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (state.isMuted) "Unmute" else "Mute",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (state.isMuted) StudioRed else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Gain Control Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gain",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${gainSlider.toInt()}%",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }

                        Slider(
                            value = gainSlider,
                            onValueChange = {
                                gainSlider = it
                                onGainChanged(it / 100f)
                            },
                            valueRange = 50f..300f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = StudioMint,
                                inactiveTrackColor = Color(0xFF22262E)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 7. Voice Enhancer / DSP Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Voice enhancer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Noise suppression
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Noise suppression", fontSize = 13.sp, color = if (state.isStreaming) TextMuted else TextPrimary)
                                if (state.isStreaming) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("🔒", fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = enableNs,
                                onCheckedChange = { if (!state.isStreaming) enableNs = it },
                                enabled = !state.isStreaming,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioMint,
                                    uncheckedThumbColor = Color(0xFF8E959E),
                                    uncheckedTrackColor = ControlWell,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = BorderHairline
                                )
                            )
                        }

                        // Echo cancellation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Echo cancellation", fontSize = 13.sp, color = if (state.isStreaming) TextMuted else TextPrimary)
                                if (state.isStreaming) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("🔒", fontSize = 11.sp)
                                }
                            }
                            Switch(
                                checked = enableAec,
                                onCheckedChange = { if (!state.isStreaming) enableAec = it },
                                enabled = !state.isStreaming,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioMint,
                                    uncheckedThumbColor = Color(0xFF8E959E),
                                    uncheckedTrackColor = ControlWell,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = BorderHairline
                                )
                            )
                        }

                        // True Stereo Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isStereoMode) "Stereo (Music/ASMR)" else "Voice Mode (Dual-Mono)",
                                        fontSize = 13.sp,
                                        color = if (state.isStreaming) TextMuted else TextPrimary
                                    )
                                    if (state.isStreaming) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🔒", fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    text = if (isStereoMode) "Dual-mic wide recording" else "Focused speech for Discord & Gaming",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = isStereoMode,
                                onCheckedChange = { if (!state.isStreaming) isStereoMode = it },
                                enabled = !state.isStreaming,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StudioMint,
                                    uncheckedThumbColor = Color(0xFF8E959E),
                                    uncheckedTrackColor = ControlWell,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = BorderHairline
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // PC Studio Enhancer banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0D141E))
                                .border(1.dp, Color(0xFF1E2A3A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎙️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("PC Studio Enhancer Ready", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StudioMint)
                                    Text("Real-time Shure SM7B broadcast warmth & compression on PC.", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Subtle Footer: Version & Creator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EZmic ${UpdateChecker.getAppVersion(context)}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "FuziwaraDesu67",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FuziwaraDesu67/EZmic"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        // Automatic / Manual In-App Update Alert Dialog (Direct APK download with package installer)
        if (showUpdateDialog && updateInfo != null && updateInfo!!.hasUpdate) {
            val info = updateInfo!!
            AlertDialog(
                onDismissRequest = {
                    if (!isDownloadingUpdate) {
                        showUpdateDialog = false
                    }
                },
                containerColor = Color(0xFF141923),
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text("🎉 New Update: ${info.latestVersion}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = info.releaseNotes.take(300),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (downloadedApkFile != null) {
                            Text(
                                text = "✅ Update downloaded successfully. Tap Install Now to complete the update.",
                                fontSize = 12.sp,
                                color = StudioMint,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (isDownloadingUpdate) {
                            Text(
                                text = "Downloading update ($updateProgressPercent% • $updateProgressText)...",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (updateProgressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = StudioMint,
                                trackColor = Color(0xFF1E2638)
                            )
                        } else if (updateErrorMessage != null) {
                            Text(
                                text = "⚠️ Download error: $updateErrorMessage",
                                fontSize = 12.sp,
                                color = Color(0xFFF87171)
                            )
                        } else {
                            Text(
                                text = "Direct in-app download and installation. No browser redirect.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                confirmButton = {
                    if (downloadedApkFile != null) {
                        Button(
                            onClick = {
                                val file = downloadedApkFile!!
                                if (InAppUpdateManager.canInstallApks(context)) {
                                    val launched = InAppUpdateManager.installApk(context, file)
                                    if (launched) {
                                        showUpdateDialog = false
                                    } else {
                                        Toast.makeText(context, "Unable to launch installer. Please check file permissions.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please allow EZmic to install unknown apps to continue", Toast.LENGTH_LONG).show()
                                    InAppUpdateManager.openInstallPermissionSettings(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioMint),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📲 Install Now", color = Color(0xFF091E16), fontWeight = FontWeight.Bold)
                        }
                    } else if (isDownloadingUpdate) {
                        Button(
                            onClick = {
                                updateDownloadJob?.cancel()
                                isDownloadingUpdate = false
                                updateErrorMessage = "Download cancelled"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = TextPrimary)
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloadingUpdate = true
                                updateErrorMessage = null
                                updateProgressPercent = 0
                                updateProgressText = "0 MB"

                                updateDownloadJob = coroutineScope.launch {
                                    InAppUpdateManager.downloadApk(context, info.apkUrl, info.latestVersion)
                                        .collect { downloadState ->
                                            when (downloadState) {
                                                is DownloadState.Progress -> {
                                                    updateProgressPercent = downloadState.percent
                                                    val readMb = String.format(java.util.Locale.US, "%.1f", downloadState.bytesRead / 1048576.0)
                                                    val totalMb = if (downloadState.totalBytes > 0) {
                                                        String.format(java.util.Locale.US, "%.1f MB", downloadState.totalBytes / 1048576.0)
                                                    } else "Unknown"
                                                    updateProgressText = "$readMb MB / $totalMb"
                                                }
                                                is DownloadState.Success -> {
                                                    isDownloadingUpdate = false
                                                    downloadedApkFile = downloadState.file
                                                    // Automatically invoke system package installer
                                                    if (InAppUpdateManager.canInstallApks(context)) {
                                                        InAppUpdateManager.installApk(context, downloadState.file)
                                                    } else {
                                                        Toast.makeText(context, "Please allow EZmic to install updates to complete", Toast.LENGTH_LONG).show()
                                                        InAppUpdateManager.openInstallPermissionSettings(context)
                                                    }
                                                }
                                                is DownloadState.Error -> {
                                                    isDownloadingUpdate = false
                                                    updateErrorMessage = downloadState.message
                                                }
                                                else -> {}
                                            }
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioMint),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (updateErrorMessage != null) "Retry" else "⚡ Update Now", color = Color(0xFF091E16), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (!isDownloadingUpdate) {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            updateErrorMessage = null
                        }) {
                            Text("Later", color = TextSecondary)
                        }
                    }
                }
            )
        }

        // Mode switch confirmation
        if (showSwitchDialog && pendingMode != null) {
            val target = pendingMode!!
            val currentName = when (selectedMode) {
                TransportMode.WIFI -> "Wi-Fi"
                TransportMode.USB -> "USB"
                TransportMode.BLUETOOTH -> "Bluetooth"
            }
            val targetName = when (target) {
                TransportMode.WIFI -> "Wi-Fi"
                TransportMode.USB -> "USB"
                TransportMode.BLUETOOTH -> "Bluetooth"
            }

            AlertDialog(
                onDismissRequest = {
                    showSwitchDialog = false
                    pendingMode = null
                },
                containerColor = Color(0xFF141923),
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text("Switch Connection Mode?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text("Your active $currentName broadcast will be stopped to switch to $targetName mode.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onStop()
                            selectedMode = target
                            showSwitchDialog = false
                            pendingMode = null
                            Toast.makeText(context, "Stopped $currentName. Switched to $targetName.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Stop & Switch", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSwitchDialog = false
                            pendingMode = null
                        }
                    ) {
                        Text("Keep Broadcasting", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun LiquidGlassDockSlider(
    selectedMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit
) {
    val modes = listOf(TransportMode.WIFI, TransportMode.USB, TransportMode.BLUETOOTH)
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(PanelSurface)
            .border(1.dp, BorderHairline, RoundedCornerShape(23.dp))
            .padding(3.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3

            val animatedX by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "DockSliderPill"
            )

            // Active Tab Pill
            Box(
                modifier = Modifier
                    .offset(x = animatedX)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF22262E))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            )

            // 3 Interactive Tabs
            Row(modifier = Modifier.fillMaxSize()) {
                modes.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val activeColor = Color.White
                    val inactiveColor = Color(0xFF8E959E)
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) StudioMint else inactiveColor,
                        label = "IconColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) activeColor else inactiveColor,
                        label = "TextColor"
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onModeSelected(mode)
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (mode) {
                            TransportMode.WIFI -> {
                                Canvas(modifier = Modifier.size(14.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawCircle(color = iconColor, radius = 1.6.dp.toPx(), center = Offset(w / 2f, h * 0.85f))
                                    drawArc(
                                        color = iconColor,
                                        startAngle = 210f,
                                        sweepAngle = 120f,
                                        useCenter = false,
                                        topLeft = Offset(w * 0.22f, h * 0.42f),
                                        size = Size(w * 0.56f, h * 0.56f),
                                        style = Stroke(width = 1.6f.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = iconColor,
                                        startAngle = 215f,
                                        sweepAngle = 110f,
                                        useCenter = false,
                                        topLeft = Offset(w * 0.04f, h * 0.12f),
                                        size = Size(w * 0.92f, h * 0.92f),
                                        style = Stroke(width = 1.6f.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                            TransportMode.USB -> {
                                Canvas(modifier = Modifier.size(14.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(color = iconColor, start = Offset(w * 0.5f, h * 0.2f), end = Offset(w * 0.5f, h * 0.85f), strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
                                    drawCircle(color = iconColor, radius = 1.7.dp.toPx(), center = Offset(w * 0.5f, h * 0.85f))
                                    drawLine(color = iconColor, start = Offset(w * 0.5f, h * 0.55f), end = Offset(w * 0.2f, h * 0.42f), strokeWidth = 1.4f.dp.toPx(), cap = StrokeCap.Round)
                                    drawCircle(color = iconColor, radius = 1.4.dp.toPx(), center = Offset(w * 0.2f, h * 0.4f))
                                    drawLine(color = iconColor, start = Offset(w * 0.5f, h * 0.45f), end = Offset(w * 0.8f, h * 0.35f), strokeWidth = 1.4f.dp.toPx(), cap = StrokeCap.Round)
                                    drawRect(color = iconColor, topLeft = Offset(w * 0.72f, h * 0.24f), size = Size(2.6.dp.toPx(), 2.6.dp.toPx()))
                                    drawLine(color = iconColor, start = Offset(w * 0.5f, h * 0.15f), end = Offset(w * 0.35f, h * 0.28f), strokeWidth = 1.4f.dp.toPx(), cap = StrokeCap.Round)
                                    drawLine(color = iconColor, start = Offset(w * 0.5f, h * 0.15f), end = Offset(w * 0.65f, h * 0.28f), strokeWidth = 1.4f.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                            TransportMode.BLUETOOTH -> {
                                Canvas(modifier = Modifier.size(14.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val path = Path().apply {
                                        moveTo(w * 0.5f, h * 0.08f)
                                        lineTo(w * 0.5f, h * 0.92f)
                                        moveTo(w * 0.22f, h * 0.32f)
                                        lineTo(w * 0.78f, h * 0.68f)
                                        lineTo(w * 0.5f, h * 0.92f)
                                        lineTo(w * 0.5f, h * 0.08f)
                                        lineTo(w * 0.78f, h * 0.32f)
                                        lineTo(w * 0.22f, h * 0.68f)
                                    }
                                    drawPath(path = path, color = iconColor, style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = when (mode) {
                                TransportMode.WIFI -> "Wi-Fi"
                                TransportMode.USB -> "USB"
                                TransportMode.BLUETOOTH -> "Bluetooth"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
