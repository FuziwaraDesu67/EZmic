namespace MicCompanion.Protocol;

public static class PacketConstants
{
    public const uint Magic = 0x4D494331; // "MIC1"
    public const int HeaderSize = 12;

    public const byte CodecOpus = 0x01;
    public const byte CodecPcm = 0x02;

    public const byte FlagMuted = 0x01;
    public const byte FlagStereo = 0x02;

    public const int SampleRate = 48000;
    public const int FrameDurationMs = 20;
    public const int SamplesPerFrame = SampleRate * FrameDurationMs / 1000; // 960 samples

    public const int DefaultWifiAudioPort = 49152;
    public const int DefaultWifiDiscoveryPort = 49150;
    public const int DefaultUsbPort = 49153;

    // Dedicated 128-bit GUID for MicCompanion RFCOMM (unblocked by Windows WinRT Bluetooth)
    public static readonly Guid BluetoothServiceUuid = new("6A7D94D1-4E87-4C09-8C7F-85121B6628A4");
}
