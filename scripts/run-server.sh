#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

if [ ! -f "bin/smarthome/server/ControllerServer.class" ]; then
    echo "Binaries not found. Compiling first..."
    bash scripts/compile.sh
fi

echo "==================================================="
echo "Launching Smart Home Controller Server on port 5000..."
echo "==================================================="
java -cp bin smarthome.server.ControllerServer "$@"
