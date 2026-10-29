@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0\.."

:: Check for java
if exist "%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe" (
    set "JAVA_CMD=%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\java.exe"
) else (
    set JAVA_CMD=java
)

if not exist "bin\smarthome\SmartHomeTestSuite.class" (
    echo Binaries not found. Compiling first...
    call scripts\compile.bat
)

echo ===================================================
echo Running Smart Home Automated Test Suite...
echo ===================================================
"!JAVA_CMD!" -cp bin smarthome.SmartHomeTestSuite %*
