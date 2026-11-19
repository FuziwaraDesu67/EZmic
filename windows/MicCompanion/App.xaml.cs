using System.Linq;
using MicCompanion.Utils;

namespace MicCompanion;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);

        if (e.Args.Contains("--uninstall"))
        {
            var result = System.Windows.MessageBox.Show(
                "Are you sure you want to completely uninstall EZmic?\n\nThis will remove all shortcuts, startup entries, and cached files from your PC.",
                "Uninstall EZmic",
                System.Windows.MessageBoxButton.YesNo,
                System.Windows.MessageBoxImage.Question);

            if (result == System.Windows.MessageBoxResult.Yes)
            {
                UninstallHelper.UninstallAndSelfDelete();
                return;
            }
            Environment.Exit(0);
            return;
        }

        if (e.Args.Contains("--minimized"))
        {
            var mainWindow = new MainWindow();
            mainWindow.WindowState = System.Windows.WindowState.Minimized;
            mainWindow.Hide();
        }
    }
}
