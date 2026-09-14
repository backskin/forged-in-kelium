"""Главы из md -> страницы -> развороты по сквозной нумерации.

python assemble.py <первая страница> <глава.md> [<глава.md> ...]
Пишет фрагмент chapters.html для build.py.
"""
import glob
import os
import re
import subprocess
import sys

D = os.path.dirname(os.path.abspath(__file__))
DIR = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*")[0]
first = int(sys.argv[1])
names = sys.argv[2:]

pages = []          # (номер, html, название главы)
n = first
for name in names:
    src = glob.glob(os.path.join(DIR, name))[0]
    tmp = os.path.join(D, "_one.html")
    subprocess.run([sys.executable, os.path.join(D, "md2page.py"), src, str(n), tmp], check=True,
                   capture_output=True)
    frag = open(tmp, encoding="utf-8").read()
    title = re.search(r"· (Глава [^<]+)</p>", frag).group(1)
    for m in re.finditer(r'  <div class="стр .*?\n    <div class="колонцифра"><span>(\d+)</span></div>\n  </div>\n', frag, re.S):
        pages.append((int(m.group(1)), m.group(0), title))
        n += 1

out = []
i = 0
# если первая страница нечётная — она правая, начинает разворот одна
if pages and pages[0][0] % 2 == 1:
    groups = [pages[:1]] + [pages[k:k + 2] for k in range(1, len(pages), 2)]
else:
    groups = [pages[k:k + 2] for k in range(0, len(pages), 2)]
for g in groups:
    a, b = g[0][0], g[0][0] + 1
    titles = " и ".join(dict.fromkeys(p[2] for p in g))
    cls = "разворот" if len(g) == 2 else ("разворот одна" if g[0][0] % 2 else "разворот левая")
    out.append(f"<!-- ============ РАЗВОРОТ {a}–{b} · {titles.upper()} ============ -->\n"
               f'<p class="подпись-разворота">Разворот · страницы {a}–{b} · {titles}</p>\n'
               f'<div class="{cls}">\n\n' + "\n".join(p[1] for p in g) + "</div>\n\n")
open(os.path.join(D, "chapters.html"), "w", encoding="utf-8").write("".join(out))
print("страниц:", len(pages), [p[0] for p in pages])
