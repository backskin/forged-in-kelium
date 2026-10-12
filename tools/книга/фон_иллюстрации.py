# -*- coding: utf-8 -*-
"""Иллюстрации под полосами книги правил (дизайнер 14.10.2026).

У каждой внутренней полосы своя картинка из «Иллюстрации/фоны и сцены».
Она лежит под мозаикой и блоками: у краёв листа — в полный цвет, к полю
текста почти пропадает (маска из двух градиентов — по горизонтали и по
вертикали, сложенных вместе, получается «рамка»). На первой полосе главы
картинка видна в полный цвет и сверху, под заголовком главы — как в книге
правил «Загадочных существ».

Картинки копируются в «вёрстка/илл» уменьшенными, имя — по хешу. Запуск
после restyle.py (так делает собрать_книгу.py): python фон_иллюстрации.py
"""
import glob
import hashlib
import io
import os
import re

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "Книга правил*", "вёрстка", "Книга правил.html"))[0]
ИЛЛ = os.path.join(os.path.dirname(КНИГА), "илл")
ПАПКА = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Иллюстрации", "фоны и сцены")

# РАЗВОРОТ → номер картинки в папке (по алфавиту). Одна картинка на разворот,
# растянутая на обе полосы: левая показывает её левую половину, правая —
# правую, поэтому цвета соседних страниц всегда совпадают (дизайнер 14.10.2026).
# Левая полоса разворота — чётная: (4,5), (6,7)…
РАЗВОРОТЫ = {4: 38, 6: 45, 8: 21, 10: 5, 12: 76, 14: 32, 16: 40, 18: 43, 20: 98, 22: 99,
             24: 103, 26: 53, 28: 17, 30: 16, 32: 36, 34: 84, 36: 100, 38: 75}

CSS = """
  /* ---- иллюстрации под полосами (фон_иллюстрации.py) ----
     Картинка лежит на всей полосе и видна в полный цвет за рамкой, у краёв
     листа. Внутри рамки на неё ложится вуаль цвета бумаги с мягким краем по
     линии рамки: картинка еле проглядывает за текстом. На первой полосе главы
     вуаль начинается ниже шапки — под заголовком главы картинка яркая. */
  .илл-фон { position: absolute; inset: 0; z-index: -3; pointer-events: none;
    background-repeat: no-repeat; background-size: 200% auto; }
  .илл-фон.лев { background-position: 0% 50%; }
  .илл-фон.прав { background-position: 100% 50%; }
  .илл-вуаль { position: absolute; inset: 7.5mm; z-index: -2; pointer-events: none;
    background: rgba(244,238,220,.8); box-shadow: 0 0 3mm 1.6mm rgba(244,238,220,.8); }
  .илл-вуаль.шапка { top: 46mm; box-shadow: 0 0 6mm 3mm rgba(244,238,220,.8); }
  .стр:has(.илл-вуаль.шапка) .глава .гл-т { background: rgba(247,241,225,.88); padding: .6mm 2.4mm .4mm;
    box-shadow: 0 0 3mm 1.5mm rgba(247,241,225,.88); }
  /* вводный абзац главы — на светлой подложке, чтобы читался на картинке */
  .стр:has(.илл-вуаль.шапка) .вводка { background: rgba(247,241,225,.9); padding: 2mm 3mm; margin-bottom: 4mm;
    outline: .18mm solid var(--кант); outline-offset: -.18mm; }
  .стр:has(.илл-вуаль.шапка) .с-заглавной > .блок { background: rgba(247,241,225,.92); }
"""


def файл(номер):
    return os.path.join(ПАПКА, sorted(x for x in os.listdir(ПАПКА) if x.lower().endswith(".jpg"))[номер])


def в_илл(путь):
    данные = open(путь, "rb").read()
    имя = hashlib.sha1(данные).hexdigest()[:16] + ".jpg"
    куда = os.path.join(ИЛЛ, имя)
    if not os.path.exists(куда):
        im = Image.open(io.BytesIO(данные)).convert("RGB")
        im.thumbnail((2600, 2600), Image.LANCZOS)
        im.save(куда, "JPEG", quality=84, optimize=True)
    return "илл/" + имя


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    t = re.sub(r'<div class="илл-фон[^"]*" style="[^"]*"></div>(<div class="илл-вуаль[^"]*"></div>)?', "", t)
    a = t.find("  /* ---- иллюстрации под полосами")
    if a >= 0:
        b = t.index("</style>", a)
        t = t[:a] + t[b:]
    t = t.replace("</style>", CSS + "</style>", 1)
    части, n, поз, кадр = [], 0, 0, {}
    for m in re.finditer(r'<div class="стр( [^"]*)?">', t):
        n += 1
        левая = n % 2 == 0
        левый = n if левая else n - 1
        if левый not in РАЗВОРОТЫ or "обложка" in (m.group(1) or ""):
            continue
        if левый not in кадр:
            кадр[левый] = в_илл(файл(РАЗВОРОТЫ[левый]))
        конец = t.find('<div class="стр', m.end())
        глава = '<div class="глава"' in t[m.end():конец if конец > 0 else len(t)]
        вставка = ('<div class="илл-фон %s" style="background-image:url(&quot;%s&quot;)"></div>'
                   '<div class="илл-вуаль%s"></div>') % ("лев" if левая else "прав", кадр[левый],
                                                         " шапка" if глава else "")
        части.append(t[поз:m.end()] + вставка)
        поз = m.end()
    t = "".join(части) + t[поз:]
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("иллюстрации под полосами:", len(части))


if __name__ == "__main__":
    main()
