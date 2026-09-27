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
    ks = [к.resize((round(к.width * высота / к.height), высота), Image.LANCZOS)
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
    ks = [к.resize((round(к.width * высота / к.height), высота), Image.LANCZOS)
          for к in картинки]
    w = max(к.width for к in ks)
    Ш, В = round(w * 1.9), round(высота * 1.75)
    out = Image.new("RGBA", (Ш, В), (0, 0, 0, 0))
    for к, (fx, fy) in zip(ks, места):
        x, y = round((Ш - к.width) * fx), round((В - к.height) * fy)
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
сохранить(кучка([img("token/barracks_p2.png"), img("token/power_plant_l1_p4.png"),
                 img("token/miner_l1_p3.png"), img("token/command_center_p1.png")], 300,
                МЕСТА), "здания")
сохранить(кучка([img("token/vehicle_p2.png"), img("token/aircraft_p3.png"),
                 img("token/tower_p4.png"), img("token/infantry_p1.png")], 300,
                МЕСТА), "войска")
# рубашки карт приказов четырёх цветов, пятая — снова первого цвета
сохранить(стопка([img("card/orders/back_yellow.png"), img("card/orders/back_green.png"),
                  img("card/orders/back_scarlet.png"), img("card/orders/back_blue.png"),
                  img("card/orders/back_yellow.png")], 520, шаг=(0.24, -0.06)), "приказы")
# планшеты — стопкой четырёх цветов; высота одна, поэтому соотношение верное
сохранить(стопка([img("board/troop-p4.png"), img("board/troop-p3.png"),
                  img("board/troop-p2.png"), img("board/troop-p1.png")], 600,
                 шаг=(0.022, -0.13)), "планшет-войск")
сохранить(стопка([img("board/storage-p4.png"), img("board/storage-p3.png"),
                  img("board/storage-p2.png"), img("board/storage-p1.png")], 600,
                 шаг=(0.047, -0.13)), "планшет-хранилища")
print("ok")
