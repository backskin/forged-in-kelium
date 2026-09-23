@echo off
rem Bot training (AlphaZero self-play). Double-click to start; close the window to stop.
rem Resumes from the last finished generation. Report and log: data\selfplay\az\
chcp 65001 >nul
title Bot training - close this window to stop
cd /d "%~dp0"
if not exist gui\target\kelium-runner.jar call mvn -q -o -DskipTests package
if not exist data\selfplay\az mkdir data\selfplay\az
rem Train from a private copy of the jar, so rebuilding the project does not break training.
copy /y gui\target\kelium-runner.jar data\selfplay\az\runner.jar >nul
start "" "data\selfplay\az"
start "" /b /low /wait java -Xmx6g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp data\selfplay\az\runner.jar kelium.TrainAZ 1000 200 64 0.5
echo.
echo Training stopped. Press any key to close.
pause >nul
