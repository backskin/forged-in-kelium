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
    return f'<img class="мед" src="{b64(name, 300)}" alt="">'


# ---------------------------------------------------------------- тексты

РАУНД = f"""
<div class="шапка"><span class="таб">Раунд игры</span></div>
<div class="фаза"><b>I</b><span>Обновление</span><i>в первом раунде — только шаг 2</i></div>
<ul>
<li>Карта рынка уходит вместе с келемием на ней. Откройте следующую.</li>
<li><b>Свалка:</b> одна из 5 карт приказов рубашкой вверх у планшета войск. В руке остаются 4.</li>
<li>Фишка {и('фишка-первого')} первого игрока — соседу по часовой стрелке.</li>
</ul>
<div class="фаза"><b>II</b><span>Управление</span><i>4 круга</i></div>
<ul>
<li>Все одновременно выбирают карту приказа и кладут взакрытую.</li>
<li>С первого игрока по часовой: вскрыл — сразу ходишь.</li>
<li>Сыгранная карта лежит лицом вверх до конца раунда.</li>
</ul>
<div class="фаза"><b>III</b><span>Возвращение</span><i></i></div>
<ul>
<li>Проверка условий {и('флаг')} конца партии.</li>
<li><b>Трофеи:</b> каждый жетон на свалке с {и('иконка-трофея')} на обороте — 1 кубик трофея в свободную ячейку хранилища. Жетоны — владельцам.</li>
<li>Все <b>5</b> карт приказов — в руку.</li>
<li>В руке не больше <b>2</b> карт заданий.</li>
</ul>
"""

ПРИКАЗ = f"""
<div class="шапка"><span class="таб">Карта приказа</span></div>
<ul>
<li><b>Выбранный (верхний) приказ</b> — оба {и('действие')} действия, каждое один раз. Такой же приказ уже вскрыт в этом круге — только <b>одно</b>.</li>
<li><b>Чужой (нижний) приказ</b> {и('чужой-приказ')} — одно действие, если его как выбранный уже вскрыл другой игрок.</li>
<li><b>+ 1</b> {и('спец-действие')} <b>спец-действие:</b> карта задания или арсенала, вскрытие контейнеров, спец-плашка карты приказа — Движение · Монета · Задание.</li>
<li><b>Безопасность</b> — любые <b>2</b> разных действия из восьми и спец-действие. Совпадения не боится и не создаёт.</li>
<li>Действия — в любом порядке, одно за другим. Можно сыграть меньше.</li>
</ul>
"""

ДЕЙСТВИЯ = [
    ("стройка", "Стройка", "Инфраструктура", f"""
<li><b>Поставить</b> здание из запаса за его цену {и('монета')} на свободные сектора подряд в зоне стройки.</li>
<li><b>Снести</b> своё здание в запас: +1 {и('монета')}. ЦУ не сносится.</li>
<li>С каждым зданием — одно из двух за действие.</li>"""),
    ("смена-энергии", "Смена энергии", "Инфраструктура", f"""
<li>Каждый источник срабатывает один раз:</li>
<li><b>отдаёт</b> {и('энергия')} свои кубики на ячейки {и('ячейка-энергии')} своих зданий как угодно,</li>
<li>или <b>забирает</b> их обратно с любых ячеек.</li>"""),
    ("снаряжение", "Снаряжение", "Разработка", f"""
<li>Каждое запитанное военное здание {и('военное-здание')} делает одно:</li>
<li><b>жетон</b> {и('жетон-войск')} своего рода войск — внутрь здания или на его гекс,</li>
<li>или <b>боеприпасы</b> {и('боеприпас')} в хранилище. Числа — на планшете войск.</li>"""),
    ("добыча", "Добыча", "Разработка", f"""
<li>Каждый запитанный добытчик берёт одно:</li>
<li><b>келемий</b> {и('келемий')} с тайла зарождения за своей стенкой,</li>
<li>или <b>карту контейнера</b> {и('контейнер')} с соседнего сектора. Опустошил тайл — награда с оборота.</li>"""),
    ("манёвр", "Манёвр", "Наступление", f"""
<li>Выберите гекс: каждый ваш жетон войск с него идёт даром на свою скорость или меньше.</li>
<li>Затем любой жетон с другого гекса — за 1 {и('боеприпас')}.</li>
<li>Стенки закрывают проход. Пехота может войти в гарнизон.</li>"""),
    ("бой", "Бой", "Наступление", f"""
<li>Выберите соседний гекс: каждый ваш жетон рядом атакует один жетон на нём.</li>
<li><b>Универсальная</b> атака — 2 {и('боеприпас')}, любой тип; <b>специальная</b> — 1 {и('боеприпас')}, тип из ячейки планшета. Урон 1.</li>
<li>Здания закрывают войска от наземных атак.</li>"""),
    ("рынок", "Рынок", "Приобретения", f"""
<li>Обмены — сколько угодно раз: 1 {и('келемий')} → 3 {и('монета')}, 2 {и('келемий')} → 7 {и('монета')}, 1 {и('келемий')} → 2 {и('карта-задания')}.</li>
<li><b>Предложение карты рынка</b> — одно за действие; келемий ложится в ячейку карты.</li>"""),
    ("наука", "Наука", "Приобретения", f"""
<li>Платите трофеями {и('иконка-трофея')} {и('трофей')} — кубиками или жетонами со свалки:</li>
<li><b>ступень трека</b> по порядку — 1 / 2 / 3 / 4;</li>
<li><b>1</b> — смена модуля · <b>2</b> — позолота · <b>2</b> — карта арсенала.</li>"""),
]

КОНЕЦ = f"""
<div class="шапка"><span class="таб">Конец партии</span><i>проверка в Возвращении</i></div>
<ul>
<li>{и('флаг')} На поле остался последний тайл зарождения.</li>
<li>{и('флаг')} Заняты вершины всех трёх треков технологий.</li>
<li>{и('флаг')} В Обновлении нечем открыть новую карту рынка.</li>
<li class="победа"><b>Военная победа</b> {и('уничтожение')} — уничтожить ЦУ, уже имея перевёрнутый модуль блокировки боя.</li>
</ul>
"""

ОЧКИ = f"""
<div class="шапка"><span class="таб">Победные очки</span><i>{и('по')} только это</i></div>
<ul class="две-кол">
<li><b>Треки технологий</b> — 1 · 1 · 2 · 3 за ступень с вашим кубиком.</li>
<li><b>Установленный арсенал</b> и супер-арсенал — звёзды на картах.</li>
<li><b>Золотые модули</b> — 1 за каждый.</li>
<li><b>Модули блокировки боя</b> стороной 3 — по 3.</li>
<li><b>Супер-задание</b> — обе категории карты.</li>
<li><b>Жетоны модулей хранилища</b> — 1 за пустую ячейку.</li>
</ul>
<p class="ничья">Ничья: гексы с жетонами → трофеи → келемий.</p>
"""

ЛЕГЕНДА = [
    ("монета", "монета"), ("келемий", "келемий"), ("боеприпас", "боеприпас"),
    ("трофей", "трофей"), ("энергия", "энергия"), ("по", "очко"),
    ("кубик-технологий", "кубик<br>техно"), ("действие", "действие"),
    ("спец-действие", "спец-<br>действие"), ("чужой-приказ", "чужой<br>приказ"),
    ("флаг", "конец<br>партии"),
    ("карта-задания", "задание"), ("карта-супер-задания", "супер-<br>задание"),
    ("карта-арсенала", "арсенал"), ("карта-супер-арсенала", "супер-<br>арсенал"),
    ("контейнер", "контейнер"), ("жетон-войск", "жетон<br>войск"),
    ("военное-здание", "военное<br>здание"), ("ячейка-энергии", "ячейка<br>энергии"),
    ("круг-энергии", "круг<br>энергии"), ("прочность", "прочность"), ("урон", "урон"),
]

# НОМЕРА СТРАНИЦ БЕРУТСЯ ИЗ САМОЙ КНИГИ, а не пишутся здесь руками: вставили
# страницу — памятка на обложке обязана указать на неё, а не на соседнюю.
НУЖНЫ = ["Об игре", "Состав игры", "Подготовка к игре", "Основы игры",
         "Ход игры", "Энергия и хранилище", "Инфраструктура", "Разработка",
         "Наступление", "Приобретения", "Модули", "Планшет науки", "Карты",
         "Конец игры и подсчёт очков"]
КОРОТКО = {"Конец игры и подсчёт очков": "Конец игры",
           "Подготовка к игре": "Подготовка",
           "Энергия и хранилище": "Хранилище"}


def ссылки():
    import re
    t = io.open(КНИГА, encoding="utf-8").read()
    стр = {}
    for m in re.finditer(r'<li[^>]*><span class="н">\d+</span>'
                         r'<span class="т">([^<]+)</span><span class="с">([^<]+)</span>', t):
        стр[m.group(1)] = m.group(2)
    out = []
    for имя in НУЖНЫ:
        n = стр.get(имя)
        if n and n.strip("—-").strip():
            out.append((КОРОТКО.get(имя, имя), n))
    return out


def действие(k):
    ikon, имя, приказ, тело = ДЕЙСТВИЯ[k]
    return (f'<div class="дей">{мед(ikon)}<div class="дей-т">'
            f'<div class="дей-имя">{имя}<span>{приказ}</span></div><ul>{тело}</ul></div></div>')


def страница():
    сетка = "".join(действие(k) for k in range(8))
    легенда = "".join(f'<div>{и(a)}<span>{b}</span></div>' for a, b in ЛЕГЕНДА)
    return f"""{МАРКЕР}
<p class="подпись-разворота">Задняя сторона обложки · памятка</p>
<div class="разворот одна">
  <div class="стр обложка задняя">
    <div class="сетка-фон"></div>
    <div class="рамка-техно"></div>

    <div class="титул">
      <div class="титул-плашка">Памятка</div>
      <div class="титул-линия"><span>раунд</span><span>карта приказа</span><span>восемь действий</span><span>конец партии</span></div>
    </div>

    <div class="пан" style="left:8mm;top:26mm;width:63mm;height:102mm"><div class="пан-в">{РАУНД}</div></div>
    <div class="пан" style="left:8mm;top:131mm;width:63mm;height:65mm"><div class="пан-в">{ПРИКАЗ}</div></div>

    <div class="пан дейпан" style="left:74mm;top:26mm;width:138mm;height:128mm"><div class="пан-в">
      <div class="шапка"><span class="таб">Восемь действий</span><i>по два на каждый приказ</i></div>
      <div class="дей-сетка">{сетка}</div>
    </div></div>

    <div class="пан низ" style="left:74mm;top:156.5mm;width:63mm;height:40mm"><div class="пан-в">{КОНЕЦ}</div></div>
    <div class="пан низ" style="left:140mm;top:156.5mm;width:72mm;height:40mm"><div class="пан-в">{ОЧКИ}</div></div>

    <div class="пан значки" style="left:8mm;top:197.8mm;width:204mm;height:19.6mm"><div class="пан-в">
      <div class="шапка"><span class="таб">Значки</span></div>
      <div class="легенда">{легенда}</div>
    </div></div>
  </div>
</div>
"""


CSS = r"""
  /* ---- задняя сторона обложки: командная панель ---- */
  .стр.обложка.задняя {
    --графит: #F7F1E1; --панель: rgba(247, 241, 225, .93); --панель2: rgba(255, 252, 244, .82); --кант: #186C24;
    --кант-т: rgba(42, 35, 24, .38); --крем: #2A2318; --крем2: #5E5139; --зел: #186C24; --охра: #6B4413;
    color: var(--крем); padding: 0; overflow: hidden;
    background: var(--графит) url("ЗАДНИК") center / cover no-repeat;
    font-family: "Tektur Narrow", "Tektur", sans-serif;
  }
  .задняя .сетка-фон { position: absolute; inset: 0; z-index: 0; opacity: 1;
    background-color: rgba(247, 241, 225, .62);
    background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='52' height='90' viewBox='0 0 52 90'><path d='M26 1 L50 15 L50 45 L26 59 L2 45 L2 15 Z M26 61 L50 75 L50 105 M26 61 L2 75 L2 105' fill='none' stroke='%232A2318' stroke-opacity='.07' stroke-width='1.1'/></svg>");
    background-size: 13mm 22.5mm; }
  .задняя .сетка-фон::after { content: ""; position: absolute; inset: 0;
    background: repeating-linear-gradient(135deg, transparent 0 2.2mm, rgba(42,35,24,.02) 2.2mm 2.7mm); }
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
  .задняя .пан-в { position: absolute; inset: .35mm; background: var(--панель); padding: 2mm 2.6mm 1.6mm;
    clip-path: polygon(3.35mm 0, 100% 0, 100% calc(100% - 3.35mm), calc(100% - 3.35mm) 100%, 0 100%, 0 3.35mm); }
  .задняя .пан-в::after { content: ""; position: absolute; right: 2.2mm; top: 2.2mm; width: 9mm; height: .45mm; background: var(--кант); }

  .задняя .шапка { display: flex; align-items: center; gap: 2mm; margin: 0 0 1.4mm; }
  .задняя .шапка::after { content: ""; flex: 1; height: .3mm; background: var(--кант); opacity: .8; }
  .задняя .шапка i { font: 500 6.8pt "Tektur Narrow", sans-serif; color: var(--крем2); font-style: normal; }
  .задняя .таб { display: inline-block; background: var(--кант); color: #F7F1E1; font: 800 8.6pt/1 "Tektur", sans-serif; text-transform: uppercase; letter-spacing: .06em;
    padding: 1.2mm 3.2mm 1mm 2.4mm; white-space: nowrap; clip-path: polygon(0 0, 100% 0, calc(100% - 2.2mm) 100%, 0 100%); }

  .задняя ul { list-style: none; margin: 0; padding: 0; }
  .задняя li { font: 500 7.4pt/1.2 "Tektur Narrow", sans-serif; color: var(--крем); margin: 0 0 .85mm; padding-left: 2.8mm; position: relative; break-inside: avoid; }
  .задняя li::before { content: ""; position: absolute; left: 0; top: 1.05mm; width: 1.5mm; height: 1.5mm; background: var(--зел); clip-path: polygon(0 0, 100% 50%, 0 100%); }
  .задняя li b { color: var(--зел); font-weight: 700; }
  /* Обложка растягивает любой img на всю страницу — здесь картинки мелкие,
     и правило обложки перебивается более точным селектором. */
  .стр.обложка.задняя img { width: auto; height: auto; object-fit: contain; display: inline-block; }
  .стр.обложка.задняя .и { height: 4.1mm; width: auto; vertical-align: -1.1mm; margin: 0 .25mm; background: rgba(255,255,255,.7); border-radius: .7mm; padding: .25mm; box-sizing: border-box; outline: .15mm solid rgba(42,35,24,.25); }

  .задняя .фаза { display: flex; align-items: baseline; gap: 1.8mm; margin: 1.6mm 0 .9mm; border-bottom: .25mm solid var(--кант-т); padding-bottom: .5mm; }
  .задняя .фаза b { font: 900 10pt/1 "Tektur", sans-serif; color: var(--охра); min-width: 5mm; }
  .задняя .фаза span { font: 800 9pt/1 "Tektur", sans-serif; color: var(--зел); text-transform: uppercase; letter-spacing: .04em; }
  .задняя .фаза i { margin-left: auto; font: 500 6.6pt "Tektur Narrow", sans-serif; color: var(--крем2); font-style: normal; }

  .задняя .дей-сетка { display: grid; grid-template-columns: 1fr 1fr; gap: 1mm 3mm; }
  .задняя .дей { display: flex; gap: 2mm; align-items: flex-start; background: var(--панель2); outline: .15mm solid var(--кант-т); outline-offset: -.15mm; padding: 1.4mm 1.8mm 1mm 1.6mm;
    clip-path: polygon(2mm 0, 100% 0, 100% calc(100% - 2mm), calc(100% - 2mm) 100%, 0 100%, 0 2mm); min-height: 24mm; }
  .стр.обложка.задняя .мед { width: 11.5mm; height: 11.5mm; border-radius: 50%; border: .4mm solid var(--кант); background: #F3EFE3; flex: none; margin-top: .6mm; }
  .задняя .дей-т { flex: 1; min-width: 0; }
  .задняя .дей-имя { font: 800 9.5pt/1 "Tektur", sans-serif; color: var(--зел); text-transform: uppercase; letter-spacing: .04em; margin: .3mm 0 1.1mm; display: flex; align-items: baseline; gap: 1.6mm; }
  .задняя .дей-имя span { font: 500 6.4pt "Tektur Narrow", sans-serif; color: var(--охра); text-transform: none; letter-spacing: .02em; }
  .задняя .дей li { font-size: 6.8pt; line-height: 1.17; margin-bottom: .45mm; }

  .задняя .низ li { font-size: 7.1pt; }
  .задняя .победа { margin-top: .9mm; padding-top: .8mm; border-top: .25mm dashed var(--кант-т); }
  .задняя .победа b { color: var(--охра); }
  .задняя .две-кол { column-count: 2; column-gap: 3mm; }
  .задняя .ничья { font: 500 7pt/1.2 "Tektur Narrow", sans-serif; color: var(--крем2); margin: .8mm 0 0; padding-top: .7mm; border-top: .25mm dashed var(--кант-т); }

  /* ПАНЕЛЬ ЗНАЧКОВ ВНИЗУ ПАМЯТКИ. Заняла место логотипа и списка страниц:
     дизайнер попросил убрать их и добавить значков (15.09.2026). */
  .задняя .легенда { display: grid; grid-template-columns: repeat(22, 1fr);
    gap: 0 .5mm; align-items: start; margin-top: .4mm; }
  .задняя .легенда div { display: flex; flex-direction: column; align-items: center;
    gap: .3mm; font: 500 4.6pt/1.04 "Tektur Narrow", sans-serif; color: var(--крем2);
    text-transform: uppercase; letter-spacing: .01em; text-align: center; }
  .стр.обложка.задняя .легенда .и { height: 5.9mm; width: auto; vertical-align: 0;
    padding: 0; background: none; border-radius: 0; outline: 0; }
"""


ЗАДНИК = r"C:\Users\backskin\Desktop\фон рулбука\задник рулбука.png"


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
        b = t.index("</style>", a)
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
