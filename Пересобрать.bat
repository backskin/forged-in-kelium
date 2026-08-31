@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul

echo Пересборка четырёх exe (обновлено 30.08.2026):
echo   KeliumConstructor.exe — конструктор раскладок
echo                           (вкладки: Конструктор, Сборка из блоков, Каталог блоков)
echo   KeliumRunner.exe      — прогоны симуляций
echo   KeliumReplay2.exe     — разбор партии
echo   KeliumHelp.exe        — справочник: правила и все карты
echo.
echo Занимает пару минут: mvn package, затем jlink + jpackage.
echo.

where mvn >nul 2>nul
if errorlevel 1 (
    echo ОШИБКА: не найден mvn. Установи Maven или добавь его в PATH.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File make-exe.ps1

if errorlevel 1 (
    echo.
    echo СБОРКА НЕ ПРОШЛА — смотри ошибку выше.
    echo Частая причина: приложение ещё запущено. Закрой все окна Kelium*.exe и повтори.
    pause
    exit /b 1
)

echo.
echo Готово. Файлы в dist\ — точное время и отпечаток в списке выше.
pause
