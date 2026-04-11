# set-server-ip.ps1
# Detects your local WiFi IP and updates local.properties.
# Run this before opening the project in Android Studio.
#
# Usage:
#   .\set-server-ip.ps1           # auto-detect IP, default port 9000
#   .\set-server-ip.ps1 -Port 9000
#   .\set-server-ip.ps1 -IP 192.168.1.44

param(
    [string]$IP   = "",
    [int]   $Port = 9000
)

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$PropsFile  = Join-Path $ScriptDir "frontend\local.properties"

if (-not $IP) {
    $IP = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
        Sort-Object InterfaceMetric |
        Select-Object -First 1 -ExpandProperty IPAddress

    if (-not $IP) {
        Write-Error "Could not auto-detect local IP. Pass it explicitly: .\set-server-ip.ps1 -IP 192.168.x.x"
        exit 1
    }
}

$ServerUrl  = "http://${IP}:${Port}"
$Escaped    = $ServerUrl -replace ':', '\:'

$lines = @()
if (Test-Path $PropsFile) { $lines = Get-Content $PropsFile }

$found = $false
$newLines = foreach ($line in $lines) {
    if ($line -match '^server\.base\.url=') {
        "server.base.url=$Escaped"
        $found = $true
    } else { $line }
}
if (-not $found) { $newLines = $newLines + "server.base.url=$Escaped" }

$newLines | Set-Content $PropsFile -Encoding UTF8

Write-Host "Set server URL to: $ServerUrl"
Write-Host "Updated: $PropsFile"
Write-Host ""
Write-Host "Now sync Gradle in Android Studio and run the app."
