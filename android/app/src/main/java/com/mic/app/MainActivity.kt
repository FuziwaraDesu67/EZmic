package com.mic.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mic.app.service.MicForegroundService
import com.mic.app.service.StreamingState
import com.mic.app.transport.BluetoothTransmitter
import com.mic.app.transport.DiscoveryBeacon
import com.mic.app.transport.UsbTransmitter
import com.mic.app.transport.WifiTransmitter
import com.mic.app.ui.MainScreen
import com.mic.app.ui.MicAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var micService: MicForegroundService? = null
    private var isBound = false
    private val localState = MutableStateFlow(StreamingState())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? MicForegroundService.LocalBinder
            micService = localBinder?.getService()
            isBound = true

            lifecycleScope.launch {
                micService?.streamingState?.collect { state ->
                    localState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            micService = null
            isBound = false
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(this, "Microphone permission is required to stream audio.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        bindStreamingService()

        setContent {
            MicAppTheme {
                val state by localState.collectAsState()
                val pairedDevices = getPairedBluetoothDevices()

                MainScreen(
                    state = state,
                    pairedDevices = pairedDevices,
                    onStartWifi = { ip, port, useOpus, ns, aec ->
                        ensureServiceStarted()
                        val transmitter = WifiTransmitter(targetIp = ip, targetPort = port)
                        micService?.startStreaming(transmitter, useOpus, ns, aec)
                    },
                    onStartUsb = { port, useOpus, ns, aec ->
                        ensureServiceStarted()
                        val transmitter = UsbTransmitter(port = port)
                        micService?.startStreaming(transmitter, useOpus, ns, aec)
                    },
                    onStartBluetooth = { device, useOpus, ns, aec ->
                        ensureServiceStarted()
                        val transmitter = BluetoothTransmitter(targetDevice = device)
                        micService?.startStreaming(transmitter, useOpus, ns, aec)
                    },
                    onStop = {
                        micService?.stopStreaming()
                    },
                    onToggleMute = { muted ->
                        micService?.setMute(muted)
                    },
                    onGainChanged = { gain ->
                        micService?.setGain(gain)
                    },
                    onDiscoverWifi = { onFound ->
                        lifecycleScope.launch {
                            Toast.makeText(this@MainActivity, "Searching for PC beacon...", Toast.LENGTH_SHORT).show()
                            val beacon = DiscoveryBeacon(this@MainActivity)
                            val discovered = beacon.discoverPc(timeoutMs = 4000)
                            if (discovered != null) {
                                onFound(discovered.ip, discovered.port, discovered.name)
                                Toast.makeText(this@MainActivity, "Found: ${discovered.name} (${discovered.ip})", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "No PC beacon found. Check Wi-Fi or enter IP manually.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ensureServiceStarted() {
        val serviceIntent = Intent(this, MicForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun bindStreamingService() {
        val serviceIntent = Intent(this, MicForegroundService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
