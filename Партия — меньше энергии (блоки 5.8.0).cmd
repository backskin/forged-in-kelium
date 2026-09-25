@echo off
rem Test game on block set 5.8.0: energy cells 3 of 5 (small block) and 4 of 6 (big).
rem Everything else is the current ruleset. Double-click to start.
chcp 65001 >nul
cd /d "%~dp0"
if not exist gui\target\kelium-runner.jar call mvn -q -o -DskipTests package
java -Dfile.encoding=UTF-8 -Dkelium.rules=content_versions.blocks=5.8.0 -jar gui\target\kelium-runner.jar
