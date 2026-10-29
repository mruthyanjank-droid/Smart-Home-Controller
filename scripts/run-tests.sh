#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

if [ ! -f "bin/smarthome/SmartHomeTestSuite.class" ]; then
    echo "Binaries not found. Compiling first..."
    bash scripts/compile.sh
fi

echo "==================================================="
echo "Running Smart Home Automated Test Suite..."
echo "==================================================="
java -cp bin smarthome.SmartHomeTestSuite "$@"
