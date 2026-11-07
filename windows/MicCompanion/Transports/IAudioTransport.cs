using MicCompanion.Protocol;

namespace MicCompanion.Transports;

public interface IAudioTransport : IDisposable
{
    string Name { get; }
    bool IsConnected { get; }
    event Action<AudioPacket>? PacketReceived;
    event Action<bool, string>? ConnectionStateChanged;
    event Action<string>? LogMessage;

    Task StartAsync(CancellationToken cancellationToken);
    Task StopAsync();
}
