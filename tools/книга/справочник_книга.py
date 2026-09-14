# -*- coding: utf-8 -*-
"""СПРАВОЧНИК — вторая книга, собранная тем же конвейером, что и правила.

Прежний справочник верстался через Word и печатался таблицами кеглем в шесть
пунктов: посреди партии в них не попасть глазом. Здесь он собирается в ту же
вёрстку, что книга правил, — 220×220 мм, кайма, плашки, палитра келемия, — и
тем же способом: содержимое берётся ИЗ ДАННЫХ ИГРЫ (`tools/справочник.py`), а
не переписывается руками, поэтому разойтись с колодами ему нечем.

Что внутри:

* карты всех колод — карточками, а не строками мелкой таблицы;
* иконки — настоящими картинками из `rules/иконки`;
* раскладки поля — картинками, нарисованными движком;
* словарь игры (он ушёл сюда из книги правил, решение дизайнера 14.09.2026).

Запуск: python tools/книга/справочник_книга.py
"""
import base64
import glob
import io
import os
import re
import sys

КОРЕНЬ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                      "..", ".."))
sys.path.insert(0, os.path.join(КОРЕНЬ, "tools"))
os.chdir(КОРЕНЬ)

import справочник as данные          # noqa: E402  (после chdir — он читает data/)

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(КОРЕНЬ, "rules", "Книга правил*", "вёрстка",
                               "Книга правил.html"))[0]
ВЫХОД = os.path.join(КОРЕНЬ, "rules", "Справочник — черновик", "вёрстка",
                     "Справочник.html")
ИКОНКИ = os.path.join(КОРЕНЬ, "rules", "иконки")
КАЙМА = io.open(os.path.join(D, "_кайма.svg"), encoding="utf-8").read().strip()


# ----------------------------------------------------------------- картинки

def b64(путь, ширина=None, формат="PNG"):
    from PIL import Image
    im = Image.open(путь)
    if ширина and im.width > ширина:
        im = im.resize((ширина, round(im.height * ширина / im.width)),
                       Image.LANCZOS)
    buf = io.BytesIO()
    if формат == "JPEG":
        im.convert("RGB").save(buf, "JPEG", quality=84, optimize=True)
        тип = "jpeg"
    else:
        im.convert("RGBA").save(buf, "PNG", optimize=True)
        тип = "png"
    return f"data:image/{тип};base64," + base64.b64encode(buf.getvalue()).decode()


def иконка(имя, класс="ик"):
    путь = os.path.join(ИКОНКИ, имя + ".png")
    if not os.path.exists(путь):
        return f'<span class="ик-нет">{имя}</span>'
    return f'<img class="{класс}" src="{b64(путь, 200)}" alt="">'


# ----------------------------------------------------------------- страницы

class Книга:
    """Складывает страницы и печатает их разворотами, как книга правил."""

    def __init__(self):
        self.страницы = []
        self.главы = []          # (имя, номер страницы)

    def стр(self, содержимое, класс="", глава=None, колонцифра=True):
        n = len(self.страницы) + 1
        if глава:
            self.главы.append((глава, n))
        фон = "фон%d" % ((n % 4) + 1)
        цифра = (f'    <div class="колонцифра"><span>{n}</span></div>\n'
                 if колонцифра else "")
        self.страницы.append(
            f'  <div class="стр {фон} {класс}">\n    {КАЙМА}\n'
            f'{содержимое}\n{цифра}  </div>\n')
        return n

    def html(self, стиль):
        out = []
        for k in range(0, len(self.страницы), 2):
            пара = self.страницы[k:k + 2]
            cls = "разворот" if len(пара) == 2 else "разворот левая"
            a = k + 1
            out.append(f'<p class="подпись-разворота">Разворот · страницы '
                       f'{a}–{a + 1}</p>\n<div class="{cls}">\n\n'
                       + "\n\n".join(пара) + "</div>\n\n")
        return ("<!doctype html>\n<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\">\n"
                "<title>Келемий. Кристалл раздора — справочник</title>\n"
                f"<style>{стиль}{ДОБАВКА}</style>\n</head>\n<body>\n\n"
                + "".join(out) + "</body>\n</html>\n")


ДОБАВКА = r"""
  /* ---- справочник: карточки колод, иконки, раскладки ---- */
  .прил { display: flex; align-items: center; gap: 3mm; margin: 0 -2.4mm 3mm;
    padding: 1.4mm 3mm 1.4mm 0; background: var(--подложка);
    outline: .18mm solid var(--кант); outline-offset: -.18mm;
    clip-path: polygon(2.8mm 0, 100% 0, 100% calc(100% - 2.8mm), calc(100% - 2.8mm) 100%, 0 100%, 0 2.8mm); }
  .прил .бк { flex: none; align-self: stretch; display: flex; align-items: center;
    background: var(--охра); color: var(--страница); font: 800 13pt/1 "Tektur", sans-serif;
    padding: 1.8mm 4.4mm 1.6mm 3.4mm; clip-path: polygon(0 0, 100% 0, calc(100% - 2.6mm) 100%, 0 100%); }
  .прил .им { font: 700 15pt/1.15 "Tektur", sans-serif; color: var(--охра); }
  .прил::after { content: ""; flex: 1; height: .5mm; background: var(--келемий); }

  .карточки { column-count: 2; column-gap: 5mm; }
  .кк { break-inside: avoid; background: var(--подложка); outline: .18mm solid var(--кант);
    outline-offset: -.18mm; padding: 1.5mm 2.2mm 1.4mm; margin: 0 0 2mm;
    clip-path: polygon(1.8mm 0, 100% 0, 100% calc(100% - 1.8mm), calc(100% - 1.8mm) 100%, 0 100%, 0 1.8mm); }
  .кк .имя { font: 700 9.6pt/1.15 "Tektur", sans-serif; color: var(--охра); margin-bottom: .7mm;
    display: flex; align-items: baseline; gap: 1.8mm; }
  .кк .имя small { font: 500 7pt "Tektur Narrow", sans-serif; color: var(--серый); text-transform: uppercase; letter-spacing: .04em; }
  .кк p { font: 8.4pt/1.22 "Tektur Narrow", sans-serif; margin: 0 0 .5mm; }
  .кк p:last-child { margin-bottom: 0; }
  .кк .кл { font: 700 7.4pt "Tektur Narrow", sans-serif; color: var(--келемий);
    text-transform: uppercase; letter-spacing: .04em; margin-right: 1mm; }
  .кк .низ { border-top: .22mm dotted var(--пример-кант); margin-top: .8mm; padding-top: .8mm; }

  .икс { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1.4mm 4mm; }
  .икс .ряд { display: flex; align-items: center; gap: 2.2mm; padding: .8mm 0;
    border-bottom: .22mm dotted var(--пример-кант); break-inside: avoid; }
  .икс .ряд img.ик { width: 7.6mm; height: 7.6mm; object-fit: contain; flex: none;
    background: rgba(255,255,255,.72); outline: .18mm solid var(--кант); padding: .5mm; }
  .икс .ик-нет { width: 7.6mm; height: 7.6mm; flex: none; background: var(--место-иконки);
    border: .25mm dashed var(--пример-кант); font: 5pt/7.6mm "Tektur Narrow", sans-serif;
    color: var(--серый); text-align: center; overflow: hidden; }
  .икс .тек { flex: 1; min-width: 0; }
  .икс .тек b { display: block; font: 700 8.6pt/1.15 "Tektur", sans-serif; color: var(--чернила); }
  .икс .тек span { font: 7.6pt/1.15 "Tektur Narrow", sans-serif; color: var(--серый); }

  .раскладки { display: grid; grid-template-columns: 1fr 1fr; gap: 3mm; }
  .раскладки figure { margin: 0; }
  .раскладки img { display: block; width: 100%; height: auto; }
  .раскладки figcaption { font: 700 8.4pt "Tektur Narrow", sans-serif; color: var(--келемий);
    text-transform: uppercase; letter-spacing: .05em; margin-top: .8mm; }

  .словарь dt { font: 700 9.4pt "Tektur", sans-serif; color: var(--охра); margin-top: 1.6mm; }
  .словарь dd { font: 8.8pt/1.25 "Tektur Narrow", sans-serif; margin: .3mm 0 0; }
"""


def стиль_книги():
    t = io.open(КНИГА, encoding="utf-8").read()
    return t[t.index("<style>") + len("<style>"):t.index("</style>")]


def шапка(буква, имя, пояснение=""):
    доп = f'<p class="пояснение">{пояснение}</p>' if пояснение else ""
    return (f'    <div class="прил"><span class="бк">{буква}</span>'
            f'<span class="им">{имя}</span></div>\n{доп}')


def экран(*блоки):
    return '    <div class="две">\n' + "\n".join(блоки) + "\n    </div>"


def main():
    print("справочник: собираю по своду", данные.свод())


if __name__ == "__main__":
    main()
