#!/usr/bin/env bash
# СЦЕНАРИИ КАРТИНКАМИ — для справочника (02.10.2026). То же, что
# tools/раскладки-в-png.ps1, но без Windows: берёт ВСЕ поля из
# data/scenarios/new (их же игра кладёт в библиотеку сценариев) и пишет
# docs/раскладки/<N>и-<k>.png, где N — число игроков, k — номер по порядку
# файлов. Плюс легенда.png.
#
# Запуск: bash tools/раскладки-снимки.sh   (нужен собранный gui/target/kelium-runner.jar)
set -euo pipefail
cd "$(dirname "$0")/.."
export LANG=C.UTF-8 LC_ALL=C.UTF-8
JAR=gui/target/kelium-runner.jar
OUT=docs/раскладки
RUN=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null; then RUN=(xvfb-run -a); fi
mkdir -p "$OUT"
find "$OUT" -maxdepth 1 -name '[234]и-*.png' -delete
declare -A K=([2]=0 [3]=0 [4]=0)
while IFS= read -r f; do
  n=$(basename "$f" | sed -nE 's/.*scenario_([234])p.*/\1/p; s/.*на ([234]) игрок.*/\1/p')
  [ -z "$n" ] && { echo "пропуск (не понять число игроков): $f"; continue; }
  K[$n]=$(( K[$n] + 1 ))
  "${RUN[@]}" java -Djava.awt.headless=false -cp "$JAR" kelium.gui.СнимокРаскладки слияние \
      "$OUT/${n}и-${K[$n]}.png" "$f" 1500 1500 >/dev/null
  echo "${n}и-${K[$n]}: $f"
done < <(find data/scenarios/new -maxdepth 1 -name '*.kmap' | sort)
"${RUN[@]}" java -Djava.awt.headless=false -cp "$JAR" kelium.gui.СнимокРаскладки легенда "$OUT/легенда.png" >/dev/null
echo "легенда: $OUT/легенда.png"
