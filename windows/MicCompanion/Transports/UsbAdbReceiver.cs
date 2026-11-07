using System.Net;
using System.Net.Sockets;
using MicCompanion.Protocol;
using MicCompanion.Utils;

namespace MicCompanion.Transports;

public class UsbAdbReceiver : IAudioTransport
{
    private TcpListener? _listener;
    private TcpClient? _client;
    private NetworkStream? _stream;
    private CancellationTokenSource? _cts;
    private Task? _listenTask;
    private Task? _monitorTask;
    private readonly int _port;

    public string Name => "USB (ADB)";
    public bool IsConnected { get; private set; }

    public event Action<AudioPacket>? PacketReceived;
    public event Action<bool, string>? ConnectionStateChanged;
    public event Action<string>? LogMessage;

    public UsbAdbReceiver(int port = PacketConstants.DefaultUsbPort)
    {
        _port = port;
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        StopInternal();

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);

        // Check if ADB is accessible
        bool adbAvailable = await AdbHelper.IsAdbAvailableAsync();
        if (!adbAvailable)
        {
            LogMessage?.Invoke("Warning: Could not run adb.exe. Check USB debugging or SDK path.");
        }
        else
        {
            var devices = await AdbHelper.GetConnectedDevicesAsync();
            if (devices.Count > 0)
            {
                LogMessage?.Invoke($"ADB detected phone: {string.Join(", ", devices)}");
            }
            else
            {
                LogMessage?.Invoke("ADB: No phone detected yet. Plug in phone and enable USB debugging.");
            }

            bool tunneled = await AdbHelper.SetupPortTunnelAsync(_port);
            if (tunneled)
            {
                LogMessage?.Invoke($"USB tunnel configured on port {_port}.");
            }
        }

        // Listen on loopback port for incoming TCP connection from Android via ADB reverse tunnel
        _listener = new TcpListener(IPAddress.Any, _port);
        _listener.Start();

        IsConnected = false;
        ConnectionStateChanged?.Invoke(false, "Waiting for phone via USB...");
        LogMessage?.Invoke($"Listening for USB audio stream on port {_port}...");
        _listenTask = Task.Run(() => AcceptLoopAsync(_cts.Token));
        _monitorTask = Task.Run(() => AdbMonitorLoopAsync(_cts.Token));
    }

    private async Task AdbMonitorLoopAsync(CancellationToken token)
    {
        bool wasTunneled = false;
        while (!token.IsCancellationRequested)
        {
            try
            {
                var devices = await AdbHelper.GetConnectedDevicesAsync();
                if (devices.Count > 0)
                {
                    if (!wasTunneled)
                    {
                        bool tunneled = await AdbHelper.SetupPortTunnelAsync(_port);
                        if (tunneled)
                        {
                            wasTunneled = true;
                            LogMessage?.Invoke($"ADB phone attached ({string.Join(", ", devices)}). Reverse tunnel active.");
                        }
                    }
                }
                else
                {
                    wasTunneled = false;
                }
            }
            catch { }

            try
            {
                await Task.Delay(2500, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _listener != null)
        {
            try
            {
                _client = await _listener.AcceptTcpClientAsync(token);
                _client.NoDelay = true;
                _stream = _client.GetStream();

                IsConnected = true;
                ConnectionStateChanged?.Invoke(true, "Connected via USB");
                LogMessage?.Invoke("USB audio stream connected!");

                await ReadStreamLoopAsync(_stream, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"USB connection error: {ex.Message}");
                }
            }
            finally
            {
                IsConnected = false;
                ConnectionStateChanged?.Invoke(false, "Waiting for phone via USB...");
                _stream?.Dispose();
                _client?.Dispose();
                _stream = null;
                _client = null;
            }
        }
    }

    private async Task ReadStreamLoopAsync(NetworkStream stream, CancellationToken token)
    {
        byte[] headerBuffer = new byte[PacketConstants.HeaderSize];

        while (!token.IsCancellationRequested)
        {
            int headerRead = 0;
            while (headerRead < PacketConstants.HeaderSize)
            {
                int read = await stream.ReadAsync(headerBuffer.AsMemory(headerRead, PacketConstants.HeaderSize - headerRead), token);
                if (read == 0) return; // Disconnected
                headerRead += read;
            }

            ushort payloadLen = (ushort)((headerBuffer[10] << 8) | headerBuffer[11]);
            byte[] fullPacket = new byte[PacketConstants.HeaderSize + payloadLen];
            Buffer.BlockCopy(headerBuffer, 0, fullPacket, 0, PacketConstants.HeaderSize);

            int payloadRead = 0;
            while (payloadRead < payloadLen)
            {
                int read = await stream.ReadAsync(fullPacket.AsMemory(PacketConstants.HeaderSize + payloadRead, payloadLen - payloadRead), token);
                if (read == 0) return;
                payloadRead += read;
            }

            var packet = AudioPacket.Parse(fullPacket, 0, fullPacket.Length);
            if (packet != null)
            {
                PacketReceived?.Invoke(packet);
            }
        }
    }

    private void StopInternal()
    {
        _cts?.Cancel();
        _stream?.Dispose();
        _client?.Dispose();
        _listener?.Stop();

        _stream = null;
        _client = null;
        _listener = null;
        _cts?.Dispose();
        _cts = null;

        if (IsConnected)
        {
            IsConnected = false;
            ConnectionStateChanged?.Invoke(false, "Disconnected");
        }
    }

    public Task StopAsync()
    {
        StopInternal();
        LogMessage?.Invoke("USB receiver stopped.");
        return Task.CompletedTask;
    }

    public void Dispose()
    {
        StopInternal();
    }
}
