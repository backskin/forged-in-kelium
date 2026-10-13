# -*- coding: utf-8 -*-
"""ЗАДНИК КОРОБКИ «Lex Talionis», версия 3 (дизайнер 14.10.2026).

Образцы дизайнера: «Звёздные войны: Восстание» (главный), «Сумерки Империи»,
«Эверделл», Ankh, Blood Rage, Дюна. Империя, Civilization, Серп, Прага,
Комната 25, Yellow & Yangtze, Fallout. Схема у всех одна:
  — заголовок-крючок, абзац мира от второго лица («вы…» и что на кону),
    абзац об игре простыми словами (что вы делаете и как устроен ход),
    итог в конце;
  — чистая сцена, на её фоне разложены компоненты;
  — выноска о главном богатстве коробки (там — «Более 150 пластиковых
    фигурок!», у нас — куча объёмных жетонов: куча_жетонов.py);
  — рамка состава, значки игроков/возраста/времени, место под маркировку.

Раскладка квадрата 300×300 мм (10 точек на мм):
  верх      — сцена с харвестером; слева логотип и тексты на затемнении,
              справа шеренга лидеров четырёх наций (лица с планшетов войск);
  середина  — слева выноска с кучей жетонов, справа стол в перспективе;
  низ       — значки, рамка состава, маркировка.
Тексты — по канону мира (rules/КНИГА МИРА) и главе 1 книги правил.

Запуск: python задник_коробки.py → design-docs/обложка/задник коробки.png (+ .pdf)
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
СТОЛ = os.path.join(D, "стол-коробка-4.png")          # другое поле, чем в книге
КУЧА = os.path.join(D, "куча-жетонов.png")            # куча_жетонов.py
АРТ = фон(38)                                          # харвестер в поле келемия
ПЕРС = os.path.join(КОРЕНЬ, "illustrations", "персонажи-png")   # вырезать.py

# лидеры наций: один холст 1152×1728 у всех → одна высота = один масштаб
лидеры = "".join(
    f'<img class="перс" src="{url(os.path.join(ПЕРС, ф + ".png"))}" style="left:{1380 + i * 365}px">'
    for i, ф in enumerate(["красный", "синий", "зелёный", "жёлтый"]))

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Bold.ttf')}"); font-weight: 700; }}
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Black.ttf')}"); font-weight: 900; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Regular.ttf')}"); font-weight: 400; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Bold.ttf')}"); font-weight: 700; }}
html, body {{ margin: 0; width: 3000px; height: 3000px; overflow: hidden; background: #141a18; }}
.з {{ position: relative; width: 3000px; height: 3000px; overflow: hidden; color: #efe9d8; font-family: N; }}
.арт {{ position: absolute; inset: 0 0 auto 0; height: 1750px;
  background: url("{url(АРТ)}") 62% 35% / cover; }}
.арт::after {{ content: ""; position: absolute; inset: 0; background:
  linear-gradient(180deg, rgba(20,26,24,0) 50%, rgba(20,26,24,.8) 80%, #141a18 100%),
  linear-gradient(90deg, rgba(20,26,24,.97) 0%, rgba(20,26,24,.9) 33%, rgba(20,26,24,.3) 47%, rgba(20,26,24,0) 58%); }}
.лого {{ position: absolute; left: 120px; top: 70px; width: 1000px; filter: drop-shadow(0 14px 24px rgba(0,0,0,.8)); }}
.текст {{ position: absolute; left: 140px; top: 470px; width: 1150px; }}
.текст h1 {{ margin: 0 0 26px; font: 900 64px/1.1 T; color: #fff; letter-spacing: .02em; text-transform: uppercase;
  text-shadow: 0 4px 14px rgba(0,0,0,.8); }}
.текст p {{ margin: 0 0 24px; font: 400 37px/1.38 N; color: #ddd7c5; }}
.текст p.мир {{ font-style: italic; color: #c9c3b1; }}
.текст p.итог {{ font: 700 39px/1.35 N; color: #fff; }}
.текст b {{ color: #8fe36b; font-weight: 700; }}
.перс {{ position: absolute; top: 420px; width: 570px; height: 855px; z-index: 3;
  -webkit-mask-image: linear-gradient(180deg, #000 80%, transparent 99%);
  filter: drop-shadow(0 24px 30px rgba(0,0,0,.7)); }}
.стол-обёртка {{ position: absolute; left: 1150px; top: 1330px; width: 2000px; height: 1300px; perspective: 2600px; z-index: 4; }}
.стол {{ position: absolute; inset: 0; background: url("{url(СТОЛ)}") center / contain no-repeat;
  transform: rotateX(46deg) rotateZ(-10deg); transform-origin: 50% 50%;
  filter: drop-shadow(0 40px 34px rgba(0,0,0,.75)); }}
.выноска {{ position: absolute; left: 70px; top: 1720px; width: 1180px; height: 760px; z-index: 5;
  border: 7px solid #8fe36b; border-radius: 60px;
  background: radial-gradient(ellipse at 50% 35%, rgba(60,80,50,.55), rgba(15,20,18,.92) 75%);
  box-shadow: 0 30px 50px rgba(0,0,0,.6), inset 0 0 60px rgba(0,0,0,.5); }}
.куча {{ position: absolute; left: -40px; top: -150px; width: 1250px; }}
.выноска .надпись {{ position: absolute; left: 0; right: 0; bottom: 46px; text-align: center;
  font: 900 70px/1.05 T; color: #fff; text-transform: uppercase; text-shadow: 0 5px 16px rgba(0,0,0,.95); }}
.выноска .надпись small {{ display: block; font: 700 38px N; color: #8fe36b; text-transform: none; margin-top: 10px; }}
.низ {{ position: absolute; left: 0; right: 0; bottom: 0; height: 400px; padding: 0 90px; box-sizing: border-box;
  display: flex; align-items: center; gap: 50px; z-index: 6;
  background: linear-gradient(180deg, rgba(14,18,17,0), #0e1211 22%); }}
.знаки {{ display: flex; gap: 26px; }}
.знак {{ display: flex; flex-direction: column; align-items: center; gap: 8px; font: 700 28px N; }}
.знак .к {{ min-width: 116px; height: 100px; padding: 0 14px; box-sizing: border-box; border: 5px solid #8fe36b;
  border-radius: 16px; display: flex; align-items: center; justify-content: center; font: 900 42px T; color: #8fe36b; }}
.состав {{ flex: 1; border: 5px solid #8fe36b; border-radius: 26px; padding: 24px 40px 26px; background: rgba(20,28,24,.9);
  font: 400 29px/1.42 N; color: #d8d2c0; text-align: center; }}
.состав h2 {{ margin: 0 0 8px; font: 900 40px T; color: #8fe36b; letter-spacing: .08em; text-transform: uppercase; }}
.место {{ border: 3px dashed rgba(239,233,216,.3); color: rgba(239,233,216,.45); font: 700 24px N;
  display: flex; align-items: center; justify-content: center; text-align: center; height: 180px; }}
</style></head><body><div class="з">
<div class="арт"></div>
{лидеры}
<div class="стол-обёртка"><div class="стол"></div></div>
<img class="лого" src="{url(ЛОГО)}">
<div class="текст">
<h1>Из Земли пророс кристалл…</h1>
<p class="мир">Люди давно расселились по Солнечной системе: одни живут на корабле у Венеры,
другие — на Марсе, третьи — на спутнике Юпитера, а земляне остались дома. Каждая нация
уверена, что остальные — отсталые дикари. Но из недр Земли пророс <b>келемий</b> — чистая
энергия в твёрдом виде. Земляне начали процветать, и космические нации, у которых силы на
исходе, возвращаются домой, чтобы забрать кристалл себе.</p>
<p>В игре «Lex Talionis» вы возглавите одну из четырёх наций. Стройте базу, добывайте келемий,
нанимайте пехоту, технику и авиацию, уничтожайте чужие здания и войска. Обломки разбитого
врага — <b>трофеи</b>: на них вы двигаете вперёд свои технологии.</p>
<p>В начале каждого круга все разом выбирают <b>карту приказа</b>, а потом по очереди вскрывают
их и сразу ходят. Действий всего пять, и в каждом — выбор из двух: добыть или построить
добытчик, двинуть войска или начать бой, торговать на Рынке или вкладываться в Науку.</p>
<p class="итог">Победит тот, у кого больше очков, — или тот, кто первым поднимется на вершины
всех трёх треков технологий.</p>
</div>
<div class="выноска"><img class="куча" src="{url(КУЧА)}">
<div class="надпись">Более 150 жетонов<small>зданий, войск и модулей — и 192 кубика</small></div></div>
<div class="низ">
<div class="знаки">
<div class="знак"><div class="к">2–4</div>игрока</div>
<div class="знак"><div class="к">14+</div>лет</div>
<div class="знак"><div class="к">90–120</div>минут</div>
</div>
<div class="состав"><h2>Состав игры</h2>10 блоков поля • 16 тайлов поля • 14 нейтральных построек •
10 планшетов • 116 жетонов зданий и войск • 28 модулей • 8 жетонов хранилища • 196 карт •
192 кубика • 50 монет • фишка первого игрока • 2 мешочка • книга правил • справочник</div>
<div class="место" style="width:300px">издатель,<br>маркировка</div>
<div class="место" style="width:260px">штрихкод</div>
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
