# -*- coding: utf-8 -*-
"""Собрать книгу правил целиком из глав .md — одной командой (дизайнер 14.10.2026).

Порядок тот же, что при ручной сборке:
  главы 1, 2, 4–11 → reflow.py; глава 3 → make_podg_text.py (разворот
  подготовки со своей вёрсткой); затем оформление (restyle.py), памятка
  на обороте (make_back.py), вынос картинок, печать PDF (build.py) и сторож
  вёрстки (ревизия.py). Готовый PDF кладётся рядом с вёрсткой:
  «Книга правил — просмотр.pdf» (полный) и «Книга правил — лёгкая.pdf».

Запуск: python собрать_книгу.py   (или кнопка «Собрать книгу правил.cmd»)
"""
import glob
import os
import shutil
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "Книга правил*"))[0]
ВЁРСТКА = os.path.join(КНИГА, "вёрстка")
ОКР = dict(os.environ, PYTHONIOENCODING="utf-8")


def шаг(*аргументы):
    имя = " ".join(аргументы)
    print("==>", имя, flush=True)
    r = subprocess.run([sys.executable, *аргументы], cwd=D, env=ОКР)
    if r.returncode:
        print("\nОШИБКА на шаге:", имя)
        sys.exit(r.returncode)


главы = sorted(os.path.basename(p) for p in glob.glob(os.path.join(КНИГА, "[0-9][0-9] — *.md")))
for г in главы:
    if г.startswith("00"):
        continue                      # план книги — не глава
    if г.startswith("03"):
        шаг("make_spread.py")         # снимок стола с выносками (стол.png)
        шаг("make_podg_text.py")
    else:
        шаг("reflow.py", г)
шаг("restyle.py")
шаг("фон_иллюстрации.py")
шаг("make_back.py")
шаг("вынести_картинки.py")
шаг("build.py")
шаг("ревизия.py")
шаг("типография.py")
shutil.copy(os.path.join(D, "book.pdf"), os.path.join(ВЁРСТКА, "Книга правил — просмотр.pdf"))
shutil.copy(os.path.join(D, "book-lite.pdf"), os.path.join(ВЁРСТКА, "Книга правил — лёгкая.pdf"))
print("\nГотово:", os.path.join(ВЁРСТКА, "Книга правил — лёгкая.pdf"))
print("Для типографии (вылеты 5 мм, метки реза):", os.path.join(ВЁРСТКА, "Книга правил — типография.pdf"))
print("Выше — отчёт ревизии: ВЫЛЕТ — что-то вылезло за полосу, её надо сократить.")
