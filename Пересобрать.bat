@echo off
setlocal
cd /d "%~dp0"

echo Rebuilding all four exe files (Constructor, Replay2, Runner, Help)...
echo This takes a minute or two - jlink + jpackage run for each app.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File make-exe.ps1

if errorlevel 1 (
    echo.
    echo BUILD FAILED - see the error above.
    echo Common cause: an app is still running - close all Kelium*.exe windows and retry.
    pause
    exit /b 1
)

echo.
echo Done. Files are in dist\ - see the list above for exact time and fingerprint.
pause
