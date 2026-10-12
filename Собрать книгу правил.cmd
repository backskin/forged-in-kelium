@echo off
rem Rulebook: rebuild the whole book from the chapter .md files.
rem Edit chapters in "rules\Книга правил — черновик\NN — ....md", then double-click this.
rem Result: "rules\Книга правил — черновик\вёрстка\Книга правил — лёгкая.pdf".
chcp 65001 >nul
title Rulebook build
cd /d "%~dp0tools\книга"
"C:\Users\backskin\AppData\Local\Programs\Python\Python312\python.exe" собрать_книгу.py
echo.
pause
