# -*- coding: utf-8 -*-
"""КАРТИНКИ ГЛАВЫ 2 «СОСТАВ ИГРЫ» — стопки и обводки из печатных текстур.

Замечания дизайнера 27.09.2026:
  * блоки поля бледные на бумаге — обвести контуром;
  * фишку первого игрока — крупнее; модуль хранилища — мельче модулей боя;
  * жетоны зданий и войск, карты приказов, планшеты — не по одному, а
    стопкой разных цветов, чтобы было видно: у каждого игрока свой набор;
  * рубашки карт приказов — цветные, не пустой шаблон для «Тейблтопа»;
  * планшеты войск и хранилища — в верном соотношении размеров.

Пишет tools/книга/_состав-*.png; глава берёт их путём ../../tools/книга/…
    python make_sostav.py
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

D = os.path.dirname(os.path.abspath(__file__))
T = os.path.join(os.path.dirname(os.path.dirname(D)), "data", "textures")


def img(путь):
    im = Image.open(os.path.join(T, путь)).convert("RGBA")
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    return im.crop(box) if box else im


def обвести(im, толщина=6, цвет=(58, 46, 30, 255)):
    """Контур по силуэту — для бледных картинок на светлой бумаге."""
    a = im.getchannel("A").point(lambda v: 255 if v > 40 else 0)
    поле = толщина + 2
    холст = Image.new("RGBA", (im.width + 2 * поле, im.height + 2 * поле), (0, 0, 0, 0))
    маска = Image.new("L", холст.size, 0)
    маска.paste(a, (поле, поле))
    шире = маска.filter(ImageFilter.MaxFilter(толщина * 2 + 1))
    контур = Image.new("RGBA", холст.size, цвет)
    холст.paste(контур, (0, 0), шире)
    холст.alpha_composite(im, (поле, поле))
    return холст


def стопка(картинки, высота, шаг=(0.16, -0.10), тень=True):
    """Веер: каждая следующая картинка сдвинута вправо и вверх и лежит сверху."""
    # ОДИН МАСШТАБ НА ВСЕХ: PNG жетонов выгружены в одном разрешении (300 dpi),
    # и их пиксели — настоящие размеры друг относительно друга. Прежде каждый
    # жетон тянули до одной высоты, и пехота выходила ростом с технику
    # (дизайнер 28.09.2026). Высота теперь — у самого высокого жетона.
    k = высота / max(к.height for к in картинки)
    ks = [к.resize((round(к.width * k), round(к.height * k)), Image.LANCZOS)
          for к in картинки]
    w = max(к.width for к in ks)
    dx, dy = round(w * шаг[0]), round(высота * шаг[1])
    n = len(ks)
    Ш = w + dx * (n - 1) + 20
    В = высота + abs(dy) * (n - 1) + 20
    out = Image.new("RGBA", (Ш, В), (0, 0, 0, 0))
    for i, к in enumerate(ks):
        x = 10 + dx * i
        y = 10 + (abs(dy) * (n - 1 - i) if dy < 0 else dy * i)
        if тень:
            т = Image.new("RGBA", к.size, (0, 0, 0, 0))
            т.putalpha(к.getchannel("A").point(lambda v: min(v, 90)))
            т = т.filter(ImageFilter.GaussianBlur(6))
            out.alpha_composite(т, (x + 5, y + 6))
        out.alpha_composite(к, (x, y))
    return out


def кучка(картинки, высота, места):
    """Жетоны россыпью: каждый по своей точке (доли ширины/высоты холста), с тенью.
    Кладутся по порядку — последний сверху."""
    # ОДИН МАСШТАБ НА ВСЕХ: PNG жетонов выгружены в одном разрешении (300 dpi),
    # и их пиксели — настоящие размеры друг относительно друга. Прежде каждый
    # жетон тянули до одной высоты, и пехота выходила ростом с технику
    # (дизайнер 28.09.2026). Высота теперь — у самого высокого жетона.
    k = высота / max(к.height for к in картинки)
    ks = [к.resize((round(к.width * k), round(к.height * k)), Image.LANCZOS)
          for к in картинки]
    # СЕТКА 2×2 ПО НАСТОЯЩИМ ШИРИНАМ: точки-доли холста годились, пока жетоны
    # были одного роста; с верными размерами мелкие жетоны разъезжались и
    # посередине оставалась дыра. Ячейка = самый крупный жетон её столбца и
    # ряда; верхний ряд прижат книзу, нижний кверху, столбцы — к середине.
    щель = round(высота * 0.07)
    ст = [max(ks[0].width, ks[2].width), max(ks[1].width, ks[3].width)]
    ря = [max(ks[0].height, ks[1].height), max(ks[2].height, ks[3].height)]
    Ш, В = ст[0] + ст[1] + щель + 12, ря[0] + ря[1] + щель + 12
    out = Image.new("RGBA", (Ш, В), (0, 0, 0, 0))
    for i, (к, (fx, fy)) in enumerate(zip(ks, места)):
        c, r = i % 2, i // 2
        x0 = 0 if c == 0 else ст[0] + щель
        x = x0 + (ст[c] - к.width) // 2
        y = ря[0] - к.height if r == 0 else ря[0] + щель
        т = Image.new("RGBA", к.size, (0, 0, 0, 0))
        т.putalpha(к.getchannel("A").point(lambda v: min(v, 90)))
        out.alpha_composite(т.filter(ImageFilter.GaussianBlur(6)), (x + 5, y + 6))
        out.alpha_composite(к, (x, y))
    box = out.getchannel("A").getbbox()
    return out.crop(box) if box else out


def сохранить(im, имя):
    im.save(os.path.join(D, "_состав-%s.png" % имя), optimize=True)


# блоки поля — с контуром
сохранить(обвести(img("block/big-1-A.png"), 7), "блок-большой")
сохранить(обвести(img("block/small-1-A.png"), 7), "блок-малый")
# фишка первого игрока — без полей, чтобы встала во всю высоту ячейки
сохранить(img("icons/first_player.png"), "фишка-первого")
# жетоны зданий и войск — разных игроков и разных видов, россыпью 2×2
МЕСТА = [(0.0, 0.0), (1.0, 0.08), (0.06, 1.0), (0.94, 0.92)]
# крупные (казарма, ЦУ) — одним столбцом, мелкие (энергостанция, добытчик) — другим
сохранить(кучка([img("token/barracks_p2.png"), img("token/power_plant_l1_p4.png"),
                 img("token/command_center_p1.png"), img("token/miner_l1_p3.png")], 300,
                МЕСТА), "здания")
сохранить(кучка([img("token/vehicle_p2.png"), img("token/aircraft_p3.png"),
                 img("token/tower_p4.png"), img("token/infantry_p1.png")], 300,
                МЕСТА), "войска")
# карты приказов: рубашка сзади, поверх — 4 разных приказа лицом (разных цветов)
сохранить(стопка([img("card/orders/back_blue.png"), img("card/orders/yellow_acquire.png"),
                  img("card/orders/green_explore.png"), img("card/orders/red_control.png"),
                  img("card/orders/blue_place.png")], 520, шаг=(0.24, -0.06)), "приказы")
# планшеты — стопкой четырёх цветов; высота одна, поэтому соотношение верное
сохранить(стопка([img("board/troop-p4.png"), img("board/troop-p3.png"),
                  img("board/troop-p2.png"), img("board/troop-p1.png")], 600,
                 шаг=(0.022, -0.13)), "планшет-войск")
сохранить(стопка([img("board/storage-p4.png"), img("board/storage-p3.png"),
                  img("board/storage-p2.png"), img("board/storage-p1.png")], 600,
                 шаг=(0.047, -0.13)), "планшет-хранилища")
# кубики технологий 4 цветов квадратом 2×2
from PIL import ImageDraw
цвета = [(59, 130, 208), (200, 55, 50), (70, 150, 70), (225, 180, 40)]
кв = Image.new("RGBA", (300, 290), (0, 0, 0, 0))
dd = ImageDraw.Draw(кв)
def тон(c, k):
    return tuple(min(255, int(x * k)) for x in c) + (255,)
for i, c in enumerate(цвета):
    x, y, r = 20 + (i % 2) * 140, 15 + (i // 2) * 135, 55
    верх = [(x + r, y), (x + 2 * r, y + r // 2), (x + r, y + r), (x, y + r // 2)]
    лево = [(x, y + r // 2), (x + r, y + r), (x + r, y + 2 * r), (x, y + r + r // 2)]
    право = [(x + r, y + r), (x + 2 * r, y + r // 2), (x + 2 * r, y + r + r // 2), (x + r, y + 2 * r)]
    dd.polygon(верх, fill=тон(c, 1.12), outline=(30, 30, 30, 255))
    dd.polygon(лево, fill=тон(c, 0.85), outline=(30, 30, 30, 255))
    dd.polygon(право, fill=тон(c, 0.65), outline=(30, 30, 30, 255))
кв.save(os.path.join(D, "_кубики-технологий.png"))
print("ok")
