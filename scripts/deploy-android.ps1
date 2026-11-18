param(
    [string]$Serial,
    [switch]$SkipBuild,
    [switch]$Preview
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot 'android'
$androidSdk = $env:ANDROID_HOME
if (-not $androidSdk) { $androidSdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
$androidJdk = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if ($androidJdk) { $env:JAVA_HOME = $androidJdk }
if (-not $androidSdk) { throw 'Configure ANDROID_HOME first.' }
$adb = Join-Path $androidSdk 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android platform-tools are unavailable.' }
$env:ANDROID_HOME = $androidSdk
if (-not $Serial) {
    $connected = @(& $adb devices | ForEach-Object {
        if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
    })
    if ($connected.Count -ne 1) { throw 'Connect one authorized device, or specify -Serial.' }
    $Serial = $connected[0]
}
$deviceArgs = @('-s', $Serial)
if (-not $SkipBuild) {
    & (Join-Path $androidRoot 'gradlew.bat') --project-dir $androidRoot --console=plain :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        $failure = [InvalidOperationException]::new('Build failed; the installed app was left in place.')
        $failure.Data['FlareTuneStage'] = 'build'
        throw $failure
    }
}
$apk = Join-Path $androidRoot 'app/build/outputs/apk/debug/app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build the debug APK first.' }
& $adb @deviceArgs install -r $apk
if ($LASTEXITCODE -ne 0) {
    $failure = [InvalidOperationException]::new('Device rejected installation. Automatic deployment stopped.')
    $failure.Data['FlareTuneStage'] = 'device'
    throw $failure
}
# Updating the same package preserves app data; restarting does reset the current in-memory screen/queue.
& $adb @deviceArgs shell am force-stop com.arcinks.flaretune
if ($LASTEXITCODE -ne 0) { throw 'Could not stop the previous app process.' }
$targetActivity = if ($Preview) { "com.arcinks.flaretune/.preview.PreviewActivity" } else { "com.arcinks.flaretune/.MainActivity" }
& $adb @deviceArgs shell am start -W -n $targetActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not launch FlareTune.' }
Write-Output 'FlareTune updated. App data preserved; app process restarted.'
