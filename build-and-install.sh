#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build-and-install.sh
#
# Detects your computer's local WiFi IP, injects it into local.properties,
# builds the debug APK, and installs it on the only connected ADB device.
#
# Usage:
#   ./build-and-install.sh            # default port 8000
#   ./build-and-install.sh 9000       # custom port
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PORT="${1:-8000}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
PROPS_FILE="$FRONTEND_DIR/local.properties"

# ─── Step 1: detect local WiFi IP ────────────────────────────────────────────

detect_local_ip() {
    local ip=""

    # Windows: use PowerShell to find a WiFi/Ethernet IPv4 in the private ranges
    if command -v powershell.exe &>/dev/null; then
        ip=$(powershell.exe -NoProfile -Command "
            Get-NetIPAddress -AddressFamily IPv4 |
            Where-Object { \$_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
            Sort-Object -Property InterfaceMetric |
            Select-Object -First 1 -ExpandProperty IPAddress
        " 2>/dev/null | tr -d '\r\n')
    fi

    # Linux/Mac fallback
    if [ -z "$ip" ] && command -v hostname &>/dev/null; then
        ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi

    # ip route fallback
    if [ -z "$ip" ] && command -v ip &>/dev/null; then
        ip=$(ip route get 1 2>/dev/null | awk '/src/{print $7; exit}')
    fi

    echo "$ip"
}

echo "──────────────────────────────────────────"
echo " LifeLogger — build & install"
echo "──────────────────────────────────────────"

LOCAL_IP=$(detect_local_ip)

if [ -z "$LOCAL_IP" ]; then
    echo ""
    echo "ERROR: Could not auto-detect local IP."
    echo "Set it manually in $PROPS_FILE:"
    echo "  server.base.url=http://<YOUR_IP>:$PORT"
    echo ""
    exit 1
fi

SERVER_URL="http://$LOCAL_IP:$PORT"
echo "Server URL : $SERVER_URL"

# ─── Step 2: write local.properties ──────────────────────────────────────────

# Preserve existing lines (comments, etc.) but update/add server.base.url
if grep -q "^server\.base\.url=" "$PROPS_FILE" 2>/dev/null; then
    # Update existing line (works on both GNU sed and macOS sed via -e)
    sed -i.bak "s|^server\.base\.url=.*|server.base.url=$SERVER_URL|" "$PROPS_FILE"
    rm -f "${PROPS_FILE}.bak"
else
    echo "server.base.url=$SERVER_URL" >> "$PROPS_FILE"
fi

echo "Updated    : $PROPS_FILE"

# ─── Step 3: check ADB device ────────────────────────────────────────────────

if ! command -v adb &>/dev/null; then
    echo ""
    echo "ERROR: 'adb' not found in PATH."
    echo "Add Android SDK platform-tools to your PATH and retry."
    echo ""
    exit 1
fi

DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo ""
    echo "ERROR: No ADB device connected."
    echo "Connect your phone via USB (or enable wireless ADB) and retry."
    echo ""
    exit 1
fi

if [ "$DEVICE_COUNT" -gt 1 ]; then
    echo ""
    echo "WARNING: Multiple ADB devices detected. Installing to all of them."
    echo "Disconnect extras if you want a single-device install."
    echo ""
fi

DEVICE_ID=$(adb devices | grep "device$" | awk '{print $1}' | head -1)
echo "ADB device : $DEVICE_ID"

# ─── Step 4: ensure gradle wrapper jar exists ─────────────────────────────────

WRAPPER_JAR="$FRONTEND_DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo ""
    echo "gradle-wrapper.jar not found — attempting to generate wrapper..."

    if command -v gradle &>/dev/null; then
        (cd "$FRONTEND_DIR" && gradle wrapper --gradle-version=8.4 --quiet)
        echo "Wrapper generated."
    else
        echo ""
        echo "ERROR: gradle-wrapper.jar is missing and 'gradle' is not on PATH."
        echo ""
        echo "Fix with one of:"
        echo "  1. Open the frontend/ folder in Android Studio once"
        echo "     (it generates the wrapper automatically)."
        echo "  2. Install Gradle and run: cd frontend && gradle wrapper --gradle-version=8.4"
        echo ""
        exit 1
    fi
fi

# ─── Step 5: build & install ──────────────────────────────────────────────────

echo ""
echo "Building..."
cd "$FRONTEND_DIR"
chmod +x gradlew
./gradlew installDebug --quiet

echo ""
echo "──────────────────────────────────────────"
echo " Done. App installed on $DEVICE_ID"
echo " Server: $SERVER_URL"
echo "──────────────────────────────────────────"
