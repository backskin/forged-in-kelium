# -*- coding: utf-8 -*-
"""СТОЛ ДЛЯ ЗАДНИКА КОРОБКИ — снимок партии на четверых (дизайнер 21.10.2026).

Дизайнер: «пересними стол на тёплых текстурах блоков и выбери другую
раскладку, симметричную и на 4 игроков». Раскладка — data/scenarios/new/
scenario_4p_2026-09-25_203643.kmap: центрально-симметричная, 4 стартовых тайла
по углам, 2 больших в середине, 2 недоступных гекса напротив друг друга.

Текстуры блоков для книги берутся из экспорта дизайнера, а не из data/: игра
и её картинки не трогаются. Скрипт собирает свою папку текстур
(tools/книга/_текстуры-книги — ссылки на data/textures, кроме блоков), кладёт
туда блоки из экспорта через tools/gen_block_art.py (тот же разбор и сверка с
набором блоков) и снимает стол движком (kelium.gui.replay2.СнимокСтола) со
свойством kelium.textures.

Запуск:
  python стол_задника.py ["<папка экспорта блоков>" [префикс файлов] [версия набора]]
Без папки — блоки из data/textures/block (как в игре).
Пишет: tools/книга/стол-коробка-5.png (берёт задник_коробки.py).

Нужна собранная Java-часть (mvn -o -q compile): классы из */target/classes,
snakeyaml и flatlaf из ~/.m2.
"""
import glob
import os
import shutil
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
РАСКЛАДКА = os.path.join(КОРЕНЬ, "data", "scenarios", "new", "scenario_4p_2026-09-25_203643.kmap")
ВЫХОД = os.path.join(D, "стол-коробка-5.png")
ШИРИНА = 2600          # точек по ширине снимка
СИД = 7

ЭКСПОРТ = sys.argv[1] if len(sys.argv) > 1 else ""
ПРЕФИКС = sys.argv[2] if len(sys.argv) > 2 else "Модули-поля-тесно"
ВЕРСИЯ = sys.argv[3] if len(sys.argv) > 3 else "6.0.0"


def связать(откуда, куда):
    """Жёсткая ссылка (на NTFS без прав администратора), иначе копия."""
    try:
        os.link(откуда, куда)
    except OSError:
        shutil.copy2(откуда, куда)


def текстуры_книги():
    """Папка текстур: всё из data/textures, блоки — из экспорта дизайнера."""
    корень = os.path.join(D, "_текстуры-книги")
    данные = os.path.join(корень, "data")
    if os.path.isdir(корень):
        shutil.rmtree(корень)
    тексты = os.path.join(данные, "textures")
    исх = os.path.join(КОРЕНЬ, "data", "textures")
    for папка, _, файлы in os.walk(исх):
        отн = os.path.relpath(папка, исх)
        if отн == "block" or отн.startswith("block" + os.sep):
            continue
        os.makedirs(os.path.join(тексты, отн), exist_ok=True)
        for ф in файлы:
            связать(os.path.join(папка, ф), os.path.join(тексты, отн, ф))
    # gen_block_art сверяет экспорт с набором блоков и пишет <корень>/data/textures/block
    shutil.copytree(os.path.join(КОРЕНЬ, "data", "blocks"), os.path.join(данные, "blocks"))
    subprocess.run([sys.executable, os.path.join(КОРЕНЬ, "tools", "gen_block_art.py"),
                    корень, ЭКСПОРТ, ВЕРСИЯ, ПРЕФИКС], check=True)
    return тексты


def путь_классов():
    jars = []
    for шаблон in ("org/yaml/snakeyaml/*/snakeyaml-*.jar", "com/formdev/flatlaf/*/flatlaf-*.jar"):
        найдено = sorted(п for п in glob.glob(os.path.join(os.path.expanduser("~"), ".m2", "repository", шаблон))
                         if not п.endswith(("-sources.jar", "-javadoc.jar")))
        if найдено:
            jars.append(найдено[-1])
    классы = [os.path.join(КОРЕНЬ, м, "target", "classes") for м in ("gui", "bots", "cards", "engine")]
    return os.pathsep.join(классы + jars)


свойства = ["-Djava.awt.headless=true", "-Dfile.encoding=UTF-8"]
if ЭКСПОРТ:
    свойства.append("-Dkelium.textures=" + текстуры_книги())
subprocess.run(["java", *свойства, "-cp", путь_классов(), "kelium.gui.replay2.СнимокСтола",
                ВЫХОД, str(ШИРИНА), РАСКЛАДКА, str(СИД), "", "4"], check=True,
               env=dict(os.environ, LANG="C.UTF-8", LC_ALL="C.UTF-8"))
якоря = os.path.splitext(ВЫХОД)[0] + "-якоря.txt"
if os.path.exists(якоря):
    os.remove(якоря)             # якоря шагов подготовки заднику не нужны
print("стол:", ВЫХОД)
