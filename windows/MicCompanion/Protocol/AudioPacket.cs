using System.Buffers.Binary;

namespace MicCompanion.Protocol;

public class AudioPacket
{
    public uint Magic { get; set; } = PacketConstants.Magic;
    public uint SequenceNumber { get; set; }
    public byte Codec { get; set; } = PacketConstants.CodecOpus;
    public byte Flags { get; set; }
    public ushort PayloadLength { get; set; }
    public byte[] Payload { get; set; } = Array.Empty<byte>();

    public bool IsMuted => (Flags & PacketConstants.FlagMuted) != 0;
    public bool IsStereo => (Flags & PacketConstants.FlagStereo) != 0;

    public static AudioPacket? Parse(byte[] data, int offset, int length)
    {
        if (length < PacketConstants.HeaderSize)
            return null;

        ReadOnlySpan<byte> span = data.AsSpan(offset, length);
        uint magic = BinaryPrimitives.ReadUInt32BigEndian(span[0..4]);
        if (magic != PacketConstants.Magic)
            return null;

        uint seq = BinaryPrimitives.ReadUInt32BigEndian(span[4..8]);
        byte codec = span[8];
        byte flags = span[9];
        ushort payloadLen = BinaryPrimitives.ReadUInt16BigEndian(span[10..12]);

        if (length < PacketConstants.HeaderSize + payloadLen)
            return null;

        byte[] payload = new byte[payloadLen];
        data.AsSpan(offset + PacketConstants.HeaderSize, payloadLen).CopyTo(payload);

        return new AudioPacket
        {
            Magic = magic,
            SequenceNumber = seq,
            Codec = codec,
            Flags = flags,
            PayloadLength = payloadLen,
            Payload = payload
        };
    }

    public byte[] Serialize()
    {
        byte[] buffer = new byte[PacketConstants.HeaderSize + Payload.Length];
        Span<byte> span = buffer.AsSpan();

        BinaryPrimitives.WriteUInt32BigEndian(span[0..4], Magic);
        BinaryPrimitives.WriteUInt32BigEndian(span[4..8], SequenceNumber);
        span[8] = Codec;
        span[9] = Flags;
        BinaryPrimitives.WriteUInt16BigEndian(span[10..12], (ushort)Payload.Length);

        Payload.CopyTo(buffer, PacketConstants.HeaderSize);
        return buffer;
    }
}
