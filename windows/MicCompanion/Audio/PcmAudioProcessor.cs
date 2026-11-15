namespace MicCompanion.Audio;

public class PcmAudioProcessor
{
    public float Gain { get; set; } = 1.0f;
    public bool IsMuted { get; set; } = false;

    // Studio Noise Gate
    public bool NoiseGateEnabled { get; set; } = true;
    public float NoiseGateThresholdDb { get; set; } = -45.0f; // dBFS
    public bool IsGateOpen { get; private set; } = false;

    // Gate Envelope State (smoothing gain to avoid clicks)
    private float _gateGain = 1.0f;
    private int _gateHoldCounter = 0;
    private const int GateHoldFrames = 8; // ~160ms hold at 20ms frames
    private const float GateAttack = 0.4f; // Quick open
    private const float GateRelease = 0.08f; // Smooth fade out

    // Soft Limiter (Anti-Clip)
    public bool SoftLimiterEnabled { get; set; } = true;

    /// <summary>
    /// Processes 16-bit PCM samples in-place:
    /// 1. Applies hardware mute
    /// 2. Applies digital gain
    /// 3. Applies studio noise gate (silences background room hum/keyboard clicks)
    /// 4. Applies soft-knee saturation limiter to prevent digital clipping
    /// 5. Calculates peak volume (0.0 to 1.0)
    /// </summary>
    public float Process(short[] samples)
    {
        if (samples.Length == 0)
            return 0f;

        if (IsMuted)
        {
            Array.Clear(samples, 0, samples.Length);
            _gateGain = 0f;
            IsGateOpen = false;
            return 0f;
        }

        float gain = Gain;
        int maxRawMagnitude = 0;

        // 1. Measure raw peak before gain/gate
        for (int i = 0; i < samples.Length; i++)
        {
            int mag = Math.Abs((int)samples[i]);
            if (mag > maxRawMagnitude)
                maxRawMagnitude = mag;
        }

        float rawPeak = (float)maxRawMagnitude / short.MaxValue;
        float rawDb = rawPeak > 0.0001f ? 20f * (float)Math.Log10(rawPeak) : -90f;

        // 2. Noise Gate logic with hold & hysteresis
        if (NoiseGateEnabled)
        {
            float threshold = NoiseGateThresholdDb;
            if (rawDb >= threshold)
            {
                IsGateOpen = true;
                _gateHoldCounter = GateHoldFrames;
            }
            else if (_gateHoldCounter > 0)
            {
                _gateHoldCounter--;
                IsGateOpen = true;
            }
            else
            {
                IsGateOpen = false;
            }

            // Smooth gate envelope transition
            float targetGateGain = IsGateOpen ? 1.0f : 0.0f;
            if (targetGateGain > _gateGain)
            {
                _gateGain = Math.Min(1.0f, _gateGain + GateAttack);
            }
            else if (targetGateGain < _gateGain)
            {
                _gateGain = Math.Max(0.0f, _gateGain - GateRelease);
            }
        }
        else
        {
            _gateGain = 1.0f;
            IsGateOpen = true;
        }

        // 3. Apply Gain, Gate, and Soft Limiter to samples
        int maxProcessedMag = 0;
        float effectiveMultiplier = gain * _gateGain;

        for (int i = 0; i < samples.Length; i++)
        {
            float sampleVal = samples[i] * effectiveMultiplier;

            // 4. Soft-Knee Anti-Clipping Limiter
            if (SoftLimiterEnabled)
            {
                float absVal = Math.Abs(sampleVal);
                if (absVal > 28000f) // Start soft compression near ceiling
                {
                    float sign = sampleVal >= 0 ? 1f : -1f;
                    float normalized = absVal / 32768f;
                    // Tanh compression curves smoothly to 1.0 without hard edges
                    float compressed = (float)Math.Tanh(normalized * 1.15f) / 1.15f;
                    sampleVal = sign * compressed * 32767f;
                }
            }

            int finalSample = (int)Math.Clamp(sampleVal, short.MinValue, short.MaxValue);
            samples[i] = (short)finalSample;

            int mag = Math.Abs(finalSample);
            if (mag > maxProcessedMag)
                maxProcessedMag = mag;
        }

        return (float)maxProcessedMag / short.MaxValue;
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
