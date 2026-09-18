# -*- coding: utf-8 -*-
"""РАСКЛАДКИ ОДНИМ PDF — страница по размеру картинки, без полей.

Картинки берутся готовыми из `docs/раскладки-new` (их кладёт туда
`СнимокРаскладки` в режиме слияния — теми же слоями, что кнопка «экспорт
слиянием» в конструкторе). Здесь только сборка в один документ.

СТРАНИЦА ПОВТОРЯЕТ ПРОПОРЦИЮ КАРТИНКИ. Раскладки квадратные (1600×1600), и
на книжном A4 они ужимались чуть не вдвое: половина листа уходила в пустоту.
Теперь страница квадратная, картинка занимает её целиком, пропорции не
трогаются. Легенда широкая и низкая — её страница такая же широкая и низкая.

Подпись с именем раскладки печатается поверх картинки в левом верхнем углу:
там у экспорта светлое поле, и надпись читается, не отнимая места у поля.

Запуск: python tools/раскладки-в-pdf.py [папка] [куда.pdf]
"""
import glob
import os
import re
import sys

import pymupdf

БАЗА = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ПАПКА = sys.argv[1] if len(sys.argv) > 1 else os.path.join(БАЗА, "docs", "раскладки-new")
ВЫХОД = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ПАПКА, "Раскладки — слияние.pdf")

# Длинная сторона страницы в пунктах. 720 pt = 254 мм: лист крупный, картинка
# на экране и в печати читается без увеличения.
СТОРОНА = 720.0
ОТСТУП = 18.0                  # где стоит подпись, от края

# ШРИФТ С КИРИЛЛИЦЕЙ. Встроенные шрифты PDF (helv и прочие base-14) кириллицу
# не знают: имя раскладки печаталось точками.
ШРИФТЫ = [
    os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", имя)
    for имя in ("segoeui.ttf", "arial.ttf", "calibri.ttf", "tahoma.ttf")
]


def по_номерам(путь):
    """Сортировка по-человечески: 2 игрока 1, 2, …, 10 — а не 1, 10, 2."""
    имя = os.path.basename(путь)
    числа = [int(n) for n in re.findall(r"\d+", имя)]
    return (числа, имя)


def main():
    картинки = sorted(
        (p for p in glob.glob(os.path.join(ПАПКА, "*.png"))
         if os.path.basename(p) != "легенда.png"
         and not os.path.basename(p).startswith("_")),
        key=по_номерам)
    легенда = os.path.join(ПАПКА, "легенда.png")
    if os.path.exists(легенда):
        картинки.append(легенда)          # легенда последней страницей
    if not картинки:
        print("в папке нет картинок:", ПАПКА)
        return 1

    шрифт = next((ш for ш in ШРИФТЫ if os.path.exists(ш)), None)
    doc = pymupdf.open()
    for путь in картинки:
        с_шириной = pymupdf.Pixmap(путь)
        ш, в = с_шириной.width, с_шириной.height
        доля = СТОРОНА / float(max(ш, в))
        стр = doc.new_page(width=ш * доля, height=в * доля)
        # Картинка занимает страницу целиком: страница сделана по её пропорции,
        # поэтому растяжения не будет.
        стр.insert_image(стр.rect, filename=путь)
        имя = os.path.splitext(os.path.basename(путь))[0]
        if шрифт:
            стр.insert_font(fontname="под", fontfile=шрифт)
        стр.insert_text((ОТСТУП, ОТСТУП + 10), имя, fontsize=11,
                        fontname="под" if шрифт else "helv",
                        color=(0.25, 0.25, 0.25))
    doc.save(ВЫХОД, deflate=True, garbage=4)
    первая = doc[0].rect
    print("страниц: %d, страница %.0f×%.0f pt -> %s (%.1f МБ)"
          % (len(картинки), первая.width, первая.height, ВЫХОД,
             os.path.getsize(ВЫХОД) / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main())
