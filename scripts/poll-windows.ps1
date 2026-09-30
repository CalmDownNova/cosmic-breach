# Watches for any visible window from the hidden test client while an autotest runs.
# Every poll logs: java processes with a window title (the check from the plan), the test
# client's visible and total top-level windows, whether it holds the foreground, visible
# java windows titled minecraft/java/cosmic (other apps are noted by process name only), and the cursor.
# A test client with a visible window is killed at once and logged as INCIDENT.
# Only processes whose command line points at cosmic-breach's (or a cb-* worktree's) test client run are killed.
param(
    [Parameter(Mandatory = $true)][string]$LogFile,
    [Parameter(Mandatory = $true)][string]$StopFile,
    [int]$MaxSeconds = 480,
    [int]$IntervalSeconds = 2
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class CosmicBreachWinProbe {
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    public static int[] Windows(uint pid) {
        int visible = 0, total = 0;
        EnumWindows((h, p) => { uint wp; GetWindowThreadProcessId(h, out wp); if (wp == pid) { total++; if (IsWindowVisible(h)) visible++; } return true; }, IntPtr.Zero);
        return new int[] { visible, total };
    }
    public static List<string> SuspiciousVisibleTitles() {
        var hits = new List<string>();
        EnumWindows((h, p) => {
            if (!IsWindowVisible(h)) return true;
            var sb = new StringBuilder(512); GetWindowText(h, sb, 512); var t = sb.ToString();
            var l = t.ToLowerInvariant();
            if (l.Contains("minecraft") || l.Contains("java") || l.Contains("cosmic")) { uint wp; GetWindowThreadProcessId(h, out wp); hits.Add(wp + ":" + t); }
            return true;
        }, IntPtr.Zero);
        return hits;
    }
    public static uint ForegroundPid() { uint pid; GetWindowThreadProcessId(GetForegroundWindow(), out pid); return pid; }
}
"@

function Log([string]$line) {
    Add-Content -Path $LogFile -Value ((Get-Date).ToString('HH:mm:ss') + ' ' + $line)
}

$deadline = (Get-Date).AddSeconds($MaxSeconds)
Log "poller started, every ${IntervalSeconds}s"
while ((Get-Date) -lt $deadline -and -not (Test-Path $StopFile)) {
    $titled = @(Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object Id,MainWindowTitle)
    $games = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -match 'cosmic-breach|cb-[A-Za-z0-9-]+' -and $_.CommandLine -match 'BootstrapLauncher|testClientRun' })
    $foreground = [CosmicBreachWinProbe]::ForegroundPid()
    $cursor = [System.Windows.Forms.Cursor]::Position
    $parts = @()
    foreach ($g in $games) {
        $w = [CosmicBreachWinProbe]::Windows([uint32]$g.ProcessId)
        $parts += "test client pid $($g.ProcessId): visible windows $($w[0]), total $($w[1]), foreground $([bool]($foreground -eq $g.ProcessId))"
    }
    if ($games.Count -eq 0) { $parts += 'no test client running' }
    # Only a java window can be the game. Other apps (a browser tab about Minecraft) are noted by
    # process name alone, never by title, and never fail a run.
    $suspicious = @()
    $others = @()
    foreach ($s in [CosmicBreachWinProbe]::SuspiciousVisibleTitles()) {
        $wpid = [int]($s.Split(':')[0])
        $pname = (Get-Process -Id $wpid -ErrorAction SilentlyContinue).ProcessName
        if ($pname -eq 'java' -or $pname -eq 'javaw') { $suspicious += $s } else { $others += $pname }
    }
    $titledText = if ($titled.Count -eq 0) { 'none' } else { ($titled | ForEach-Object { "$($_.Id)='$($_.MainWindowTitle)'" }) -join '; ' }
    Log ("titled java windows: $titledText | " + ($parts -join ' | ') + " | visible java windows titled minecraft/java/cosmic: $($suspicious.Count) | cursor $($cursor.X),$($cursor.Y)")
    foreach ($s in $suspicious) { Log "INCIDENT visible window titled like the game: $s" }
    foreach ($o in ($others | Sort-Object -Unique)) { Log "note: a $o window mentions the game; not a test client, ignored" }
    foreach ($t in $titled) {
        if (@($games | Where-Object { $_.ProcessId -eq $t.Id }).Count -gt 0) {
            Log "INCIDENT test client pid $($t.Id) shows window '$($t.MainWindowTitle)': killing it"
            taskkill /PID $t.Id /F | Out-Null
        } else {
            Log "INCIDENT titled window in java pid $($t.Id), not the test client: left alone"
        }
    }
    foreach ($g in $games) {
        $w = [CosmicBreachWinProbe]::Windows([uint32]$g.ProcessId)
        if ($w[0] -gt 0) {
            Log "INCIDENT test client pid $($g.ProcessId) has $($w[0]) visible window(s): killing it"
            taskkill /PID $g.ProcessId /F | Out-Null
        }
    }
    Start-Sleep -Seconds $IntervalSeconds
}
Log "poller stopped"
