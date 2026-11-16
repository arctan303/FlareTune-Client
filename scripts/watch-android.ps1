param(
    [string]$Serial,
    [switch]$Once,
    [switch]$Preview,
    [switch]$NoInitialDeploy,
    [ValidateRange(250, 5000)][int]$DebounceMs = 1000
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot 'android'
$deployScript = Join-Path $PSScriptRoot 'deploy-android.ps1'
function Get-AndroidSourceStamp {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $androidRoot 'app/src/main'), (Join-Path $androidRoot 'app/src/debug') -Recurse -File)
    $files += @(Get-Item -LiteralPath (Join-Path $androidRoot 'app/build.gradle.kts'),
        (Join-Path $androidRoot 'build.gradle.kts'), (Join-Path $androidRoot 'gradle/libs.versions.toml'),
        (Join-Path $androidRoot 'gradle.properties'))
    return (($files | Sort-Object FullName | ForEach-Object {
        $_.FullName + ':' + $_.Length + ':' + $_.LastWriteTimeUtc.Ticks
    }) -join '|')
}
function Invoke-AndroidDeploy {
    $deployArguments = @{}
    if ($Serial) { $deployArguments.Serial = $Serial }
    if ($Preview) { $deployArguments.Preview = $true }
    & $deployScript @deployArguments
}
if ($Once) { Invoke-AndroidDeploy; return }
Write-Output 'Watching Android sources. Each successful build updates and restarts the app. Ctrl+C stops watching.'
$previous = Get-AndroidSourceStamp
if (-not $NoInitialDeploy) { Invoke-AndroidDeploy }
while ($true) {
    Start-Sleep -Milliseconds 500
    $current = Get-AndroidSourceStamp
    if ($current -eq $previous) { continue }
    Start-Sleep -Milliseconds $DebounceMs
    $previous = Get-AndroidSourceStamp
    try { Invoke-AndroidDeploy }
    catch {
        if ($_.Exception.Data['FlareTuneStage'] -eq 'build') {
            Write-Warning $_.Exception.Message
            continue
        }
        # A device refusal is not permission to keep trying or alter device security settings.
        throw
    }
}
