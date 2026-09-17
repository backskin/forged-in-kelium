"""Пересобрать главу из md внутри книги и перенумеровать страницы, развороты, оглавление.

python reflow.py <md-файл главы> ["Глава N."]
Страницы главы в вёрстке — от страницы с её заголовком до страницы со следующим заголовком главы.
"""
import glob
import os
import re
import subprocess
import sys
import time

D = os.path.dirname(os.path.abspath(__file__))
DIR = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*")[0]
HTML = os.path.join(DIR, "вёрстка", "Книга правил.html")
md = os.path.join(DIR, sys.argv[1])


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


h = open(HTML, encoding="utf-8").read()
head, body = h.split("<body>", 1)
pages = pages_of(body)

# ХВОСТ КНИГИ — выходные данные и задняя обложка — не участвует в разбивке на
# развороты: это не главы, их собирают свои сборщики (make_credits.py,
# make_back.py), и пересборка главы не имеет права их терять.
хвост = []
while pages and ("обложка задняя" in pages[-1] or "создатели" in pages[-1]):
    хвост.insert(0, pages.pop())

title = re.match(r"# (Глава \d+\.)", open(md, encoding="utf-8").read()).group(1)
ГЛАВА = re.compile(r'<div class="глава">(?:<span class="гл-н">)?(Глава \d+)[.<]')


def глава_на(p):
    m = ГЛАВА.search(p)
    return m.group(1) if m else None


start = next(i for i, p in enumerate(pages) if глава_на(p) == title.rstrip("."))
end = next((i for i in range(start + 1, len(pages)) if глава_на(pages[i])), len(pages))

tmp = os.path.join(D, "_reflow.html")
subprocess.run([sys.executable, os.path.join(D, "md2page.py"), md, str(start + 1), tmp], check=True, capture_output=True)
new = pages_of(open(tmp, encoding="utf-8").read())
pages = pages[:start] + new + pages[end:]

final = []
for idx, p in enumerate(pages, start=1):
    p = re.sub(r'<div class="колонцифра"><span>\d+</span></div>', f'<div class="колонцифра"><span>{idx}</span></div>', p)
    p = re.sub(r'<div class="стр фон\d', f'<div class="стр фон{(idx % 4) + 1}', p, count=1) if 'обложка' not in p and 'подготовка' not in p else p
    final.append(p)
for k, p in enumerate(хвост):
    if "создатели" in p:
        хвост[k] = re.sub(r'<div class="колонцифра"><span>\d+</span></div>',
                          f'<div class="колонцифра"><span>{len(final) + 1}</span></div>', p)
setup = [i for i, p in enumerate(final, start=1) if "левая-подг" in p]
assert setup and setup[0] % 2 == 0, f"разворот подготовки начинается на нечётной стр. {setup}"

items = []
for idx, p in enumerate(final, start=1):
    for m in re.finditer(r'<div class="глава">(?:<span class="гл-н">)?Глава (\d+)(?:</span><span class="гл-т">|\. )([^<]+)</(?:span></div|div)>', p):
        items.append((int(m.group(1)), m.group(2), idx))
# СЛОВАРЬ В КНИГЕ ПРАВИЛ НЕ БУДЕТ (решение дизайнера 14.09.2026): он уходит
# в справочник целиком, и обещать его в оглавлении книги больше нечем.
later = []
done = {n for n, _, _ in items}
rows = "".join(f'<li><span class="н">{n}</span><span class="т">{t}</span><span class="с">{s}</span></li>' for n, t, s in items)
rows += "".join(f'<li class="будет"><span class="н">{n}</span><span class="т">{t}</span><span class="с">—</span></li>'
                for n, t in later if n not in done)
toc_i = next(i for i, p in enumerate(final) if '<div class="глава">Содержание</div>' in p)
final[toc_i] = re.sub(r'<ol class="оглавление">.*?</ol>', f'<ol class="оглавление">{rows}</ol>', final[toc_i], flags=re.S)

out = [f'<!-- ============ ОБЛОЖКА ============ -->\n<div class="разворот одна">\n  {final[0]}\n</div>\n\n']
for k in range(1, len(final), 2):
    a = k + 1
    grp = final[k:k + 2]
    cls = "разворот" if len(grp) == 2 else "разворот левая"
    out.append(f'<!-- ============ РАЗВОРОТ {a}–{a + 1} ============ -->\n'
               f'<p class="подпись-разворота">Разворот · страницы {a}–{a + 1}</p>\n'
               f'<div class="{cls}">\n\n  ' + "\n\n  ".join(grp) + "\n</div>\n\n")
for p in хвост:
    подпись = "Создатели игры" if "создатели" in p else "Задняя сторона обложки · памятка"
    out.append(f'<p class="подпись-разворота">{подпись}</p>\n'
               f'<div class="разворот одна">\n\n  ' + p + "\n</div>\n\n")
# ЗАПИСЬ С ПОВТОРОМ: файл вёрстки иногда занят (его держит просмотрщик),
# и Windows отвечает OSError 22 — из-за этого пересборка главы срывалась.
готово = head + "<body>\n\n" + "".join(out) + "</body>\n</html>\n"
for попытка in range(12):
    try:
        open(HTML, "w", encoding="utf-8").write(готово)
        break
    except OSError:
        if попытка == 11:
            raise
        time.sleep(0.4)
# ВЫНОС КАРТИНОК — СРАЗУ ЗА ЗАПИСЬЮ. md2page вставляет свежие рисунки и
# значки как `data:image/...;base64`, и без этого шага вёрстка снова пухнет
# до десятков мегабайт, а гит хранит ещё одну её копию целиком. Шаг
# идемпотентный: уже вынесенное он не трогает.
subprocess.run([sys.executable, os.path.join(D, "вынести_картинки.py")],
               check=True, capture_output=True)
print("страниц:", len(final) + len(хвост), "глава", title, "стр.", start + 1, "–", start + len(new))
