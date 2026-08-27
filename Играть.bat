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
rem ============================================================================
chcp 65001 >nul
cd /d "%~dp0"

if /i "%~1"=="пересобрать" goto rebuild
if /i "%~1"=="rebuild" goto rebuild
if /i "%~1"=="clean" goto rebuild
goto run

:rebuild
echo Пересобираю начисто ^(это займёт минуту-две^)...
call mvn -q -o clean compile -DskipTests
if errorlevel 1 (
    echo.
    echo ОШИБКА сборки. Нужны Java 21+ и Maven.
    pause
    exit /b 1
)
call mvn -q -o -pl gui dependency:build-classpath -Dmdep.outputFile=deps.txt
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

if not exist "deps.txt" (
    call mvn -q -o -pl gui dependency:build-classpath -Dmdep.outputFile=deps.txt
)
set /p CP=<deps.txt

java -cp "engine\target\classes;cards\target\classes;bots\target\classes;gui\target\classes;%CP%" -Dkelium.data="%~dp0data" -Dfile.encoding=UTF-8 kelium.gui.StartMenuWindow
if errorlevel 1 (
    echo.
    echo Программа завершилась с ошибкой — смотрите сообщение выше.
    pause
)
