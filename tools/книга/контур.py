# -*- coding: utf-8 -*-
"""Общие мелочи рисунков книги: тёмный контур вокруг картинки (05.10.2026).

Светлые гексы и серые обороты жетонов сливались с кремовой страницей
(«нужны обводки, чтобы лучше считывались»). Контур строится по непрозрачности
картинки: силуэт раздувается на толщину линии и заливается тёмным тоном.
"""
from PIL import Image, ImageFilter

ТЁМНЫЙ = (60, 48, 32, 255)


def обвести(im, толщина=6, цвет=ТЁМНЫЙ):
    im = im.convert("RGBA")
    п = толщина + 2
    холст = Image.new("RGBA", (im.width + 2 * п, im.height + 2 * п), (0, 0, 0, 0))
    a = Image.new("L", холст.size, 0)
    a.paste(im.getchannel("A").point(lambda v: 255 if v > 40 else 0), (п, п))
    a = a.filter(ImageFilter.MaxFilter(толщина * 2 + 1))
    слой = Image.new("RGBA", холст.size, цвет)
    слой.putalpha(a)
    холст.alpha_composite(слой)
    холст.alpha_composite(im, (п, п))
    return холст
