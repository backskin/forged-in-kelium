# -*- coding: utf-8 -*-
"""ЗАДНИК КОРОБКИ «Lex Talionis», версия 5 (дизайнер 21.10.2026).

Разбор дизайнера по версии 4: лидеры разного размера (у синей голова
заметно меньше), много пустого места — снимок поля можно сильно увеличить,
пусть уходит за край; текст «по-дебильному», шаблонный.

Что сделано:
  — лидеры выровнены по РАЗМЕРУ ГОЛОВЫ (корень из «макушка–подбородок» ×
    ширина головы на линии глаз), а не по «глаза–подбородок»: у синей голова
    уже и без волос, и по прежнему замеру выходила на 10–15 % меньше
    остальных; все четверо крупнее;
  — стол — главная картинка: во всю ширину, в перспективе, уходит под правый
    обрез;
  — текст по образцу оборотов «Звёздные войны: Восстание», Root, Kemet,
    «Серп»: заголовок-крючок, три предложения мира, четыре коротких тезиса
    «чем игра особенная» (заголовок и строка-две), итог про победу. Правила
    по шагам на обороте не пересказываются.

Раскладка квадрата 300×300 мм (10 точек на мм): верх — сцена, логотип и
мир слева, лидеры справа; середина — тезисы слева, огромный стол справа под
обрез, куча жетонов; низ — состав, значки, маркировка.

Запуск: python задник_коробки.py → design-docs/обложка/задник коробки.png (+ .pdf)
        и «задник коробки — с вылетами 10 мм» (.png, .pdf 320×320 мм с TrimBox).
Без Яндекс.Диска (облако) вместо картины высадки и текстуры земли берутся
подставки из репозитория — это только ПРЕВЬЮ; печатный задник собирать там,
где есть Диск.
"""
import os
import subprocess

import pymupdf

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ДИСК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium")
ФОНЫ = os.path.join(ДИСК, "Иллюстрации", "фоны и сцены")
ВЫХОД = os.environ.get("ZADNIK_OUT") or os.path.join(КОРЕНЬ, "design-docs", "обложка")
CHROME = next((п for п in (r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                           "/opt/pw-browsers/chromium/chrome-linux/chrome",
                           "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
               if os.path.exists(п)), "chromium")


def url(п):
    return "file:///" + п.replace("\\", "/").lstrip("/")


def шрифт(*имена):
    """Первый найденный файл шрифта: Windows, затем копии в репозитории."""
    for и in имена:
        for папка in (r"C:\Windows\Fonts", os.path.join(КОРЕНЬ, "tools", "fonts"),
                      os.path.join(КОРЕНЬ, "gui", "src", "main", "resources", "fonts")):
            п = os.path.join(папка, и)
            if os.path.exists(п):
                return п
    return ""


ПРЕВЬЮ = not os.path.isdir(ФОНЫ)
if ПРЕВЬЮ:
    АРТ = os.path.join(КОРЕНЬ, "rules", "иллюстрации", "задник рулбука.jpg")
    ЗЕМЛЯ = ""
else:
    АРТ = os.path.join(ФОНЫ, sorted(x for x in os.listdir(ФОНЫ) if x.lower().endswith(".jpg"))[75])
    ИЛЛ = os.path.dirname(ФОНЫ)
    ЗЕМЛЯ = os.path.join(ИЛЛ, sorted(x for x in os.listdir(ИЛЛ)
                                     if x.lower().endswith((".jpg", ".png", ".webp")))[10])

ЛОГО = os.path.join(КОРЕНЬ, "design-docs", "логотип-лекс", "LEX TALIONIS — логотип (рамка).png")
СТОЛ_ВЕСЬ = os.path.join(D, "стол-коробка-4.png")
# В кадре только середина стола — поле, колоды, наука, рынок и общий запас;
# планшеты игроков отрезаны (сверху по пустой полосе, снизу — гаснут), чтобы
# поле было крупным и ни на что не налезало.
СТОЛ = os.path.join(D, "_стол-задник.png")
СТОЛ_РАМКА = (170, 600, 2312, 1600)
КУЧА = os.path.join(D, "куча-жетонов.png")            # куча_жетонов.py
ПЕРС = os.path.join(КОРЕНЬ, "illustrations", "персонажи-png")   # вырезать.py

# ЛИДЕРЫ — РАВНЫЕ ГОЛОВЫ. Замер на холсте 1152×1728: y макушки, y глаз,
# y подбородка, ширина головы на линии глаз, x середины головы.
ЗАМЕР = {
    "красный": (136, 402, 567, 311, 607),
    "синий":   (104, 319, 483, 285, 548),
    "зелёный": (89, 375, 532, 302, 554),
    "жёлтый":  (120, 375, 525, 320, 584),
}
ГОЛОВА = 190                     # «размер головы» в точках задника
ГЛАЗА_Y = 830                    # линия глаз
ЦЕНТРЫ = (1600, 1975, 2350, 2725)  # середины голов по x


def лидер(i, ф):
    макушка, глаз, подб, ширина, сер = ЗАМЕР[ф]
    k = ГОЛОВА / ((подб - макушка) * ширина) ** 0.5
    return (f'<img class="перс" src="{url(os.path.join(ПЕРС, ф + ".png"))}" '
            f'style="left:{ЦЕНТРЫ[i] - сер * k:.0f}px; top:{ГЛАЗА_Y - глаз * k:.0f}px; '
            f'width:{1152 * k:.0f}px; height:{1728 * k:.0f}px; z-index:{3 + (i == 1)}">')


лидеры = "".join(лидер(i, ф) for i, ф in enumerate(["красный", "синий", "зелёный", "жёлтый"]))

В = 100                 # вылет 10 мм (10 точек на мм)
П = 3000 + 2 * В        # сторона листа с вылетами

ЗЕМЛЯ_CSS = (f'url("{url(ЗЕМЛЯ)}") 0 0 / 1100px auto repeat' if ЗЕМЛЯ
             else "radial-gradient(ellipse at 60% 30%, #3a4a3c, #1a221e 70%)")

ТЕЗИСЫ = [
    ("Читай соперника",
     "Все тайно выбирают приказ и вскрывают по очереди. Повторил чужой ход — "
     "потерял действие. Угадал его — сыграл больше."),
    ("Энергии на всех не хватит",
     "Поле собирается из блоков, раскладок много. Мест под энергостанции "
     "мало, и за каждое придётся драться."),
    ("Чужие технологии — в обломках",
     "Разбитый враг оставляет трофеи. На них растёт ваша наука: модули боя, "
     "сборки и хранилища."),
    ("От шахты до армады",
     "Начинаете с центра управления и одного войска. К концу партии войска "
     "идут пачками, а чужие заводы горят."),
]
тезисы = "".join(f'<div class="тезис"><h3>{з}</h3><p>{т}</p></div>' for з, т in ТЕЗИСЫ)

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: T; src: url("{url(шрифт('Tektur-Bold.ttf'))}"); font-weight: 700; }}
@font-face {{ font-family: T; src: url("{url(шрифт('Tektur-Black.ttf', 'Tektur-Bold.ttf'))}"); font-weight: 900; }}
@font-face {{ font-family: N; src: url("{url(шрифт('TekturNarrow-Regular.ttf'))}"); font-weight: 400; }}
@font-face {{ font-family: N; src: url("{url(шрифт('TekturNarrow-Bold.ttf'))}"); font-weight: 700; }}
html, body {{ margin: 0; width: {П}px; height: {П}px; overflow: hidden; background: #141a18; }}
.з {{ position: absolute; left: {В}px; top: {В}px; width: 3000px; height: 3000px; overflow: visible;
  color: #efe9d8; font-family: N; }}
.земля {{ position: absolute; inset: 1150px -{В}px -{В}px -{В}px;
  background: linear-gradient(180deg, rgba(20,26,24,.9) 0%, rgba(20,26,24,.45) 30%, rgba(20,26,24,.65) 100%),
    {ЗЕМЛЯ_CSS}; filter: saturate(.75) brightness(.62); }}
.арт {{ position: absolute; inset: -{В}px -{В}px auto -{В}px; height: {1330 + В}px;
  background: url("{url(АРТ)}") 50% 30% / cover;
  -webkit-mask-image: linear-gradient(180deg, #000 80%, transparent 100%); }}
/* ЗОНЫ НЕ ПЕРЕКРЫВАЮТСЯ (дизайнер 21.10.2026): лидеры обрезаны низом сцены,
   стол лежит ниже, тезисы — слева от стола, куча — в полосе состава */
.шеренга {{ position: absolute; left: 0; top: 0; width: 3000px; height: 1300px; overflow: hidden; z-index: 3;
  -webkit-mask-image: linear-gradient(180deg, #000 82%, transparent 100%); }}
.арт::after {{ content: ""; position: absolute; inset: 0; background:
  linear-gradient(180deg, rgba(20,26,24,0) 50%, rgba(20,26,24,.55) 90%),
  linear-gradient(90deg, rgba(20,26,24,.97) 0%, rgba(20,26,24,.88) 32%, rgba(20,26,24,.25) 46%, rgba(20,26,24,0) 56%); }}
.лого {{ position: absolute; left: 90px; top: 50px; width: 960px; z-index: 6;
  filter: drop-shadow(0 14px 24px rgba(0,0,0,.8)); }}
.мир {{ position: absolute; left: 120px; top: 420px; width: 1130px; z-index: 6; }}
.мир h1 {{ margin: 0 0 30px; font: 900 76px/1.08 T; color: #fff; text-transform: uppercase;
  text-shadow: 0 4px 14px rgba(0,0,0,.85); }}
.мир h1 em {{ font-style: normal; color: #8fe36b; }}
.мир p {{ margin: 0; font: 400 40px/1.4 N; color: #e2dccb; text-shadow: 0 2px 8px rgba(0,0,0,.9); }}
.мир b {{ color: #8fe36b; font-weight: 700; }}
.перс {{ position: absolute;
  filter: drop-shadow(0 24px 30px rgba(0,0,0,.7)); }}
.стол-обёртка {{ position: absolute; left: 980px; top: 1300px; width: 2350px; height: 1100px;
  perspective: 3400px; z-index: 5; }}
.стол {{ position: absolute; inset: 0; background: url("{url(СТОЛ)}") center / contain no-repeat;
  transform: rotateX(24deg) rotateZ(-3deg); transform-origin: 50% 50%;
  -webkit-mask-image: linear-gradient(180deg, #000 80%, transparent 100%);
  filter: drop-shadow(0 50px 40px rgba(0,0,0,.8)); }}
.тезисы {{ position: absolute; left: 100px; top: 1330px; width: 900px; z-index: 7;
  display: flex; flex-direction: column; gap: 30px; }}
.тезис {{ padding: 0 0 0 34px; border-left: 9px solid #8fe36b; }}
.тезис h3 {{ margin: 0 0 8px; font: 900 48px/1.1 T; color: #fff; text-transform: uppercase;
  text-shadow: 0 3px 12px rgba(0,0,0,.95); }}
.тезис p {{ margin: 0; font: 400 37px/1.36 N; color: #ddd7c5; text-shadow: 0 2px 8px rgba(0,0,0,.95); }}
.итог {{ margin-top: 6px;
  font: 700 40px/1.32 N; color: #fff; text-shadow: 0 2px 10px rgba(0,0,0,.95); }}
.итог b {{ color: #8fe36b; }}
.куча {{ position: relative; width: 640px; align-self: center;
  filter: drop-shadow(0 26px 26px rgba(0,0,0,.8)); }}
.куча img {{ width: 100%; display: block; }}
.куча div {{ position: absolute; left: -20px; right: -20px; bottom: 20px; text-align: center; padding: 16px 0 12px;
  background: linear-gradient(90deg, rgba(14,18,16,0), rgba(14,18,16,.82) 18%, rgba(14,18,16,.82) 82%, rgba(14,18,16,0));
  font: 900 54px/1 T; color: #fff; text-transform: uppercase; text-shadow: 0 4px 14px rgba(0,0,0,1), 0 0 30px rgba(0,0,0,.9); }}
.куча small {{ display: block; margin-top: 6px; font: 700 32px N; color: #8fe36b; text-transform: none; }}
.низ {{ position: absolute; left: 60px; right: 60px; top: 2440px; bottom: 50px; z-index: 9;
  display: flex; gap: 36px; }}
.состав {{ flex: 1; border: 5px solid #8fe36b; border-radius: 30px; padding: 20px 40px 20px;
  background: rgba(16,22,19,.95); box-shadow: 0 20px 40px rgba(0,0,0,.5); }}
.состав h2 {{ margin: 0 0 12px; font: 900 46px T; color: #8fe36b; letter-spacing: .08em; text-transform: uppercase; text-align: center; }}
.столбцы {{ display: grid; grid-template-columns: 1fr 1.05fr 1.15fr; gap: 0 30px; }}
.столбцы h3 {{ margin: 0 0 6px; font: 900 32px T; color: #fff; text-transform: uppercase; letter-spacing: .05em; }}
.столбцы ul {{ margin: 0; padding: 0; list-style: none; font: 400 28px/1.27 N; color: #d8d2c0; }}
.столбцы li {{ margin-bottom: 3px; padding-left: 24px; text-indent: -24px; }}
.столбцы li::before {{ content: "◆ "; color: #8fe36b; font-size: 20px; vertical-align: 4px; }}
.столбцы b {{ color: #fff; font-weight: 700; }}
.справа {{ width: 380px; display: flex; flex-direction: column; gap: 20px; }}
.знаки {{ display: flex; justify-content: space-between; }}
.знак {{ display: flex; flex-direction: column; align-items: center; gap: 6px; font: 700 26px N; }}
.знак .к {{ width: 112px; height: 96px; box-sizing: border-box; border: 5px solid #8fe36b;
  border-radius: 16px; display: flex; align-items: center; justify-content: center; font: 900 38px T; color: #8fe36b;
  background: rgba(16,22,19,.9); }}
.знак .к.д {{ font-size: 25px; letter-spacing: -.02em; }}
.место {{ flex: 1;  border: 3px dashed rgba(239,233,216,.3); color: rgba(239,233,216,.45); font: 700 24px N;
  display: flex; align-items: center; justify-content: center; text-align: center; background: rgba(16,22,19,.95); }}
.превью {{ position: absolute; right: 40px; top: 30px; z-index: 20; font: 700 30px N; color: #ff8;
  background: rgba(0,0,0,.6); padding: 6px 16px; }}
</style></head><body><div class="з">
<div class="земля"></div><div class="арт"></div>
<div class="шеренга">{лидеры}</div>
<div class="стол-обёртка"><div class="стол"></div></div>
<img class="лого" src="{url(ЛОГО)}">
<div class="мир">
<h1>Кристалл пророс на Земле.<br><em>За ним вернулись все.</em></h1>
<p>Марс, Венера, Юпитер и Земля давно живут порознь и считают друг друга
дикарями. Потом из земных недр полез <b>келемий</b> — чистая энергия в твёрдом
виде, — и колонии, у которых кончаются силы, летят домой забрать его.
На поле боя выясняется: никто не отстал, и у каждого своё оружие.</p>
</div>
<div class="тезисы">{тезисы}
<div class="итог">Побеждает тот, у кого больше очков. Или сразу — кто первым возьмёт
<b>вершины всех трёх технологий</b> либо уничтожит <b>второй центр управления</b> соперников.</div></div>
<div class="низ">
<div class="куча"><img src="{url(КУЧА)}"><div>Более 150 жетонов<small>зданий, войск и модулей — и 192 кубика</small></div></div>
<div class="состав"><h2>Состав игры</h2><div class="столбцы">
<div><h3>Поле</h3><ul>
<li><b>10</b> блоков поля: 5 больших и 5 малых</li>
<li><b>8</b> тайлов зарождения</li>
<li><b>8</b> тайлов недоступных гексов</li>
<li><b>14</b> нейтральных построек</li>
<li>планшет науки и планшет рынка</li>
<li>фишка первого игрока, 2 мешочка</li></ul></div>
<div><h3>Наборы четырёх наций</h3><ul>
<li><b>4</b> планшета войск и <b>4</b> планшета хранилища</li>
<li><b>48</b> жетонов зданий</li>
<li><b>64</b> жетона войск: пехота, техника, авиация, вышки</li>
<li><b>20</b> карт приказов, <b>32</b> кубика технологий</li>
<li><b>8</b> памяток</li></ul></div>
<div><h3>Общий запас</h3><ul>
<li><b>28</b> модулей, <b>8</b> жетонов хранилища</li>
<li><b>4</b> жетона супер-войск</li>
<li><b>176</b> карт: 74 задания, 56 арсенала, 10 рынка, 36 контейнеров</li>
<li><b>160</b> кубиков и <b>50</b> монет</li>
<li>книга правил и справочник</li></ul></div>
</div></div>
<div class="справа">
<div class="знаки">
<div class="знак"><div class="к">2–4</div>игрока</div>
<div class="знак"><div class="к">14+</div>лет</div>
<div class="знак"><div class="к д">90–120</div>минут</div>
</div>
<div class="место">издатель, маркировка</div>
<div class="место">штрихкод</div>
</div>
</div>
{'<div class="превью">ПРЕВЬЮ: фон — подставка</div>' if ПРЕВЬЮ else ''}
</div></body></html>"""

os.makedirs(ВЫХОД, exist_ok=True)
from PIL import Image
_с = Image.open(СТОЛ_ВЕСЬ)
_с.crop(СТОЛ_РАМКА).save(СТОЛ)
html = os.path.join(D, "_задник.html")
open(html, "w", encoding="utf-8").write(HTML)
лист = os.path.join(ВЫХОД, "задник коробки — с вылетами 10 мм.png")
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-sandbox",
                "--allow-file-access-from-files",
                "--user-data-dir=" + os.path.join(D, "chr"), "--virtual-time-budget=8000",
                f"--window-size={П},{П}", "--screenshot=" + лист, url(html)], check=True,
               capture_output=True)
os.remove(html)
os.remove(СТОЛ)
# обрезной формат — для просмотра
from PIL import Image
png = os.path.join(ВЫХОД, "задник коробки.png")
Image.open(лист).crop((В, В, В + 3000, В + 3000)).save(png)
ММ = 72 / 25.4
pdf = pymupdf.open()
стр = pdf.new_page(width=300 * ММ, height=300 * ММ)
стр.insert_image(стр.rect, filename=png)
pdf.save(os.path.join(ВЫХОД, "задник коробки.pdf"), deflate=True)
# печатный: 320×320 мм, TrimBox — формат 300×300 мм, BleedBox — весь лист
pdf = pymupdf.open()
стр = pdf.new_page(width=320 * ММ, height=320 * ММ)
стр.insert_image(стр.rect, filename=лист)
стр.set_bleedbox(стр.rect)
стр.set_trimbox(pymupdf.Rect(10 * ММ, 10 * ММ, 310 * ММ, 310 * ММ))
pdf.save(os.path.join(ВЫХОД, "задник коробки — с вылетами 10 мм.pdf"), deflate=True)
print("готово:", png, "и", лист)
