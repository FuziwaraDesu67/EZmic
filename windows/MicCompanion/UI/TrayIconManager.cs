using System.Drawing;
using System.Windows.Forms;

namespace MicCompanion.UI;

public class TrayIconManager : IDisposable
{
    private readonly NotifyIcon _notifyIcon;
    private readonly Action _onRestore;
    private readonly Action _onToggleMute;
    private readonly Action _onExit;
    private readonly ToolStripMenuItem _muteMenuItem;
    private readonly ToolStripMenuItem _statusMenuItem;

    public TrayIconManager(Action onRestore, Action onToggleMute, Action onExit)
    {
        _onRestore = onRestore;
        _onToggleMute = onToggleMute;
        _onExit = onExit;

        _statusMenuItem = new ToolStripMenuItem("Status: Disconnected") { Enabled = false };
        _muteMenuItem = new ToolStripMenuItem("Mute Microphone", null, (s, e) => _onToggleMute());
        var openMenuItem = new ToolStripMenuItem("Open EZmic", null, (s, e) => _onRestore());
        var exitMenuItem = new ToolStripMenuItem("Exit", null, (s, e) => _onExit());

        var contextMenu = new ContextMenuStrip();
        contextMenu.Items.Add(_statusMenuItem);
        contextMenu.Items.Add(new ToolStripSeparator());
        contextMenu.Items.Add(_muteMenuItem);
        contextMenu.Items.Add(openMenuItem);
        contextMenu.Items.Add(new ToolStripSeparator());
        contextMenu.Items.Add(exitMenuItem);

        Icon trayIcon = SystemIcons.Application;
        try
        {
            string icoPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "app.ico");
            if (System.IO.File.Exists(icoPath))
            {
                trayIcon = new Icon(icoPath);
            }
        }
        catch { }

        _notifyIcon = new NotifyIcon
        {
            Icon = trayIcon,
            Text = "EZmic",
            Visible = true,
            ContextMenuStrip = contextMenu
        };

        _notifyIcon.DoubleClick += (s, e) => _onRestore();
    }

    public void UpdateStatus(bool connected, string text)
    {
        _statusMenuItem.Text = $"Status: {text}";
        _notifyIcon.Text = $"EZmic - {text}".Length > 63 
            ? $"EZmic - {text}"[..63] 
            : $"EZmic - {text}";
    }

    public void UpdateMute(bool isMuted)
    {
        _muteMenuItem.Text = isMuted ? "Unmute Microphone" : "Mute Microphone";
        _muteMenuItem.Checked = isMuted;
    }

    public void ShowNotification(string title, string message)
    {
        _notifyIcon.ShowBalloonTip(3000, title, message, ToolTipIcon.Info);
    }

    public void Dispose()
    {
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
    }
}
