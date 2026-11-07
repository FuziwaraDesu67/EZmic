using System.Linq;

namespace MicCompanion;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);

        if (e.Args.Contains("--minimized"))
        {
            var mainWindow = new MainWindow();
            mainWindow.WindowState = System.Windows.WindowState.Minimized;
            mainWindow.Hide();
        }
    }
}
