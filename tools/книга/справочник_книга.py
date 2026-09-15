# -*- coding: utf-8 -*-
"""СПРАВОЧНИК — вторая книга, собранная тем же конвейером, что и правила.

Прежний справочник верстался через Word и печатался таблицами кеглем в шесть
пунктов: посреди партии в них не попасть глазом. Здесь он собирается в ту же
вёрстку, что книга правил, — 220×220 мм, кайма, плашки, палитра келемия, — и
тем же способом: содержимое берётся ИЗ ДАННЫХ ИГРЫ (`data/cards/*.yaml` через
`tools/справочник.py`), а не переписывается руками, поэтому разойтись
с колодами ему нечем.

Что внутри:

* карты всех колод — карточками, а не строками мелкой таблицы;
* иконки — настоящими картинками из экспорта дизайнера;
* словарь игры (он ушёл сюда из книги правил, решение дизайнера 14.09.2026).

Запуск: python tools/книга/справочник_книга.py
"""
import base64
import glob
import io
import os
import subprocess
import sys

КОРЕНЬ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                      "..", ".."))
sys.path.insert(0, os.path.join(КОРЕНЬ, "tools"))
os.chdir(КОРЕНЬ)

import справочник as данные          # noqa: E402  (после chdir — он читает data/)

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(os.path.join(КОРЕНЬ, "rules", "Книга правил*", "вёрстка",
                               "Книга правил.html"))[0]
ПАПКА = os.path.join(КОРЕНЬ, "rules", "Справочник — черновик", "вёрстка")
ВЫХОД = os.path.join(ПАПКА, "Справочник.html")
ИКОНКИ = [os.path.join(КОРЕНЬ, "rules", "иконки-экспорт"),
          os.path.join(КОРЕНЬ, "rules", "иконки")]
КАЙМА = io.open(os.path.join(D, "_кайма.svg"), encoding="utf-8").read().strip()
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


# ----------------------------------------------------------------- картинки

def b64(путь, ширина=None):
    from PIL import Image
    im = Image.open(путь).convert("RGBA")
    if ширина and im.width > ширина:
        im = im.resize((ширина, round(im.height * ширина / im.width)),
                       Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


_кэш = {}


def иконка(имя, класс="ик"):
    if имя not in _кэш:
        путь = None
        for папка in ИКОНКИ:
            п = os.path.join(папка, имя + ".png")
            if os.path.exists(п):
                путь = п
                break
        _кэш[имя] = b64(путь, 160) if путь else None
    if _кэш[имя] is None:
        return '<span class="ик-нет">нет</span>'
    return '<img class="%s" src="%s" alt="">' % (класс, _кэш[имя])


# ----------------------------------------------------------------- страницы

class Книга:
    """Складывает страницы и печатает их разворотами, как книга правил."""

    def __init__(self):
        self.страницы = []
        self.разделы = []        # (буква, имя, номер страницы)

    def стр(self, содержимое, класс="", раздел=None, колонцифра=True):
        n = len(self.страницы) + 1
        if раздел:
            self.разделы.append((раздел[0], раздел[1], n))
        фон = "фон%d" % ((n % 4) + 1)
        цифра = ('    <div class="колонцифра"><span>%d</span></div>\n' % n
                 if колонцифра else "")
        self.страницы.append(
            '  <div class="стр %s %s">\n    %s\n%s\n%s  </div>\n'
            % (фон, класс, КАЙМА, содержимое, цифра))
        return n

    def html(self, стиль):
        out = []
        for k in range(0, len(self.страницы), 2):
            пара = self.страницы[k:k + 2]
            cls = "разворот" if len(пара) == 2 else "разворот левая"
            a = k + 1
            out.append('<p class="подпись-разворота">Разворот · страницы '
                       '%d–%d</p>\n<div class="%s">\n\n' % (a, a + 1, cls)
                       + "\n\n".join(пара) + "</div>\n\n")
        return ('<!doctype html>\n<html lang="ru">\n<head>\n<meta charset="utf-8">\n'
                '<title>Келемий. Кристалл раздора — справочник</title>\n'
                "<style>%s%s</style>\n</head>\n<body>\n\n" % (стиль, ДОБАВКА)
                + "".join(out) + "</body>\n</html>\n")


ДОБАВКА = r"""
  /* ---- справочник: карточки колод, иконки, словарь ---- */
  .прил { display: flex; align-items: center; gap: 3mm; margin: 0 0 3mm;
    padding: 0 3mm 0 0; }
  .прил .бк { flex: none; align-self: stretch; display: flex; align-items: center;
    background: var(--охра); color: var(--страница); font: 800 13pt/1 "Tektur", sans-serif;
    padding: 1.8mm 4.4mm 1.6mm 3.4mm; clip-path: polygon(0 0, 100% 0, calc(100% - 2.6mm) 100%, 0 100%); }
  .прил .им { font: 700 15pt/1.15 "Tektur", sans-serif; color: var(--охра); }
  .прил .счёт { font: 500 8.4pt "Tektur Narrow", sans-serif; color: var(--серый);
    text-transform: uppercase; letter-spacing: .05em; }
  .прил::after { content: ""; flex: 1; height: .5mm; background: var(--келемий); }

  .карточки { column-count: 2; column-gap: 5mm; }
  .кк { break-inside: avoid; background: rgba(247,241,225,.66);
    outline: .18mm solid var(--кант); outline-offset: -.18mm;
    padding: 1.4mm 2.2mm 1.3mm; margin: 0 0 2mm;
    clip-path: polygon(1.8mm 0, 100% 0, 100% calc(100% - 1.8mm), calc(100% - 1.8mm) 100%, 0 100%, 0 1.8mm); }
  .кк .имя { font: 700 9.4pt/1.15 "Tektur", sans-serif; color: var(--охра);
    margin-bottom: .8mm; display: flex; align-items: baseline; gap: 1.8mm; }
  .кк .имя small { font: 500 6.6pt "Tektur Narrow", sans-serif; color: var(--серый);
    text-transform: uppercase; letter-spacing: .04em; margin-left: auto; }
  .кк p { font: 8.2pt/1.2 "Tektur Narrow", sans-serif; margin: 0 0 .6mm; }
  .кк p:last-child { margin-bottom: 0; }
  .кк .кл { font: 700 7.2pt "Tektur Narrow", sans-serif; color: var(--келемий);
    text-transform: uppercase; letter-spacing: .04em; margin-right: 1.2mm; }
  .кк .низ { border-top: .22mm dotted var(--пример-кант); margin-top: .8mm; padding-top: .8mm; }

  .икс { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.6mm 4mm; }
  .икс .ряд { display: flex; align-items: center; gap: 2.2mm; padding: .9mm 0;
    border-bottom: .22mm dotted var(--пример-кант); break-inside: avoid; }
  .икс .ряд img.ик { width: 8.4mm; height: 8.4mm; object-fit: contain; flex: none; }
  .икс .ик-нет { width: 8.4mm; height: 8.4mm; flex: none; background: var(--место-иконки);
    border: .25mm dashed var(--пример-кант); font: 5pt/8.4mm "Tektur Narrow", sans-serif;
    color: var(--серый); text-align: center; }
  .икс .тек { flex: 1; min-width: 0; }
  .икс .тек b { display: block; font: 700 8.2pt/1.12 "Tektur", sans-serif; color: var(--чернила); }
  .икс .тек span { font: 7.2pt/1.12 "Tektur Narrow", sans-serif; color: var(--серый); }

  .словарь { column-count: 2; column-gap: 5mm; }
  .словарь div { break-inside: avoid; margin: 0 0 1.8mm; }
  .словарь b { font: 700 9.2pt "Tektur", sans-serif; color: var(--охра); }
  .словарь p { font: 8.4pt/1.22 "Tektur Narrow", sans-serif; margin: .2mm 0 0; }

  .титул-спр { position: absolute; inset: 26mm 18mm; display: flex;
    flex-direction: column; justify-content: center; align-items: center; text-align: center; gap: 5mm; }
  .титул-спр img { width: 128mm; height: auto; }
  .титул-спр .под { font: 800 24pt "Tektur", sans-serif; color: var(--охра);
    letter-spacing: .06em; text-transform: uppercase; }
  .титул-спр p { font: 10pt/1.35 "Tektur Narrow", sans-serif; color: var(--серый); max-width: 120mm; }

  .огл-спр { list-style: none; margin: 0; padding: 3mm 5mm; background: var(--подложка);
    outline: .18mm solid var(--кант); outline-offset: -.18mm; }
  .огл-спр li { display: flex; align-items: baseline; gap: 2.4mm; padding: 1.1mm 0;
    border-bottom: .22mm dotted var(--пример-кант); font: 10pt "Tektur", sans-serif; }
  .огл-спр li:last-child { border-bottom: 0; }
  .огл-спр .б { flex: none; width: 7mm; font-weight: 800; color: var(--охра); }
  .огл-спр .т { flex: 1; }
  .огл-спр .с { font-weight: 700; color: var(--келемий); }
"""


def стиль_книги():
    t = io.open(КНИГА, encoding="utf-8").read()
    return t[t.index("<style>") + len("<style>"):t.index("</style>")]


def шапка(буква, имя, счёт=""):
    доп = '<span class="счёт">%s</span>' % счёт if счёт else ""
    return ('    <div class="прил"><span class="бк">%s</span>'
            '<span class="им">%s</span>%s</div>' % (буква, имя, доп))


def карточки(куски):
    return '    <div class="карточки">\n' + "\n".join(куски) + "\n    </div>"


def карточка(имя, метка, строки):
    низ = "".join('<p>%s</p>' % s for s in строки)
    м = "<small>%s</small>" % метка if метка else ""
    return ('      <div class="кк"><div class="имя">%s%s</div>%s</div>'
            % (имя, м, низ))


def разбить(список, по):
    return [список[i:i + по] for i in range(0, len(список), по)]


# ----------------------------------------------------------------- разделы

ВИД_ЗАДАНИЯ = {"state": "состояние", "incident": "происшествие",
               "sacrifice": "жертва"}
ЦВЕТ_КОЛОДЫ = {"blue": "голубая", "scarlet": "алая", "green": "зелёная",
               "yellow": "жёлтая", "security": "безопасность"}
ЯРУС = {"rare": "редкий", "common": "обычный", "usual": "обычный"}


def текст(узел, ключ="label"):
    if isinstance(узел, dict):
        return узел.get(ключ) or узел.get("условие") or ""
    return str(узел or "")


def задания(книга, версии):
    из = данные.набор("objectives", версии["objectives"])
    обычные = [c for c in из if c.get("kind") != "starting"]
    начальные = [c for c in из if c.get("kind") == "starting"]
    for имя, набор_карт, буква in (("Карты заданий", обычные, "А"),
                                   ("Начальные задания", начальные, "Б")):
        куски = []
        for c in набор_карт:
            строки = [
                '<span class="кл">верх</span>%s' % текст(c.get("top")),
                '<span class="кл">низ</span>%s' % текст(c.get("requirement")),
                '<span class="кл">награда</span>%s' % данные.награда(c.get("base_reward")),
            ]
            if c.get("enhanced"):
                строки.append('<span class="кл">усиление</span>%s — %s'
                              % (текст(c["enhanced"]),
                                 данные.награда(c.get("special_reward"))))
            куски.append(карточка(c["name"], ВИД_ЗАДАНИЯ.get(c.get("type"), ""),
                                  строки))
        for k, кусок in enumerate(разбить(куски, 12)):
            книга.стр(
                (шапка(буква, имя, "%d карт" % len(набор_карт)) if k == 0 else "")
                + "\n" + карточки(кусок),
                раздел=(буква, имя) if k == 0 else None)


def арсенал(книга, версии):
    из = данные.набор("arsenal", версии["arsenal"])
    обычные = [c for c in из if c.get("kind") != "starting"]
    начальные = [c for c in из if c.get("kind") == "starting"]
    for имя, набор_карт, буква in (("Карты арсенала", обычные, "В"),
                                   ("Начальный арсенал", начальные, "Г")):
        куски = []
        for c in набор_карт:
            куски.append(карточка(c["name"], "", [
                '<span class="кл">верх</span>%s' % текст(c.get("top")),
                '<span class="кл">низ</span>%s' % текст(c.get("bottom")),
            ]))
        for k, кусок in enumerate(разбить(куски, 14)):
            книга.стр(
                (шапка(буква, имя, "%d карт" % len(набор_карт)) if k == 0 else "")
                + "\n" + карточки(кусок),
                раздел=(буква, имя) if k == 0 else None)


def контейнеры(книга, версии):
    из = данные.набор("containers", версии["containers"])
    куски = [карточка(c["name"], ЯРУС.get(c.get("tier"), ""),
                      ['<span class="кл">даёт</span>%s' % текст(c.get("a"))])
             for c in из]
    for k, кусок in enumerate(разбить(куски, 22)):
        книга.стр((шапка("Д", "Карты контейнеров", "%d карт" % len(из))
                   if k == 0 else "") + "\n" + карточки(кусок),
                  раздел=("Д", "Карты контейнеров") if k == 0 else None)


def рынок(книга, версии):
    из = данные.набор("market", версии["market"])
    куски = []
    for c in из:
        лев, прав = c.get("left") or {}, c.get("right") or {}
        куски.append(карточка(c["name"], "", [
            '<span class="кл">%s</span>%s' % (лев.get("name", "слева"),
                                              текст(лев)),
            '<span class="кл">%s</span>%s' % (прав.get("name", "справа"),
                                              текст(прав)),
        ]))
    for k, кусок in enumerate(разбить(куски, 12)):
        книга.стр((шапка("Е", "Карты рынка", "%d карт" % len(из))
                   if k == 0 else "") + "\n" + карточки(кусок),
                  раздел=("Е", "Карты рынка") if k == 0 else None)


def супер(книга, версии):
    зад = данные.набор("super_objectives", версии["super_objectives"])
    арс = данные.набор("super_arsenal", версии["super_arsenal"])
    куски = [карточка(c["name"], "супер-задание",
                      ['<span class="кл">очки за</span>%s'
                       % ", ".join(c.get("categories", []))]) for c in зад]
    книга.стр(шапка("Ж", "Супер-задания", "%d карт" % len(зад)) + "\n"
              + карточки(куски), раздел=("Ж", "Супер-задания"))
    куски = [карточка(c["name"], c.get("kind", ""),
                      ['<span class="кл">на карте</span>%s'
                       % (c.get("текст_карты") or текст(c))]) for c in арс]
    for k, кусок in enumerate(разбить(куски, 6)):
        книга.стр((шапка("З", "Супер-арсенал", "%d карт" % len(арс))
                   if k == 0 else "") + "\n" + карточки(кусок),
                  раздел=("З", "Супер-арсенал") if k == 0 else None)


def иконки(книга):
    ряды = []
    for группа, список in данные.ИКОНКИ:
        ряды.append(('<div class="ряд" style="border:0"><div class="тек">'
                     '<b style="color:var(--келемий)">%s</b></div></div>' % группа))
        for ключ, имя, пояснение in список:
            ряды.append('<div class="ряд">%s<div class="тек"><b>%s</b>'
                        '<span>%s</span></div></div>'
                        % (иконка(ключ), имя, пояснение))
    сколько = sum(len(с) for _, с in данные.ИКОНКИ)
    for k, кусок in enumerate(разбить(ряды, 39)):
        книга.стр((шапка("И", "Иконки игры", "%d значков" % сколько)
                   if k == 0 else "")
                  + '\n    <div class="икс">\n' + "\n".join(кусок)
                  + "\n    </div>",
                  раздел=("И", "Иконки игры") if k == 0 else None)


СЛОВАРЬ = [
    ("Гекс", "Шестиугольник поля: шесть наземных секторов по краям и небо в центре."),
    ("Сектор", "Одна из шести наземных долей гекса. На секторе стоит жетон."),
    ("Небо", "Середина гекса: место для одного жетона авиации."),
    ("Стенка", "Сектор здания, обращённый к соседнему гексу. Закрывает сторону "
               "для чужих наземных войск и расширяет зону стройки."),
    ("Зона стройки", "Гексы со своими зданиями и соседний гекс, к которому "
                     "здание обращено стенкой."),
    ("Запас", "Ваши жетоны вне поля. Уничтоженный жетон возвращается в запас владельца."),
    ("Свалка", "Карта приказов рубашкой вверх, на которую кладут уничтоженные "
               "за раунд жетоны оборотом вверх."),
    ("Оборот жетона", "Сторона, которой уничтоженный жетон лежит на свалке: "
                      "трофеи и карты контейнеров."),
    ("Запитанное здание", "Здание, у которого все ячейки энергии заняты кубиками."),
    ("Источник энергии", "ЦУ и энергостанции: с них энергия перекладывается на здания."),
    ("Круг энергии", "Сектор поля, на котором энергостанция даёт свой номинал."),
    ("Хранилище", "Ячейки на планшете хранилища под келемий, боеприпасы и трофеи."),
    ("Выбранный приказ", "Верхний приказ вскрытой карты: даёт оба своих действия."),
    ("Чужой приказ", "Нижний приказ карты. Работает, если этот приказ уже "
                     "вскрыл кто-то до вас в этом круге."),
    ("Совпадение", "Кто-то до вас вскрыл тот же выбранный приказ: он даёт "
                   "вам только одно действие."),
    ("Спец-действие", "Одно действие в ход сверх приказа: карты, контейнеры, "
                      "спец-плашка."),
    ("Спец-плашка", "Мелкое действие, напечатанное на карте приказа: "
                    "Движение, Монета или Задание."),
    ("Утиль", "Сжечь карту ради эффекта в её верхней части. Карта уходит в сброс."),
    ("Установленная карта", "Карта арсенала, перевёрнутая лицом вверх в ячейке "
                            "под планшетом войск."),
    ("Гарнизон", "Место для одного жетона пехоты внутри нейтральной постройки "
                 "на 2 сектора."),
    ("Тайл зарождения", "Тайл, занимающий весь гекс; с него добытчики берут келемий."),
    ("Ступень трека", "Ряд ячеек на треке технологий. Ступеней четыре, "
                      "вершина — четвёртая."),
    ("Трофей", "Ресурс Науки: кубик трофея в хранилище или жетон со своей свалки."),
    ("Победное очко", "Звезда. Очки считают в конце партии."),
]


def словарь(книга):
    куски = ['      <div><b>%s</b><p>%s</p></div>' % (т, о) for т, о in СЛОВАРЬ]
    for k, кусок in enumerate(разбить(куски, 24)):
        книга.стр((шапка("К", "Словарь игры", "%d слов" % len(СЛОВАРЬ))
                   if k == 0 else "")
                  + '\n    <div class="словарь">\n' + "\n".join(кусок)
                  + "\n    </div>",
                  раздел=("К", "Словарь игры") if k == 0 else None)


def титул(книга):
    лого = (r"C:\shared\Yandex.Disk\Forged in Kelium\Общие компоненты"
            r"\экспорт-иконки\логотип.png")
    книга.стр(
        '    <div class="титул-спр">\n'
        '      <img src="%s" alt="">\n'
        '      <div class="под">Справочник</div>\n'
        '      <p>Все карты игры, все значки и словарь. Правила — в книге правил; '
        'сюда заглядывают посреди партии, когда нужен точный текст карты.</p>\n'
        '    </div>' % b64(лого, 1600), колонцифра=False)


def содержание(книга, места):
    строки = "".join('<li><span class="б">%s</span><span class="т">%s</span>'
                     '<span class="с">%d</span></li>' % (б, и, n)
                     for б, и, n in места)
    книга.страницы[1] = книга.страницы[1].replace("ОГЛАВЛЕНИЕ-СЮДА",
                                                  строки)


def main():
    в = данные.версии(данные.свод())
    книга = Книга()
    титул(книга)
    книга.стр('    <div class="глава"><span class="гл-т">Содержание</span></div>\n'
              '    <ol class="огл-спр">ОГЛАВЛЕНИЕ-СЮДА</ol>')
    задания(книга, в)
    арсенал(книга, в)
    контейнеры(книга, в)
    рынок(книга, в)
    супер(книга, в)
    иконки(книга)
    словарь(книга)
    содержание(книга, книга.разделы)

    os.makedirs(ПАПКА, exist_ok=True)
    io.open(ВЫХОД, "w", encoding="utf-8", newline="\n").write(
        книга.html(стиль_книги()))
    print("справочник: свод %s, страниц %d" % (данные.свод(), len(книга.страницы)))

    pdf = os.path.join(D, "spravochnik.pdf")
    if os.path.exists(pdf):
        os.remove(pdf)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    "--user-data-dir=" + os.path.join(D, "chr"),
                    "--virtual-time-budget=20000", "--print-to-pdf=" + pdf,
                    "file:///" + ВЫХОД.replace("\\", "/")], check=True)
    print("PDF: %.1f МБ" % (os.path.getsize(pdf) / 1e6))


if __name__ == "__main__":
    main()
