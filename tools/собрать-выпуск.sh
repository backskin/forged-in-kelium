#!/usr/bin/env bash
# СОБРАТЬ ВЫПУСК ИГРЫ — один архив, который запускается на любой машине с Java 21.
#
#   tools/собрать-выпуск.sh <имя выпуска> [--без-тестов]
#
# Что делает:
#   1. mvn install — сборка и ВСЕ тесты всех модулей (окно игры — под Xvfb,
#      если экрана нет). Хоть один тест красный — выпуска нет.
#   2. Складывает dist/<имя>/: kelium-runner.jar (вся игра одним файлом),
#      data/ (правила, карты, поля, текстуры, сети ботов), запускалки
#      Играть.bat и играть.sh, ПРОЧТИ.txt.
#   3. Проверяет, что игра в архиве стартует: настоящая партия ботов
#      вчетвером через тот же jar и ту же папку data (kelium.ПробаВыпуска).
#   4. Пакует dist/<имя>.zip.
set -euo pipefail
cd "$(dirname "$0")/.."
NAME="${1:?имя выпуска, например v1.50.0}"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
if [ "${2:-}" != "--без-тестов" ]; then
  if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null; then
    xvfb-run -a -s "-screen 0 1920x1080x24" mvn -B -q install
  else
    mvn -B -q install
  fi
else
  mvn -B -q -DskipTests install
fi
D="dist/$NAME"
rm -rf "$D" "dist/$NAME.zip"
mkdir -p "$D"
cp gui/target/kelium-runner.jar "$D/"
# data без рабочих хвостов: самоигра, черновые прогоны и журналы в выпуск не идут
mkdir -p "$D/data"
tar -C data --exclude='./selfplay' --exclude='*.log' --exclude='_archive' -cf - . | tar -C "$D/data" -xf -
cat > "$D/Играть.bat" <<'BAT'
@echo off
chcp 65001 >nul
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -Dkelium.data="%~dp0data" -cp kelium-runner.jar kelium.gui.StartMenuWindow
if errorlevel 1 pause
BAT
cat > "$D/играть.sh" <<'SH'
#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java -Dfile.encoding=UTF-8 -Dkelium.data="$PWD/data" -cp kelium-runner.jar kelium.gui.StartMenuWindow
SH
chmod +x "$D/играть.sh"
cat > "$D/ПРОЧТИ.txt" <<TXT
Кристаллы Раздора («Всход») — выпуск $NAME

Нужна Java 21 или новее (https://adoptium.net).
Windows: двойной щелчок по «Играть.bat».
macOS и Linux: ./играть.sh

Откроется «Штаб»: режим, правила (свод), поле, своё место и соперники-боты.
Папку data не переносить отдельно от kelium-runner.jar.
TXT
# проба: партия ботов из собранного архива
( cd "$D" && java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dkelium.data="$PWD/data" \
    -cp kelium-runner.jar kelium.ПробаВыпуска )
( cd dist && zip -qr "$NAME.zip" "$NAME" )
echo "выпуск готов: dist/$NAME.zip ($(du -h "dist/$NAME.zip" | cut -f1))"
