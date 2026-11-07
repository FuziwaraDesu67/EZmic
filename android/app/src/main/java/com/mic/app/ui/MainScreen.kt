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
import androidx.compose.ui.graphics.Color
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

            // 2. Hardware Segmented Transport Selector
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Transport",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Recessed Segmented Pill Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChassisBackground)
                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                            .padding(3.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TransportMode.values().forEach { mode ->
                                val isSelected = selectedMode == mode
                                val label = when (mode) {
                                    TransportMode.WIFI -> "Wi-Fi"
                                    TransportMode.USB -> "USB"
                                    TransportMode.BLUETOOTH -> "Bluetooth"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF252A34) else Color.Transparent)
                                        .clickable(enabled = !state.isStreaming) { selectedMode = mode }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) TextPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }

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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Noise suppression", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = enableNs,
                            onCheckedChange = { enableNs = it },
                            enabled = !state.isStreaming
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Echo cancellation", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = enableAec,
                            onCheckedChange = { enableAec = it },
                            enabled = !state.isStreaming
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
    }
}
