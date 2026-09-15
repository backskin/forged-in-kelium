# -*- coding: utf-8 -*-
"""Убрать главу из вёрстки целиком: её полосы просто выкидываются.

Нужно ровно один раз — когда глава расформирована и её содержимое разошлось
по другим главам (глава 13 «Карты», 15.09.2026). Нумерацию и оглавление потом
чинит обычный reflow.py любой соседней главы.

python удалить_главу.py "Глава 13"
"""
import glob
import io
import os
import re
import sys

DIR = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*")[0]
HTML = os.path.join(DIR, "вёрстка", "Книга правил.html")
цель = sys.argv[1]


def pages_of(s):
    out, i = [], 0
    while True:
        k = s.find('<div class="стр', i)
        if k < 0:
            return out
        depth = 0
        for m in re.finditer(r"<div\b|</div>", s[k:]):
            depth += 1 if m.group(0) == "<div" else -1
            if depth == 0:
                j = k + m.end()
                break
        out.append(s[k:j])
        i = j


ГЛАВА = re.compile(r'<div class="глава">(?:<span class="гл-н">)?(Глава \d+)[.<]')
h = io.open(HTML, encoding="utf-8").read()
head, body = h.split("<body>", 1)
pages = pages_of(body)
хвост = []
while pages and ("обложка задняя" in pages[-1] or "создатели" in pages[-1]):
    хвост.insert(0, pages.pop())

текущая = None
оставить = []
for p in pages:
    m = ГЛАВА.search(p)
    if m:
        текущая = m.group(1)
    if текущая != цель:
        оставить.append(p)
print("выкинуто полос:", len(pages) - len(оставить))

out = [f'<!-- ============ ОБЛОЖКА ============ -->\n<div class="разворот одна">\n  {оставить[0]}\n</div>\n\n']
for k in range(1, len(оставить), 2):
    a = k + 1
    grp = оставить[k:k + 2]
    cls = "разворот" if len(grp) == 2 else "разворот левая"
    out.append(f'<div class="{cls}">\n\n  ' + "\n\n  ".join(grp) + "\n</div>\n\n")
for p in хвост:
    out.append(f'<div class="разворот одна">\n\n  ' + p + "\n</div>\n\n")
io.open(HTML, "w", encoding="utf-8", newline="\n").write(
    head + "<body>\n\n" + "".join(out) + "</body>\n</html>\n")
