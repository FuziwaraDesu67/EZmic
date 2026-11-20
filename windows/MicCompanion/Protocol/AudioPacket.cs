using System.Buffers.Binary;

namespace MicCompanion.Protocol;

public class AudioPacket
{
    public uint Magic { get; set; } = PacketConstants.MagicV2;
    public uint SessionToken { get; set; } = 0;
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

        if (magic == PacketConstants.MagicV2)
        {
            if (length < PacketConstants.HeaderSizeV2)
                return null;

            uint token = BinaryPrimitives.ReadUInt32BigEndian(span[4..8]);
            uint seq = BinaryPrimitives.ReadUInt32BigEndian(span[8..12]);
            byte codec = span[12];
            byte flags = span[13];
            ushort payloadLen = BinaryPrimitives.ReadUInt16BigEndian(span[14..16]);

            if (length < PacketConstants.HeaderSizeV2 + payloadLen)
                return null;

            byte[] payload = new byte[payloadLen];
            data.AsSpan(offset + PacketConstants.HeaderSizeV2, payloadLen).CopyTo(payload);

            return new AudioPacket
            {
                Magic = magic,
                SessionToken = token,
                SequenceNumber = seq,
                Codec = codec,
                Flags = flags,
                PayloadLength = payloadLen,
                Payload = payload
            };
        }
        else if (magic == PacketConstants.Magic)
        {
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
                SessionToken = 0,
                SequenceNumber = seq,
                Codec = codec,
                Flags = flags,
                PayloadLength = payloadLen,
                Payload = payload
            };
        }

        return null;
    }

    public byte[] Serialize()
    {
        byte[] buffer = new byte[PacketConstants.HeaderSizeV2 + Payload.Length];
        Span<byte> span = buffer.AsSpan();

        BinaryPrimitives.WriteUInt32BigEndian(span[0..4], Magic);
        BinaryPrimitives.WriteUInt32BigEndian(span[4..8], SessionToken);
        BinaryPrimitives.WriteUInt32BigEndian(span[8..12], SequenceNumber);
        span[12] = Codec;
        span[13] = Flags;
        BinaryPrimitives.WriteUInt16BigEndian(span[14..16], (ushort)Payload.Length);

        Payload.CopyTo(buffer, PacketConstants.HeaderSizeV2);
        return buffer;
    }
}
