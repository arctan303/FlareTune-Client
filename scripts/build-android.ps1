param(
    [switch]$Lint
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot 'android'

# Prefer the user's configured Android JDK over inherited machine-wide Java 8.
$configuredJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if ($configuredJavaHome) {
    $env:JAVA_HOME = $configuredJavaHome
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Configure JAVA_HOME with JDK 17 or newer before building Android.'
}

$buildArguments = @('--project-dir', $androidRoot, '--console=plain', ':app:assembleDebug')
if ($Lint) {
    $buildArguments += ':app:lintDebug'
}

& (Join-Path $androidRoot 'gradlew.bat') @buildArguments
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Output (Join-Path $androidRoot 'app/build/outputs/apk/debug/app-debug.apk')
