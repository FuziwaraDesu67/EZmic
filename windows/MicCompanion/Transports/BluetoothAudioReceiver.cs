using System.Net.Sockets;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;
using MicCompanion.Protocol;

namespace MicCompanion.Transports;

public class BluetoothAudioReceiver : IAudioTransport
{
    private BluetoothListener? _listener;
    private BluetoothClient? _client;
    private NetworkStream? _stream;
    private CancellationTokenSource? _cts;
    private Task? _listenTask;

    public string Name => "Bluetooth (RFCOMM)";
    public bool IsConnected { get; private set; }

    public event Action<AudioPacket>? PacketReceived;
    public event Action<bool, string>? ConnectionStateChanged;
    public event Action<string>? LogMessage;

    public Task StartAsync(CancellationToken cancellationToken)
    {
        StopInternal();

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);

        try
        {
            _listener = new BluetoothListener(PacketConstants.BluetoothServiceUuid)
            {
                ServiceName = "MicToPcStream"
            };
            _listener.Start();

            IsConnected = false;
            ConnectionStateChanged?.Invoke(false, "Waiting for Bluetooth connection...");
            LogMessage?.Invoke("Bluetooth RFCOMM listener started. Ready for paired phone.");

            _listenTask = Task.Run(() => AcceptLoopAsync(_cts.Token));
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke($"Bluetooth initialization error: {ex.Message}");
            ConnectionStateChanged?.Invoke(false, $"Bluetooth error: {ex.Message}");
        }

        return Task.CompletedTask;
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _listener != null)
        {
            try
            {
                _client = _listener.AcceptBluetoothClient();
                _stream = _client.GetStream();

                IsConnected = true;
                ConnectionStateChanged?.Invoke(true, $"Connected to {_client.RemoteMachineName}");
                LogMessage?.Invoke($"Bluetooth connection established with {_client.RemoteMachineName}");

                await ReadStreamLoopAsync(_stream, token);
            }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"Bluetooth connection dropped: {ex.Message}");
                }
            }
            finally
            {
                IsConnected = false;
                ConnectionStateChanged?.Invoke(false, "Waiting for Bluetooth connection...");
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
                if (read == 0) return;
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
        LogMessage?.Invoke("Bluetooth receiver stopped.");
        return Task.CompletedTask;
    }

    public void Dispose()
    {
        StopInternal();
    }
}
