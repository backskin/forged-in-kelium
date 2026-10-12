# -*- coding: utf-8 -*-
"""ОБЛОЖКИ КНИГИ ПРАВИЛ И СПРАВОЧНИКА — LEX TALIONIS (дизайнер 12.10.2026).

Чистый арт дизайнера с Диска («Обложка/обложка рулбука.png» — книга правил,
«обложка коробки лицо.png» — справочник) режется в квадрат, сверху — логотип
с плашкой («design-docs/логотип-лекс/LEX TALIONIS — логотип (рамка).png»),
внизу — полоса с названием книги, как на прежних обложках.

    python tools/обложка/собрать_обложки.py
→ вёрстка книги правил: обложка заменяется; справочник:
  tools/книга/обложки-справочника/справочник-обложка.jpg
"""
import glob
import os
import re

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ДИСК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium", "Обложка")
ЛОГО = os.path.join(ROOT, "design-docs", "логотип-лекс", "LEX TALIONIS — логотип (рамка).png")
ШРИФТ = r"C:\Windows\Fonts\TekturNarrow-Bold.ttf"
S = 2200


def квадрат(арт, сдвиг):
    """Квадрат по высоте; сдвиг — доля свободной ширины слева (0 — левый край)."""
    w, h = арт.size
    k = min(w, h)
    x = round((w - k) * сдвиг)
    y = round((h - k) / 2)
    return арт.crop((x, y, x + k, y + k)).resize((S, S), Image.LANCZOS)


def собрать(арт_файл, сдвиг, подпись, куда):
    фон = квадрат(Image.open(os.path.join(ДИСК, арт_файл)).convert("RGB"), сдвиг).convert("RGBA")
    # логотип: тень под плашкой, чтобы она не сливалась с небом
    лого = Image.open(ЛОГО).convert("RGBA")
    ш = round(S * 0.80)
    лого = лого.resize((ш, round(лого.height * ш / лого.width)), Image.LANCZOS)
    x, y = (S - ш) // 2, round(S * 0.05)
    тень = Image.new("RGBA", фон.size, (0, 0, 0, 0))
    тень.paste((0, 0, 0, 150), (x + 18, y + 24), лого.getchannel("A"))
    фон.alpha_composite(тень.filter(ImageFilter.GaussianBlur(22)))
    фон.alpha_composite(лого, (x, y))
    # полоса с названием книги — как на прежних обложках
    d = ImageDraw.Draw(фон, "RGBA")
    в, н = round(S * 0.77), round(S * 0.90)
    d.rectangle((0, в, S, н), fill=(20, 30, 22, 165))
    d.line((0, в, S, в), fill=(120, 200, 140, 200), width=6)
    d.line((0, н, S, н), fill=(120, 200, 140, 200), width=6)
    ф = ImageFont.truetype(ШРИФТ, round(S * 0.075))
    d.text((S / 2 + 6, (в + н) / 2 + 8), подпись, font=ф, fill=(0, 0, 0, 160), anchor="mm")
    d.text((S / 2, (в + н) / 2), подпись, font=ф, fill=(235, 240, 230, 255), anchor="mm")
    фон.convert("RGB").save(куда, quality=92)
    print("ok", куда)


КНИГА = os.path.join(ROOT, "rules", "Книга правил — черновик", "вёрстка")
вывод = os.path.join(ROOT, "design-docs", "обложка")
os.makedirs(вывод, exist_ok=True)
собрать("обложка рулбука.png", 0.15, "Правила игры", os.path.join(вывод, "обложка книги правил — LEX TALIONIS.jpg"))
собрать("обложка коробки лицо.png", 0.5, "Справочник",
        os.path.join(ROOT, "tools", "книга", "обложки-справочника", "справочник-обложка.jpg"))

# в вёрстку книги правил: обложка — первая картинка полосы «обложка»
html = os.path.join(КНИГА, "Книга правил.html")
t = open(html, encoding="utf-8").read()
m = re.search(r'(<div class="стр обложка"><div class="рамка-техно"></div><img src=")(илл/[^"]+)(")', t)
# имя — хеш содержимого, как у всех картинок вёрстки (вынести_картинки.py
# удаляет из илл/ всё, что названо иначе)
import hashlib
данные = open(os.path.join(вывод, "обложка книги правил — LEX TALIONIS.jpg"), "rb").read()
файл = "илл/%s.jpg" % hashlib.sha1(данные).hexdigest()[:16]
open(os.path.join(КНИГА, файл), "wb").write(данные)
t = t[:m.start(2)] + файл + t[m.end(2):]
open(html, "w", encoding="utf-8").write(t)
print("ok вёрстка")
