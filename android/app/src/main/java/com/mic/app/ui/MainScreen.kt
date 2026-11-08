package com.mic.app.ui

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlin.math.log10
import kotlin.math.max

enum class TransportMode {
    WIFI, USB, BLUETOOTH
}

@Composable
fun MainScreen(
    state: StreamingState,
    pairedDevices: List<BluetoothDevice>,
    onStartWifi: (ip: String, port: Int, useOpus: Boolean, ns: Boolean, aec: Boolean) -> Unit,
    onStartUsb: (port: Int, useOpus: Boolean, ns: Boolean, aec: Boolean) -> Unit,
    onStartBluetooth: (device: BluetoothDevice, useOpus: Boolean, ns: Boolean, aec: Boolean) -> Unit,
    onStop: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onGainChanged: (Float) -> Unit,
    onDiscoverWifi: ((Discovered: (String, Int, String) -> Unit) -> Unit)
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(TransportMode.USB) }
    var wifiIp by remember { mutableStateOf("192.168.1.100") }
    var wifiPort by remember { mutableStateOf("49152") }
    var usbPort by remember { mutableStateOf("49153") }
    var selectedDevice by remember { 
        mutableStateOf(
            pairedDevices.find { 
                val n = it.name ?: ""
                n.contains("DESKTOP", true) || n.contains("PC", true) || n.contains("LAPTOP", true)
            } ?: pairedDevices.firstOrNull()
        ) 
    }
    var enableNs by remember { mutableStateOf(true) }
    var enableAec by remember { mutableStateOf(true) }
    var gainSlider by remember { mutableFloatStateOf(100f) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<TransportMode?>(null) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Header with Broadcast Tally
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
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

                // Authentic Broadcast Tally Pill
                val tallyText = when {
                    state.isMuted && state.isStreaming -> "MUTED"
                    state.isStreaming -> "ON AIR"
                    else -> "STANDBY"
                }
                val tallyColor = when {
                    state.isMuted && state.isStreaming -> StudioRed
                    state.isStreaming -> StudioGreen
                    else -> TextMuted
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelSurface)
                        .border(1.dp, tallyColor.copy(alpha = if (state.isStreaming) 0.5f else 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
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
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = tallyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isStreaming) tallyColor else TextSecondary
                        )
                    }
                }
            }

            // 2. Connection Transport Selector (Liquid Glass Waterline Dock)
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transport",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = when (selectedMode) {
                                TransportMode.WIFI -> "Wi-Fi Ready"
                                TransportMode.USB -> "USB Ready"
                                TransportMode.BLUETOOTH -> "Bluetooth Ready"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioGreen
                        )
                    }

                    // Liquid Glass Dock Capsule (Waterline Pill)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Transport Contextual Config
                    when (selectedMode) {
                        TransportMode.USB -> {
                            Text(
                                text = "Plug phone into PC with USB debugging enabled for 5ms latency.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        TransportMode.WIFI -> {
                            OutlinedTextField(
                                value = wifiIp,
                                onValueChange = { wifiIp = it },
                                label = { Text("PC IP address", fontSize = 12.sp) },
                                singleLine = true,
                                enabled = !state.isStreaming,
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
                                Text("Auto-detect PC", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                        TransportMode.BLUETOOTH -> {
                            if (pairedDevices.isEmpty()) {
                                Text(
                                    text = "No paired Bluetooth devices found. Pair PC in Bluetooth settings first.",
                                    fontSize = 12.sp,
                                    color = StudioRed
                                )
                            } else {
                                var expanded by remember { mutableStateOf(false) }
                                Column {
                                    Text(
                                        text = "Target Bluetooth PC:",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ControlWell)
                                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                                            .clickable(enabled = !state.isStreaming) { expanded = true }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedDevice?.name ?: selectedDevice?.address ?: "Select paired device...",
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
            }

            // 3. Hero Moment: 24-Segment Broadcast LED Ladder VU Meter
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
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
                            fontSize = 11.sp,
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

                    // 24-Segment Discrete LED Ladder Meter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090A0D))
                            .border(1.dp, Color(0xFF1C2028), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 7.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        ) {
                            val segmentCount = 24
                            val gap = 2.5f.dp.toPx()
                            val totalGaps = gap * (segmentCount - 1)
                            val segWidth = (size.width - totalGaps) / segmentCount
                            val litCount = (animatedLevel * segmentCount).toInt()

                            for (i in 0 until segmentCount) {
                                val isLit = i < litCount
                                val activeColor = when {
                                    i < 16 -> StudioGreen
                                    i < 20 -> StudioAmber
                                    else -> StudioRed
                                }
                                val unlitColor = when {
                                    i < 16 -> Color(0xFF0C241A)
                                    i < 20 -> Color(0xFF281B07)
                                    else -> Color(0xFF280D0D)
                                }

                                drawRoundRect(
                                    color = if (isLit) activeColor else unlitColor,
                                    topLeft = Offset(i * (segWidth + gap), 0f),
                                    size = Size(segWidth, size.height),
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            }
                        }
                    }

                    // Calibrated Decibel Scale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("-42", "-24", "-12", "-6", "0 dB").forEach { mark ->
                            Text(
                                text = mark,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 4. Hardware Gain & Audio Enhancements
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                    .padding(bottom = 18.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gain",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = "${gainSlider.toInt()}%",
                            fontSize = 11.sp,
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = state.isStreaming) {
                                Toast.makeText(context, "Noise suppression is locked while broadcasting.", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Noise suppression",
                                fontSize = 13.sp,
                                color = if (state.isStreaming) TextMuted else TextPrimary
                            )
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
                                disabledCheckedThumbColor = StudioGreen.copy(alpha = 0.6f),
                                disabledCheckedTrackColor = StudioGreen.copy(alpha = 0.25f),
                                disabledUncheckedThumbColor = Color(0xFF6B7280).copy(alpha = 0.4f),
                                disabledUncheckedTrackColor = Color(0xFF374151).copy(alpha = 0.2f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = state.isStreaming) {
                                Toast.makeText(context, "Echo cancellation is locked while broadcasting.", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Echo cancellation",
                                fontSize = 13.sp,
                                color = if (state.isStreaming) TextMuted else TextPrimary
                            )
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
                                disabledCheckedThumbColor = StudioGreen.copy(alpha = 0.6f),
                                disabledCheckedTrackColor = StudioGreen.copy(alpha = 0.25f),
                                disabledUncheckedThumbColor = Color(0xFF6B7280).copy(alpha = 0.4f),
                                disabledUncheckedTrackColor = Color(0xFF374151).copy(alpha = 0.2f)
                            )
                        )
                    }

                    if (state.isStreaming) {
                        Text(
                            text = "🔒 Hardware DSP filters are locked while broadcast is on air.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Status Readout
            val isError = state.statusMessage.contains("failed", ignoreCase = true) || 
                          state.statusMessage.contains("error", ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isError -> StudioRed
                                state.isConnected -> StudioGreen
                                state.isStreaming -> StudioAmber
                                else -> Color(0xFF6B7280)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.statusMessage.ifEmpty { "Ready to connect" },
                    fontSize = 12.sp,
                    color = when {
                        isError -> StudioRed
                        state.isConnected -> TextPrimary
                        else -> TextSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 5. Primary Broadcast Push-Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (state.isStreaming) {
                            onStop()
                        } else {
                            when (selectedMode) {
                                TransportMode.WIFI -> onStartWifi(wifiIp, wifiPort.toIntOrNull() ?: 49152, true, enableNs, enableAec)
                                TransportMode.USB -> onStartUsb(usbPort.toIntOrNull() ?: 49153, true, enableNs, enableAec)
                                TransportMode.BLUETOOTH -> {
                                    val dev = selectedDevice
                                    if (dev != null) {
                                        onStartBluetooth(dev, true, enableNs, enableAec)
                                    } else {
                                        Toast.makeText(context, "Select your paired PC first", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isStreaming) Color(0xFF1E222A) else StudioGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(
                            width = if (state.isStreaming) 1.dp else 0.dp,
                            color = if (state.isStreaming) StudioRed else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        text = if (state.isStreaming) "Stop broadcast" else "Start broadcast",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (state.isStreaming) StudioRed else Color(0xFF042F1E)
                    )
                }

                if (state.isStreaming) {
                    FilledTonalButton(
                        onClick = { onToggleMute(!state.isMuted) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (state.isMuted) Color(0xFF3B1212) else Color(0xFF1E222A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = if (state.isMuted) StudioRed else BorderHairline,
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Text(
                            text = if (state.isMuted) "Unmute" else "Mute",
                            color = if (state.isMuted) StudioRed else TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F141F), Color(0xFF080B12))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))
                ),
                RoundedCornerShape(23.dp)
            )
            .padding(3.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3

            val animatedX by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "LiquidWaterlineDrop"
            )

            // Liquid Sliding Water Drop
            Box(
                modifier = Modifier
                    .offset(x = animatedX)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x38FFFFFF),
                                Color(0x2810B981),
                                Color(0x14082216)
                            ),
                            center = Offset(Float.POSITIVE_INFINITY, 0f),
                            radius = 200f
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color(0x90FFFFFF), Color(0x2510B981))
                        ),
                        RoundedCornerShape(20.dp)
                    )
            )

            // 3 Interactive Tabs
            Row(modifier = Modifier.fillMaxSize()) {
                modes.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val activeColor = Color.White
                    val inactiveColor = Color(0xFF8B949E)
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) StudioGreen else inactiveColor,
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
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
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
                                    val path = androidx.compose.ui.graphics.Path().apply {
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
