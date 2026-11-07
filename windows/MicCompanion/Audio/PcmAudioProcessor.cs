namespace MicCompanion.Audio;

public class PcmAudioProcessor
{
    public float Gain { get; set; } = 1.0f;
    public bool IsMuted { get; set; } = false;

    /// <summary>
    /// Processes 16-bit PCM samples in-place: applies mute, applies gain, and calculates peak volume (0.0 to 1.0).
    /// </summary>
    public float Process(short[] samples)
    {
        if (samples.Length == 0)
            return 0f;

        if (IsMuted)
        {
            Array.Clear(samples, 0, samples.Length);
            return 0f;
        }

        int maxMagnitude = 0;
        float gain = Gain;

        for (int i = 0; i < samples.Length; i++)
        {
            int val = samples[i];
            if (Math.Abs(gain - 1.0f) > 0.01f)
            {
                val = (int)(val * gain);
                if (val > short.MaxValue) val = short.MaxValue;
                else if (val < short.MinValue) val = short.MinValue;
                samples[i] = (short)val;
            }

            int mag = Math.Abs(val);
            if (mag > maxMagnitude)
                maxMagnitude = mag;
        }

        return (float)maxMagnitude / short.MaxValue;
    }

    /// <summary>
    /// Duplicates mono 16-bit samples into 2-channel stereo byte array (Little-Endian)
    /// to perfectly match VB-Audio Cable's standard 48kHz Stereo format.
    /// </summary>
    public static byte[] ToStereoByteArray(short[] samples)
    {
        byte[] bytes = new byte[samples.Length * 4];
        for (int i = 0; i < samples.Length; i++)
        {
            short s = samples[i];
            int offset = i * 4;
            byte b0 = (byte)(s & 0xFF);
            byte b1 = (byte)((s >> 8) & 0xFF);

            // Left Channel
            bytes[offset] = b0;
            bytes[offset + 1] = b1;

            // Right Channel
            bytes[offset + 2] = b0;
            bytes[offset + 3] = b1;
        }
        return bytes;
    }

    public static byte[] ToByteArray(short[] samples)
    {
        byte[] bytes = new byte[samples.Length * 2];
        Buffer.BlockCopy(samples, 0, bytes, 0, bytes.Length);
        return bytes;
    }
}
