@echo off
setlocal
cd /d "%~dp0"

echo What to launch?
echo   1 - Constructor (layouts)
echo   2 - Replay2 (game review)
echo   3 - Runner (batch simulations)
echo   4 - Help (rulebook)
set /p CHOICE=Number:

set MAIN=kelium.gui.LayoutEditor
if "%CHOICE%"=="2" set MAIN=kelium.gui.replay2.Replay2Gui
if "%CHOICE%"=="3" set MAIN=kelium.gui.RunnerGui
if "%CHOICE%"=="4" set MAIN=kelium.gui.replay2.HelpApp

rem Три модуля (движок/боты/GUI, разделено 14.08.2026) вместо одного
rem target\classes; deps.txt = только сторонние библиотеки (regenerate:
rem mvn -q -o -pl gui dependency:build-classpath -Dmdep.outputFile=deps.txt).
set /p CP=<deps.txt

java -cp "engine\target\classes;bots\target\classes;gui\target\classes;%CP%" -Dkelium.data="%~dp0data" -Dfile.encoding=UTF-8 %MAIN%

if errorlevel 1 (
    echo.
    echo App crashed - see the error message above.
    pause
)
