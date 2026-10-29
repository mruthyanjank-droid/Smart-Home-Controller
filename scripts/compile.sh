#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

mkdir -p bin
echo "==================================================="
echo "Compiling Smart Home Java Project..."
echo "==================================================="

javac -d bin src/smarthome/util/*.java src/smarthome/protocol/*.java src/smarthome/device/*.java src/smarthome/server/*.java src/smarthome/client/*.java src/smarthome/launcher/*.java tests/smarthome/*.java

echo "[SUCCESS] Compilation completed successfully! All binaries placed in 'bin/'."
