using MicCompanion.Protocol;

namespace MicCompanion.Audio;

public class JitterBuffer
{
    private readonly SortedDictionary<uint, AudioPacket> _packets = new();
    private readonly object _lock = new();
    private uint _nextExpectedSeq;
    private bool _initialized;
    private readonly int _targetCapacity;

    public JitterBuffer(int targetCapacity = 3)
    {
        _targetCapacity = targetCapacity;
    }

    public void AddPacket(AudioPacket packet)
    {
        lock (_lock)
        {
            if (!_initialized)
            {
                _nextExpectedSeq = packet.SequenceNumber;
                _initialized = true;
            }

            // Stale packet check
            if (packet.SequenceNumber < _nextExpectedSeq && (_nextExpectedSeq - packet.SequenceNumber) < 1000)
                return;

            _packets[packet.SequenceNumber] = packet;

            // Prevent buffer bloat (cap to max 20 packets = 400ms)
            while (_packets.Count > 20)
            {
                var oldest = _packets.Keys.First();
                _packets.Remove(oldest);
            }
        }
    }

    public AudioPacket? PopNext(out bool packetLost)
    {
        packetLost = false;
        lock (_lock)
        {
            if (!_initialized || _packets.Count == 0)
                return null;

            if (_packets.TryGetValue(_nextExpectedSeq, out var packet))
            {
                _packets.Remove(_nextExpectedSeq);
                _nextExpectedSeq++;
                return packet;
            }

            // If we have newer packets waiting and oldest is past expected
            var smallestKey = _packets.Keys.First();
            if (smallestKey > _nextExpectedSeq)
            {
                // A gap occurred
                packetLost = true;
                _nextExpectedSeq++;
                return null;
            }

            return null;
        }
    }

    public void Reset()
    {
        lock (_lock)
        {
            _packets.Clear();
            _initialized = false;
        }
    }
}
