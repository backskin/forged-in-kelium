@echo off
setlocal
cd /d "%~dp0"

rem Три модуля вместо одного target\classes (разделено 14.08.2026).
set /p CP=<deps.txt

java -cp "engine\target\classes;bots\target\classes;gui\target\classes;%CP%" -Dkelium.data="%~dp0data" -Dfile.encoding=UTF-8 kelium.gui.replay2.Replay2Gui

if errorlevel 1 (
    echo.
    echo App crashed - see the error message above.
    pause
)
