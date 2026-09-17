# -*- coding: utf-8 -*-
"""СТОРОЖ ССЫЛОК КНИГИ: значки, рисунки, ссылки на главы.

Зачем. Три вещи в книге резолвятся не текстом, а файлами и номерами, и
ломаются молча:

  * `[иконка: имя]` и `:заглавная: имя` ищут ФАЙЛ `имя.png` в `rules/иконки-экспорт`
    и `rules/иконки`. Не нашли — сборщик рисует видимую пометку «уточнить»
    прямо в книге, и её легко проглядеть на сорока полосах;
  * `[[имя]]` ищет `tools/книга/_имя.svg`. Нет файла — пустое место;
  * `(глава N)` при N > 12 ведёт в главу, которой нет: книга из двенадцати глав,
    а прежние планы обещали пятнадцать.

Реестр `Иконки — список.md` для этой проверки не источник: он перечисляет
пожелания дизайнера, и больше трети его имён файлами пока не существует.
Правда здесь — файлы.

Запуск: python связи.py — печатает сломанное и выходит с кодом 1, если оно есть.
"""
import glob
import io
import os
import re
import sys

БАЗА = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ГЛАВЫ = glob.glob(os.path.join(БАЗА, "rules", "Книга правил*"))[0]
ФИГУРЫ = os.path.dirname(os.path.abspath(__file__))
ПАПКИ_ИКОНОК = [os.path.join(БАЗА, "rules", "иконки-экспорт"),
                os.path.join(БАЗА, "rules", "иконки")]
ГЛАВ_В_КНИГЕ = 12


def значки():
    есть = set()
    for d in ПАПКИ_ИКОНОК:
        if not os.path.isdir(d):
            continue
        for f in os.listdir(d):
            if f.lower().endswith(".png"):
                есть.add(os.path.splitext(f)[0].lower())
    return есть


def ключ(имя):
    """Так же, как md2page.значок: до тире, без пробелов, в нижнем регистре."""
    return имя.split("—")[0].strip().lower().replace(" ", "-")


def проверить():
    есть = значки()
    беды = []
    for путь in sorted(glob.glob(os.path.join(ГЛАВЫ, "[01][0-9] — *.md"))):
        имя = os.path.basename(путь)
        if имя.startswith("00"):
            continue                      # план книги — не глава
        текст = io.open(путь, encoding="utf-8").read()
        for m in re.finditer(r"\[иконка:\s*([^\]]+)\]", текст):
            if ключ(m.group(1)) not in есть:
                беды.append((имя, "значка нет файлом", m.group(1).strip()))
        for m in re.finditer(r"^:заглавная:\s*(.+)$", текст, re.M):
            if ключ(m.group(1)) not in есть:
                беды.append((имя, "буквицы нет файлом", m.group(1).strip()))
        for m in re.finditer(r"^\[\[([^\]]+)\]\]$", текст, re.M):
            if not os.path.exists(os.path.join(ФИГУРЫ, "_" + m.group(1) + ".svg")):
                беды.append((имя, "рисунка нет файлом", m.group(1)))
        for m in re.finditer(r"\(глава (\d+)", текст):
            if int(m.group(1)) > ГЛАВ_В_КНИГЕ:
                беды.append((имя, "ссылка на несуществующую главу", m.group(1)))
    return беды


def main():
    беды = проверить()
    if not беды:
        print("связи целы: значки, рисунки и ссылки на главы на месте")
        return 0
    print("СЛОМАНО:", len(беды))
    for файл, что, кто in беды:
        print("  %-34s %s: %s" % (файл, что, кто))
    return 1


if __name__ == "__main__":
    sys.exit(main())
