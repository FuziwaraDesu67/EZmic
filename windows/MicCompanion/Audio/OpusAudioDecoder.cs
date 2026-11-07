using Concentus.Enums;
using Concentus.Structs;
using MicCompanion.Protocol;

namespace MicCompanion.Audio;

public class OpusAudioDecoder : IDisposable
{
    private OpusDecoder? _decoder;
    private readonly int _sampleRate;
    private readonly int _channels;
    private readonly short[] _decodeBuffer;

    public OpusAudioDecoder(int sampleRate = PacketConstants.SampleRate, int channels = 1)
    {
        _sampleRate = sampleRate;
        _channels = channels;
        _decodeBuffer = new short[PacketConstants.SamplesPerFrame * channels * 2];
        _decoder = new OpusDecoder(_sampleRate, _channels);
    }

    public short[] Decode(byte[] opusData, int offset, int length)
    {
        if (_decoder == null)
            throw new ObjectDisposedException(nameof(OpusAudioDecoder));

        int samplesDecoded = _decoder.Decode(opusData, offset, length, _decodeBuffer, 0, PacketConstants.SamplesPerFrame, false);
        short[] result = new short[samplesDecoded * _channels];
        Array.Copy(_decodeBuffer, result, result.Length);
        return result;
    }

    public short[] DecodeLoss()
    {
        if (_decoder == null)
            return Array.Empty<short>();

        int samplesDecoded = _decoder.Decode(null, 0, 0, _decodeBuffer, 0, PacketConstants.SamplesPerFrame, true);
        short[] result = new short[samplesDecoded * _channels];
        Array.Copy(_decodeBuffer, result, result.Length);
        return result;
    }

    public void Reset()
    {
        _decoder?.ResetState();
    }

    public void Dispose()
    {
        _decoder = null;
    }
}
