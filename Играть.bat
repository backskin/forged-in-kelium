@echo off
rem ============================================================================
rem  КРИСТАЛЛЫ РАЗДОРА — ИГРАТЬ.
rem  Двойной клик открывает «Штаб»: собрать стол — режим, правила, поле, своё
rem  место, соперников — и сесть играть.
rem
rem  Запускается из СВЕЖЕСОБРАННЫХ классов, а не из fat-jar: jar собирается
rem  редко и легко отстаёт от кода, а отставший jar открылся бы без половины
rem  игры. Нет классов — соберём.
rem
rem  «Играть.bat пересобрать» — сборка НАЧИСТО (mvn clean), как у остальных
rem  наших приложений: если что-то ведёт себя странно после обновления кода,
rem  начинать надо с неё.
rem
rem  «Играть.bat exe» — пересобрать ОДНОФАЙЛОВЫЙ dist\Играть.exe (и остальные
rem  наши exe заодно: они собираются одним проходом). Занимает несколько минут:
rem  внутрь зашивается урезанная Java-среда.
rem ============================================================================
chcp 65001 >nul
cd /d "%~dp0"

if /i "%~1"=="пересобрать" goto rebuild
if /i "%~1"=="rebuild" goto rebuild
if /i "%~1"=="clean" goto rebuild
if /i "%~1"=="exe" goto makeexe
if /i "%~1"=="эксе" goto makeexe
goto run

:makeexe
echo Пересобираю exe начисто ^(несколько минут: внутрь зашивается Java^)...
call mvn -o clean package -DskipTests -q
if errorlevel 1 (
    echo.
    echo ОШИБКА сборки. Нужны Java 21+ и Maven.
    pause
    exit /b 1
)
powershell -ExecutionPolicy Bypass -File "%~dp0make-exe.ps1"
if errorlevel 1 (
    echo.
    echo ОШИБКА сборки exe — смотрите сообщение выше.
    pause
    exit /b 1
)
echo.
echo Готово: dist\Играть.exe
pause
exit /b 0

:rebuild
echo Пересобираю начисто ^(это займёт минуту-две^)...
call mvn -q -o clean compile -DskipTests
if errorlevel 1 (
    echo.
    echo ОШИБКА сборки. Нужны Java 21+ и Maven.
    pause
    exit /b 1
)
call mvn -q -o -pl gui dependency:build-classpath -Dmdep.outputFile="%~dp0deps.txt"
echo Пересобрано.
echo.
goto run

:run
if not exist "gui\target\classes\kelium\gui\StartMenuWindow.class" (
    echo Собираю программу ^(подождите^)...
    call mvn -q -o -DskipTests compile
    if errorlevel 1 (
        echo.
        echo ОШИБКА сборки. Нужны Java 21+ и Maven.
        pause
        exit /b 1
    )
)

rem СПИСОК БИБЛИОТЕК ПЕРЕСОБИРАЕТСЯ КАЖДЫЙ ЗАПУСК, а не только когда файла нет.
rem Протухший список — это молча пропавшая библиотека: список от 17.08.2026 не
rem знал про onnxruntime, и бот уровня «гроссмейстер» валил партию посреди хода
rem с NoClassDefFoundError. Пара секунд на запуске дешевле такой поломки.
rem
rem Путь ОБЯЗАТЕЛЬНО абсолютный: с относительным плагин пишет gui\deps.txt,
rem а читается корневой — именно так и разъехались эти два файла.
call mvn -q -o -pl gui dependency:build-classpath -Dmdep.outputFile="%~dp0deps.txt"
if not exist "deps.txt" (
    echo.
    echo ОШИБКА: не удалось собрать список библиотек.
    pause
    exit /b 1
)
set /p CP=<deps.txt

java -cp "engine\target\classes;cards\target\classes;bots\target\classes;gui\target\classes;%CP%" -Dkelium.data="%~dp0data" -Dfile.encoding=UTF-8 kelium.gui.StartMenuWindow
if errorlevel 1 (
    echo.
    echo Программа завершилась с ошибкой — смотрите сообщение выше.
    pause
)
