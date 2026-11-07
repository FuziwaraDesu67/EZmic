using System.ComponentModel;
using System.Windows;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Threading;
using Color = System.Windows.Media.Color;
using Rectangle = System.Windows.Shapes.Rectangle;
using Canvas = System.Windows.Controls.Canvas;
using MicCompanion.Audio;
using MicCompanion.Transports;
using MicCompanion.UI;
using MicCompanion.Utils;

namespace MicCompanion;

public partial class MainWindow : Window
{
    private readonly AudioOutputManager _audioManager;
    private readonly DiscoveryService _discoveryService;
    private IAudioTransport? _currentTransport;
    private readonly TrayIconManager _trayManager;
    private bool _isStarted = false;
    private bool _isMuted = false;
    private bool _isRealExit = false;

    private List<AudioDeviceItem> _deviceList = new();

    // 24-Segment VU Meter State
    private const int SegmentCount = 24;
    private readonly Rectangle[] _segments = new Rectangle[SegmentCount];
    private float _targetLevel = 0f;
    private float _smoothedLevel = 0f;
    private float _peakHold = 0f;
    private int _peakHoldCounter = 0;
    private readonly DispatcherTimer _meterTimer;

    // Palette Colors
    private static readonly Color ColorGreenActive = Color.FromRgb(0x10, 0xB9, 0x81);
    private static readonly Color ColorGreenUnlit = Color.FromRgb(0x0C, 0x24, 0x1A);
    private static readonly Color ColorAmberActive = Color.FromRgb(0xF5, 0x9E, 0x0B);
    private static readonly Color ColorAmberUnlit = Color.FromRgb(0x28, 0x1B, 0x07);
    private static readonly Color ColorRedActive = Color.FromRgb(0xEF, 0x44, 0x44);
    private static readonly Color ColorRedUnlit = Color.FromRgb(0x28, 0x0D, 0x0D);

    public MainWindow()
    {
        InitializeComponent();

        _audioManager = new AudioOutputManager();
        _discoveryService = new DiscoveryService();

        _trayManager = new TrayIconManager(
            onRestore: RestoreFromTray,
            onToggleMute: ToggleMute,
            onExit: ForceExit
        );

        _audioManager.LevelChanged += OnLevelChanged;
        _audioManager.LogMessage += AppendLog;
        _discoveryService.LogMessage += AppendLog;

        _meterTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromMilliseconds(16) // ~60 FPS animation loop
        };
        _meterTimer.Tick += MeterTimer_Tick;

        Loaded += MainWindow_Loaded;
        Closing += MainWindow_Closing;
        CanvasMeter.SizeChanged += CanvasMeter_SizeChanged;
    }

    private void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        InitializeMeterSegments();
        PopulateOutputDevices();
        ChkStartup.IsChecked = StartupHelper.IsRunAtStartup();
        _meterTimer.Start();
        _discoveryService.Start();
        AppendLog("EZmic studio bridge initialized.");
    }

    private void InitializeMeterSegments()
    {
        CanvasMeter.Children.Clear();
        for (int i = 0; i < SegmentCount; i++)
        {
            var rect = new Rectangle
            {
                RadiusX = 2,
                RadiusY = 2,
                Fill = new SolidColorBrush(GetSegmentUnlitColor(i))
            };
            _segments[i] = rect;
            CanvasMeter.Children.Add(rect);
        }
        LayoutMeterSegments();
    }

    private void CanvasMeter_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        LayoutMeterSegments();
    }

    private void LayoutMeterSegments()
    {
        double width = CanvasMeter.ActualWidth;
        double height = CanvasMeter.ActualHeight > 0 ? CanvasMeter.ActualHeight : 14;
        if (width <= 0) return;

        double gap = 2.5;
        double totalGaps = gap * (SegmentCount - 1);
        double segWidth = Math.Max(2, (width - totalGaps) / SegmentCount);

        for (int i = 0; i < SegmentCount; i++)
        {
            var rect = _segments[i];
            if (rect == null) continue;

            rect.Width = segWidth;
            rect.Height = height;
            Canvas.SetLeft(rect, i * (segWidth + gap));
            Canvas.SetTop(rect, 0);
        }
    }

    private Color GetSegmentUnlitColor(int index)
    {
        if (index < 16) return ColorGreenUnlit;
        if (index < 20) return ColorAmberUnlit;
        return ColorRedUnlit;
    }

    private Color GetSegmentActiveColor(int index)
    {
        if (index < 16) return ColorGreenActive;
        if (index < 20) return ColorAmberActive;
        return ColorRedActive;
    }

    private void MeterTimer_Tick(object? sender, EventArgs e)
    {
        // Smooth exponential ballistics
        if (_targetLevel > _smoothedLevel)
        {
            _smoothedLevel = _targetLevel; // Fast attack
        }
        else
        {
            _smoothedLevel = Math.Max(0f, _smoothedLevel * 0.88f); // Smooth decay
        }

        // Peak Hold logic
        if (_smoothedLevel >= _peakHold)
        {
            _peakHold = _smoothedLevel;
            _peakHoldCounter = 40; // Hold for ~650ms
        }
        else if (_peakHoldCounter > 0)
        {
            _peakHoldCounter--;
        }
        else
        {
            _peakHold = Math.Max(0f, _peakHold - 0.025f);
        }

        int litCount = (int)Math.Round(_smoothedLevel * SegmentCount);
        int peakIndex = Math.Clamp((int)Math.Round(_peakHold * SegmentCount) - 1, 0, SegmentCount - 1);

        for (int i = 0; i < SegmentCount; i++)
        {
            var rect = _segments[i];
            if (rect == null) continue;

            bool isLit = (i < litCount) || (i == peakIndex && _peakHold > 0.02f);
            var targetBrush = (SolidColorBrush)rect.Fill;
            var expectedColor = isLit ? GetSegmentActiveColor(i) : GetSegmentUnlitColor(i);

            if (targetBrush.Color != expectedColor)
            {
                rect.Fill = new SolidColorBrush(expectedColor);
            }
        }

        // Decibel readout
        if (_smoothedLevel <= 0.008f)
        {
            TxtLevelDecibel.Text = "-inf dB";
        }
        else
        {
            float db = 20f * (float)Math.Log10(_smoothedLevel);
            TxtLevelDecibel.Text = $"{db:0.0} dB";
        }
    }

    private void PopulateOutputDevices()
    {
        CmbOutputDevices.Items.Clear();
        _deviceList = AudioOutputManager.GetOutputDevices();
        int defaultIndex = 0;

        for (int i = 0; i < _deviceList.Count; i++)
        {
            var dev = _deviceList[i];
            CmbOutputDevices.Items.Add(dev.Name);

            if (dev.IsPreferred)
            {
                defaultIndex = i;
            }
        }

        bool hasCable = _deviceList.Any(d => d.IsPreferred || d.Name.Contains("CABLE", StringComparison.OrdinalIgnoreCase));
        BorderDriverNotice.Visibility = hasCable ? Visibility.Collapsed : Visibility.Visible;

        if (CmbOutputDevices.Items.Count > 0)
        {
            CmbOutputDevices.SelectedIndex = defaultIndex;
        }
        else
        {
            AppendLog("Warning: No audio output endpoints detected.");
        }
    }

    private async void BtnInstallDriver_Click(object sender, RoutedEventArgs e)
    {
        BtnInstallDriver.IsEnabled = false;
        BtnInstallDriver.Content = "Installing...";
        AppendLog("Beginning automatic Virtual Audio Cable installation...");

        bool success = await DriverInstaller.DownloadAndInstallCableAsync(AppendLog);

        PopulateOutputDevices();
        BtnInstallDriver.IsEnabled = true;
        BtnInstallDriver.Content = "Install Free Cable";

        if (success)
        {
            AppendLog("Virtual Audio Cable installation completed!");
        }
    }

    private async void BtnConnect_Click(object sender, RoutedEventArgs e)
    {
        if (_isStarted)
        {
            await StopSessionAsync();
        }
        else
        {
            await StartSessionAsync();
        }
    }

    private async Task StartSessionAsync()
    {
        if (CmbOutputDevices.SelectedIndex < 0 || CmbOutputDevices.SelectedIndex >= _deviceList.Count)
        {
            System.Windows.MessageBox.Show("Please select an audio endpoint first.", "Audio Output", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var selectedDevice = _deviceList[CmbOutputDevices.SelectedIndex];

        try
        {
            // 1. Start audio output
            _audioManager.Start(selectedDevice.Id);

            // 2. Select transport
            if (RbWifi.IsChecked == true)
            {
                var wifi = new WifiAudioReceiver();
                _currentTransport = wifi;
                _discoveryService.Start();
            }
            else if (RbUsb.IsChecked == true)
            {
                _currentTransport = new UsbAdbReceiver();
            }
            else
            {
                _currentTransport = new BluetoothAudioReceiver();
            }

            _currentTransport.PacketReceived += _audioManager.EnqueuePacket;
            _currentTransport.ConnectionStateChanged += OnConnectionStateChanged;
            _currentTransport.LogMessage += AppendLog;

            await _currentTransport.StartAsync(CancellationToken.None);

            _isStarted = true;
            BtnConnect.Content = "Stop receiver";
            BtnConnect.Background = new SolidColorBrush(Color.FromRgb(0x1F, 0x24, 0x2C));
            BtnConnect.BorderBrush = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            BtnConnect.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            BtnConnect.BorderThickness = new Thickness(1);

            UpdateTallyState();
            _trayManager.UpdateStatus(true, $"Active ({_currentTransport.Name})");
        }
        catch (Exception ex)
        {
            AppendLog($"Failed to start: {ex.Message}");
            await StopSessionAsync();
        }
    }

    private async Task StopSessionAsync()
    {
        if (_currentTransport != null)
        {
            await _currentTransport.StopAsync();
            _currentTransport.Dispose();
            _currentTransport = null;
        }

        _audioManager.Stop();

        _isStarted = false;
        BtnConnect.Content = "Start receiver";
        BtnConnect.Background = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        BtnConnect.Foreground = new SolidColorBrush(Color.FromRgb(0x04, 0x2F, 0x1E));
        BtnConnect.BorderThickness = new Thickness(0);

        TxtStatus.Text = "Ready to connect";
        UpdateTallyState();
        _trayManager.UpdateStatus(false, "Standby");
        _targetLevel = 0f;
    }

    private void UpdateTallyState()
    {
        if (_isMuted && _isStarted)
        {
            TxtTally.Text = "MUTED";
            TxtTally.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            DotTally.Fill = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            BorderTally.BorderBrush = new SolidColorBrush(Color.FromArgb(0x80, 0xEF, 0x44, 0x44));
        }
        else if (_isStarted)
        {
            TxtTally.Text = "ON AIR";
            TxtTally.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            DotTally.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            BorderTally.BorderBrush = new SolidColorBrush(Color.FromArgb(0x80, 0x10, 0xB9, 0x81));
        }
        else
        {
            TxtTally.Text = "STANDBY";
            TxtTally.Foreground = new SolidColorBrush(Color.FromRgb(0x8B, 0x94, 0x9E));
            DotTally.Fill = new SolidColorBrush(Color.FromRgb(0x48, 0x4F, 0x58));
            BorderTally.BorderBrush = new SolidColorBrush(Color.FromArgb(0x40, 0x26, 0x2A, 0x33));
        }
    }

    private void OnConnectionStateChanged(bool connected, string message)
    {
        Dispatcher.Invoke(() =>
        {
            TxtStatus.Text = message;
            _trayManager.UpdateStatus(connected, message);
        });
    }

    private void OnLevelChanged(float level)
    {
        _targetLevel = level;
    }

    private void SliderGain_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (TxtGainValue == null) return;
        int val = (int)e.NewValue;
        TxtGainValue.Text = $"{val}%";
        if (_audioManager != null)
        {
            _audioManager.Processor.Gain = val / 100.0f;
        }
    }

    private void BtnMute_Click(object sender, RoutedEventArgs e)
    {
        ToggleMute();
    }

    private void ToggleMute()
    {
        _isMuted = !_isMuted;
        _audioManager.Processor.IsMuted = _isMuted;
        _trayManager.UpdateMute(_isMuted);

        Dispatcher.Invoke(() =>
        {
            BtnMute.Content = _isMuted ? "Unmute" : "Mute";
            BtnMute.Background = _isMuted 
                ? new SolidColorBrush(Color.FromRgb(0x3B, 0x12, 0x12)) 
                : new SolidColorBrush(Color.FromRgb(0x1E, 0x22, 0x2A));
            BtnMute.BorderBrush = _isMuted 
                ? new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)) 
                : new SolidColorBrush(Color.FromRgb(0x26, 0x2A, 0x33));
            BtnMute.Foreground = _isMuted 
                ? new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)) 
                : new SolidColorBrush(Color.FromRgb(0xE6, 0xED, 0xF3));

            UpdateTallyState();
        });
    }

    private void ChkStartup_Click(object sender, RoutedEventArgs e)
    {
        StartupHelper.SetRunAtStartup(ChkStartup.IsChecked == true);
    }

    private void AppendLog(string message)
    {
        Dispatcher.Invoke(() =>
        {
            string line = $"[{DateTime.Now:HH:mm:ss}] {message}\n";
            TxtLog.AppendText(line);
            ScrollLog.ScrollToEnd();
        });
    }

    private void MainWindow_Closing(object? sender, CancelEventArgs e)
    {
        if (!_isRealExit && ChkMinimizeToTray.IsChecked == true)
        {
            e.Cancel = true;
            Hide();
            _trayManager.ShowNotification("Mic Companion", "Minimized to tray. Double-click icon to restore.");
        }
    }

    private void RestoreFromTray()
    {
        Dispatcher.Invoke(() =>
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        });
    }

    private void ForceExit()
    {
        _isRealExit = true;
        _meterTimer.Stop();
        _discoveryService.Stop();
        _trayManager.Dispose();
        _ = StopSessionAsync();
        Close();
    }
}
