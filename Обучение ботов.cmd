@echo off
rem Bot training (AlphaZero self-play). Double-click to start; close the window to stop.
rem Resumes from the last finished generation. Report and log: data\selfplay\az\
cd /d "%~dp0"
if not exist gui\target\kelium-runner.jar call mvn -q -o -DskipTests package
if not exist data\selfplay\az mkdir data\selfplay\az
rem Train from a private copy of the jar, so rebuilding the project does not break training.
copy /y gui\target\kelium-runner.jar data\selfplay\az\runner.jar >nul
start "Bot training - close this window to stop" /low /min cmd /c "java -Xmx6g -Dfile.encoding=UTF-8 -cp data\selfplay\az\runner.jar kelium.TrainAZ 1000 200 64 0.5 >> data\selfplay\az\train.log 2>&1"
start "" "data\selfplay\az"
