using System.Runtime.InteropServices;
using System.Windows.Interop;

namespace MicCompanion.Utils;

public class HotkeyManager : IDisposable
{
    private const int WM_HOTKEY = 0x0312;
    private const int HOTKEY_ID = 9001;

    // Modifiers: MOD_CONTROL = 0x0002, MOD_SHIFT = 0x0004
    private const uint MOD_CONTROL = 0x0002;
    private const uint MOD_SHIFT = 0x0004;
    private const uint VK_M = 0x4D; // 'M' key

    [DllImport("user32.dll")]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll")]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    private IntPtr _windowHandle;
    private HwndSource? _hwndSource;
    private readonly Action _onHotkeyTriggered;
    private bool _isRegistered = false;

    public HotkeyManager(IntPtr windowHandle, Action onHotkeyTriggered)
    {
        _windowHandle = windowHandle;
        _onHotkeyTriggered = onHotkeyTriggered;

        _hwndSource = HwndSource.FromHwnd(_windowHandle);
        _hwndSource?.AddHook(HwndHook);

        Register();
    }

    public bool Register()
    {
        if (_isRegistered || _windowHandle == IntPtr.Zero) return _isRegistered;

        _isRegistered = RegisterHotKey(_windowHandle, HOTKEY_ID, MOD_CONTROL | MOD_SHIFT, VK_M);
        return _isRegistered;
    }

    public void Unregister()
    {
        if (_isRegistered && _windowHandle != IntPtr.Zero)
        {
            UnregisterHotKey(_windowHandle, HOTKEY_ID);
            _isRegistered = false;
        }
    }

    private IntPtr HwndHook(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_HOTKEY && wParam.ToInt32() == HOTKEY_ID)
        {
            _onHotkeyTriggered?.Invoke();
            handled = true;
        }

        return IntPtr.Zero;
    }

    public void Dispose()
    {
        Unregister();
        if (_hwndSource != null)
        {
            _hwndSource.RemoveHook(HwndHook);
            _hwndSource = null;
        }
    }
}
