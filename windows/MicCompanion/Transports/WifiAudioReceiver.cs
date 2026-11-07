using System.Net;
using System.Net.Sockets;
using MicCompanion.Protocol;

namespace MicCompanion.Transports;

public class WifiAudioReceiver : IAudioTransport
{
    private UdpClient? _udpClient;
    private CancellationTokenSource? _cts;
    private Task? _receiveTask;
    private readonly int _port;

    public string Name => "Wi-Fi";
    public bool IsConnected { get; private set; }

    public event Action<AudioPacket>? PacketReceived;
    public event Action<bool, string>? ConnectionStateChanged;
    public event Action<string>? LogMessage;

    public WifiAudioReceiver(int port = PacketConstants.DefaultWifiAudioPort)
    {
        _port = port;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        StopInternal();

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _udpClient = new UdpClient(new IPEndPoint(IPAddress.Any, _port));

        _receiveTask = Task.Run(() => ReceiveLoop(_cts.Token));

        IsConnected = true;
        ConnectionStateChanged?.Invoke(true, $"Listening on UDP port {_port}");
        LogMessage?.Invoke($"Wi-Fi receiver active on UDP port {_port}");

        return Task.CompletedTask;
    }

    private async Task ReceiveLoop(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _udpClient != null)
        {
            try
            {
                var result = await _udpClient.ReceiveAsync(token);
                if (result.Buffer.Length >= PacketConstants.HeaderSize)
                {
                    var packet = AudioPacket.Parse(result.Buffer, 0, result.Buffer.Length);
                    if (packet != null)
                    {
                        PacketReceived?.Invoke(packet);
                    }
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"Wi-Fi packet receive warning: {ex.Message}");
                }
            }
        }
    }

    private void StopInternal()
    {
        _cts?.Cancel();
        _udpClient?.Dispose();
        _udpClient = null;
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
        LogMessage?.Invoke("Wi-Fi receiver stopped.");
        return Task.CompletedTask;
    }

    public void Dispose()
    {
        StopInternal();
    }
}
