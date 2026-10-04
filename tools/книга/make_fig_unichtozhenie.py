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


def сцена(имя):
    png = os.path.join(D, "_%s.png" % имя)
    subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8",
                    "-Djava.awt.headless=true", "-cp", РАННЕР, "kelium.gui.replay2.СнимокСцены",
                    os.path.join(D, "сцены", имя + ".yaml"), png], check=True,
                   capture_output=True, cwd=КОРЕНЬ,
                   env=dict(os.environ, LANG="C.UTF-8", LC_ALL="C.UTF-8"))
    return Image.open(png).convert("RGBA")


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


контейнер = картинка(os.path.join(ИКОНКИ, "контейнер.png"), 140)
трофей = картинка(os.path.join(ИКОНКИ, "кубик-трофея.png"), 130)
оборот = картинка(os.path.join(КОРЕНЬ, "data", "textures", "token", "barracks_trophy.png"), 150)

р1 = ряд(сцена("здание-до"), сцена("здание-после"),
         [("на свалку Акселя", [оборот]), ("Бриане — контейнеры", [контейнер])])
р2 = ряд(сцена("постройка-до"), сцена("постройка-после"),
         [("Акселю — контейнер и трофей", [контейнер, трофей])])
W = max(р1.width, р2.width)
холст = Image.new("RGBA", (W, р1.height + р2.height + 80), (0, 0, 0, 0))
холст.alpha_composite(р1, (0, 0))
холст.alpha_composite(р2, (0, р1.height + 80))
png = os.path.join(D, "_уничтожение.png")
холст.save(png)
figure("уничтожение", png, 1800, холст.width, холст.height, [], css_w="100%", pad="0.6mm 0 1.0mm")
print("ok", холст.size)
