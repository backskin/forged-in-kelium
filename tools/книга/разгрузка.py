# -*- coding: utf-8 -*-
"""РАЗГРУЗКА ПОЛОС: переполненную страницу разрезать по ближайшему заголовку.

После того как блоки текста получили воздух (просьба дизайнера 14.09.2026),
часть полос перестала вмещать свой текст. Здесь это чинится не уменьшением
кегля, а тем, чем и положено: лишний раздел уезжает на новую полосу.

Как работает: печатает PDF, находит полосы, чей текст выходит за поле набора,
сопоставляет их с главами и вставляет в md разрыв `<!-- стр -->` перед
последним заголовком такой полосы. Повторяет, пока переполнений не останется
или пока разрезать больше нечего.

Запуск: python разгрузка.py [сколько проходов]
"""
import glob
import io
import os
import re
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
DIR = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*")[0]
HTML = os.path.join(DIR, "вёрстка", "Книга правил.html")
PDF = os.path.join(D, "book.pdf")
ГЛАВЫ = sorted(glob.glob(os.path.join(DIR, "[01][0-9] — *.md")))


def печать():
    subprocess.run([sys.executable, os.path.join(D, "build.py")], check=True,
                   capture_output=True, cwd=D)


def переполнения():
    import pymupdf
    d = pymupdf.open(PDF)
    out = []
    for i, pg in enumerate(d):
        H = pg.rect.height
        плохо = [b for b in pg.get_text("blocks")
                 if b[3] > H - 40 and b[4].strip() != str(i + 1)]
        if плохо:
            out.append((i + 1, round(max(b[3] for b in плохо), 1)))
    d.close()
    return out


def страницы_глав():
    """Книжная страница → (файл главы, номер её полосы внутри главы)."""
    t = io.open(HTML, encoding="utf-8").read()
    body = t[t.index("<body>"):]
    из = {}
    глава = None
    внутри = 0
    for m in re.finditer(r'<div class="глава">(?:<span class="гл-н">)?([^<]*)[^>]*>'
                         r'|<div class="колонцифра"><span>(\d+)</span>', body):
        if m.group(1) is not None:
            имя = m.group(1)
            if имя.startswith("Глава "):
                глава = имя
                внутри = -1
        elif m.group(2):
            n = int(m.group(2))
            внутри += 1
            if глава:
                из[n] = (глава, внутри)
    return из


def файл_главы(имя):
    номер = int(имя.split()[1])
    for f in ГЛАВЫ:
        if os.path.basename(f).startswith("%02d " % номер):
            return f
    return None


def разрезать(файл, индекс):
    """Вставить разрыв перед последним заголовком указанной полосы главы."""
    t = io.open(файл, encoding="utf-8").read()
    куски = t.split("<!-- стр -->")
    if индекс >= len(куски):
        return False
    кусок = куски[индекс]
    заголовки = list(re.finditer(r"^#{2,3} .+$", кусок, re.M))
    if len(заголовки) < 2:
        return False
    m = заголовки[-1]
    куски[индекс] = кусок[:m.start()] + "<!-- стр -->\n\n" + кусок[m.start():]
    io.open(файл, "w", encoding="utf-8").write("<!-- стр -->".join(куски))
    return True


def main():
    проходов = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    for проход in range(проходов):
        печать()
        плохие = переполнения()
        print("проход %d: переполнено полос %d — %s"
              % (проход + 1, len(плохие), плохие))
        if not плохие:
            return
        карта = страницы_глав()
        тронуто = set()
        for стр, _ in плохие:
            если = карта.get(стр)
            if not если:
                continue
            имя, индекс = если
            файл = файл_главы(имя)
            if not файл or файл in тронуто:
                continue
            if разрезать(файл, индекс):
                тронуто.add(файл)
                subprocess.run([sys.executable, os.path.join(D, "reflow.py"),
                                os.path.basename(файл)], check=True,
                               capture_output=True, cwd=D)
                print("   разрезана", os.path.basename(файл), "полоса", индекс + 1)
        if not тронуто:
            print("   резать больше нечего")
            return


if __name__ == "__main__":
    main()
