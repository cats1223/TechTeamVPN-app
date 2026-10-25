using System;
using System.Linq;
using System.Threading;
using System.Windows;

namespace TechTeamVPN;

public partial class App : Application
{
    private const string MutexName = @"Local\TechTeamVPN.SingleInstance";
    private const string ShowEventName = @"Local\TechTeamVPN.Show";
    private Mutex? mutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        // One copy at a time; a second launch brings the first one forward. An elevated restart
        // waits a little for the old copy to exit.
        mutex = new Mutex(false, MutexName);
        var owned = false;
        try { owned = mutex.WaitOne(e.Args.Contains("--elevated") ? TimeSpan.FromSeconds(10) : TimeSpan.Zero); }
        catch (AbandonedMutexException) { owned = true; }
        if (!owned)
        {
            if (EventWaitHandle.TryOpenExisting(ShowEventName, out var show)) show.Set();
            Shutdown();
            return;
        }

        var window = new MainWindow(startMinimized: e.Args.Contains("--minimized"), connectNow: e.Args.Contains("--connect"));
        var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
        new Thread(() =>
        {
            while (showEvent.WaitOne()) Dispatcher.Invoke(window.ShowFromTray);
        }) { IsBackground = true }.Start();
        if (!e.Args.Contains("--minimized")) window.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try { mutex?.ReleaseMutex(); } catch (ApplicationException) { }
        base.OnExit(e);
    }
}
