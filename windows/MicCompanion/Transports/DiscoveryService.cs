using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using MicCompanion.Protocol;

namespace MicCompanion.Transports;

public class DiscoveryService : IDisposable
{
    private UdpClient? _udpClient;
    private UdpClient? _listenerClient;
    private CancellationTokenSource? _cts;
    private Task? _broadcastTask;
    private Task? _listenerTask;

    // Security (EZMIC-VULN-WIFI-SPOOFED-LOCKOUT): Ephemeral session authentication token
    private static uint _activeSessionToken = (uint)Random.Shared.Next(100000, 999999);
    public static uint ActiveSessionToken => _activeSessionToken;

    public static void RefreshSessionToken()
    {
        _activeSessionToken = (uint)Random.Shared.Next(100000, 999999);
    }

    public event Action<string>? LogMessage;

    public void Start(int audioPort = PacketConstants.DefaultWifiAudioPort)
    {
        Stop();
        RefreshSessionToken();
        _cts = new CancellationTokenSource();
        _udpClient = new UdpClient();
        _udpClient.EnableBroadcast = true;

        try
        {
            _listenerClient = new UdpClient(new IPEndPoint(IPAddress.Any, PacketConstants.DefaultWifiDiscoveryPort));
            _listenerTask = Task.Run(() => QueryResponseLoop(_cts.Token, audioPort));
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke($"Discovery query listener note: {ex.Message}");
        }

        _broadcastTask = Task.Run(() => BroadcastLoop(_cts.Token, audioPort));
        LogMessage?.Invoke($"Wi-Fi auto-discovery beacon started (Auth Token: {ActiveSessionToken}).");
    }

    private async Task BroadcastLoop(CancellationToken token, int audioPort)
    {
        string hostName = Dns.GetHostName();

        while (!token.IsCancellationRequested)
        {
            try
            {
                var endpoints = GetBroadcastEndpoints();

                foreach (var (broadcastEp, localIp) in endpoints)
                {
                    try
                    {
                        var payload = new
                        {
                            service = "mic-to-pc",
                            version = "2.0",
                            device = hostName,
                            ip = localIp,
                            port = audioPort,
                            token = ActiveSessionToken
                        };

                        byte[] bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload));

                        if (_udpClient != null)
                        {
                            await _udpClient.SendAsync(bytes, bytes.Length, broadcastEp);
                        }
                    }
                    catch { /* Ignore single socket send failure to avoid killing the loop */ }
                }
            }
            catch (Exception ex)
            {
                if (!token.IsCancellationRequested)
                {
                    LogMessage?.Invoke($"Discovery broadcast warning: {ex.Message}");
                }
            }

            try
            {
                await Task.Delay(1500, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task QueryResponseLoop(CancellationToken token, int audioPort)
    {
        string hostName = Dns.GetHostName();

        while (!token.IsCancellationRequested && _listenerClient != null)
        {
            try
            {
                var result = await _listenerClient.ReceiveAsync(token);
                string req = Encoding.UTF8.GetString(result.Buffer);
                if (req.Contains("mic-query") || req.Contains("mic-to-pc"))
                {
                    var localIp = GetPrimaryLanIp();
                    var payload = new
                    {
                        service = "mic-to-pc",
                        version = "2.0",
                        device = hostName,
                        ip = localIp,
                        port = audioPort,
                        token = ActiveSessionToken
                    };

                    byte[] bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(payload));
                    await _listenerClient.SendAsync(bytes, bytes.Length, result.RemoteEndPoint);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch
            {
                // Ignore transient receive failure or malformed packets
            }
        }
    }

    public static string GetPrimaryLanIp()
    {
        try
        {
            var interfaces = NetworkInterface.GetAllNetworkInterfaces();
            foreach (var ni in interfaces)
            {
                if (ni.OperationalStatus != OperationalStatus.Up ||
                    ni.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                    continue;

                var ipProps = ni.GetIPProperties();
                foreach (var unicast in ipProps.UnicastAddresses)
                {
                    if (unicast.Address.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ipStr = unicast.Address.ToString();
                        if (!ipStr.StartsWith("169.254.") && !ipStr.StartsWith("127."))
                        {
                            return ipStr;
                        }
                    }
                }
            }
        }
        catch { }

        return "127.0.0.1";
    }

    private static List<(IPEndPoint Endpoint, string LocalIp)> GetBroadcastEndpoints()
    {
        var list = new List<(IPEndPoint, string)>();
        int port = PacketConstants.DefaultWifiDiscoveryPort;
        string primaryIp = GetPrimaryLanIp();

        // 1. Global broadcast with primary LAN IP
        list.Add((new IPEndPoint(IPAddress.Broadcast, port), primaryIp));

        // 2. Subnet directed broadcasts for active network adapters
        try
        {
            var interfaces = NetworkInterface.GetAllNetworkInterfaces();
            foreach (var ni in interfaces)
            {
                if (ni.OperationalStatus != OperationalStatus.Up ||
                    ni.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                    continue;

                var ipProps = ni.GetIPProperties();
                foreach (var unicast in ipProps.UnicastAddresses)
                {
                    if (unicast.Address.AddressFamily == AddressFamily.InterNetwork)
                    {
                        var ip = unicast.Address;
                        string ipStr = ip.ToString();
                        if (ipStr.StartsWith("169.254.") || ipStr.StartsWith("127."))
                            continue;

                        var mask = unicast.IPv4Mask;
                        if (mask != null)
                        {
                            byte[] ipBytes = ip.GetAddressBytes();
                            byte[] maskBytes = mask.GetAddressBytes();
                            byte[] broadcastBytes = new byte[4];

                            for (int i = 0; i < 4; i++)
                            {
                                broadcastBytes[i] = (byte)(ipBytes[i] | ~maskBytes[i]);
                            }

                            var subnetBroadcast = new IPAddress(broadcastBytes);
                            list.Add((new IPEndPoint(subnetBroadcast, port), ipStr));
                        }
                    }
                }
            }
        }
        catch { }

        return list;
    }

    public void Stop()
    {
        _cts?.Cancel();
        _udpClient?.Dispose();
        _udpClient = null;
        _listenerClient?.Dispose();
        _listenerClient = null;
        _cts?.Dispose();
        _cts = null;
        _broadcastTask = null;
        _listenerTask = null;
    }

    public void Dispose()
    {
        Stop();
    }
}
