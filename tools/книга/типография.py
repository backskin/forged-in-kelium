# -*- coding: utf-8 -*-
"""Типографская версия книги: вылеты 5 мм, белое поле для меток, метки реза.

Каждая полоса печатается на листе 244×244 мм: чистый формат 220 мм посередине,
вокруг него вылет 5 мм (фон и картинки доведены до края вылета) и ещё 7 мм
белого поля сверх вылета с метками реза и подписью полосы. В PDF выставлены
TrimBox и BleedBox, поэтому типографская программа сама видит и формат, и вылет.

Как это устроено. Вёрстка полосы не трогается: она лежит в листе как есть на
расстоянии 12 мм от края. Весь фон полосы — бумагу, мозаику, картинку, вуаль,
картинку обложки — рисует «подложка»: пустая полоса того же класса, растянутая
до 230×230 мм. У самой полосы фона нет, поэтому на линии реза нет шва.

python типография.py [вёрстка.html] [выход.pdf]
(по умолчанию — книга правил; справочник: передать его html и имя pdf)
"""
import glob
import os
import re
import subprocess
import sys

import pymupdf

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
ВЫЛЕТ, ПОЛЕ, ФОРМАТ = 5, 12, 220            # мм: вылет, поле от обреза до края листа, чистый формат
ЛИСТ = ФОРМАТ + 2 * ПОЛЕ


def полосы(s):
    """Страницы верхнего уровня `<div class="стр …">…</div>`: (начало, конец)."""
    out, i = [], 0
    while True:
        k = s.find('<div class="стр', i)
        if k < 0:
            return out
        глубина = 0
        for m in re.finditer(r"<div\b|</div>", s[k:]):
            глубина += 1 if m.group(0) == "<div" else -1
            if глубина == 0:
                j = k + m.end()
                break
        out.append((k, j))
        i = j


def метки(n):
    """Метки реза в четырёх углах: короткие линии за границей вылета."""
    mm = lambda v: "%.3f" % v                # единицы viewBox — миллиметры
    линии = []
    a, b = ПОЛЕ - ВЫЛЕТ - 1.0, ПОЛЕ - ВЫЛЕТ - 6.0          # отрезок метки лежит вне вылета
    for x in (ПОЛЕ, ПОЛЕ + ФОРМАТ):
        for (y0, y1) in ((a, b), (ЛИСТ - a, ЛИСТ - b)):
            линии.append((x, y0, x, y1))
    for y in (ПОЛЕ, ПОЛЕ + ФОРМАТ):
        for (x0, x1) in ((a, b), (ЛИСТ - a, ЛИСТ - b)):
            линии.append((x0, y, x1, y))
    svg = "".join('<line x1="%s" y1="%s" x2="%s" y2="%s"/>' % tuple(mm(v) for v in л) for л in линии)
    # чёрточки границы вылета — короткие, по центрам сторон
    c = ЛИСТ / 2
    вл = ПОЛЕ - ВЫЛЕТ
    svg += "".join('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="#00AEEF"/>' % tuple(mm(v) for v in л) for л in (
        (c - 3, вл, c + 3, вл), (c - 3, ЛИСТ - вл, c + 3, ЛИСТ - вл),
        (вл, c - 3, вл, c + 3), (ЛИСТ - вл, c - 3, ЛИСТ - вл, c + 3)))
    return ('<svg class="метки" viewBox="0 0 %d %d" xmlns="http://www.w3.org/2000/svg" style="width:%dmm;height:%dmm">'
            '<g stroke="#000" stroke-width=".25" fill="none">%s</g></svg>') % (ЛИСТ, ЛИСТ, ЛИСТ, ЛИСТ, svg)


CSS = """
  @media print {
    html, body { background: #fff !important; }
    .разворот, .разворот.одна { display: block; width: auto; margin: 0; }
    .подпись-разворота { display: none; }
    @page { size: %(л)dmm %(л)dmm; margin: 0; }
    .лист-тип { position: relative; width: %(л)dmm; height: %(л)dmm; overflow: hidden; background: #fff;
      break-after: page; break-inside: avoid; }
    /* ВЫЛЕТ — РАСТЯНУТЫЙ ФОН. Подложка — пустая полоса того же класса размером
       230 мм: на ней весь фон полосы (бумага, мозаика, картинка, вуаль, картинка
       обложки), растянутый до края вылета. У настоящей полосы поверх неё фона
       нет совсем — только вёрстка, поэтому на линии реза нет шва. */
    .лист-тип > .стр.подложка { position: absolute; left: %(п1)dmm; top: %(п1)dmm; width: %(в)dmm; height: %(в)dmm;
      box-shadow: none; padding: 0; margin: 0; break-after: auto; overflow: hidden; }
    .лист-тип > .стр.подложка > * { display: none; }
    .лист-тип > .стр.подложка > .илл-фон, .лист-тип > .стр.подложка > .илл-вуаль,
    .лист-тип > .стр.подложка > img { display: block; }
    .лист-тип > .стр.подложка > img { position: absolute; inset: 0; width: 100%%; height: 100%%; object-fit: cover; }
    /* вуаль — на своём месте полосы (+вылет), у корешка — до края вылета */
    .лист-тип > .стр.подложка > .илл-вуаль.лев { inset: %(вп)smm 0 %(вп)smm %(вп)smm; }
    .лист-тип > .стр.подложка > .илл-вуаль.прав { inset: %(вп)smm %(вп)smm %(вп)smm 0; }
    .лист-тип > .стр:not(.подложка) { position: absolute; left: %(п)dmm; top: %(п)dmm; margin: 0; break-after: auto;
      background: none !important; box-shadow: none; }
    .лист-тип > .стр:not(.подложка)::after { display: none !important; }
    .лист-тип > .стр:not(.подложка) > .илл-фон, .лист-тип > .стр:not(.подложка) > .илл-вуаль,
    .лист-тип > .стр:not(.подложка) > img { visibility: hidden; }
    /* РАМКА ПО СТОРОНЕ РАЗВОРОТА. В книге её задаёт место полосы в развороте;
       на отдельном листе — явный признак: у левой полосы открыт правый край
       (корешок), у правой — левый. */
    .лист-тип.лев-стр .кайма .к-прав, .лист-тип.лев-стр .кайма .к-хвл { display: none !important; }
    .лист-тип.лев-стр .кайма .к-лев, .лист-тип.лев-стр .кайма .к-хвп { display: inline !important; }
    .лист-тип.прав-стр .кайма .к-лев, .лист-тип.прав-стр .кайма .к-хвп { display: none !important; }
    .лист-тип.прав-стр .кайма .к-прав, .лист-тип.прав-стр .кайма .к-хвл { display: inline !important; }
    .лист-тип .метки { position: absolute; left: 0; top: 0; z-index: 50; }
    .лист-тип .подпись-листа { position: absolute; z-index: 50; left: 40mm; top: 3.2mm;
      font: 400 6.5pt/1 "Tektur Narrow", sans-serif; color: #000; letter-spacing: .02em; }
  }
"""


def главное(html, pdf):
    t = open(html, encoding="utf-8").read()
    илл = os.path.join(os.path.dirname(html), "илл").replace("\\", "/")
    t = t.replace('"илл/', '"file:///' + илл + '/').replace("'илл/", "'file:///" + илл + "/") \
         .replace("&quot;илл/", "&quot;file:///" + илл + "/")
    куски, поз = [], 0
    for n, (a, b) in enumerate(полосы(t), 1):
        страница = t[a:b]
        классы = re.match(r'<div class="(стр[^"]*)"', страница).group(1)
        подложка = '<div class="%s подложка">' % классы
        # картинки подложки: фон-иллюстрация и картинка обложки
        for m in re.finditer(r'<div class="илл-(?:фон|вуаль)[^"]*"[^>]*></div>', страница):
            подложка += m.group(0)
        m = re.search(r'<img [^>]*>', страница) if "обложка" in классы else None
        if m:
            подложка += m.group(0)
        подложка += "</div>"
        обёртка = ('<div class="лист-тип %s">' % ("лев-стр" if n % 2 == 0 else "прав-стр") + подложка + страница + метки(n)
                   + '<div class="подпись-листа">%s · полоса %d · формат %d мм · вылет %d мм</div></div>'
                   % (os.path.basename(html).rsplit(".", 1)[0], n, ФОРМАТ, ВЫЛЕТ))
        куски.append(t[поз:a] + обёртка)
        поз = b
    t = "".join(куски) + t[поз:]
    css = CSS % dict(л=ЛИСТ, п=ПОЛЕ, п1=ПОЛЕ - ВЫЛЕТ, в=ФОРМАТ + 2 * ВЫЛЕТ, вп=5 + ВЫЛЕТ)
    t = t.replace("</style>", css + "</style>", 1)
    врем = os.path.join(D, "_типография.html")
    open(врем, "w", encoding="utf-8").write(t)
    if os.path.exists(pdf):
        os.remove(pdf)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    "--user-data-dir=" + os.path.join(D, "chr"), "--virtual-time-budget=15000",
                    "--print-to-pdf=" + pdf, "file:///" + врем.replace("\\", "/")], check=True)
    # TrimBox / BleedBox
    doc = pymupdf.open(pdf)
    pt = 72 / 25.4
    for стр in doc:
        w, h = стр.rect.width, стр.rect.height
        стр.set_trimbox(pymupdf.Rect(ПОЛЕ * pt, ПОЛЕ * pt, w - ПОЛЕ * pt, h - ПОЛЕ * pt))
        стр.set_bleedbox(pymupdf.Rect((ПОЛЕ - ВЫЛЕТ) * pt, (ПОЛЕ - ВЫЛЕТ) * pt,
                                      w - (ПОЛЕ - ВЫЛЕТ) * pt, h - (ПОЛЕ - ВЫЛЕТ) * pt))
    doc.save(pdf + ".tmp", garbage=3, deflate=True)
    doc.close()
    os.replace(pdf + ".tmp", pdf)
    print("типография:", pdf, "полос:", len(pymupdf.open(pdf)))


if __name__ == "__main__":
    КНИГА = glob.glob(os.path.join(КОРЕНЬ, "rules", "Книга правил*", "вёрстка", "Книга правил.html"))[0]
    html = sys.argv[1] if len(sys.argv) > 1 else КНИГА
    выход = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(КНИГА), "Книга правил — типография.pdf")
    главное(html, выход)
