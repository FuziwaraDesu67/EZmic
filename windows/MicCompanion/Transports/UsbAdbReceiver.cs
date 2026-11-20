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

        // Listen on loopback port only (prevents external LAN exposure; resolves S-02)
        _listener = new TcpListener(IPAddress.Loopback, _port);
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
        byte[] initialHeader = new byte[PacketConstants.HeaderSize];

        while (!token.IsCancellationRequested)
        {
            // Security (EZMIC-VULN-USB-LOOPBACK-HIJACK):
            // Enforce a 5-second initial read timeout so unauthenticated/idle local connections
            // cannot occupy the loopback port and block the legitimate phone ADB connection.
            using var timeoutCts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            using var linkedCts = CancellationTokenSource.CreateLinkedTokenSource(token, timeoutCts.Token);

            int headerRead = 0;
            while (headerRead < PacketConstants.HeaderSize)
            {
                int read = await stream.ReadAsync(initialHeader.AsMemory(headerRead, PacketConstants.HeaderSize - headerRead), linkedCts.Token);
                if (read == 0) return; // Disconnected
                headerRead += read;
            }

            uint magic = System.Buffers.Binary.BinaryPrimitives.ReadUInt32BigEndian(initialHeader.AsSpan(0, 4));
            if (magic != PacketConstants.Magic && magic != PacketConstants.MagicV2)
            {
                LogMessage?.Invoke("[Security] Disconnected unauthorized local connection on USB port (Invalid magic header).");
                return;
            }

            int headerSize = (magic == PacketConstants.MagicV2) ? PacketConstants.HeaderSizeV2 : PacketConstants.HeaderSize;
            byte[] fullHeader = new byte[headerSize];
            Buffer.BlockCopy(initialHeader, 0, fullHeader, 0, PacketConstants.HeaderSize);

            if (headerSize > PacketConstants.HeaderSize)
            {
                int extraRead = 0;
                int needed = headerSize - PacketConstants.HeaderSize;
                while (extraRead < needed)
                {
                    int r = await stream.ReadAsync(fullHeader.AsMemory(PacketConstants.HeaderSize + extraRead, needed - extraRead), linkedCts.Token);
                    if (r == 0) return;
                    extraRead += r;
                }
            }

            ushort payloadLen = (magic == PacketConstants.MagicV2)
                ? System.Buffers.Binary.BinaryPrimitives.ReadUInt16BigEndian(fullHeader.AsSpan(14, 2))
                : System.Buffers.Binary.BinaryPrimitives.ReadUInt16BigEndian(fullHeader.AsSpan(10, 2));

            byte[] fullPacket = new byte[headerSize + payloadLen];
            Buffer.BlockCopy(fullHeader, 0, fullPacket, 0, headerSize);

            int payloadRead = 0;
            while (payloadRead < payloadLen)
            {
                int read = await stream.ReadAsync(fullPacket.AsMemory(headerSize + payloadRead, payloadLen - payloadRead), token);
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

    public async Task StopAsync()
    {
        StopInternal();
        try
        {
            await AdbHelper.RemovePortTunnelAsync(_port);
        }
        catch { }
        LogMessage?.Invoke("USB receiver stopped and ADB tunnel removed.");
    }

    public void Dispose()
    {
        StopInternal();
    }
}
