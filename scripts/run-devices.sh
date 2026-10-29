#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

if [ ! -f "bin/smarthome/launcher/DeviceLauncher.class" ]; then
    echo "Binaries not found. Compiling first..."
    bash scripts/compile.sh
fi

echo "==================================================="
echo "Launching All 4 Simulated Smart Devices..."
echo "==================================================="
java -cp bin smarthome.launcher.DeviceLauncher "$@"
