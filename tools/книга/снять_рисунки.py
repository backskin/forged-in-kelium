# -*- coding: utf-8 -*-
"""Снять готовые рисунки главы из нынешней вёрстки в `_имя.svg`.

Генераторы части рисунков (figs.py, make_fig_*.py) читают экспорт дизайнера
с его диска, которого в облаке нет, — и пересборка главы падала на первом же
`[[рисунке]]`. Рисунки при этом уже стоят в вёрстке. Здесь глава собирается
с метками на месте недостающих рисунков, метки и рисунки вёрстки
сопоставляются по порядку, и каждый недостающий рисунок записывается
`_имя.svg` — дальше reflow.py работает как обычно.

python снять_рисунки.py "<глава.md>"
"""
import glob
import os
import re
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
DIR = glob.glob(os.path.join(КОРЕНЬ, "rules", "Книга правил*"))[0]
HTML = os.path.join(DIR, "вёрстка", "Книга правил.html")
md = os.path.join(DIR, sys.argv[1])
РИС = re.compile(r'<div class="рисунок-(?:в-колонке|во-всю)"')


def div_с(t, i):
    """Весь <div>…</div>, начинающийся в позиции i."""
    глуб = 0
    for m in re.finditer(r"<div\b|</div>", t[i:]):
        глуб += 1 if m.group(0) == "<div" else -1
        if глуб == 0:
            return t[i:i + m.end()]
    raise ValueError("незакрытый div")


def рисунки(t):
    return [div_с(t, m.start()) for m in РИС.finditer(t)]


h = open(HTML, encoding="utf-8").read()
title = re.match(r"# (Глава \d+)\.", open(md, encoding="utf-8").read()).group(1)
ГЛ = re.compile(r'<div class="глава">(?:<span class="гл-н">)?(Глава \d+)[.<]')
стр = [m.start() for m in re.finditer(r'<div class="стр', h)]
начала = [(i, ГЛ.search(h[i:i + 3000])) for i in стр]
start = next(k for k, (i, m) in enumerate(начала) if m and m.group(1) == title)
end = next((k for k in range(start + 1, len(начала)) if начала[k][1]), len(начала))
кусок = h[стр[start]:стр[end] if end < len(стр) else h.index("</body>")]
старые = рисунки(кусок)

tmp = os.path.join(D, "_снять.html")
subprocess.run([sys.executable, os.path.join(D, "md2page.py"), md, "1", tmp], check=True,
               env=dict(os.environ, KNIGA_FIG_MARK="1"))
новые = рисунки(open(tmp, encoding="utf-8").read())
os.remove(tmp)
if len(старые) != len(новые):
    sys.exit("рисунков в вёрстке %d, в главе %d — сопоставить нельзя" % (len(старые), len(новые)))
for стар, нов in zip(старые, новые):
    m = re.search(r'data-нет="([^"]+)"', нов)
    if m:
        open(os.path.join(D, "_" + m.group(1) + ".svg"), "w", encoding="utf-8").write("        " + стар)
        print("снят:", m.group(1))
