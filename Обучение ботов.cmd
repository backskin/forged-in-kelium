@echo off
rem Bot training (AlphaZero self-play). Double-click to start; close the window to stop.
rem First start: networks learn to copy the old bots, then self-play generations.
rem Resumes from the last finished generation. Report and log: data\selfplay\az\
chcp 65001 >nul
title Bot training - close this window to stop
cd /d "%~dp0"
rem Always rebuild, so training runs on the current rules.
echo Building the game with the current rules...
call mvn -q -o -DskipTests package
if errorlevel 1 (
  if exist gui\target\kelium-runner.jar (
    echo Build failed - training on the previous build.
  ) else (
    echo Build failed and there is no previous build. Press any key to close.
    pause >nul
    exit /b 1
  )
)
if not exist data\selfplay\az mkdir data\selfplay\az
rem Train from a private copy of the jar, so rebuilding the project does not break training.
copy /y gui\target\kelium-runner.jar data\selfplay\az\runner.jar >nul
start "" "data\selfplay\az"
rem generations, games per generation, search probes per decision, rollout share, imitation games
start "" /b /low /wait java -Xmx8g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp data\selfplay\az\runner.jar kelium.TrainAZ 1000 200 128 0 4000
echo.
echo Training stopped. Press any key to close.
pause >nul
