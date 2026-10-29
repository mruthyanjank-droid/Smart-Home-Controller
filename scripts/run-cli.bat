@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0\.."

:: Check for java
if exist "%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe" (
    set "JAVA_CMD=%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe"
) else (
    set JAVA_CMD=java
)

if not exist "bin\smarthome\client\UserClient.class" (
    echo Binaries not found. Compiling first...
    call scripts\compile.bat
)

echo ===================================================
echo Launching Smart Home Interactive CLI Client...
echo ===================================================
"!JAVA_CMD!" -cp bin smarthome.client.UserClient %*
