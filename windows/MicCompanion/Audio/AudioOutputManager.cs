using NAudio.CoreAudioApi;
using NAudio.Wave;
using MicCompanion.Protocol;

namespace MicCompanion.Audio;

public class AudioDeviceItem
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public bool IsPreferred { get; set; }

    public string DisplayName
    {
        get
        {
            if (Name.Contains("EZmic", StringComparison.OrdinalIgnoreCase))
            {
                return Name;
            }
            if (Name.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase))
            {
                return "EZmic Audio Cable (Virtual Mic)";
            }
            return Name;
        }
    }

    public override string ToString() => DisplayName;
}

public class AudioOutputManager : IDisposable
{
    private IWavePlayer? _outputPlayer;
    private BufferedWaveProvider? _waveProvider;
    private IWavePlayer? _monitorPlayer;
    private BufferedWaveProvider? _monitorWaveProvider;

    private readonly PcmAudioProcessor _processor;
    private OpusAudioDecoder? _opusDecoder;
    private readonly object _audioLock = new();
    private DateTime _lastDecodeErrorLog = DateTime.MinValue;

    public event Action<float>? LevelChanged;
    public event Action<string>? LogMessage;

    public PcmAudioProcessor Processor => _processor;

    public bool IsMonitorEnabled
    {
        get => _isMonitorEnabled;
        set
        {
            if (_isMonitorEnabled != value)
            {
                _isMonitorEnabled = value;
                if (_isMonitorEnabled)
                {
                    StartMonitoring();
                }
                else
                {
                    StopMonitoring();
                }
            }
        }
    }
    private bool _isMonitorEnabled = false;

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

                // Strongly prefer "EZmic Audio Cable" or "CABLE Input" over other devices
                if ((name.Contains("EZmic", StringComparison.OrdinalIgnoreCase) ||
                     name.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase)) &&
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

            // Attempt modern WASAPI Shared Mode first
            try
            {
                using var enumerator = new MMDeviceEnumerator();
                var endpoint = enumerator.GetDevice(deviceId);

                var wasapi = new WasapiOut(endpoint, AudioClientShareMode.Shared, useEventSync: true, latency: 50);
                wasapi.Init(_waveProvider);
                wasapi.Play();
                _outputPlayer = wasapi;

                LogMessage?.Invoke($"WASAPI Audio Engine connected to: {endpoint.FriendlyName}");
            }
            catch (Exception wasapiEx)
            {
                LogMessage?.Invoke($"WASAPI init note: {wasapiEx.Message}. Trying WaveOut fallback...");

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

            if (_isMonitorEnabled)
            {
                StartMonitoring();
            }
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

            if (packet.Codec == PacketConstants.CodecOpus)
            {
                _opusDecoder ??= new OpusAudioDecoder();
                samples = _opusDecoder.Decode(packet.Payload, 0, packet.Payload.Length);
            }
            else
            {
                if (packet.Payload == null || packet.Payload.Length < 2)
                    return;

                // Security (S-11): Ensure byte count is strictly even for 16-bit PCM before copying
                int validBytes = packet.Payload.Length - (packet.Payload.Length % 2);
                int sampleCount = validBytes / 2;
                samples = new short[sampleCount];
                Buffer.BlockCopy(packet.Payload, 0, samples, 0, validBytes);
            }

            if (packet.IsMuted)
            {
                Array.Clear(samples, 0, samples.Length);
            }

            // Process gain, noise gate, soft-limiter & calculate peak level for UI
            float peak = _processor.Process(samples);
            LevelChanged?.Invoke(peak);

            // Convert to 2-channel stereo byte array
            byte[] stereoBytes = packet.IsStereo
                ? PcmAudioProcessor.ToByteArray(samples)
                : PcmAudioProcessor.ToStereoByteArray(samples);

            lock (_audioLock)
            {
                _waveProvider?.AddSamples(stereoBytes, 0, stereoBytes.Length);

                if (_isMonitorEnabled && _monitorWaveProvider != null)
                {
                    _monitorWaveProvider.AddSamples(stereoBytes, 0, stereoBytes.Length);
                }
            }
        }
        catch (Exception ex)
        {
            DateTime now = DateTime.UtcNow;
            if ((now - _lastDecodeErrorLog).TotalSeconds > 2)
            {
                _lastDecodeErrorLog = now;
                LogMessage?.Invoke($"Audio packet decode error: {ex.Message}");
            }
        }
    }

    private void StartMonitoring()
    {
        StopMonitoring();

        try
        {
            using var enumerator = new MMDeviceEnumerator();
            var defaultPlayback = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            if (defaultPlayback == null) return;

            var waveFormat = new WaveFormat(PacketConstants.SampleRate, 16, 2);
            _monitorWaveProvider = new BufferedWaveProvider(waveFormat)
            {
                DiscardOnBufferOverflow = true,
                BufferDuration = TimeSpan.FromMilliseconds(300)
            };

            var wasapiMonitor = new WasapiOut(defaultPlayback, AudioClientShareMode.Shared, useEventSync: true, latency: 40);
            wasapiMonitor.Init(_monitorWaveProvider);
            wasapiMonitor.Play();
            _monitorPlayer = wasapiMonitor;

            LogMessage?.Invoke($"Mic monitoring (sidetone) enabled on: {defaultPlayback.FriendlyName}");
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke($"Sidetone monitoring init note: {ex.Message}");
        }
    }

    private void StopMonitoring()
    {
        lock (_audioLock)
        {
            try
            {
                _monitorPlayer?.Stop();
                _monitorPlayer?.Dispose();
                _monitorPlayer = null;
            }
            catch { }

            _monitorWaveProvider?.ClearBuffer();
            _monitorWaveProvider = null;
        }
    }

    public void Stop()
    {
        lock (_audioLock)
        {
            StopMonitoring();

            try
            {
                _outputPlayer?.Stop();
                _outputPlayer?.Dispose();
                _outputPlayer = null;
            }
            catch { }

            _waveProvider?.ClearBuffer();
            _waveProvider = null;

            _opusDecoder?.Reset();
        }

        LevelChanged?.Invoke(0f);
    }

    public void Dispose()
    {
        Stop();
        _opusDecoder?.Dispose();
        _opusDecoder = null;
    }
}
