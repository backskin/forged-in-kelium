@echo off
rem Reference book (spravochnik): card tables are taken from the game data,
rem so edit cards in the game data first, then double-click this.
rem RULESET is the rules version the book is built for - change it when the version changes.
chcp 65001 >nul
title Reference book build
set RULESET=1.51.0
cd /d "%~dp0"
echo Card table for ruleset %RULESET%...
java -Dkelium.ruleset=%RULESET% -cp "bots\target\kelium-bots-0.1.0.jar;gui\target\kelium-runner.jar" kelium.ТаблицаКарт "docs\таблицы карт\КАРТЫ — свод %RULESET%.xlsx"
if errorlevel 1 goto fail
cd /d "%~dp0tools\книга"
set KELIUM_RULESET=%RULESET%
set PYTHONIOENCODING=utf-8
"C:\Users\backskin\AppData\Local\Programs\Python\Python312\python.exe" справочник_книга.py
if errorlevel 1 goto fail
rem Pictures out of the HTML into "илл" next to it: git keeps the parts, not one big file.
"C:\Users\backskin\AppData\Local\Programs\Python\Python312\python.exe" вынести_картинки.py "..\..\rules\Справочник — черновик\вёрстка\Справочник.html"
echo.
echo Done: "rules\Справочник — черновик\вёрстка\Справочник.pdf"
pause
exit /b 0
:fail
echo.
echo BUILD FAILED - see the messages above.
pause
exit /b 1
