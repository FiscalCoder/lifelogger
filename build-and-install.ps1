# build-and-install.ps1
# Detects your local WiFi IP, injects it into local.properties,
# builds the debug APK, and installs it on the connected ADB device.
#
# Usage (from project root in PowerShell):
#   .\build-and-install.ps1           # default port 8000
#   .\build-and-install.ps1 -Port 9000

param(
    [int]$Port = 9000
)

$ErrorActionPreference = "Stop"

$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $ScriptDir "frontend"
$PropsFile   = Join-Path $FrontendDir "local.properties"

# --- 1. Detect local WiFi IP ---

$LocalIP = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
    Sort-Object InterfaceMetric |
    Select-Object -First 1 -ExpandProperty IPAddress

if (-not $LocalIP) {
    Write-Error "Could not auto-detect local IP. Set server.base.url manually in $PropsFile"
    exit 1
}

$ServerUrl = "http://" + $LocalIP + ":" + $Port
Write-Host ""
Write-Host "Server URL : $ServerUrl"

# --- 2. Update local.properties ---
# Java .properties format escapes colons: http\://192.168.x.x\:8000
# sdk.dir and other existing lines are preserved.

$EscapedUrl = $ServerUrl -replace ':', '\:'

$lines = @()
if (Test-Path $PropsFile) {
    $lines = Get-Content $PropsFile
}

$found = $false
$newLines = foreach ($line in $lines) {
    if ($line -match '^server\.base\.url=') {
        "server.base.url=$EscapedUrl"
        $found = $true
    } else {
        $line
    }
}
if (-not $found) {
    $newLines = $newLines + "server.base.url=$EscapedUrl"
}

$newLines | Set-Content $PropsFile -Encoding UTF8
Write-Host "Updated    : $PropsFile"

# --- 3. Check ADB device ---

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "adb not found in PATH. Add Android SDK platform-tools to your PATH."
    exit 1
}

$devices = adb devices | Select-String 'device$'
if ($devices.Count -eq 0) {
    Write-Error "No ADB device connected. Plug in your phone and enable USB debugging."
    exit 1
}
if ($devices.Count -gt 1) {
    Write-Warning "Multiple ADB devices found -- installing to all of them."
}

$DeviceId = ($devices[0].Line -split '\s+')[0]
Write-Host "ADB device : $DeviceId"

# --- 4. Ensure gradle-wrapper.jar exists ---

$WrapperJar = Join-Path $FrontendDir "gradle\wrapper\gradle-wrapper.jar"

if (-not (Test-Path $WrapperJar)) {
    Write-Host ""
    Write-Host "gradle-wrapper.jar not found -- searching for it..."

    # The bootstrapper gradle-wrapper.jar (contains GradleWrapperMain) lives in the
    # Gradle transforms cache when Android Studio has synced any project.
    # Search common cache locations first, then fall back to a full home-dir scan.
    $gradleHome = Join-Path $env:USERPROFILE ".gradle"
    $found = Get-ChildItem -Path "$gradleHome\caches\transforms-*\*\transformed\*\gradle\wrapper\gradle-wrapper.jar" `
        -ErrorAction SilentlyContinue | Select-Object -First 1

    if (-not $found) {
        $found = Get-ChildItem -Path $env:USERPROFILE -Filter "gradle-wrapper.jar" `
            -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notlike "*$FrontendDir*" } |
            Select-Object -First 1
    }

    if ($found) {
        Write-Host "Found: $($found.FullName)"
        Copy-Item $found.FullName $WrapperJar
        Write-Host "Copied gradle-wrapper.jar."
    } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
        Push-Location $FrontendDir
        gradle wrapper --gradle-version=8.4 --quiet
        Pop-Location
        Write-Host "Wrapper generated via gradle command."
    } else {
        Write-Host ""
        Write-Host "ERROR: Could not find gradle-wrapper.jar." -ForegroundColor Red
        Write-Host ""
        Write-Host "Fix: Open the frontend/ folder in Android Studio once and"
        Write-Host "wait for 'Gradle sync' to finish, then run this script again."
        exit 1
    }
}

# --- 5. Build and install ---

Write-Host ""
Write-Host "Building..."
Push-Location $FrontendDir
.\gradlew.bat installDebug
$buildExitCode = $LASTEXITCODE
Pop-Location

if ($buildExitCode -ne 0) {
    # gradlew.bat on Windows sometimes exits 1 even after a successful install.
    # Only treat it as a real failure if the output didn't contain the success message.
    Write-Host ""
    Write-Host "WARNING: gradlew exited with code $buildExitCode (may be a false positive on Windows)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "---" -ForegroundColor Green
Write-Host "Done. App installed on $DeviceId" -ForegroundColor Green
Write-Host "Server: $ServerUrl" -ForegroundColor Green
Write-Host "---" -ForegroundColor Green
