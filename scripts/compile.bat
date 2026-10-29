@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0\.."

:: Check for javac
where javac >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set JAVAC_CMD=javac
) else if exist "%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\javac.exe" (
    set "JAVAC_CMD=%LOCALAPPDATA%\Programs\BurpSuite\jre\bin\javac.exe"
) else (
    echo [ERROR] javac compiler not found on PATH.
    echo Please install JDK 17+ or add javac to your PATH.
    exit /b 1
)

if not exist bin mkdir bin
echo ===================================================
echo Compiling Smart Home Java Project...
echo Compiler: !JAVAC_CMD!
echo ===================================================

"!JAVAC_CMD!" -d bin src\smarthome\util\*.java src\smarthome\protocol\*.java src\smarthome\device\*.java src\smarthome\server\*.java src\smarthome\client\*.java src\smarthome\launcher\*.java tests\smarthome\*.java

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Compilation completed successfully! All binaries placed in 'bin\'.
) else (
    echo [ERROR] Compilation failed.
    exit /b 1
)
