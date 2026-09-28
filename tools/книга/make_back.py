# -*- coding: utf-8 -*-
"""Задняя сторона обложки — памятка на всю игру.

Содержание — четыре карты-памятки дизайнера (Раунд игры, Розыгрыш карт
приказов, две Памятки действий), выверенные по книге: где карта и книга
расходятся, берётся книга. Иконки — врезки с самих карт (папка icons/).

Оформление — «командная панель»: графит, скошенные углы, тонкие зелёные канты
келемия, охра для чисел и плашки-заголовки с косым срезом, как на картах
приказов. Всё плоское и векторное, без свечения и градиентов — как велит
стильбиблия для интерфейсной графики.
"""
import base64
import glob
import io
import os

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
# ИКОНКИ БЕРУТСЯ ИЗ ЭКСПОРТА ДИЗАЙНЕРА, а не из врезок: врезки резались с карт
# и тащили за собой кусок фона, отчего памятка выглядела грязно (замечание
# дизайнера 15.09.2026). Врезки остаются запасным вариантом для значков,
# которых в экспорте нет.
КОРЕНЬ_ИКОНОК = os.path.join("C:", os.sep, "shared", "forged-in-kelium", "rules")
ПАПКИ = [
    os.path.join(КОРЕНЬ_ИКОНОК, "иконки-экспорт"),
    os.path.join(КОРЕНЬ_ИКОНОК, "иконки"),
    os.path.join(D, "icons"),
]
ICONS = os.path.join(D, "icons")
КНИГА = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*\вёрстка\Книга правил.html")[0]
МАРКЕР = "<!-- ============ ЗАДНЯЯ ОБЛОЖКА ============ -->"


def найти(name):
    for папка in ПАПКИ:
        п = os.path.join(папка, name + ".png")
        if os.path.exists(п):
            return п
    raise FileNotFoundError(name)


def b64(name, h=None):
    im = Image.open(найти(name)).convert("RGBA")
    if h and im.height > h:
        im = im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def и(name):
    """Мелкая иконка в строке текста."""
    return f'<img class="и" src="{b64(name, 160)}" alt="">'


def мед(name):
    """Медальон действия: печатный кружок «действие» и поверх — само действие.

    Своя зелёная обводка заменена настоящей иконкой действия из экспорта
    (просьба дизайнера 15.09.2026). Иконке действия позволено чуть вылезти
    за край кружка — обе картинки центрированы друг относительно друга.
    """
    return (f'<span class="мед"><img class="мед-круг" src="{b64("действие", 300)}" alt="">'
            f'<img class="мед-знак" src="{b64(name, 300)}" alt=""></span>')


# ---------------------------------------------------------------- тексты
#
# ВЁРСТКА 28.09.2026 — ПО СЕТКЕ ИЗ ДВУХ РЯДОВ. Верхний ряд: «Раунд игры» и
# «Пять действий» одной высоты; нижний: «Карта приказа», «Конец партии» и
# «Победные очки» — тоже одной. Прежде раунд еле влезал, а у карты приказа
# пустовала треть панели; медальоны действий были в полтора раза крупнее
# текста и распирали строки (замечания дизайнера 28.09.2026).
#
# ДЕЙСТВИЕ — ОДНА СТРОКА-РАЗВИЛКА: имя, ветка, «или», ветка. Так на памятке
# видно то, чем действие и устроено: одно из двух.

РАУНД = f"""
<div class="шапка"><span class="таб">Раунд игры</span></div>
<div class="фаза"><b>I</b><span>Обновление</span></div>
<p class="прим">В первом раунде — только шаг 1.</p>
<ol>
<li><b>Свалка:</b> одна из 5 карт приказов — рубашкой вверх у планшета войск. В руке остаются 4.</li>
<li><b>Рынок:</b> карта рынка уходит вместе с келемием на ней. Откройте следующую.</li>
<li><b>Первый игрок:</b> фишка {и('фишка-первого')} — соседу против часовой стрелки.</li>
</ol>
<div class="фаза"><b>II</b><span>Управление</span></div>
<p class="прим">4 круга. В каждом круге:</p>
<ol>
<li>Все одновременно выбирают карту приказа и кладут рубашкой вверх.</li>
<li>С первого игрока по часовой: вскрыл — сразу ходишь.</li>
</ol>
<p class="прим">Сыгранная карта лежит лицом вверх до конца раунда.</p>
<div class="фаза"><b>III</b><span>Возвращение</span></div>
<ol>
<li><b>Конец партии:</b> проверьте условия {и('флаг')}.</li>
<li><b>Трофеи:</b> каждый жетон на вашей свалке с {и('иконка-трофея')} на обороте — 1 кубик трофея в свободную ячейку хранилища. Жетоны — владельцам.</li>
<li><b>Приказы:</b> все 5 карт — в руку. Задания остаются как есть.</li>
</ol>
"""

ПРИКАЗ = f"""
<div class="шапка"><span class="таб">Карта приказа</span></div>
<ul>
<li><b>Верх</b> — оба действия {и('действие')}. Такую же карту в этом круге уже вскрыли — только <b>одно</b>.</li>
<li><b>Низ</b> {и('чужой-приказ')} — одно действие, если карту с названием низа в этом круге уже вскрыли.</li>
<li>Каждое действие — <b>одна ветка из двух</b>, за ход — один раз.</li>
<li><b>+1 спец-действие</b> {и('спец-действие')}: задание, арсенал, контейнеры, ЦУ из запаса или спец-плашка.</li>
</ul>
"""

ДЕЙСТВИЯ = [
    ("добыча", "Добыча", "освоить · исследовать",
     f"<b>Добыть:</b> каждый запитанный добытчик берёт келемий {и('келемий')} с тайла за своей стенкой или контейнер {и('контейнер')} со своего гекса.",
     f"<b>Построить добытчик</b> за его цену {и('монета')} — он сразу добывает."),
    ("питание", "Питание", "освоить · мобилизовать",
     f"<b>Переложить энергию:</b> любые свои кубики {и('энергия')} — с источников на ячейки {и('ячейка-энергии')} зданий и обратно.",
     "<b>Построить энергостанцию</b> — её кубики сразу раскладываются."),
    ("снабжение", "Снабжение", "мобилизовать · наступать",
     f"<b>Выпустить:</b> каждое запитанное военное здание и ЦУ делает жетон {и('жетон-войск')} своего рода или боеприпас {и('боеприпас')}.",
     "<b>Построить военное здание</b> — оно сразу выпускает."),
    ("командование", "Командование", "наступать · контролировать",
     "<b>Манёвр:</b> выведите войска с одного гекса и введите на него войска, которые дойдут.",
     f"<b>Бой:</b> выберите гекс — каждый ваш жетон рядом атакует один жетон на нём: 2 {и('боеприпас')} — любой, 1 {и('боеприпас')} — тип с планшета."),
    ("развитие", "Развитие", "контролировать · исследовать",
     f"<b>Рынок:</b> келемий {и('келемий')} на монеты {и('монета')}: 0→1, 1→3, 2→7; 1 келемий — задание; предложение карты рынка.",
     f"<b>Наука:</b> шаг на треке за трофеи {и('иконка-трофея')} или келемий {и('келемий')} — 2 / 3 / 4 / 5; обмены Научного отдела."),
]

КОНЕЦ = f"""
<div class="шапка"><span class="таб">Конец партии</span></div>
<p class="прим">Проверка — в Возвращении, шаг 1.</p>
<ul>
<li>{и('флаг')} На тайлах зарождения не осталось келемия.</li>
<li>{и('флаг')} В колоде рынка не осталось карт.</li>
</ul>
<p class="победа"><b>Сразу побеждает</b> тот, кто второй раз уничтожил ЦУ (втроём и вчетвером) или встал на вершины всех 3 треков.</p>
"""

ОЧКИ = f"""
<div class="шапка"><span class="таб">Победные очки</span><i>{и('по')} — 1 очко</i></div>
<ul class="две-кол">
<li><b>Треки технологий</b> — 1 · 2 · 3 · 5 за ступень с вашим кубиком.</li>
<li><b>Установленный арсенал</b> и супер-арсенал — звёзды на картах.</li>
<li><b>Золотые модули</b> — 1 за каждый.</li>
<li><b>Модули блокировки боя</b> стороной 3 — по 3.</li>
<li><b>Супер-задание</b> — звёзды за условия карты.</li>
<li><b>Модули хранилища</b> — 1 за пустую ячейку.</li>
</ul>
<p class="ничья">Ничья: больше гексов с жетонами → трофеев → келемия.</p>
"""

ЛЕГЕНДА = [
    ("монета", "монета"), ("келемий", "келемий"), ("боеприпас", "боеприпас"),
    ("трофей", "кубик<br>трофея"), ("иконка-трофея", "трофей"),
    ("энергия", "энергия"), ("по", "очко"), ("действие", "действие"),
    ("спец-действие", "спец-<br>действие"), ("чужой-приказ", "низ<br>карты"),
    ("флаг", "конец<br>партии"),
    ("карта-задания", "задание"), ("карта-супер-задания", "супер-<br>задание"),
    ("карта-арсенала", "арсенал"), ("карта-супер-арсенала", "супер-<br>арсенал"),
    ("контейнер", "контейнер"), ("жетон-войск", "жетон<br>войск"),
    ("военное-здание", "военное<br>здание"), ("ячейка-энергии", "ячейка<br>энергии"),
    ("круг-энергии", "круг<br>энергии"), ("прочность", "прочность"), ("урон", "урон"),
    ("уничтожение", "уничто-<br>жение"),
]


def действие(k):
    ikon, имя, приказ, a, b = ДЕЙСТВИЯ[k]
    return (f'<div class="дей"><div class="дей-гол">{мед(ikon)}'
            f'<div class="дей-имя">{имя}<span>{приказ}</span></div></div>'
            f'<div class="вет">{a}</div><div class="или">или</div><div class="вет">{b}</div></div>')


# Сетка, мм: поля 8; верхний ряд 26–146, нижний 149–198; значки 200–215.
ВЕРХ, НИЗ_В, НИЗ_Н, НИЗ_К = 26, 146, 149, 198


def страница():
    сетка = "".join(действие(k) for k in range(5))
    легенда = "".join(f'<div>{и(a)}<span>{b}</span></div>' for a, b in ЛЕГЕНДА)
    вв = НИЗ_В - ВЕРХ
    вн = НИЗ_К - НИЗ_Н
    return f"""{МАРКЕР}
<p class="подпись-разворота">Задняя сторона обложки · памятка</p>
<div class="разворот одна">
  <div class="стр обложка задняя">
    <div class="сетка-фон"></div>
    <div class="рамка-техно"></div>

    <div class="титул">
      <div class="титул-плашка">Памятка</div>
      <div class="титул-линия"><span>раунд</span><span>карта приказа</span><span>пять действий</span><span>конец партии</span></div>
    </div>

    <div class="пан" style="left:8mm;top:{ВЕРХ}mm;width:64mm;height:{вв}mm"><div class="пан-в">{РАУНД}</div></div>
    <div class="пан дейпан" style="left:75mm;top:{ВЕРХ}mm;width:137mm;height:{вв}mm"><div class="пан-в">
      <div class="шапка"><span class="таб">Пять действий</span><i>каждое — одна ветка из двух</i></div>
      <div class="дей-сетка">{сетка}</div>
    </div></div>

    <div class="пан" style="left:8mm;top:{НИЗ_Н}mm;width:64mm;height:{вн}mm"><div class="пан-в">{ПРИКАЗ}</div></div>
    <div class="пан низ" style="left:75mm;top:{НИЗ_Н}mm;width:60mm;height:{вн}mm"><div class="пан-в">{КОНЕЦ}</div></div>
    <div class="пан низ" style="left:138mm;top:{НИЗ_Н}mm;width:74mm;height:{вн}mm"><div class="пан-в">{ОЧКИ}</div></div>

    <div class="пан значки" style="left:8mm;top:200mm;width:204mm;height:15mm"><div class="пан-в">
      <div class="легенда">{легенда}</div>
    </div></div>
  </div>
</div>
"""


CSS = r"""
  /* ---- задняя сторона обложки: командная панель ---- */
  .стр.обложка.задняя {
    --графит: #F7F1E1; --панель: rgba(247, 241, 225, .94); --панель2: rgba(255, 252, 244, .86); --кант: #186C24;
    --кант-т: rgba(42, 35, 24, .38); --крем: #2A2318; --крем2: #5E5139; --зел: #186C24; --охра: #6B4413;
    color: var(--крем); padding: 0; overflow: hidden;
    background: var(--графит) url("ЗАДНИК") center / cover no-repeat;
    font-family: "Tektur Narrow", "Tektur", sans-serif;
  }
  .задняя .сетка-фон { position: absolute; inset: 0; z-index: 0; opacity: 1;
    background-color: rgba(247, 241, 225, .62);
    background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='52' height='90' viewBox='0 0 52 90'><path d='M26 1 L50 15 L50 45 L26 59 L2 45 L2 15 Z M26 61 L50 75 L50 105 M26 61 L2 75 L2 105' fill='none' stroke='%232A2318' stroke-opacity='.07' stroke-width='1.1'/></svg>");
    background-size: 13mm 22.5mm; }
  .задняя .рамка-техно { position: absolute; inset: 4.5mm; z-index: 1; pointer-events: none;
    border: .45mm solid #2A2318;
    clip-path: polygon(6mm 0, 100% 0, 100% calc(100% - 6mm), calc(100% - 6mm) 100%, 0 100%, 0 6mm); }
  .задняя .рамка-техно::before, .задняя .рамка-техно::after { content: ""; position: absolute; width: 14mm; height: 14mm; border: .7mm solid var(--кант); }
  .задняя .рамка-техно::before { right: -.3mm; top: -.3mm; border-left: 0; border-bottom: 0; }
  .задняя .рамка-техно::after { left: -.3mm; bottom: -.3mm; border-right: 0; border-top: 0; }

  .задняя .титул { position: absolute; left: 8mm; top: 8.5mm; right: 8mm; height: 14mm; z-index: 2; display: flex; align-items: center; gap: 4mm; }
  .задняя .титул-плашка { background: var(--кант); color: #F7F1E1; font: 900 20pt/1 "Tektur", sans-serif; letter-spacing: .08em; text-transform: uppercase;
    padding: 2.6mm 7mm 2.2mm 5mm; clip-path: polygon(0 0, 100% 0, calc(100% - 4mm) 100%, 0 100%); }
  .задняя .титул-линия { flex: 1; display: flex; align-items: center; gap: 3mm; font: 600 8pt "Tektur", sans-serif; color: var(--крем2); text-transform: uppercase; letter-spacing: .12em; }
  .задняя .титул-линия::before, .задняя .титул-линия span + span::before { content: ""; display: inline-block; width: 1.6mm; height: 1.6mm; background: var(--охра); transform: rotate(45deg); margin-right: 3mm; vertical-align: middle; }
  .задняя .титул-линия::after { content: ""; flex: 1; height: .5mm; background: var(--кант); margin-left: 2mm; }

  .задняя .пан { position: absolute; z-index: 2; background: var(--кант-т);
    clip-path: polygon(3.6mm 0, 100% 0, 100% calc(100% - 3.6mm), calc(100% - 3.6mm) 100%, 0 100%, 0 3.6mm); }
  .задняя .пан-в { position: absolute; inset: .35mm; background: var(--панель); padding: 2.4mm 3mm 2.2mm;
    display: flex; flex-direction: column;
    clip-path: polygon(3.35mm 0, 100% 0, 100% calc(100% - 3.35mm), calc(100% - 3.35mm) 100%, 0 100%, 0 3.35mm); }
  .задняя .пан-в::after { content: ""; position: absolute; right: 2.2mm; top: 2.2mm; width: 9mm; height: .45mm; background: var(--кант); }

  .задняя .шапка { display: flex; align-items: center; gap: 2mm; margin: 0 0 1.8mm; }
  .задняя .шапка::after { content: ""; flex: 1; height: .3mm; background: var(--кант); opacity: .8; }
  .задняя .шапка i { font: 500 7pt "Tektur Narrow", sans-serif; color: var(--крем2); font-style: normal; white-space: nowrap; }
  .задняя .таб { display: inline-block; background: var(--кант); color: #F7F1E1; font: 800 8.6pt/1 "Tektur", sans-serif; text-transform: uppercase; letter-spacing: .06em;
    padding: 1.2mm 3.2mm 1mm 2.4mm; white-space: nowrap; clip-path: polygon(0 0, 100% 0, calc(100% - 2.2mm) 100%, 0 100%); }

  .задняя ul, .задняя ol { list-style: none; margin: 0; padding: 0; }
  .задняя li::marker { content: ""; }
  .задняя li { font: 500 7.9pt/1.28 "Tektur Narrow", sans-serif; color: var(--крем); margin: 0 0 1.1mm; padding-left: 3mm; position: relative; break-inside: avoid; }
  .задняя ul > li::before { content: ""; position: absolute; left: 0; top: 1.1mm; width: 1.5mm; height: 1.5mm; background: var(--зел); clip-path: polygon(0 0, 100% 50%, 0 100%); }
  /* ШАГИ ФАЗ — С НОМЕРАМИ: «в первом раунде только шаг 1» без номера не найти */
  .задняя ol { counter-reset: шаг; }
  .задняя ol > li { counter-increment: шаг; padding-left: 3.6mm; }
  .задняя ol > li::before { content: counter(шаг); position: absolute; left: 0; top: 0; font: 800 7.9pt/1.28 "Tektur", sans-serif; color: var(--охра); }
  .задняя li b { color: var(--зел); font-weight: 700; }
  .стр.обложка.задняя img { width: auto; height: auto; object-fit: contain; display: inline-block; }
  /* ИКОНКА В СТРОКЕ — РОСТОМ СО СТРОЧНУЮ С ЗАПАСОМ, не выше: крупная иконка
     раздвигала межстрочие, и строки с ней стояли реже прочих. */
  .стр.обложка.задняя .и { height: 3.7mm; width: auto; vertical-align: -.95mm;
    margin: 0 .2mm; background: none; border-radius: 0; padding: 0; outline: 0; }

  .задняя .фаза { display: flex; align-items: baseline; gap: 1.8mm; margin: 2.2mm 0 .8mm; border-bottom: .25mm solid var(--кант-т); padding-bottom: .6mm; }
  .задняя .шапка + .фаза { margin-top: .4mm; }
  .задняя .фаза b { font: 900 10pt/1 "Tektur", sans-serif; color: var(--охра); min-width: 5mm; }
  .задняя .фаза span { font: 800 9pt/1 "Tektur", sans-serif; color: var(--зел); text-transform: uppercase; letter-spacing: .04em; }
  .задняя .прим { font: 500 7.2pt/1.25 "Tektur Narrow", sans-serif; color: var(--крем2); margin: 0 0 1mm; }

  /* ПЯТЬ ДЕЙСТВИЙ — ПЯТЬ СТРОК-РАЗВИЛОК, растянутых на всю высоту панели */
  .задняя .дей-сетка { flex: 1; display: grid; grid-template-rows: repeat(5, 1fr); gap: 1.5mm; }
  .задняя .дей { display: grid; grid-template-columns: 39mm 1fr 6mm 1fr; align-items: center; column-gap: 1.8mm;
    background: var(--панель2); outline: .15mm solid var(--кант-т); outline-offset: -.15mm; padding: 1.2mm 2.4mm 1.2mm 1.6mm;
    clip-path: polygon(2mm 0, 100% 0, 100% calc(100% - 2mm), calc(100% - 2mm) 100%, 0 100%, 0 2mm); }
  .задняя .дей-гол { display: flex; align-items: center; gap: 1.8mm; align-self: stretch;
    border-right: .25mm solid var(--кант-т); padding-right: 1.4mm; }
  .задняя .мед { position: relative; width: 9.5mm; height: 9.5mm; flex: none; display: block; }
  .стр.обложка.задняя .мед .мед-круг { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; }
  .стр.обложка.задняя .мед .мед-знак { position: absolute; left: 50%; top: 50%;
    width: 100%; height: 100%; transform: translate(-50%, -50%); object-fit: contain; }
  .задняя .дей-имя { font: 800 7.9pt/1 "Tektur", sans-serif; color: var(--зел); text-transform: uppercase; letter-spacing: .01em; min-width: 0; }
  .задняя .дей-имя span { display: block; margin-top: .9mm; font: 600 6.2pt/1.15 "Tektur Narrow", sans-serif; color: var(--охра); text-transform: uppercase; letter-spacing: .03em; }
  .задняя .вет { font: 500 7.9pt/1.28 "Tektur Narrow", sans-serif; color: var(--крем); }
  .задняя .вет b { color: var(--зел); font-weight: 700; }
  .задняя .или { font: 800 6.6pt/1 "Tektur", sans-serif; color: #F7F1E1; background: var(--охра); text-transform: uppercase;
    text-align: center; padding: 1mm 0 .8mm; clip-path: polygon(0 0, 100% 0, calc(100% - 1mm) 100%, 1mm 100%); }

  .задняя .низ li { font-size: 7.6pt; }
  .задняя .победа { font: 500 7.6pt/1.28 "Tektur Narrow", sans-serif; margin: 1.4mm 0 0; padding-top: 1.2mm; border-top: .25mm dashed var(--кант-т); }
  .задняя .победа b { color: var(--охра); font-weight: 700; }
  .задняя .две-кол { column-count: 2; column-gap: 3.4mm; }
  .задняя .ничья { font: 500 7.2pt/1.2 "Tektur Narrow", sans-serif; color: var(--крем2); margin: auto 0 0; padding-top: 1mm; border-top: .25mm dashed var(--кант-т); }

  /* ПОЛОСА ЗНАЧКОВ: подпись читаемого кегля и строчными — прописные 4,8 pt
     были не видны вовсе (замечание дизайнера 28.09.2026). */
  .задняя .значки .пан-в { padding: 1.4mm 3mm 1.1mm; }
  .задняя .значки .пан-в::after { display: none; }
  .задняя .легенда { display: grid; grid-template-columns: repeat(23, 1fr);
    gap: 0 .4mm; align-items: start; }
  .задняя .легенда div { display: flex; flex-direction: column; align-items: center;
    gap: .4mm; font: 600 5.8pt/1.05 "Tektur Narrow", sans-serif; color: var(--крем);
    letter-spacing: 0; text-align: center; }
  .стр.обложка.задняя .легенда .и { height: 5.8mm; width: auto; vertical-align: 0;
    padding: 0; background: none; border-radius: 0; outline: 0; }
"""


ЗАДНИК = os.path.join(os.path.dirname(os.path.dirname(D)), "rules", "иллюстрации", "задник рулбука.jpg")


def задник():
    im = Image.open(ЗАДНИК).convert("RGB")
    im = im.resize((2000, round(im.height * 2000 / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=86, optimize=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    css = CSS.replace("ЗАДНИК", задник())
    # CSS — один раз, перед </style>
    if ".стр.обложка.задняя" not in t:
        t = t.replace("\n</style>", css + "\n</style>", 1)
    else:
        a = t.index("  /* ---- задняя сторона обложки")
        # свой блок кончается на следующем разделе стилей, а не на </style>:
        # иначе стёрлось бы всё, что дописано после него (случилось 28.09)
        b = t.find("\n  /* ---- ", a + 10)
        b = t.index("</style>", a) if b < 0 else b + 1
        t = t[:a] + css.lstrip("\n") + "\n" + t[b:]
    # СТАРУЮ СТРАНИЦУ УБРАТЬ ПО САМОЙ СТРАНИЦЕ, А НЕ ПО КОММЕНТАРИЮ: reflow
    # пересобирает тело книги и комментарии-маяки не сохраняет, из-за чего
    # обложка приклеивалась второй раз.
    метки = [МАРКЕР, '<p class="подпись-разворота">Задняя сторона обложки']
    for метка in метки:
        while метка in t:
            a = t.index(метка)
            # хвост страницы — до конца её разворота
            b = t.index('<div class="стр обложка задняя">', a)
            k = t.index("</div>", t.index('<div class="колонцифра"', b) if '<div class="колонцифра"' in t[b:t.index("</body>", b)] else b)
            b = t.index("</body>", a)
            t = t[:a] + t[b:]
    while '<div class="стр обложка задняя">' in t:
        a = t.rindex('<p class="подпись-разворота">', 0,
                     t.index('<div class="стр обложка задняя">'))
        b = t.index("</body>", a)
        t = t[:a] + t[b:]
    j = t.rindex("</body>")
    t = t[:j] + страница() + "\n" + t[j:]
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("задняя обложка собрана")


if __name__ == "__main__":
    main()
