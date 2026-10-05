# -*- coding: utf-8 -*-
"""Рисунок главы 8: уничтожение чужого здания и нейтральной постройки
(дизайнер 04.10.2026: «показать последствия — освобождение сектора, награды
с оборота жетонов, отправку жетона на свалку»).

Кадры рисует движок (kelium.gui.replay2.СнимокСцены, сцены в сцены/*.yaml);
здесь они складываются в два ряда «до → после» с наградами справа.
"""
import os
import subprocess

from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ns = {"__file__": os.path.join(D, "figs.py")}
exec(open(os.path.join(D, "figs.py"), encoding="utf-8-sig").read().split("# планшет войск")[0], ns)
figure = ns["figure"]
РАННЕР = os.path.join(КОРЕНЬ, "gui", "target", "kelium-runner.jar")
ИКОНКИ = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт")
ШРИФТ = next(п for п in (r"C:\Windows\Fonts\TekturNarrow-Bold.ttf",
                         os.path.expanduser("~/.fonts/TekturNarrow-Bold.ttf")) if os.path.exists(п))
шр = ImageFont.truetype(ШРИФТ, 72)
ОХРА = (107, 68, 19, 255)


import sys
sys.path.insert(0, D)
from контур import обвести  # noqa: E402


def сцена(имя):
    png = os.path.join(D, "_%s.png" % имя)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8",
                    "-Djava.awt.headless=true", "-cp", РАННЕР, "kelium.gui.replay2.СнимокСцены",
                    os.path.join(D, "сцены", имя + ".yaml"), png], check=True,
                   capture_output=True, cwd=КОРЕНЬ,
                   env=dict(os.environ, LANG="C.UTF-8", LC_ALL="C.UTF-8"))
    return обвести(Image.open(png).convert("RGBA"), 5)


def картинка(путь, h):
    im = Image.open(путь).convert("RGBA")
    return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)


def стрелка(d, x, y):
    d.polygon([(x, y - 40), (x + 150, y - 40), (x + 150, y - 85), (x + 240, y),
               (x + 150, y + 85), (x + 150, y + 40), (x, y + 40)], fill=ОХРА)


def ряд(до, после, награды):
    """до → после | награды: [(подпись, [картинки])]"""
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    ш_наград = max(round(мерка.textlength(п, font=шр)) for п, _ in награды) + 20
    W = до.width + 300 + после.width + 80 + ш_наград
    H = max(до.height, после.height)
    холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    холст.alpha_composite(до, (0, (H - до.height) // 2))
    x = до.width + 30
    d = ImageDraw.Draw(холст)
    стрелка(d, x, H // 2)
    x += 270
    холст.alpha_composite(после, (x, (H - после.height) // 2))
    x += после.width + 80
    высота = len(награды) * 250
    y = (H - высота) // 2
    for подпись, картинки in награды:
        d.text((x, y), подпись, font=шр, fill=ОХРА)
        xx = x
        for im in картинки:
            холст.alpha_composite(im, (xx, y + 90))
            xx += im.width + 24
        y += 250
    return холст


РУБАШКА = os.path.join(КОРЕНЬ, "data", "textures", "card", "orders", "back_blue.png")


def свалка(оборот, h):
    """Карта свалки (рубашка приказа набок) с жетоном оборотом вверх на ней."""
    from PIL import ImageFilter
    карта = Image.open(РУБАШКА).convert("RGBA").rotate(90, expand=True)
    карта = карта.resize((round(карта.width * h / карта.height), h), Image.LANCZOS)
    м = Image.new("L", карта.size, 0)
    ImageDraw.Draw(м).rounded_rectangle((0, 0, карта.width - 1, h - 1), radius=round(h * 0.06), fill=255)
    карта.putalpha(м)
    ж = обвести(оборот.resize((round(оборот.width * h * 0.52 / оборот.height), round(h * 0.52)), Image.LANCZOS), 3)
    ж = ж.rotate(-6, resample=Image.BICUBIC, expand=True)
    x, y = (карта.width - ж.width) // 2, (h - ж.height) // 2
    т = Image.new("RGBA", ж.size, (20, 15, 5, 0))
    т.putalpha(ж.getchannel("A").point(lambda v: int(v * 0.4)))
    слой = Image.new("RGBA", карта.size, (0, 0, 0, 0))
    слой.alpha_composite(т, (x + 8, y + 10))
    карта = Image.alpha_composite(карта, слой.filter(ImageFilter.GaussianBlur(6)))
    карта.alpha_composite(ж, (x, y))
    return карта


def ряд_н(до, после, оборот, подписи):
    """Две строки: «до → после», под ней — КРУПНЫЙ оборот → карта свалки."""
    def выс(im, h):
        return im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
    до, после = выс(до, 560), выс(после, 560)
    об = обвести(выс(оборот, 520), 6)
    св = свалка(оборот, 520)
    мерка = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    ш_подп = int(max(мерка.textlength(п, font=шр) for п in подписи))
    W1 = до.width + 300 + после.width
    W2 = об.width + 60 + ш_подп + 60 + 260 + св.width
    W = max(W1, W2)
    H = 560 + 60 + 520 + 110
    холст = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(холст)
    x = (W - W1) // 2
    холст.alpha_composite(до, (x, 0))
    стрелка(d, x + до.width + 30, 280)
    холст.alpha_composite(после, (x + до.width + 300, 0))
    y2 = 560 + 60
    x = (W - W2) // 2
    холст.alpha_composite(об, (x, y2))
    x += об.width + 60
    yt = y2 + 260 - len(подписи) * 43
    for п in подписи:
        d.text((x, yt), п, font=шр, fill=ОХРА)
        yt += 86
    x += ш_подп + 60
    стрелка(d, x, y2 + 260)
    x += 260
    холст.alpha_composite(св, (x, y2))
    d.text((x + св.width // 2, y2 + 540), "свалка Акселя", font=шр, fill=ОХРА, anchor="ma")
    return холст


оборот_казармы = Image.open(os.path.join(КОРЕНЬ, "data", "textures", "token", "barracks_trophy.png")).convert("RGBA")
оборот_постройки = Image.open(os.path.join(КОРЕНЬ, "data", "textures", "field", "neutral_big_trophy.png")).convert("RGBA")
р1 = ряд_н(сцена("здание-до"), сцена("здание-после"), оборот_казармы,
           ["2 трофея — Акселю на свалку", "контейнер — Бриане (владельцу)"])
р2 = ряд_н(сцена("постройка-до"), сцена("постройка-после"), оборот_постройки,
           ["1 трофей — Акселю на свалку", "контейнер — Акселю"])
W = max(р1.width, р2.width)
холст = Image.new("RGBA", (W, р1.height + р2.height + 120), (0, 0, 0, 0))
холст.alpha_composite(р1, ((W - р1.width) // 2, 0))
ImageDraw.Draw(холст).line((80, р1.height + 60, W - 80, р1.height + 60), fill=(190, 175, 140, 255), width=6)
холст.alpha_composite(р2, ((W - р2.width) // 2, р1.height + 120))
png = os.path.join(D, "_уничтожение.png")
холст.save(png)
figure("уничтожение", png, 2000, холст.width, холст.height, [], css_w="63%", pad="0.2mm 0 0.4mm")
print("ok", холст.size)
