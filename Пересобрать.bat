@echo off
setlocal
cd /d "%~dp0"

rem КОДИРОВКА ЭТОГО ФАЙЛА - CP866 (OEM-кириллица), И ЭТО ВАЖНО.
rem cmd.exe разбирает bat в кодовой странице консоли, а по умолчанию на
rem русской Windows это 866. Файл, сохранённый в UTF-8, ломается не только
rem видом: многобайтные буквы разваливаются на куски, и cmd пытается
rem выполнить обрывки слов как команды (проверено 30.08.2026). Длинного тире
rem в CP866 нет вовсе - только обычный дефис.

echo Пересборка четырёх exe:
echo   KeliumConstructor.exe - конструктор раскладок
echo                           вкладки: Конструктор, Сборка из блоков, Каталог блоков
echo   KeliumRunner.exe      - прогоны симуляций
echo   KeliumReplay2.exe     - разбор партии
echo   KeliumHelp.exe        - справочник: правила и все карты
echo.
echo Занимает пару минут: mvn package, затем jlink и jpackage.
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
    echo СБОРКА НЕ ПРОШЛА - смотри ошибку выше.
    echo Частая причина: приложение ещё запущено. Закрой окна Kelium*.exe и повтори.
    pause
    exit /b 1
)

echo.
echo Готово. Файлы в dist\ - точное время и отпечаток в списке выше.
pause
