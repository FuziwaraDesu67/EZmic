using System;

namespace MicCompanion.Audio;

public enum StudioProfile
{
    BroadcastWarmth,  // Shure SM7B radio voice curve
    CrispEsports,     // High-clarity presence for Discord & gaming callouts
    PodcastStudio,    // Balanced vocal presence with de-esser
    Natural           // Clean uncolored 48kHz bypass
}

public class BiquadFilter
{
    private double _b0 = 1.0, _b1, _b2, _a1, _a2;
    private double _x1, _x2, _y1, _y2;
    public bool Enabled { get; set; } = true;

    public void SetHighPass(double sampleRate, double freq, double q = 0.707)
    {
        double omega = 2.0 * Math.PI * freq / sampleRate;
        double sin = Math.Sin(omega);
        double cos = Math.Cos(omega);
        double alpha = sin / (2.0 * q);

        double b0 = (1.0 + cos) / 2.0;
        double b1 = -(1.0 + cos);
        double b2 = (1.0 + cos) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cos;
        double a2 = 1.0 - alpha;

        _b0 = b0 / a0;
        _b1 = b1 / a0;
        _b2 = b2 / a0;
        _a1 = a1 / a0;
        _a2 = a2 / a0;
    }

    public void SetLowShelf(double sampleRate, double freq, double gainDb, double q = 0.707)
    {
        double a = Math.Pow(10.0, gainDb / 40.0);
        double omega = 2.0 * Math.PI * freq / sampleRate;
        double sin = Math.Sin(omega);
        double cos = Math.Cos(omega);
        double alpha = sin / (2.0 * q);
        double twoSqrtAAlpha = 2.0 * Math.Sqrt(a) * alpha;

        double b0 = a * ((a + 1.0) - (a - 1.0) * cos + twoSqrtAAlpha);
        double b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos);
        double b2 = a * ((a + 1.0) - (a - 1.0) * cos - twoSqrtAAlpha);
        double a0 = (a + 1.0) + (a - 1.0) * cos + twoSqrtAAlpha;
        double a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos);
        double a2 = (a + 1.0) + (a - 1.0) * cos - twoSqrtAAlpha;

        _b0 = b0 / a0;
        _b1 = b1 / a0;
        _b2 = b2 / a0;
        _a1 = a1 / a0;
        _a2 = a2 / a0;
    }

    public void SetPeaking(double sampleRate, double freq, double gainDb, double q = 1.0)
    {
        double a = Math.Pow(10.0, gainDb / 40.0);
        double omega = 2.0 * Math.PI * freq / sampleRate;
        double sin = Math.Sin(omega);
        double cos = Math.Cos(omega);
        double alpha = sin / (2.0 * q);

        double b0 = 1.0 + alpha * a;
        double b1 = -2.0 * cos;
        double b2 = 1.0 - alpha * a;
        double a0 = 1.0 + alpha / a;
        double a1 = -2.0 * cos;
        double a2 = 1.0 - alpha / a;

        _b0 = b0 / a0;
        _b1 = b1 / a0;
        _b2 = b2 / a0;
        _a1 = a1 / a0;
        _a2 = a2 / a0;
    }

    public void SetHighShelf(double sampleRate, double freq, double gainDb, double q = 0.707)
    {
        double a = Math.Pow(10.0, gainDb / 40.0);
        double omega = 2.0 * Math.PI * freq / sampleRate;
        double sin = Math.Sin(omega);
        double cos = Math.Cos(omega);
        double alpha = sin / (2.0 * q);
        double twoSqrtAAlpha = 2.0 * Math.Sqrt(a) * alpha;

        double b0 = a * ((a + 1.0) + (a - 1.0) * cos + twoSqrtAAlpha);
        double b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cos);
        double b2 = a * ((a + 1.0) + (a - 1.0) * cos - twoSqrtAAlpha);
        double a0 = (a + 1.0) - (a - 1.0) * cos + twoSqrtAAlpha;
        double a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cos);
        double a2 = (a + 1.0) - (a - 1.0) * cos - twoSqrtAAlpha;

        _b0 = b0 / a0;
        _b1 = b1 / a0;
        _b2 = b2 / a0;
        _a1 = a1 / a0;
        _a2 = a2 / a0;
    }

    public void Reset()
    {
        _x1 = _x2 = _y1 = _y2 = 0;
    }

    public double Process(double sample)
    {
        if (!Enabled) return sample;

        double y = _b0 * sample + _b1 * _x1 + _b2 * _x2 - _a1 * _y1 - _a2 * _y2;
        _x2 = _x1;
        _x1 = sample;
        _y2 = _y1;
        _y1 = y;
        return y;
    }
}

public class BroadcastCompressor
{
    public bool Enabled { get; set; } = true;
    public double ThresholdDb { get; set; } = -18.0;
    public double Ratio { get; set; } = 3.5;
    public double MakeupGainDb { get; set; } = 2.5;

    private double _envelope = 0.0;
    private const double AttackCoeff = 0.02;  // ~5ms at 48kHz
    private const double ReleaseCoeff = 0.0006; // ~70ms at 48kHz

    public void Reset()
    {
        _envelope = 0.0;
    }

    public double Process(double sample)
    {
        if (!Enabled) return sample;

        double absSample = Math.Abs(sample);
        if (absSample > _envelope)
        {
            _envelope += AttackCoeff * (absSample - _envelope);
        }
        else
        {
            _envelope += ReleaseCoeff * (absSample - _envelope);
        }

        double envDb = _envelope > 1e-4 ? 20.0 * Math.Log10(_envelope / 32768.0) : -90.0;
        double gainDb = 0.0;

        if (envDb > ThresholdDb)
        {
            gainDb = (ThresholdDb - envDb) * (1.0 - 1.0 / Ratio);
        }

        gainDb += MakeupGainDb;
        double linearMultiplier = Math.Pow(10.0, gainDb / 20.0);
        return sample * linearMultiplier;
    }
}

public class StudioDspChain
{
    private const double SampleRate = 48000.0;

    private readonly BiquadFilter _highPass = new();
    private readonly BiquadFilter _lowShelf = new();
    private readonly BiquadFilter _midPeaking = new();
    private readonly BiquadFilter _presencePeaking = new();
    private readonly BiquadFilter _airShelf = new();
    private readonly BiquadFilter _deEsser = new();
    private readonly BroadcastCompressor _compressor = new();

    private StudioProfile _currentProfile = StudioProfile.BroadcastWarmth;

    public StudioProfile Profile
    {
        get => _currentProfile;
        set
        {
            _currentProfile = value;
            ApplyProfile(value);
        }
    }

    public bool CompressorEnabled
    {
        get => _compressor.Enabled;
        set => _compressor.Enabled = value;
    }

    public bool DeEsserEnabled
    {
        get => _deEsser.Enabled;
        set => _deEsser.Enabled = value;
    }

    public StudioDspChain()
    {
        ApplyProfile(_currentProfile);
    }

    public void ApplyProfile(StudioProfile profile)
    {
        Reset();

        switch (profile)
        {
            case StudioProfile.BroadcastWarmth: // Shure SM7B Radio DJ Curve
                _highPass.Enabled = true;
                _highPass.SetHighPass(SampleRate, 75.0, 0.707); // Cut sub-rumble

                _lowShelf.Enabled = true;
                _lowShelf.SetLowShelf(SampleRate, 140.0, 4.0, 0.707); // Rich proximity chest warmth

                _midPeaking.Enabled = true;
                _midPeaking.SetPeaking(SampleRate, 450.0, -2.5, 1.2); // Remove boxy phone hollow sound

                _presencePeaking.Enabled = true;
                _presencePeaking.SetPeaking(SampleRate, 3600.0, 3.5, 1.0); // Vocal intelligibility & speech clarity

                _airShelf.Enabled = false;

                _deEsser.Enabled = true;
                _deEsser.SetPeaking(SampleRate, 6800.0, -2.5, 3.0); // Soften harsh sibilance

                _compressor.Enabled = true;
                _compressor.ThresholdDb = -18.0;
                _compressor.Ratio = 3.5;
                _compressor.MakeupGainDb = 2.5;
                break;

            case StudioProfile.CrispEsports: // Ultra-clear Discord & Gaming calls
                _highPass.Enabled = true;
                _highPass.SetHighPass(SampleRate, 85.0, 0.707); // Clean tight bass cutoff

                _lowShelf.Enabled = true;
                _lowShelf.SetLowShelf(SampleRate, 200.0, 1.0, 0.707);

                _midPeaking.Enabled = true;
                _midPeaking.SetPeaking(SampleRate, 400.0, -3.5, 1.4); // Scoop background room noise

                _presencePeaking.Enabled = true;
                _presencePeaking.SetPeaking(SampleRate, 3200.0, 5.0, 1.0); // Cuts through gunshots & game music

                _airShelf.Enabled = true;
                _airShelf.SetHighShelf(SampleRate, 9000.0, 2.0, 0.707); // Crisp condenser sheen

                _deEsser.Enabled = true;
                _deEsser.SetPeaking(SampleRate, 7000.0, -3.0, 3.0);

                _compressor.Enabled = true;
                _compressor.ThresholdDb = -16.0;
                _compressor.Ratio = 4.0;
                _compressor.MakeupGainDb = 3.0;
                break;

            case StudioProfile.PodcastStudio: // Rode PodMic Balanced Studio Tone
                _highPass.Enabled = true;
                _highPass.SetHighPass(SampleRate, 75.0, 0.707);

                _lowShelf.Enabled = true;
                _lowShelf.SetLowShelf(SampleRate, 160.0, 2.5, 0.707);

                _midPeaking.Enabled = true;
                _midPeaking.SetPeaking(SampleRate, 500.0, -2.0, 1.1);

                _presencePeaking.Enabled = true;
                _presencePeaking.SetPeaking(SampleRate, 3800.0, 2.5, 1.0);

                _airShelf.Enabled = false;

                _deEsser.Enabled = true;
                _deEsser.SetPeaking(SampleRate, 6500.0, -3.5, 2.8);

                _compressor.Enabled = true;
                _compressor.ThresholdDb = -18.0;
                _compressor.Ratio = 2.8;
                _compressor.MakeupGainDb = 2.0;
                break;

            case StudioProfile.Natural: // Pure 48kHz uncolored bypass
            default:
                _highPass.Enabled = false;
                _lowShelf.Enabled = false;
                _midPeaking.Enabled = false;
                _presencePeaking.Enabled = false;
                _airShelf.Enabled = false;
                _deEsser.Enabled = false;
                _compressor.Enabled = false;
                break;
        }
    }

    public void Reset()
    {
        _highPass.Reset();
        _lowShelf.Reset();
        _midPeaking.Reset();
        _presencePeaking.Reset();
        _airShelf.Reset();
        _deEsser.Reset();
        _compressor.Reset();
    }

    public void Process(short[] samples)
    {
        if (_currentProfile == StudioProfile.Natural && !_compressor.Enabled && !_deEsser.Enabled)
        {
            return; // Fast path bypass
        }

        for (int i = 0; i < samples.Length; i++)
        {
            double val = samples[i];

            // 1. Rumble high-pass filter
            val = _highPass.Process(val);

            // 2. Chest warmth low-shelf
            val = _lowShelf.Process(val);

            // 3. Mud scoop mid-peaking
            val = _midPeaking.Process(val);

            // 4. Vocal presence peaking
            val = _presencePeaking.Process(val);

            // 5. Air sheen high-shelf
            val = _airShelf.Process(val);

            // 6. De-Esser sibilance cut
            val = _deEsser.Process(val);

            // 7. Broadcast Dynamic Compressor
            val = _compressor.Process(val);

            samples[i] = (short)Math.Clamp((int)Math.Round(val), short.MinValue, short.MaxValue);
        }
    }
}
