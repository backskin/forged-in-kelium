# -*- coding: utf-8 -*-
"""ЗАДНИК КОРОБКИ «Lex Talionis» (дизайнер 14.10.2026; образцы — Дюна. Империя,
Civilization, Серп, Прага, Комната 25, Yellow & Yangtze, Fallout).

Слои, сверху вниз:
  1. фон — арт поля келемия во всю коробку, книзу уходит в тёмную сталь;
  2. логотип и две вводки: мир (зачем вы здесь) и игра (что вы делаете);
  3. крупно — подготовленный стол в перспективе (снимок стола из книги);
  4. веер карт поверх стола; пять действий значками;
  5. «Особенности» и «Состав игры» плашками;
  6. нижняя полоса: игроки, возраст, время, места под маркировку и штрихкод.

Квадрат 300×300 мм, 10 точек на мм. Запуск: python задник_коробки.py
Итог: design-docs/обложка/задник коробки.png (+ .pdf)
"""
import os
import subprocess

import pymupdf

D = os.path.dirname(os.path.abspath(__file__))
КОРЕНЬ = os.path.dirname(os.path.dirname(D))
ДИСК = os.path.join(os.path.expanduser("~"), "Yandex.Disk", "Forged in Kelium")
ФОНЫ = os.path.join(ДИСК, "Иллюстрации", "фоны и сцены")
ВЫХОД = os.path.join(КОРЕНЬ, "design-docs", "обложка")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


def url(п):
    return "file:///" + п.replace("\\", "/")


def фон(номер):
    return os.path.join(ФОНЫ, sorted(x for x in os.listdir(ФОНЫ) if x.lower().endswith(".jpg"))[номер])


ЛОГО = os.path.join(КОРЕНЬ, "design-docs", "логотип-лекс", "LEX TALIONIS — логотип (рамка).png")
СТОЛ = os.path.join(D, "стол.png")
АРТ = фон(75)                     # поле келемия, войска в окопе
КАРТЫ = os.path.join(КОРЕНЬ, "data", "textures", "card")
ИКОНКИ = os.path.join(КОРЕНЬ, "rules", "иконки-экспорт")

# веер — только вертикальные карты одного размера: задание, супер-задание, задание
веер = ["objective/o07.png", "objective_super/s9_03.png", "objective/o03.png"]
карты = "".join(
    f'<img class="карта" src="{url(os.path.join(КАРТЫ, к))}" '
    f'style="transform: rotate({-15 + i * 15}deg)">'
    for i, к in enumerate(веер))
действия = [("добыча", "Добыча"), ("питание", "Питание"), ("снабжение", "Снабжение"),
            ("командование", "Командование"), ("развитие", "Развитие")]
значки = "".join(f'<div class="дейст"><img src="{url(os.path.join(ИКОНКИ, f + ".png"))}">'
                 f'<span>{и}</span></div>' for f, и in действия)

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: T; src: url("{url(r'C:/Windows/Fonts/Tektur-Bold.ttf')}"); font-weight: 700; }}
@font-face {{ font-family: T; src: url("{url(r'C:/Windows/Fonts/Tektur-Black.ttf')}"); font-weight: 900; }}
@font-face {{ font-family: N; src: url("{url(r'C:/Windows/Fonts/TekturNarrow-Regular.ttf')}"); font-weight: 400; }}
@font-face {{ font-family: N; src: url("{url(r'C:/Windows/Fonts/TekturNarrow-Bold.ttf')}"); font-weight: 700; }}
html, body {{ margin: 0; width: 3000px; height: 3000px; overflow: hidden; background: #14181a; }}
.з {{ position: relative; width: 3000px; height: 3000px; overflow: hidden; color: #efe9d8; font-family: N; }}
.арт {{ position: absolute; inset: 0 0 auto 0; height: 2050px;
  background: url("{url(АРТ)}") center 30% / cover; }}
.арт::after {{ content: ""; position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(12,16,18,.80) 0%, rgba(12,16,18,.45) 34%, rgba(12,16,18,.15) 55%,
    rgba(20,24,26,.85) 82%, #14181a 100%); }}
.сталь {{ position: absolute; inset: 2000px 0 0 0;
  background: repeating-linear-gradient(135deg, rgba(255,255,255,.025) 0 6px, transparent 6px 14px), #14181a; }}
.лого {{ position: absolute; left: 150px; top: 120px; width: 1250px;
  filter: drop-shadow(0 18px 30px rgba(0,0,0,.7)); }}
.вводка {{ position: absolute; left: 160px; top: 640px; width: 1180px; font-size: 46px; line-height: 1.38; }}
.вводка p {{ margin: 0 0 36px; text-shadow: 0 3px 10px rgba(0,0,0,.85); }}
.вводка .мир {{ font-style: normal; color: #d9f0c8; }}
.вводка b {{ color: #8fe36b; font-weight: 700; }}
.девиз {{ position: absolute; left: 160px; top: 1450px; font: 900 64px T; letter-spacing: .04em;
  color: #fff; text-shadow: 0 4px 16px rgba(0,0,0,.9); }}
.девиз span {{ color: #7ee05a; }}
.стол-обёртка {{ position: absolute; left: 1180px; top: 300px; width: 1900px; height: 1900px;
  perspective: 2600px; }}
.стол {{ position: absolute; inset: 0; background: url("{url(СТОЛ)}") center / contain no-repeat;
  transform: rotateX(46deg) rotateZ(-9deg) scale(.98); transform-origin: 50% 60%;
  filter: drop-shadow(0 40px 40px rgba(0,0,0,.75)) saturate(1.08); }}
.веер {{ position: absolute; left: 60px; top: 1600px; width: 1100px; height: 640px; }}
.карта {{ position: absolute; left: 380px; top: 30px; width: 330px; border-radius: 16px;
  box-shadow: 0 0 0 4px #3c3020, 0 22px 40px rgba(0,0,0,.7); transform-origin: 50% 150%; }}
.действия {{ position: absolute; left: 160px; top: 2390px; display: flex; gap: 26px; }}
.дейст {{ width: 196px; text-align: center; font: 700 30px N; letter-spacing: .02em; }}
.дейст img {{ width: 150px; height: 150px; object-fit: contain; display: block; margin: 0 auto 8px;
  filter: drop-shadow(0 8px 12px rgba(0,0,0,.6)); }}
.подпись-дейст {{ position: absolute; left: 160px; top: 2320px; font: 700 34px T; color: #8fe36b;
  text-transform: uppercase; letter-spacing: .08em; }}
.плашка {{ position: absolute; background: rgba(20,26,28,.88); border: 3px solid #4f6b3c;
  clip-path: polygon(28px 0, 100% 0, 100% calc(100% - 28px), calc(100% - 28px) 100%, 0 100%, 0 28px);
  padding: 34px 44px 30px; }}
.плашка h3 {{ margin: 0 0 18px; font: 900 40px T; letter-spacing: .08em; text-transform: uppercase; color: #8fe36b; }}
.особ {{ left: 1650px; top: 2000px; width: 1180px; }}
.особ ul {{ margin: 0; padding: 0; list-style: none; font-size: 36px; line-height: 1.32; }}
.особ li {{ padding-left: 44px; position: relative; margin-bottom: 10px; }}
.особ li::before {{ content: ""; position: absolute; left: 0; top: 13px; width: 20px; height: 20px;
  background: #7ee05a; clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%); }}
.состав {{ left: 1650px; top: 2410px; width: 1180px; font-size: 27px; line-height: 1.36; color: #d8d2c0; }}
.состав h3 {{ font-size: 32px; margin-bottom: 10px; }}
.низ {{ position: absolute; left: 0; right: 0; bottom: 0; height: 260px; display: flex; align-items: center;
  gap: 60px; padding: 0 160px; box-sizing: border-box;
  background: linear-gradient(180deg, rgba(0,0,0,0), rgba(0,0,0,.35)); }}
.знак {{ display: flex; flex-direction: column; align-items: center; gap: 10px; font: 700 34px N; }}
.знак .к {{ width: 120px; height: 120px; border: 5px solid #8fe36b; border-radius: 18px;
  display: flex; align-items: center; justify-content: center; font: 900 46px T; color: #8fe36b; }}
.место {{ border: 3px dashed rgba(239,233,216,.35); color: rgba(239,233,216,.5); font: 700 26px N;
  display: flex; align-items: center; justify-content: center; text-align: center; }}
</style></head><body><div class="з">
<div class="арт"></div><div class="сталь"></div>
<div class="стол-обёртка"><div class="стол"></div></div>
<img class="лого" src="{url(ЛОГО)}">
<div class="вводка">
<p class="мир">Из выжженной земли прорастает <b>келемий</b> — кристалл, ради которого четыре
нации пришли на одну равнину. Здесь каждый удар возвращается: за снесённый завод — завод,
за разбитый взвод — взвод. Это и есть закон возмездия.</p>
<p>«Lex Talionis» — стратегия на 2–4 игроков о войне за ресурс. Стройте базу, добывайте келемий,
нанимайте войска, сносите чужие здания и вкладывайте добытое в технологии. Каждый ход — одна карта
приказа и <b>выбор из двух</b>: добыть или построить, двинуть войска или ударить. Приказы
вскрывают по кругу, и каждое ваше решение ломает чей-то план.</p>
</div>
<div class="девиз">Око за око. <span>Кристалл за кристалл.</span></div>
<div class="веер">{карты}</div>
<div class="подпись-дейст">Пять действий — каждое на выбор из двух</div>
<div class="действия">{значки}</div>
<div class="плашка особ"><h3>Особенности</h3><ul>
<li>Приказы в открытую по кругу: 4 хода за раунд, без простоя</li>
<li>Три трека технологий, модули и золотые улучшения</li>
<li>Поле из блоков — каждая партия на новой карте</li>
<li>Пять путей к победным очкам и две мгновенные победы</li>
</ul></div>
<div class="плашка состав"><h3>Состав игры</h3>
10 блоков поля • 8 тайлов зарождения • 8 тайлов недоступных гексов • 14 нейтральных построек •
планшет науки • планшет рынка • 4 планшета войск • 4 планшета хранилища • 48 жетонов зданий •
64 жетона войск • 4 жетона супер-войск • 28 модулей • 8 жетонов хранилища • 196 карт •
192 кубика • 50 монет • фишка первого игрока • 2 мешочка • книга правил • справочник
</div>
<div class="низ">
<div class="знак"><div class="к">2–4</div>игрока</div>
<div class="знак"><div class="к">14+</div>лет</div>
<div class="знак"><div class="к" style="width:220px">90–120</div>минут</div>
<div class="место" style="width:520px;height:170px;margin-left:auto">издатель, адрес,<br>маркировка</div>
<div class="место" style="width:360px;height:170px">штрихкод</div>
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
pdf = pymupdf.open()
стр = pdf.new_page(width=300 / 25.4 * 72, height=300 / 25.4 * 72)
стр.insert_image(стр.rect, filename=png)
pdf.save(os.path.join(ВЫХОД, "задник коробки.pdf"), deflate=True)
print("готово:", png)
