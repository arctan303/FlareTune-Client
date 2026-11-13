param(
    [string]$Serial,
    [ValidateRange(640,4096)][int]$MaxSize = 1920,
    [ValidateRange(15,120)][int]$MaxFps = 60
)
$ErrorActionPreference = 'Stop'
$taskTitle = 'FlareTune 平板投屏'
$existingMirror = Get-Process -Name scrcpy -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq $taskTitle }
if ($existingMirror) {
    Write-Output '投屏窗口已打开。'
    return
}
$taskSdk = $env:ANDROID_HOME
if (-not $taskSdk) { $taskSdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME','User') }
if (-not $taskSdk) { throw '请先配置 ANDROID_HOME。' }
$taskAdb = Join-Path $taskSdk 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $taskAdb)) { throw '找不到 Android platform-tools/adb.exe。' }
$taskScrcpy = (Get-Command scrcpy.exe -ErrorAction SilentlyContinue).Source
if (-not $taskScrcpy) {
    $taskPackages = Join-Path $env:LOCALAPPDATA 'Microsoft/WinGet/Packages'
    $taskCandidates = @(Get-ChildItem -Path (Join-Path $taskPackages 'Genymobile.scrcpy_*') -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        Get-ChildItem -LiteralPath $_.FullName -Directory | ForEach-Object {
            $candidatePath = Join-Path $_.FullName 'scrcpy.exe'
            if (Test-Path -LiteralPath $candidatePath) { $candidatePath }
        }
    })
    $taskScrcpy = $taskCandidates | Sort-Object -Descending | Select-Object -First 1
}
if (-not $taskScrcpy) { throw '请先安装官方 scrcpy：winget install --exact --source winget Genymobile.scrcpy' }
if (-not $Serial) {
    $taskDevices = @(& $taskAdb devices | ForEach-Object { if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] } })
    if ($taskDevices.Count -ne 1) { throw '请连接一台已授权USB调试的设备，或使用 -Serial 指定。' }
    $Serial = $taskDevices[0]
}
if ($Serial -notmatch '^[A-Za-z0-9_.:-]+$') { throw '设备序列号格式无效。' }
$taskLogDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'android/build/mirroring'
New-Item -ItemType Directory -Path $taskLogDir -Force | Out-Null
$previousAdb = $env:ADB
try {
    $env:ADB = $taskAdb
    $taskArgs = @("--serial=$Serial",'--no-audio',"--max-size=$MaxSize", "--max-fps=$MaxFps",'--video-bit-rate=12M',
        '--window-title="FlareTune 平板投屏"','--window-width=1280','--window-height=800','--no-clipboard-autosync')
    # The user explicitly requested a visible interactive mirroring window.
    $mirror = Start-Process -FilePath $taskScrcpy -ArgumentList $taskArgs -WorkingDirectory (Split-Path -Parent $taskScrcpy) `
        -WindowStyle Normal -PassThru -RedirectStandardOutput (Join-Path $taskLogDir 'scrcpy.log') `
        -RedirectStandardError (Join-Path $taskLogDir 'scrcpy-error.log')
    Write-Output "投屏已启动，进程 $($mirror.Id)。声音保留在平板；关闭窗口即可结束。"
} finally { $env:ADB = $previousAdb }
