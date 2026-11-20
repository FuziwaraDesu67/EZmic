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

    // Security: Active Client IP Pinning & Session Locking (Resolves S-03 Audio Injection)
    private IPAddress? _lockedSenderIp;
    private DateTime _lastAuthorizedPacketTime = DateTime.MinValue;
    private DateTime _lastUnauthorizedWarningTime = DateTime.MinValue;

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
        _lockedSenderIp = null;
        _lastAuthorizedPacketTime = DateTime.MinValue;

        _receiveTask = Task.Run(() => ReceiveLoop(_cts.Token));

        IsConnected = true;
        ConnectionStateChanged?.Invoke(true, $"Listening on UDP port {_port} (Session Lock Active)");
        LogMessage?.Invoke($"Wi-Fi receiver active on UDP port {_port} (Protected with Client IP Pinning)");

        return Task.CompletedTask;
    }

    private async Task ReceiveLoop(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _udpClient != null)
        {
            try
            {
                var result = await _udpClient.ReceiveAsync(token);
                if (result.Buffer.Length < PacketConstants.HeaderSize)
                    continue;

                var senderIp = result.RemoteEndPoint.Address;

                // 1. Verify that sender is on local/private network
                if (!IsPrivateOrLocal(senderIp))
                {
                    LogRateLimitedSecurityWarning(senderIp, "Rejected packet from non-private IP");
                    continue;
                }

                // 2. Parse packet header
                var packet = AudioPacket.Parse(result.Buffer, 0, result.Buffer.Length);
                if (packet == null)
                    continue;

                // 3. Security (EZMIC-VULN-WIFI-SPOOFED-LOCKOUT): Authenticated Session Token Verification
                // Packets must present the valid ephemeral session token generated during discovery.
                // Unauthenticated packets are discarded immediately without altering session lock state.
                uint activeToken = DiscoveryService.ActiveSessionToken;
                if (activeToken != 0)
                {
                    if (packet.Magic != PacketConstants.MagicV2 || packet.SessionToken != activeToken)
                    {
                        LogRateLimitedSecurityWarning(senderIp, "Rejected packet with invalid or missing session token");
                        continue;
                    }
                }

                // 4. Client IP Pinning & Session Lock enforcement
                DateTime now = DateTime.UtcNow;
                if (_lockedSenderIp == null)
                {
                    _lockedSenderIp = senderIp;
                    _lastAuthorizedPacketTime = now;
                    LogMessage?.Invoke($"[Security] Session locked to authorized client IP: {senderIp}");
                }
                else if (!_lockedSenderIp.Equals(senderIp))
                {
                    // Allow fast session handover if previous client has been idle/silent for > 2.5 seconds
                    if ((now - _lastAuthorizedPacketTime).TotalSeconds > 2.5)
                    {
                        var oldIp = _lockedSenderIp;
                        _lockedSenderIp = senderIp;
                        _lastAuthorizedPacketTime = now;
                        LogMessage?.Invoke($"[Security] Previous session from {oldIp} timed out. Re-locked to {senderIp}");
                    }
                    else
                    {
                        // Drop unauthorized audio packet from other LAN devices!
                        LogRateLimitedSecurityWarning(senderIp, $"Blocked unauthorized audio injection attempt while locked to {_lockedSenderIp}");
                        continue;
                    }
                }

                _lastAuthorizedPacketTime = now;
                PacketReceived?.Invoke(packet);
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

    private void LogRateLimitedSecurityWarning(IPAddress senderIp, string reason)
    {
        DateTime now = DateTime.UtcNow;
        if ((now - _lastUnauthorizedWarningTime).TotalSeconds > 3)
        {
            _lastUnauthorizedWarningTime = now;
            LogMessage?.Invoke($"[Security Warning] {reason} (Sender: {senderIp})");
        }
    }

    private static bool IsPrivateOrLocal(IPAddress ip)
    {
        if (IPAddress.IsLoopback(ip))
            return true;

        if (ip.AddressFamily == AddressFamily.InterNetwork)
        {
            byte[] bytes = ip.GetAddressBytes();
            // 10.0.0.0/8
            if (bytes[0] == 10) return true;
            // 172.16.0.0/12
            if (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) return true;
            // 192.168.0.0/16
            if (bytes[0] == 192 && bytes[1] == 168) return true;
            // 169.254.0.0/16 (Link-local)
            if (bytes[0] == 169 && bytes[1] == 254) return true;
        }

        return false;
    }

    private void StopInternal()
    {
        _cts?.Cancel();
        _udpClient?.Dispose();
        _udpClient = null;
        _cts?.Dispose();
        _cts = null;
        _lockedSenderIp = null;
        _lastAuthorizedPacketTime = DateTime.MinValue;

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
