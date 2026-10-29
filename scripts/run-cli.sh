#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

if [ ! -f "bin/smarthome/client/UserClient.class" ]; then
    echo "Binaries not found. Compiling first..."
    bash scripts/compile.sh
fi

echo "==================================================="
echo "Launching Smart Home Interactive CLI Client..."
echo "==================================================="
java -cp bin smarthome.client.UserClient "$@"
