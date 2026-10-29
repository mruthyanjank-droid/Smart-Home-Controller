@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0\.."

:: Check for java
if exist "%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe" (
    set "JAVA_CMD=%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe"
) else (
    set JAVA_CMD=java
)

if not exist "bin\smarthome\server\ControllerServer.class" (
    echo Binaries not found. Compiling first...
    call scripts\compile.bat
)

echo ===================================================
echo Launching Smart Home Controller Server on port 5000...
echo ===================================================
"!JAVA_CMD!" -cp bin smarthome.server.ControllerServer %*
