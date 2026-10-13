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
        и «задник коробки — с вылетами 10 мм» (.png, .pdf 320×320 мм с TrimBox) для типографии
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
АРТ = фон(75)            # высадка у стены келемия (дизайнер 14.10.2026: фон поинтереснее)
ИЛЛ = os.path.dirname(ФОНЫ)
# низ — не голый чёрный: пустыня с кристаллами (текстура тайлов), притемнённая
ЗЕМЛЯ = os.path.join(ИЛЛ, sorted(x for x in os.listdir(ИЛЛ) if x.lower().endswith((".jpg", ".png", ".webp")))[10])
ПЕРС = os.path.join(КОРЕНЬ, "illustrations", "персонажи-png")   # вырезать.py

# ЛИДЕРЫ НАЦИЙ — как люди, стоящие рядом (дизайнер 14.10.2026: «равных
# размеров»). Замер на холсте 1152×1728: (y глаз, y подбородка). Масштаб
# каждого — чтобы «глаза–подбородок» было одинаковым, и глаза на одной линии.
ЗАМЕР = {"красный": (402, 567), "синий": (319, 483), "зелёный": (375, 532), "жёлтый": (375, 525)}
ГЛАЗА_Y, ЛИЦО, ШАГ = 760, 80, 380          # линия глаз, глаза–подбородок, шаг шеренги (точки задника)


def лидер(i, ф):
    глаз, подб = ЗАМЕР[ф]
    k = ЛИЦО / (подб - глаз)                  # точек задника на точку холста
    w, h = 1152 * k, 1728 * k
    return (f'<img class="перс" src="{url(os.path.join(ПЕРС, ф + ".png"))}" '
            f'style="left:{1340 + i * ШАГ:.0f}px; top:{ГЛАЗА_Y - глаз * k:.0f}px; '
            f'width:{w:.0f}px; height:{h:.0f}px">')


лидеры = "".join(лидер(i, ф) for i, ф in enumerate(["красный", "синий", "зелёный", "жёлтый"]))

В = 100                 # вылет 10 мм (10 точек на мм)
П = 3000 + 2 * В        # сторона листа с вылетами

HTML = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Bold.ttf')}"); font-weight: 700; }}
@font-face {{ font-family: T; src: url("{url('C:/Windows/Fonts/Tektur-Black.ttf')}"); font-weight: 900; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Regular.ttf')}"); font-weight: 400; }}
@font-face {{ font-family: N; src: url("{url('C:/Windows/Fonts/TekturNarrow-Bold.ttf')}"); font-weight: 700; }}
html, body {{ margin: 0; width: {П}px; height: {П}px; overflow: hidden; background: #141a18; }}
/* ВЫЛЕТЫ (дизайнер 14.10.2026): лист {П}×{П}, обрезной формат 3000×3000 сдвинут
   на {В} точек; фоны уходят под обрез, вся вёрстка — внутри формата */
.з {{ position: absolute; left: {В}px; top: {В}px; width: 3000px; height: 3000px; overflow: visible;
  color: #efe9d8; font-family: N; }}
.земля {{ position: absolute; inset: 1150px -{В}px -{В}px -{В}px;
  background: linear-gradient(180deg, rgba(20,26,24,.85) 0%, rgba(20,26,24,.5) 30%, rgba(20,26,24,.6) 100%),
    url("{url(ЗЕМЛЯ)}") 0 0 / 1100px auto repeat; filter: saturate(.75) brightness(.62); }}
.арт {{ position: absolute; inset: -{В}px -{В}px auto -{В}px; height: {1700 + В}px;
  background: url("{url(АРТ)}") 50% 30% / cover;
  -webkit-mask-image: linear-gradient(180deg, #000 62%, transparent 98%); }}
.арт::after {{ content: ""; position: absolute; inset: 0; background:
  linear-gradient(180deg, rgba(20,26,24,0) 55%, rgba(20,26,24,.5) 90%),
  linear-gradient(90deg, rgba(20,26,24,.97) 0%, rgba(20,26,24,.9) 33%, rgba(20,26,24,.3) 47%, rgba(20,26,24,0) 58%); }}
.лого {{ position: absolute; left: 120px; top: 70px; width: 1000px; filter: drop-shadow(0 14px 24px rgba(0,0,0,.8)); }}
.текст {{ position: absolute; left: 140px; top: 470px; width: 1150px; }}
.текст h1 {{ margin: 0 0 26px; font: 900 64px/1.1 T; color: #fff; letter-spacing: .02em; text-transform: uppercase;
  text-shadow: 0 4px 14px rgba(0,0,0,.8); }}
.текст p {{ margin: 0 0 24px; font: 400 37px/1.38 N; color: #ddd7c5; }}
.текст p.мир {{ font-style: italic; color: #c9c3b1; }}
.текст p.итог {{ font: 700 39px/1.35 N; color: #fff; }}
.текст b {{ color: #8fe36b; font-weight: 700; }}
.перс {{ position: absolute; z-index: 3;
  -webkit-mask-image: linear-gradient(180deg, #000 74%, transparent 92%);
  filter: drop-shadow(0 24px 30px rgba(0,0,0,.7)); }}
.стол-обёртка {{ position: absolute; left: 1300px; top: 1300px; width: 1680px; height: 1040px; perspective: 2600px; z-index: 4; }}
.стол {{ position: absolute; inset: 0; background: url("{url(СТОЛ)}") center / contain no-repeat;
  transform: rotateX(46deg) rotateZ(-10deg); transform-origin: 50% 50%;
  filter: drop-shadow(0 40px 34px rgba(0,0,0,.75)); }}
.выноска {{ position: absolute; left: 70px; top: 1520px; width: 1160px; height: 820px; z-index: 5;
  border: 7px solid #8fe36b; border-radius: 60px;
  background: radial-gradient(ellipse at 50% 35%, rgba(60,80,50,.55), rgba(15,20,18,.92) 75%);
  box-shadow: 0 30px 50px rgba(0,0,0,.6), inset 0 0 60px rgba(0,0,0,.5); }}
.куча {{ position: absolute; left: 50px; top: 20px; width: 1060px; }}
.выноска .надпись {{ position: absolute; left: 0; right: 0; bottom: 46px; text-align: center;
  font: 900 70px/1.05 T; color: #fff; text-transform: uppercase; text-shadow: 0 5px 16px rgba(0,0,0,.95); }}
.выноска .надпись small {{ display: block; font: 700 38px N; color: #8fe36b; text-transform: none; margin-top: 10px; }}
.низ {{ position: absolute; left: 60px; right: 60px; top: 2390px; bottom: 50px; z-index: 6;
  display: flex; gap: 36px; }}
.состав {{ flex: 1; border: 5px solid #8fe36b; border-radius: 30px; padding: 22px 40px 24px;
  background: rgba(16,22,19,.93); box-shadow: 0 20px 40px rgba(0,0,0,.5); }}
.состав h2 {{ margin: 0 0 16px; font: 900 50px T; color: #8fe36b; letter-spacing: .08em; text-transform: uppercase; text-align: center; }}
.столбцы {{ display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0 44px; }}
.столбцы h3 {{ margin: 0 0 8px; font: 900 36px T; color: #fff; text-transform: uppercase; letter-spacing: .05em; }}
.столбцы ul {{ margin: 0; padding: 0; list-style: none; font: 400 35px/1.3 N; color: #d8d2c0; }}
.столбцы li {{ margin-bottom: 4px; padding-left: 26px; text-indent: -26px; }}
.столбцы li::before {{ content: "◆ "; color: #8fe36b; font-size: 22px; vertical-align: 4px; }}
.столбцы b {{ color: #fff; font-weight: 700; }}
.справа {{ width: 380px; display: flex; flex-direction: column; gap: 22px; }}
.знаки {{ display: flex; justify-content: space-between; }}
.знак {{ display: flex; flex-direction: column; align-items: center; gap: 6px; font: 700 26px N; }}
.знак .к {{ width: 112px; height: 96px; box-sizing: border-box; border: 5px solid #8fe36b;
  border-radius: 16px; display: flex; align-items: center; justify-content: center; font: 900 38px T; color: #8fe36b;
  background: rgba(16,22,19,.9); }}
.знак .к.д {{ font-size: 25px; letter-spacing: -.02em; }}
.место {{ flex: 1;  border: 3px dashed rgba(239,233,216,.3); color: rgba(239,233,216,.45); font: 700 24px N;
  display: flex; align-items: center; justify-content: center; text-align: center; background: rgba(16,22,19,.6); }}
</style></head><body><div class="з">
<div class="земля"></div><div class="арт"></div>
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
<div class="состав"><h2>Состав игры</h2><div class="столбцы">
<div><h3>Поле</h3><ul>
<li><b>10</b> блоков поля: 5 больших и 5 малых</li>
<li><b>8</b> тайлов зарождения: 4 стартовых и 4 больших</li>
<li><b>8</b> тайлов недоступных гексов</li>
<li><b>14</b> нейтральных построек</li>
<li>планшет науки и планшет рынка</li>
<li>фишка первого игрока, 2 мешочка</li></ul></div>
<div><h3>Наборы четырёх наций</h3><ul>
<li><b>4</b> планшета войск и <b>4</b> планшета хранилища</li>
<li><b>48</b> жетонов зданий: ЦУ, казармы, заводы, авиабазы, добытчики, энергостанции</li>
<li><b>64</b> жетона войск: пехота, техника, авиация, вышки</li>
<li><b>20</b> карт приказов, <b>32</b> кубика технологий</li>
<li><b>4</b> памятки хода и <b>4</b> памятки действий</li></ul></div>
<div><h3>Общий запас</h3><ul>
<li><b>28</b> модулей: 12 сборки, 12 боя, 4 блокировки боя</li>
<li><b>4</b> жетона супер-войск, <b>8</b> жетонов хранилища</li>
<li><b>176</b> карт: 74 задания, 56 арсенала, 10 рынка, 36 контейнеров</li>
<li><b>160</b> кубиков: келемий, боеприпасы, трофеи, энергия</li>
<li><b>50</b> монет, книга правил, справочник</li></ul></div>
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
</div></body></html>"""

os.makedirs(ВЫХОД, exist_ok=True)
html = os.path.join(D, "_задник.html")
open(html, "w", encoding="utf-8").write(HTML)
лист = os.path.join(ВЫХОД, "задник коробки — с вылетами 10 мм.png")
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--user-data-dir=" + os.path.join(D, "chr"), "--virtual-time-budget=8000",
                f"--window-size={П},{П}", "--screenshot=" + лист, url(html)], check=True,
               capture_output=True)
os.remove(html)
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
