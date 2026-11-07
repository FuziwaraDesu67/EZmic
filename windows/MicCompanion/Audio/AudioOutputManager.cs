using NAudio.CoreAudioApi;
using NAudio.Wave;
using MicCompanion.Protocol;

namespace MicCompanion.Audio;

public class AudioDeviceItem
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public bool IsPreferred { get; set; }

    public override string ToString() => Name;
}

public class AudioOutputManager : IDisposable
{
    private IWavePlayer? _outputPlayer;
    private BufferedWaveProvider? _waveProvider;
    private readonly PcmAudioProcessor _processor;
    private readonly object _audioLock = new();

    public event Action<float>? LevelChanged;
    public event Action<string>? LogMessage;

    public PcmAudioProcessor Processor => _processor;

    public AudioOutputManager()
    {
        _processor = new PcmAudioProcessor();
    }

    public static List<AudioDeviceItem> GetOutputDevices()
    {
        var devices = new List<AudioDeviceItem>();

        try
        {
            using var enumerator = new MMDeviceEnumerator();
            var endpoints = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);

            foreach (var ep in endpoints)
            {
                string name = ep.FriendlyName;
                bool isPreferred = false;

                // Strongly prefer standard "CABLE Input" over "16ch" or other devices
                if (name.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase) &&
                    !name.Contains("16ch", StringComparison.OrdinalIgnoreCase))
                {
                    isPreferred = true;
                }

                devices.Add(new AudioDeviceItem
                {
                    Id = ep.ID,
                    Name = name,
                    IsPreferred = isPreferred
                });
            }
        }
        catch
        {
            // Fallback to legacy WaveOut enumeration if MMDevice fails
            int count = WaveOut.DeviceCount;
            for (int i = 0; i < count; i++)
            {
                var caps = WaveOut.GetCapabilities(i);
                devices.Add(new AudioDeviceItem
                {
                    Id = i.ToString(),
                    Name = caps.ProductName,
                    IsPreferred = caps.ProductName.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase)
                });
            }
        }

        return devices;
    }

    public void Start(string deviceId)
    {
        Stop();

        try
        {
            // Standard 48,000 Hz, 16-bit, 2-Channel Stereo (universal for Windows Audio & Discord)
            var waveFormat = new WaveFormat(PacketConstants.SampleRate, 16, 2);
            _waveProvider = new BufferedWaveProvider(waveFormat)
            {
                DiscardOnBufferOverflow = true,
                BufferDuration = TimeSpan.FromMilliseconds(400)
            };

            // Pre-seed buffer with 50ms of silence
            byte[] silence = new byte[PacketConstants.SampleRate * 2 * 2 * 50 / 1000];
            _waveProvider.AddSamples(silence, 0, silence.Length);

            // Attempt modern WASAPI Shared Mode first (bypasses all waveOutOpen format errors)
            try
            {
                using var enumerator = new MMDeviceEnumerator();
                var endpoint = enumerator.GetDevice(deviceId);

                var wasapi = new WasapiOut(endpoint, AudioClientShareMode.Shared, useEventSync: true, latency: 50);
                wasapi.Init(_waveProvider);
                wasapi.Play();
                _outputPlayer = wasapi;

                LogMessage?.Invoke($"WASAPI Audio Engine connected to: {endpoint.FriendlyName}");
                return;
            }
            catch (Exception wasapiEx)
            {
                LogMessage?.Invoke($"WASAPI init note: {wasapiEx.Message}. Trying WaveOut fallback...");
            }

            // Fallback to WaveOutEvent
            int deviceNumber = -1;
            if (int.TryParse(deviceId, out int parsed))
            {
                deviceNumber = parsed;
            }

            var waveOut = new WaveOutEvent
            {
                DeviceNumber = deviceNumber,
                DesiredLatency = 50,
                NumberOfBuffers = 3
            };
            waveOut.Init(_waveProvider);
            waveOut.Play();
            _outputPlayer = waveOut;

            LogMessage?.Invoke("WaveOut Audio Engine active.");
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke($"Failed to initialize audio output: {ex.Message}");
            throw;
        }
    }

    public void EnqueuePacket(AudioPacket packet)
    {
        if (_waveProvider == null) return;

        try
        {
            short[] samples;
            int sampleCount = packet.Payload.Length / 2;
            samples = new short[sampleCount];
            Buffer.BlockCopy(packet.Payload, 0, samples, 0, packet.Payload.Length);

            if (packet.IsMuted)
            {
                Array.Clear(samples, 0, samples.Length);
            }

            // Process gain & calculate peak level for UI
            float peak = _processor.Process(samples);
            LevelChanged?.Invoke(peak);

            // Convert mono to 2-channel stereo byte array
            byte[] stereoBytes = PcmAudioProcessor.ToStereoByteArray(samples);

            lock (_audioLock)
            {
                _waveProvider?.AddSamples(stereoBytes, 0, stereoBytes.Length);
            }
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke($"Audio packet decode error: {ex.Message}");
        }
    }

    public void Stop()
    {
        lock (_audioLock)
        {
            try
            {
                _outputPlayer?.Stop();
                _outputPlayer?.Dispose();
                _outputPlayer = null;
            }
            catch { }

            _waveProvider?.ClearBuffer();
            _waveProvider = null;
        }

        LevelChanged?.Invoke(0f);
    }

    public void Dispose()
    {
        Stop();
    }
}
