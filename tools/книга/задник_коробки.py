# -*- coding: utf-8 -*-
"""ЗАДНИК КОРОБКИ «Lex Talionis», версия 2 (дизайнер 14.10.2026; образцы — Дюна.
Империя, Civilization, Серп, Прага, Комната 25, Yellow & Yangtze, Fallout;
по разбору агента-критика).

Зоны квадрата 300×300 мм (10 точек на мм):
  0–45 %   арт: харвестер вгрызается в поле келемия; слева логотип и тексты
           на тёмном градиенте — ни одна строка не идёт по фигурам;
  40–80 %  разложенная партия в перспективе на сукне, крупно; на переднем
           плане — рука из 4 карт приказа с подписью;
  80–91 %  полоса «Пять действий — в каждом выбор из двух»: значок, название, А / Б;
  91–100 % игроки, возраст, время, состав одной строкой; места под маркировку.
Тексты — только то, что есть в правилах (гл. 1 книги).

Запуск: python задник_коробки.py → design-docs/обложка/задник коробки.png (+ .pdf)
"""
import os
import subprocess

import pymupdf

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ДИСК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium")
ФОНЫ = os.path.join(ДИСК, "Иллюстрации", "фоны и сцены")
ПРИКАЗЫ = os.path.join(ДИСК, "Компоненты игрока", "экспорт-карты-приказов", "выпуск 27.09.2026 — пять развилок")
ВЫХОД = os.path.join(КОРЕНЬ, "design-docs", "обложка")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


def url(п):
    return "file:///" + п.replace("\\", "/")


def фон(номер):
    return os.path.join(ФОНЫ, sorted(x for x in os.listdir(ФОНЫ) if x.lower().endswith(".jpg"))[номер])


ЛОГО = os.path.join(КОРЕНЬ, "design-docs", "логотип-лекс", "LEX TALIONIS — логотип (рамка).png")
# стол — отдельный снимок для коробки: другое поле, чем в книге
СТОЛ = os.path.join(D, "стол-коробка-4.png")
АРТ = фон(38)                     # харвестер в поле келемия: сцена читается сразу
ИКОНКИ = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт")

рука = ["синий — ОСВОИТЬ", "синий — НАСТУПАТЬ", "синий — ИССЛЕДОВАТЬ", "синий — КОНТРОЛИРОВАТЬ"]
карты = "".join(
    f'<img class="карта" src="{url(os.path.join(ПРИКАЗЫ, к + ".png"))}" '
    f'style="transform: rotate({-18 + i * 12}deg)">' for i, к in enumerate(рука))
действия = [("добыча", "Добыча", "Добыть", "Построить добытчик"),
            ("питание", "Питание", "Переложить энергию", "Построить энергостанцию"),
            ("снабжение", "Снабжение", "Нанять", "Построить военное здание"),
            ("командование", "Командование", "Манёвр", "Бой"),
            ("развитие", "Развитие", "Рынок", "Наука")]
полоса = "".join(
    f'<div class="дейст"><img src="{url(os.path.join(ИКОНКИ, f + ".png"))}"><div>'
    f'<b>{и}</b><span>{а}<i>или</i>{б}</span></div></div>' for f, и, а, б in действия)

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Bold.ttf')}"); font-weight: 700; }}
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Black.ttf')}"); font-weight: 900; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Regular.ttf')}"); font-weight: 400; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Bold.ttf')}"); font-weight: 700; }}
html, body {{ margin: 0; width: 3000px; height: 3000px; overflow: hidden; background: #171b1c; }}
.з {{ position: relative; width: 3000px; height: 3000px; overflow: hidden; color: #efe9d8; font-family: N; }}
.арт {{ position: absolute; inset: 0 0 auto 0; height: 1500px;
  background: url("{url(АРТ)}") 70% 40% / cover; }}
.арт::after {{ content: ""; position: absolute; inset: 0; background:
  linear-gradient(180deg, rgba(29,38,34,0) 55%, rgba(29,38,34,.85) 88%, #1d2622 100%),
  linear-gradient(90deg, rgba(29,38,34,.96) 0%, rgba(29,38,34,.88) 34%, rgba(29,38,34,.25) 52%, rgba(29,38,34,0) 64%); }}
.сукно {{ position: absolute; inset: 1490px 0 0 0;
  background: linear-gradient(180deg, #1d2622 0%, #1d2622 6%, rgba(29,38,34,0) 40%),
    radial-gradient(ellipse at 60% 20%, #24302a 0%, #1b2420 55%, #151a18 100%); }}
.лого {{ position: absolute; left: 140px; top: 90px; width: 1000px;
  filter: drop-shadow(0 14px 24px rgba(0,0,0,.8)); }}
.тексты {{ position: absolute; left: 160px; top: 520px; width: 1120px; font-size: 44px; line-height: 1.4; }}
.тексты p {{ margin: 0 0 30px; }}
.тексты .мир {{ font-size: 50px; color: #ffffff; font-weight: 700; line-height: 1.3; }}
.тексты b {{ color: #8fe36b; }}
.стол-обёртка {{ position: absolute; left: 760px; top: 930px; width: 2450px; height: 1560px; perspective: 3000px; }}
.стол {{ position: absolute; inset: 0; background: url("{url(СТОЛ)}") center / contain no-repeat;
  transform: rotateX(44deg) rotateZ(-6deg); transform-origin: 50% 55%;
  filter: drop-shadow(0 50px 40px rgba(0,0,0,.7)); }}
.рука {{ position: absolute; left: 150px; top: 1640px; width: 900px; height: 820px; }}
.карта {{ position: absolute; left: 300px; top: 60px; width: 330px; border-radius: 16px;
  box-shadow: 0 0 0 4px #2a2318, 0 26px 40px rgba(0,0,0,.75); transform-origin: 50% 170%; }}
.подпись {{ position: absolute; font: 900 40px T; letter-spacing: .06em; text-transform: uppercase; color: #8fe36b;
  text-shadow: 0 3px 10px rgba(0,0,0,.9); }}
.подпись.р {{ left: 200px; top: 1560px; }}
.полоса {{ position: absolute; left: 0; right: 0; top: 2420px; height: 330px; padding: 0 120px;
  box-sizing: border-box; display: flex; gap: 24px; align-items: center;
  background: linear-gradient(180deg, rgba(10,13,12,.0), rgba(10,13,12,.86) 18%, rgba(10,13,12,.86)); }}
.подпись.п {{ left: 160px; top: 2440px; }}
.дейст {{ flex: 1; display: flex; gap: 18px; align-items: center; margin-top: 60px; }}
.дейст img {{ width: 140px; height: 140px; object-fit: contain; filter: drop-shadow(0 8px 12px rgba(0,0,0,.6)); }}
.дейст b {{ display: block; font: 900 38px T; color: #fff; margin-bottom: 6px; }}
.дейст span {{ display: block; font: 700 31px/1.18 N; color: #d8d2c0; }}
.дейст i {{ display: block; font: 400 26px N; color: #8fe36b; font-style: normal; }}
.низ {{ position: absolute; left: 0; right: 0; bottom: 0; height: 250px; padding: 0 120px; box-sizing: border-box;
  display: flex; align-items: center; gap: 44px; background: #0f1312; border-top: 4px solid #4f6b3c; }}
.знак {{ display: flex; flex-direction: column; align-items: center; gap: 8px; font: 700 30px N; }}
.знак .к {{ min-width: 116px; height: 100px; padding: 0 14px; box-sizing: border-box; border: 5px solid #8fe36b;
  border-radius: 16px; display: flex; align-items: center; justify-content: center; font: 900 42px T; color: #8fe36b; }}
.состав {{ flex: 1; font: 400 28px/1.35 N; color: #d8d2c0; }}
.состав b {{ font: 900 30px T; color: #8fe36b; letter-spacing: .06em; text-transform: uppercase; margin-right: 12px; }}
.место {{ border: 3px dashed rgba(239,233,216,.3); color: rgba(239,233,216,.45); font: 700 24px N;
  display: flex; align-items: center; justify-content: center; text-align: center; height: 160px; }}
</style></head><body><div class="з">
<div class="сукно"></div><div class="арт"></div>
<div class="стол-обёртка"><div class="стол"></div></div>
<img class="лого" src="{url(ЛОГО)}">
<div class="тексты">
<p class="мир">Келемий прорастает из земли — зелёный кристалл, за который покупают технологии.
Четыре нации спорят за одно поле. Кристалла хватит не всем.</p>
<p>В начале круга все разом выбирают карту приказа, потом по очереди вскрывают и сразу ходят.
Действий пять, и в каждом <b>выбор из двух</b>: добыть келемий или поставить новый добытчик,
двинуть войска или начать бой, торговать на Рынке или вкладываться в Науку. За разбитые жетоны
врага вы получаете трофеи — ими же платите за технологии.</p>
<p>Партия кончается, когда пустеет колода рынка или поле. Побеждает тот, у кого больше очков,
— или тот, кто раньше займёт вершины всех трёх треков технологий.</p>
</div>
<div class="подпись р">Ваш ход — одна карта приказа</div>
<div class="рука">{карты}</div>
<div class="полоса">{полоса}</div>
<div class="подпись п">Пять действий — в каждом выбор из двух</div>
<div class="низ">
<div class="знак"><div class="к">2–4</div>игрока</div>
<div class="знак"><div class="к">14+</div>лет</div>
<div class="знак"><div class="к">90–120</div>минут</div>
<div class="состав"><b>В коробке</b>10 блоков поля • 30 тайлов и нейтральных построек • 10 планшетов •
116 жетонов зданий и войск • 36 модулей • 196 карт • 192 кубика • 50 монет • фишка первого игрока •
2 мешочка • книга правил и справочник</div>
<div class="место" style="width:380px">издатель,<br>маркировка</div>
<div class="место" style="width:300px">штрихкод</div>
</div>
</div></body></html>"""

os.makedirs(ВЫХОД, exist_ok=True)
html = os.path.join(D, "_задник.html")
open(html, "w", encoding="utf-8").write(HTML)
png = os.path.join(ВЫХОД, "задник коробки.png")
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--user-data-dir=" + os.path.join(D, "chr"), "--virtual-time-budget=8000",
                "--window-size=3000,3000", "--screenshot=" + png, url(html)], check=True,
               capture_output=True)
os.remove(html)
pdf = pymupdf.open()
стр = pdf.new_page(width=300 / 25.4 * 72, height=300 / 25.4 * 72)
стр.insert_image(стр.rect, filename=png)
pdf.save(os.path.join(ВЫХОД, "задник коробки.pdf"), deflate=True)
print("готово:", png)
