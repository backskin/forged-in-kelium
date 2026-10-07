@echo off
rem Bot training: the planner bot (whole-turn planning + opponents' reply round)
rem learns its position evaluation by self-play. Double-click to start; close the
rem window to stop. Restart resumes from the last finished generation.
rem If rules or card decks changed, the best network is fine-tuned on the new
rem cards instead of starting from scratch.
rem Report and log: data\selfplay\strateg\
chcp 65001 >nul
title Bot training - close this window to stop
cd /d "%~dp0"
rem Always rebuild, so training runs on the current rules and cards.
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
if not exist data\selfplay\strateg mkdir data\selfplay\strateg
rem Train from a private copy of the jar, so rebuilding the project does not break training.
copy /y gui\target\kelium-runner.jar data\selfplay\strateg\runner.jar >nul
start "" "data\selfplay\strateg"
rem generations, self-play games per generation, benchmark games per generation
start "" /b /low /wait java -Xmx10g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp data\selfplay\strateg\runner.jar kelium.TrainStrateg 1000 240 64
echo.
echo Training stopped. Press any key to close.
pause >nul
