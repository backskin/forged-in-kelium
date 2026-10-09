# -*- coding: utf-8 -*-
"""Рисунок главы 10: модули боя и сборки + жетоны хранилища в одном ряду
(дизайнер 04.10.2026: «перетащить жетоны хранилища на ту же строку, чуть
меньше, двумя под-рядами: лицо → оборот»).

Берёт готовый рисунок `_модули.svg` (с выносками А–Ж) и дописывает справа
два под-ряда жетонов хранилища: ячейка и энергия, обычная → золотая сторона.
Выноски остаются на месте: левая часть картинки и её масштаб не меняются,
вьюбокс расширяется на ту же долю, что и картинка.

Картинки жетонов — из `rules/жетоны-модулей/` (экспорт дизайнера):
ячейка.png, ячейка-золото.png, энергия.png, энергия-золото.png. Пока их нет,
берутся старые текстуры, а золотая сторона рисуется временной подменой
(золотая рамка и звезда) — до экспорта дизайнера.
"""
import base64
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont, ImageOps

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ЖЕТОНЫ = os.path.join(КОРЕНЬ, "rules", "жетоны модулей")
# экспорт дизайнера 05.10.2026: номер файла → сторона
ФАЙЛЫ = {
    "энергия": "Жетон улучшения планшета хранилища-1.png",
    "энергия-золото": "Жетон улучшения планшета хранилища-2.png",
    "ячейка": "Жетон улучшения планшета хранилища-3.png",
    "ячейка-золото": "Жетон улучшения планшета хранилища-4.png",
}
# модуль сборки на рисунке: «2 боеприпаса / 2 войска ↑» и его золотая сторона
СБОРКА = ("Жетоны модулей сборки-1.png", "Жетоны модулей сборки-5.png")
ТЕКСТУРЫ = os.path.join(КОРЕНЬ, "data", "textures", "module")
ИКОНКИ = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт")
ШРИФТ = next(п for п in (r"C:\Windows\Fonts\TekturNarrow-Bold.ttf",
                         os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf")) if os.path.exists(п))
ВЁРСТКА = os.path.join(КОРЕНЬ, "rules", "Книга правил — черновик", "вёрстка")


def позолотить(im):
    """Временная золотая сторона: серую рамку — в золото, звезда в углу."""
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    кант = round(w * 0.09)
    for y in range(h):
        for x in range(w):
            if x < кант or y < кант or x >= w - кант or y >= h - кант:
                r, g, b, a = px[x, y]
                if a and max(r, g, b) - min(r, g, b) < 40:
                    v = (r + g + b) / 3 / 255
                    px[x, y] = (int(250 * v + 5), int(200 * v), int(40 * v), a)
    звезда = Image.open(os.path.join(ИКОНКИ, "по.png")).convert("RGBA")
    звезда = звезда.resize((round(w * 0.38), round(w * 0.38)), Image.LANCZOS)
    im.alpha_composite(звезда, (w - звезда.width - кант // 2, кант // 2))
    return im


def жетон(имя, запасной, золото=False):
    п = os.path.join(ЖЕТОНЫ, ФАЙЛЫ.get(имя, имя + ".png"))
    if os.path.exists(п):
        return Image.open(п).convert("RGBA")
    im = Image.open(os.path.join(ТЕКСТУРЫ, запасной)).convert("RGBA")
    return позолотить(im) if золото else im


# исходный рисунок модулей: картинка и выноски
svg = open(os.path.join(D, "_модули.svg"), encoding="utf-8").read()
src = re.search(r'<img src="([^"]+)"', svg).group(1)
# основа — картинка модулей, которую пишет make_fig.py (модули боя и сборки
# там уже из экспорта дизайнера)
база = Image.open(os.path.join(D, "_модули.png")).convert("RGBA")
vb_w, vb_h = [float(x) for x in re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', svg).groups()]
css_w = float(re.search(r'class="рис" style="width:([\d.]+)%"', svg).group(1))
W0, H0 = база.size

Т = round(H0 * 0.30)                    # сторона жетона хранилища
стрелка_w, поле = round(Т * 0.55), round(Т * 0.25)
шр = ImageFont.truetype(ШРИФТ, round(Т * 0.2))
подпись_w = round(Т * 0.95)
доп = поле + подпись_w + Т + стрелка_w + Т + поле
холст = Image.new("RGBA", (W0 + доп, H0), (0, 0, 0, 0))
холст.alpha_composite(база, (0, 0))
d = ImageDraw.Draw(холст)
ЗЕЛ = (24, 108, 36, 255)
ряды = [("ячейка", "mod_store_cells.png"), ("энергия", "mod_store_energy.png")]
верх = (H0 - (2 * Т + поле)) // 2
for k, (имя, запасной) in enumerate(ряды):
    y = верх + k * (Т + поле)
    x = W0 + поле
    d.text((x + подпись_w - 20, y + Т // 2), имя, font=шр, fill=(107, 68, 19, 255), anchor="rm")
    x += подпись_w
    лицо = жетон(имя, запасной).resize((Т, Т), Image.LANCZOS)
    золото = жетон(имя + "-золото", запасной, True).resize((Т, Т), Image.LANCZOS)
    холст.alpha_composite(лицо, (x, y))
    ax = x + Т + round(стрелка_w * 0.15)
    ay = y + Т // 2
    d.line((ax, ay, ax + round(стрелка_w * 0.55), ay), fill=ЗЕЛ, width=max(4, Т // 28))
    d.polygon([(ax + round(стрелка_w * 0.5), ay - Т // 12), (ax + round(стрелка_w * 0.72), ay),
               (ax + round(стрелка_w * 0.5), ay + Т // 12)], fill=ЗЕЛ)
    холст.alpha_composite(золото, (x + Т + стрелка_w, y))

буфер = io.BytesIO()
холст.save(буфер, "PNG", optimize=True)
доля = холст.width / W0
нов = svg.replace(src, "data:image/png;base64," + base64.b64encode(буфер.getvalue()).decode())
нов = re.sub(r'viewBox="0 0 [\d.]+ ([\d.]+)"', 'viewBox="0 0 %.1f \\1"' % (vb_w * доля), нов, 1)
нов = нов.replace('class="рис" style="width:%s%%"' % re.search(r'class="рис" style="width:([\d.]+)%"', svg).group(1),
                  'class="рис" style="width:%.1f%%"' % min(100.0, css_w * доля), 1)
open(os.path.join(D, "_модули-и-хранилище.svg"), "w", encoding="utf-8").write(нов)
print("ok", холст.size, "ширина %.1f%%" % min(100.0, css_w * доля))
