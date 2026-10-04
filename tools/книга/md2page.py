"""Глава из md (узкое подмножество) -> фрагмент вёрстки.

Разметка страниц задаётся в md комментарием-строкой `<!-- стр -->`:
всё до первого разрыва — первая страница (с заголовком главы), дальше по разрывам.
Разделы `---` игнорируются. `[...]` в отдельной строке: «уточнить» -> пометка,
иначе -> заглушка иллюстрации.

python md2page.py <глава.md> <первая страница> <выход.html>
"""
import base64
import glob
import html
import io
import os
import re
import sys

КОРЕНЬ_КНИГ = os.path.dirname(glob.glob(
    os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "rules", "Книга правил*"))[0])

src, first, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
text = open(src, encoding="utf-8").read()

title = re.match(r"# (.+)", text).group(1)
body = text.split("\n", 1)[1]
# :ПРОСТОРНО: — глава свёрстана с увеличенными полями блоков и промежутками
# между ними (замечание дизайнера 27.09.2026: «нет отступов, всё слиплось»).
# Главы переходят на неё по одной, по мере переписывания.
ПРОСТОРНО = bool(re.search(r"^:просторно:\s*$", body, flags=re.M))
body = re.sub(r"^:просторно:\s*\n", "", body, flags=re.M)
КАРТИНКИ = os.path.join(os.path.dirname(КОРЕНЬ_КНИГ), "data", "textures")


def текст_карточки(строки):
    """Текст карточки: абзацы через пустую строку; «- …» — пункт списка,
    следующие строки без дефиса продолжают пункт."""
    out, абзац, пункты = [], [], []
    вид = ["ul"]

    def сброс():
        if абзац:
            out.append("<p>" + inline(" ".join(" ".join(абзац).split())) + "</p>")
            абзац.clear()
        if пункты:
            out.append("<%s>" % вид[0] + "".join("<li>" + inline(" ".join(x.split())) + "</li>"
                                               for x in пункты) + "</%s>" % вид[0])
            пункты.clear()

    for с in строки:
        номер = re.match(r"\d+\.\s+", с)
        if not с:
            сброс()
        elif с.startswith("- ") or номер:
            новый = "ol" if номер else "ul"
            if абзац or (пункты and вид[0] != новый):
                сброс()
            вид[0] = новый
            пункты.append(с[номер.end():] if номер else с[2:])
        elif пункты:
            пункты[-1] += " " + с
        else:
            абзац.append(с)
    сброс()
    return "".join(out)


def картинка_текстуры(путь, ширина=360):
    """Печатная картинка компонента (data/textures/…) — встроенным PNG."""
    from PIL import Image
    # «путь@0.75» — картинка на четверть мельче соседей по ряду: так жетон
    # модуля хранилища стоит меньше модулей боя, как на столе
    путь, _, доля = путь.strip().partition("@")
    стиль = ' style="max-height:%d%%"' % round(float(доля) * 100) if доля else ""
    im = Image.open(os.path.join(КАРТИНКИ, путь.strip())).convert("RGBA")
    box = im.getbbox()
    if box:
        im = im.crop(box)
    # крупные картинки (планшеты, лица карт) — до 1000 точек: при печати 300 dpi
    ширина = max(ширина, min(im.width, 1000)) if im.width >= 600 else ширина
    if im.width > ширина:
        im = im.resize((ширина, max(1, round(im.height * ширина / im.width))), Image.LANCZOS)
    буфер = io.BytesIO()
    im.save(буфер, "PNG", optimize=True)
    # иконки (кубики, шестерёнки) рисуются мельче жетонов и тайлов: при одной
    # высоте кубик выглядел бы втрое крупнее жетона ЦУ
    класс = ' class="значок"' if путь.strip().startswith("icons/") else ""
    return ('<img%s%s src="data:image/png;base64,%s" alt="">'
            % (класс, стиль, base64.b64encode(буфер.getvalue()).decode()))


# НАСТОЯЩИЕ ИКОНКИ ИГРЫ. `[иконка: монета]` в тексте превращается в саму
# иконку из экспорта дизайнера (`rules/иконки-экспорт`), а не в заглушку.
# Чего в экспорте нет, ищется среди врезок с карт-памяток (`rules/иконки`);
# не нашлось нигде — остаётся видимая пометка «уточнить».
ПАПКИ_ИКОНОК = [
    os.path.join(КОРЕНЬ_КНИГ, "иконки-экспорт"),
    os.path.join(КОРЕНЬ_КНИГ, "иконки"),
]
_иконки_кэш = {}


def значок(имя):
    ключ = имя.strip().lower().replace(" ", "-")
    if ключ in _иконки_кэш:
        return _иконки_кэш[ключ]
    путь = None
    for папка in ПАПКИ_ИКОНОК:
        п = os.path.join(папка, ключ + ".png")
        if os.path.exists(п):
            путь = п
            break
    if путь is None:
        _иконки_кэш[ключ] = None
        return None
    from PIL import Image
    im = Image.open(путь).convert("RGBA")
    # 256 точек: в строке значок 4–5 мм, при печати это ~1300 dpi с запасом
    # на крупные значки действий (15 мм). 96 было мыльно (дизайнер, 02.10.2026).
    # широкие (вырез плашки с карты) — до 600 точек, чтобы не мылились
    ш = min(600 if im.width > im.height * 1.5 else 256, im.width)
    im = im.resize((ш, max(1, round(im.height * ш / im.width))), Image.LANCZOS)
    буфер = io.BytesIO()
    im.save(буфер, "PNG", optimize=True)
    код = base64.b64encode(буфер.getvalue()).decode()
    _иконки_кэш[ключ] = '<img class="и" src="data:image/png;base64,%s" alt="">' % код
    return _иконки_кэш[ключ]


def _иконка_в_тексте(m):
    имя = m.group(1).split("—")[0].strip()
    з = значок(имя)
    if з:
        return з
    return '<span class="уточнить">[иконка: %s]</span>' % html.escape(имя)


def ячейка_с_иконкой(текст):
    """«[иконка: бой] Бой» в ячейке таблицы — иконка крупно, подпись под ней.

    Мелкая иконка в строку в таблице не читается (замечание дизайнера
    15.09.2026), поэтому такая ячейка набирается столбиком по центру.
    """
    m = re.fullmatch(r"\[иконка:\s*([^\]]+)\]\s*(.+)", текст.strip())
    # Столбиком набирается только ячейка с ОБЫЧНОЙ подписью. Там, где подпись
    # жирная (таблица ресурсов), значок остаётся в строке: иначе таблица
    # растёт вдвое и не помещается на полосу.
    if not m or m.group(2).startswith("**"):
        return None
    з = значок(m.group(1).split("—")[0].strip())
    if not з:
        return None
    return ('<span class="дст">' + з.replace('class="и"', 'class="и"')
            + "<b>" + inline(m.group(2), ресурсы=False) + "</b></span>")


def гекс_номер(номер):
    """Римская цифра фазы в светлом гексе — картинкой. Текстом цифра склеивалась
    при разборе PDF с заголовком соседнего столбца (ложное «наложение»)."""
    from PIL import Image, ImageDraw, ImageFont
    import math
    ш, в = 208, 240
    im = Image.new("RGBA", (ш, в), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    def гекс(отступ):
        cx, cy, r = ш / 2, в / 2, в / 2 - отступ
        return [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
                for a in (-90, -30, 30, 90, 150, 210)]
    d.polygon(гекс(0), fill=(107, 68, 19, 255))          # охряной кант
    d.polygon(гекс(12), fill=(247, 241, 225, 255))       # светлое поле
    d.polygon(гекс(24), outline=(24, 108, 36, 255), width=5)
    шрифт = ImageFont.truetype(next(п for п in (
        r"C:\Windows\Fonts\Tektur-ExtraBold.ttf",
        os.path.expanduser("~/.fonts/Tektur-Black.ttf"),
        os.path.expanduser("~/.fonts/Tektur-Bold.ttf"),
        r"C:\Windows\Fonts\TekturNarrow-Bold.ttf") if os.path.exists(п)), 96)
    d.text((ш / 2, в / 2 + 4), номер, font=шрифт, fill=(24, 108, 36, 255), anchor="mm")
    буфер = io.BytesIO()
    im.save(буфер, "PNG", optimize=True)
    return ('<img class="ф-н" src="data:image/png;base64,%s" alt="">'
            % base64.b64encode(буфер.getvalue()).decode())


# РЕСУРС В ТЕКСТЕ — СО ЗНАЧКОМ (дизайнер 04.10.2026: «где энергия, келемий,
# боеприпасы, трофеи — добавлять иконку этих ресурсов в текст»). Значок
# встаёт за первым упоминанием ресурса в абзаце или пункте; если значок уже
# стоит рядом (перед словом или сразу за ним), второй не ставится.
РЕСУРСЫ = [
    (re.compile(r"(?<![\w-])(энерги[яиюей]|энергией)(?![\w-])", re.I), "энергия"),
    (re.compile(r"(?<![\w-])(келеми[йяюеи]|келемием)(?![\w-])", re.I), "келемий"),
    (re.compile(r"(?<![\w-])(боеприпас(?:а|ов|ы|ом|ами|ах|ам)?)(?![\w-])", re.I), "боеприпас"),
    (re.compile(r"(?<![\w-])(трофе(?:й|я|ю|ем|е|и|ев|ям|ями|ях))(?![\w-])", re.I), "трофей"),
]


def значки_ресурсов(s):
    куски = re.split(r"(<[^>]+>)", s)
    for рег, имя in РЕСУРСЫ:
        з = значок(имя)
        if not з or з in s:
            continue
        for k, кус in enumerate(куски):
            if кус.startswith("<"):
                continue
            m = рег.search(кус)
            if not m:
                continue
            # слово внутри <b>…</b>: значок — за закрывающим тегом
            if k + 1 < len(куски) and куски[k + 1] == "</b>" and m.end() == len(кус):
                куски[k + 1] = "</b>" + з
            else:
                куски[k] = кус[:m.end()] + з + кус[m.end():]
            break
    return "".join(куски)


def inline(s, ресурсы=True):
    s = html.escape(s, quote=False)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"\[иконка:\s*([^\]]+)\]", _иконка_в_тексте, s)
    if ресурсы:
        s = значки_ресурсов(s)
    # «с. 20», «главы 6–9»: сокращение и число не разрываются переносом строки
    s = re.sub(r"(?<![\w])(с\.|стр\.|гл\.|глава|главы|главе)[ \u00a0]+(\d)", "\\1\u00a0\\2", s)
    return s


def заголовок_главы(title):
    """«Глава 4. Основы игры» → плашка с номером и сам заголовок."""
    m = re.match(r"(Глава \d+)\.\s*(.+)", title)
    if not m:
        return f'<div class="глава">{inline(title, ресурсы=False)}</div>'
    return (f'<div class="глава"><span class="гл-н">{inline(m.group(1), ресурсы=False)}</span>'
            f'<span class="гл-т">{inline(m.group(2), ресурсы=False)}</span></div>')


# Кайма страницы — векторная рамка, одна на все страницы (её пишет restyle.py).
КАЙМА = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "_кайма.svg"),
             encoding="utf-8").read().strip()


def фрагмент(имя):
    """Готовый рисунок `_имя.svg` (его пишут make_fig_*.py / figs.py).

    Нет файла и задан KNIGA_FIG_MARK — метка на его месте: по ней
    снять_рисунки.py достаёт рисунок из нынешней вёрстки (генераторы части
    рисунков читают папки дизайнера, которых в облаке нет)."""
    путь = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_" + имя + ".svg")
    if not os.path.exists(путь) and os.environ.get("KNIGA_FIG_MARK"):
        return '<div class="рисунок-в-колонке" data-нет="%s"></div>' % имя
    return open(путь, encoding="utf-8").read()


фрагмент_ = фрагмент


def blocks(md):
    """md-кусок -> список html-блоков (каждый .блок / .пример / .заглушка)."""
    res, cur = [], []
    надвое = [False]
    пара = [False]
    заглавная = [None]
    вовсю = [False]
    крупно = [False]

    def flush():
        if not cur:
            return
        if пара[0]:
            # ПАРА ПРИМЕРОВ: шапка h2, дальше два куска по «### …»; в каждом
            # рисунок слева и текст справа (раздел :пара:, 02.10.2026).
            шапка, куски = [], []
            for ч in cur:
                if "<h2>" in ч:
                    шапка.append(ч)
                elif "<h3>" in ч:
                    куски.append(([ч], [], []))
                elif куски and ('class="рисунок' in ч or 'class="рис"' in ч):
                    куски[-1][1].append(ч)
                elif куски:
                    куски[-1][2].append(ч)
            res.append('      <div class="блок пара">\n' + "\n".join(шапка) + "".join(
                '\n        <div class="половина">\n' + "\n".join(з)
                + '\n        <div class="пара-ряд"><div>' + "\n".join(р)
                + "</div><div>" + "\n".join(т) + "</div></div>\n        </div>"
                for з, р, т in куски) + "\n      </div>")
            пара[0] = False
            cur.clear()
            return
        if надвое[0]:
            # БЛОК НА ВСЮ ШИРИНУ, РАЗДЕЛЁННЫЙ ПОПОЛАМ: слева рисунки с
            # выносками, справа весь текст. Так дизайнер попросил показывать
            # карту контейнера (15.09.2026): в колонку блок не лез и наезжал
            # на низ полосы.
            шапка, лево, право = [], [], []
            for ч in cur:
                if "<h2>" in ч or "<h3>" in ч:
                    шапка.append(ч)
                elif 'class="рисунок' in ч or 'class="рис"' in ч:
                    лево.append(ч)
                else:
                    право.append(ч)
            # «:надвое: справа» — текст слева, рисунки справа
            if надвое[0] == "справа":
                лево, право = право, лево
            res.append('      <div class="блок надвое">\n' + "\n".join(шапка)
                       + '\n        <div class="половина">\n' + "\n".join(лево)
                       + '\n        </div>\n        <div class="половина">\n'
                       + "\n".join(право) + "\n        </div>\n      </div>")
            надвое[0] = False
        else:
            классы = "блок"
            if крупно[0]:
                # Ступень крупнее: когда на полосе остаётся воздух, текст лучше
                # дать больше, чем растягивать пустоту (просьба дизайнера).
                классы += " крупно"
                крупно[0] = False
            if вовсю[0]:
                # Во всю ширину полосы: под широким рисунком колонка остаётся
                # одна, и блок жмётся к левому краю.
                классы += " вовсю"
                вовсю[0] = False
            блок = ('      <div class="%s">\n' % классы + "\n".join(cur)
                    + "\n      </div>")
            if заглавная[0]:
                # ЗАГЛАВНАЯ ИКОНКА (дизайнер 04.10.2026): крупно слева от
                # вступительного блока, ростом с сам блок.
                з = значок(заглавная[0]) or ""
                блок = ('      <div class="с-заглавной"><div class="заглавная">'
                        + з.replace('class="и"', 'class="заг-и"') + "</div>\n"
                        + блок.replace('<div class="блок вовсю">', '<div class="блок">', 1)
                        + "\n      </div>")
                заглавная[0] = None
            res.append(блок)
        cur.clear()

    lines = md.strip("\n").split("\n")
    i = 0
    while i < len(lines):
        ln = lines[i]
        if not ln.strip() or ln.strip() == "---":
            i += 1
            continue
        if ln.startswith("## "):
            flush()
            cur.append(f"        <h2>{inline(ln[3:], ресурсы=False)}</h2>")
            i += 1
            continue
        if ln.startswith("### "):
            if not пара[0] and not (len(cur) == 1 and cur[0].lstrip().startswith("<h2>")):
                flush()
            cur.append(f"        <h3>{inline(ln[4:], ресурсы=False)}</h3>")
            i += 1
            continue
        if ln.startswith("> "):
            flush()
            q = []
            while i < len(lines) and lines[i].startswith(">"):
                q.append(lines[i][1:].strip())
                i += 1
            # РИСУНОК ВНУТРИ ПРИМЕРА: строка «> [[имя]]» кладёт рисунок в ту же
            # рамку под текстом — пример и его картинка читаются одним куском.
            рис = [x[2:-2] for x in q if re.fullmatch(r"\[\[[^\]]+\]\]", x)]
            q = [x for x in q if x and not re.fullmatch(r"\[\[[^\]]+\]\]", x)]
            qt = " ".join(q)
            m = re.match(r"\*\*(.+?)\*\*\s*(.*)", qt)
            label, rest = (m.group(1).rstrip("."), m.group(2)) if m else ("Пример", qt)
            вид = {"Важно!": " важно", "Важно": " важно", "Совет": " совет"}.get(label, "")
            res.append(f'      <div class="пример{вид}">\n        <span class="метка">{inline(label)}</span>\n'
                       f"        <p>{inline(rest)}</p>\n"
                       + "".join(фрагмент_(r) for r in рис)
                       + "      </div>")
            continue
        # ТРИ ФАЗЫ РАУНДА — сетка из трёх колонок (CSS .фазы). В разметке:
        #   :фазы:
        #   I. Обновление | стол готовится к новому раунду
        #   :конец:
        # :добор: подпись — заполнитель вынужденной пустоты на полосе.
        # Дизайнер вставит сюда художественную иллюстрацию в конце работы.
        # :надвое: — раздел идёт блоком во всю ширину: рисунки слева, текст справа.
        if ln.strip().startswith(":заглавная:"):
            заглавная[0] = ln.strip()[len(":заглавная:"):].strip()
            i += 1
            continue
        if ln.strip().startswith(":надвое:"):
            надвое[0] = ln.strip()[len(":надвое:"):].strip() or True
            i += 1
            continue
        # :пара: — два примера рядом (### заголовок, [[рисунок]], текст — дважды).
        if ln.strip() == ":пара:":
            пара[0] = True
            i += 1
            continue
        # :крупно: — блок набирается на ступень крупнее обычного.
        if ln.strip() == ":крупно:":
            крупно[0] = True
            i += 1
            continue
        # :вовсю: — раздел идёт блоком во всю ширину полосы.
        if ln.strip() == ":вовсю:":
            вовсю[0] = True
            i += 1
            continue
        if ln.strip().startswith(":добор:"):
            подпись = ln.strip()[len(":добор:"):].strip() or "ИЛЛЮСТРАЦИЯ"
            flush()
            res.append('      <div class="добор"><div class="заглушка рисунок">'
                       + f"[{inline(подпись.upper())}]</div></div>")
            i += 1
            continue
        # КАРТОЧКИ — вещи игры сеткой в две колонки во всю ширину полосы:
        # у каждой картинка компонента сверху, под ней заголовок и текст.
        #   :карточки:
        #   картинки: token/command_center_p1.png
        #   ## Центр управления
        #   текст…
        #   :конец:
        # СОСТАВ ИГРЫ — сетка компонентов внутри блока: картинка, число, название.
        #   :компоненты: 4
        #   block/big-1-A.png | 5 | Большие блоки поля
        #   token/command_center_p1.png + token/miner_l1_p1.png | 12 | Жетоны зданий
        #   :конец:
        if ln.strip().startswith(":компоненты:"):
            # «:компоненты: 7 20» — 7 колонок, картинки высотой 20 мм
            параметры = ln.strip()[len(":компоненты:"):].split()
            колонок = параметры[0] if параметры else "4"
            высота = параметры[1] if len(параметры) > 1 else "16"
            i += 1
            ячейки = []
            while i < len(lines) and lines[i].strip() != ":конец:":
                части = [ч.strip() for ч in lines[i].split("|")]
                if len(части) == 3:
                    рис = "".join(картинка_текстуры(r, 300) for r in части[0].split("+"))
                    ячейки.append('<div class="комп"><div class="комп-рис">' + рис
                                  + '</div><div class="комп-число">' + inline(части[1], ресурсы=False)
                                  + '</div><div class="комп-имя">' + inline(части[2], ресурсы=False)
                                  + "</div></div>")
                i += 1
            i += 1
            cur.append('        <div class="комп-сетка к%s" style="--комп-h:%smm">' % (колонок, высота)
                       + "".join(ячейки) + "</div>")
            continue
        # ПЛИТКИ — иконка, название и две ветки «или»: действия-развилки в ряд.
        #   :плитки: 5
        #   добыча | Добыча | Добыть келемий | Построить добытчик
        #   :конец:
        if ln.strip().startswith(":плитки:"):
            пар = ln.strip()[len(":плитки:"):].split()
            колонок = пар[0] if пар else "5"
            иконка_мм = пар[1] if len(пар) > 1 else "14"
            i += 1
            ячейки = []
            while i < len(lines) and lines[i].strip() != ":конец:":
                ч = [x.strip() for x in lines[i].split("|")]
                if len(ч) >= 3:
                    з = значок(ч[0]) or ""
                    ветки = '<span class="или">или</span>'.join(
                        '<span class="ветка">' + inline(x, ресурсы=False) + "</span>" for x in ч[2:])
                    ячейки.append('<div class="плитка">' + з.replace('class="и"', 'class="и плит"')
                                  + '<b>' + inline(ч[1], ресурсы=False) + "</b>" + ветки + "</div>")
                i += 1
            i += 1
            cur.append('        <div class="плитки к%s" style="--плит-h:%smm">' % (колонок, иконка_мм)
                       + "".join(ячейки) + "</div>")
            continue
        # РАЗДЕЛ — заголовок во всю ширину полосы без подложки: иконка действия
        # и его имя крупно, под ним линия. Для групп карточек одного действия.
        #   :раздел: Добыча
        if ln.strip().startswith(":раздел:"):
            имя = ln.strip()[len(":раздел:"):].strip()
            з = значок(имя) or ""
            flush()
            res.append('      <div class="раздел">' + з.replace('class="и"', 'class="и разд"')
                       + "<span>" + inline(имя) + "</span></div>")
            i += 1
            continue
        if ln.strip().startswith(":карточки:"):
            # «:карточки: 16» — высота картинки карточки в мм (по умолчанию 27)
            рис_мм = ln.strip()[len(":карточки:"):].strip()
            i += 1
            карточки = []
            while i < len(lines) and lines[i].strip() != ":конец:":
                l = lines[i].strip()
                if l.startswith("картинки:"):
                    карточки.append({"рис": [x for x in l[len("картинки:"):].split(",") if x.strip()],
                                     "имя": "", "текст": []})
                elif l.startswith("## ") and карточки:
                    карточки[-1]["имя"] = l[3:]
                elif карточки:
                    карточки[-1]["текст"].append(l)
                i += 1
            i += 1
            flush()
            html_к = []
            for к in карточки:
                # карточка без картинки («картинки:» пустая) — просто блок в сетке
                рис = (('<div class="карт-рис"%s>' % (' style="height:%smm"' % рис_мм if рис_мм else ""))
                       + "".join(картинка_текстуры(r) for r in к["рис"]) + "</div>"
                       if к["рис"] else "")
                html_к.append('<div class="блок карточка">' + рис
                              + "<h2>" + inline(к["имя"], ресурсы=False) + "</h2>"
                              + текст_карточки(к["текст"]) + "</div>")
            res.append('      <div class="карточки">' + "".join(html_к) + "</div>")
            continue
        # ЦИКЛ РАУНДА — три фазы в три столбца, читаются слева направо;
        # между столбцами короткие стрелки, под ними стрелка возврата от
        # последней фазы к первой (дизайнер 30.09.2026). В разметке:
        #   :цикл: подпись стрелки возврата
        #   # I | Обновление | в начале раунда
        #   абзац
        #
        #   [иконка: рынок] **2. Рынок.** текст шага
        #   :конец:
        if ln.strip().startswith(":цикл:"):
            подпись = ln.strip()[len(":цикл:"):].strip()
            i += 1
            фазы = []
            while i < len(lines) and lines[i].strip() != ":конец:":
                t = lines[i].rstrip()
                if t.startswith("# "):
                    номер, имя, когда = [x.strip() for x in t[2:].split("|")]
                    фазы.append({"н": номер, "имя": имя, "когда": когда, "абз": [[]]})
                elif not t.strip():
                    фазы[-1]["абз"].append([])
                else:
                    фазы[-1]["абз"][-1].append(t.strip())
                i += 1
            i += 1
            flush()
            куски = []
            for k, ф in enumerate(фазы):
                тело = []
                for абз in ф["абз"]:
                    if not абз:
                        continue
                    текст = " ".join(абз)
                    # «[[имя]]» — картинка _имя.png внизу столбца фазы
                    мк = re.fullmatch(r"\[\[([^\]]+)\]\]", текст)
                    if мк:
                        with open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                               "_" + мк.group(1) + ".png"), "rb") as f:
                            тело.append('<div class="ф-низ"><img src="data:image/png;base64,%s" alt=""></div>'
                                        % base64.b64encode(f.read()).decode())
                        continue
                    m = re.match(r"\[иконка:\s*([^\]]+)\]\s*(.*)", текст)
                    # Значок шага — иконка из экспорта или, если имя с «/»,
                    # текстура компонента (карта приказа лицом или рубашкой).
                    if m and "/" in m.group(1):
                        з = картинка_текстуры(m.group(1).strip(), 160).replace("<img", '<img class="ф-и ф-карта"', 1)
                    else:
                        з = значок(m.group(1)).replace('class="и"', 'class="ф-и"') if m and значок(m.group(1)) else None
                    if з:
                        тело.append('<div class="ф-шаг">' + з + "<p>" + inline(m.group(2)) + "</p></div>")
                    else:
                        тело.append("<p>" + inline(текст) + "</p>")
                куски.append('<section class="фаза"><header>' + гекс_номер(ф["н"])
                             + '<div><b>' + inline(ф["имя"]) + "</b><i>" + inline(ф["когда"])
                             + '</i></div></header><div class="ф-тело">' + "".join(тело) + "</div></section>")
                if k < len(фазы) - 1:
                    куски.append('<div class="ф-стрелка"></div>')
            res.append('      <div class="цикл"><div class="цикл-ряд">' + "".join(куски)
                       + '</div><div class="цикл-возврат"><span>' + inline(подпись)
                       + "</span></div></div>")
            continue
        if ln.strip() == ":фазы:":
            i += 1
            ячейки = []
            while i < len(lines) and lines[i].strip() != ":конец:":
                имя, _, текст = lines[i].partition("|")
                ячейки.append("<div><b>" + inline(имя.strip()) + "</b><p>"
                              + inline(текст.strip()) + "</p></div>")
                i += 1
            i += 1
            flush()
            res.append('      <div class="фазы">'
                       + "".join(ячейки) + "</div>")
            continue
        if re.fullmatch(r"\[\[[^\]]+\]\]", ln.strip()):
            фрагмент = фрагмент_(ln.strip()[2:-2])
            # РИСУНОК НА ВСЮ ШИРИНУ НЕ КЛАДЁТСЯ В .блок: у него column-span:all,
            # он уходит из потока колонки, а подложка блока остаётся пустым
            # прямоугольником над следующим заголовком (баг, замеченный
            # дизайнером на странице 33).
            if "рисунок-во-всю" in фрагмент:
                flush()
                res.append(фрагмент)
            else:
                cur.append(фрагмент)
            i += 1
            continue
        if ln.startswith("["):
            q = [ln]
            # КВАДРАТНАЯ СКОБКА НЕ ГЛОТАЕТ ГЛАВУ: раньше сбор шёл до ближайшей
            # закрывающей скобки и на строке вида «[иконка: …].» съедал всё
            # до конца полосы (15.09.2026). Теперь сбор кончается на пустой
            # строке и на заголовке.
            while (not q[-1].rstrip().endswith("]") and i + 1 < len(lines)
                   and lines[i + 1].strip()
                   and not lines[i + 1].startswith(("#", ">", "|", "-", "<!--"))):
                i += 1
                q.append(lines[i])
            i += 1
            inner = " ".join(x.strip() for x in q)[1:-1]
            # ОТДЕЛЬНОЙ СТРОКОЙ `[иконка: имя — подпись]` — сама иконка крупно
            # и подпись рядом, а не заглушка во всю ширину блока.
            if inner.lower().startswith("иконка:"):
                имя, _, подпись = inner.split(":", 1)[1].partition("—")
                з = значок(имя)
                if з:
                    cur.append('        <p class="иконка-строка">'
                               + з.replace('class="и"', 'class="и крупно"')
                               + "<span>" + inline(подпись.strip() or имя.strip())
                               + "</span></p>")
                    continue
            if "уточнить" in inner:
                cur.append(f'        <p><span class="уточнить">{inline(inner)}</span></p>')
            else:
                # ВЫСОТА ЗАГЛУШКИ ЗАДАЁТСЯ ПОСЛЕ «|»: «[ИЛЛЮСТРАЦИЯ — … | 62мм]».
                # Без этого пустое место на полосе закрывать нечем — заглушка
                # всегда была ростом 30 мм (просьба дизайнера 15.09.2026).
                высота = "30mm"
                if "|" in inner:
                    inner, хвост = inner.rsplit("|", 1)
                    inner = inner.strip()
                    высота = хвост.strip().replace("мм", "mm")
                cur.append(f'        <div class="заглушка рисунок" style="height:{высота}">'
                           f'[{inline(inner.upper())}]</div>')
            continue
        if ln.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip("|").split("|")])
                i += 1
            head, data = rows[0], [r for r in rows[1:] if not set("".join(r)) <= set("-: ")]
            t = ['        <table>']
            # ШИРИНЫ СТОЛБЦОВ — по числу дефисов в строке-разделителе:
            # «|--|---|-----|» даёт 20 %, 30 %, 50 %. Одинаковые — без ширин.
            раздел = next((r for r in rows[1:2] if set("".join(r)) <= set("-: ")), None)
            if раздел and len({len(c.strip(":")) for c in раздел}) > 1:
                весь = sum(len(c.strip(":")) for c in раздел)
                t.append("          <colgroup>" + "".join(
                    f'<col style="width:{100 * len(c.strip(":")) / весь:.0f}%">' for c in раздел) + "</colgroup>")
            t.append("          <tr>" + "".join(f"<th>{inline(h.capitalize())}</th>" for h in head) + "</tr>")
            for r in data:
                t.append("          <tr>" + "".join(
                    "<td>%s</td>" % (ячейка_с_иконкой(c) or inline(c)) for c in r)
                    + "</tr>")
            t.append("        </table>")
            cur.extend(t)
            continue
        m = re.match(r"(\s*)(-|\d+\.)\s+(.*)", ln)
        if m:
            tag = "ul" if m.group(2) == "-" else "ol"
            items = []
            while i < len(lines):
                mm = re.match(r"(-|\d+\.)\s+(.*)", lines[i])
                if mm and ((mm.group(1) == "-") == (tag == "ul")):
                    items.append(mm.group(2))
                elif lines[i].startswith("  ") and items and lines[i].strip():
                    items[-1] += " " + lines[i].strip()
                else:
                    break
                i += 1
            cur.append(f"        <{tag}>")
            for it in items:
                cur.append(f"          <li>{inline(it)}</li>")
            cur.append(f"        </{tag}>")
            continue
        p = [ln.strip()]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r"(#|>|\||-\s|\d+\.\s|\[|---)", lines[i]):
            p.append(lines[i].strip())
            i += 1
        cur.append(f"        <p>{inline(' '.join(p))}</p>")
    flush()
    # ЛЕГЕНДА ПОД РИСУНКОМ НА ВСЮ ШИРИНУ — в две колонки. Одной колонкой список
    # из девяти пунктов не помещается на полосу, а рисунок ужимать некуда.
    for k in range(1, len(res)):
        предыдущий = res[k - 1]
        # ТЕКСТ ПОД ШИРОКИМ РИСУНКОМ — ТОЖЕ В ДВЕ КОЛОНКИ: одной колонкой он
        # не помещается на полосу, а рисунок ужимать некуда (15.09.2026).
        if "рисунок-во-всю" in предыдущий and "<h2>" not in res[k]:
            res[k] = res[k].replace('<div class="блок', '<div class="блок легенда', 1)
    return res


pages = [p for p in body.split("<!-- стр -->")]
fons = ["фон1", "фон2", "фон3", "фон4"]
html_pages = []
for k, chunk in enumerate(pages):
    n = first + k
    head = f'    {заголовок_главы(title)}\n\n' if k == 0 else ""
    куски = blocks(chunk)
    # ВВОДНЫЙ АБЗАЦ ГЛАВЫ: один короткий абзац в начале главы набирается
    # крупнее и без подложки — это зачин полосы, а не правило.
    if (k == 0 and куски and len(куски) > 1
            and куски[0].count("<p>") == 1
            and "<h2>" not in куски[0] and "<h3>" not in куски[0]):
        куски[0] = куски[0].replace('<div class="блок">',
                                    '<div class="блок вводка">', 1)
    inner = (chr(10) * 2).join(куски)
    просторно = " просторно" if ПРОСТОРНО else ""
    html_pages.append(f'  <div class="стр {fons[n % 4]}{просторно}">\n    {КАЙМА}\n{head}    <div class="две">\n{inner}\n    </div>\n\n'
                      f'    <div class="колонцифра"><span>{n}</span></div>\n  </div>\n')

frag = []
for k in range(0, len(html_pages), 2):
    a = first + k
    b = a + 1
    frag.append(f"<!-- ============ РАЗВОРОТ {a}–{b} · {title.upper()} ============ -->\n"
                f'<p class="подпись-разворота">Разворот · страницы {a}–{b} · {title}</p>\n'
                f'<div class="разворот">\n\n' + "\n".join(html_pages[k:k + 2]) + "</div>\n\n")
open(out, "w", encoding="utf-8").write("".join(frag))
print(len(html_pages), "стр.")
