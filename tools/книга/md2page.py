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
    r"C:\shared\forged-in-kelium\rules\Книга правил*")[0])

src, first, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
text = open(src, encoding="utf-8").read()

title = re.match(r"# (.+)", text).group(1)
body = text.split("\n", 1)[1]


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
    ш = 96
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
            + "<b>" + inline(m.group(2)) + "</b></span>")


def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"\[иконка:\s*([^\]]+)\]", _иконка_в_тексте, s)
    return s


def заголовок_главы(title):
    """«Глава 4. Основы игры» → плашка с номером и сам заголовок."""
    m = re.match(r"(Глава \d+)\.\s*(.+)", title)
    if not m:
        return f'<div class="глава">{inline(title)}</div>'
    return (f'<div class="глава"><span class="гл-н">{inline(m.group(1))}</span>'
            f'<span class="гл-т">{inline(m.group(2))}</span></div>')


# Кайма страницы — векторная рамка, одна на все страницы (её пишет restyle.py).
КАЙМА = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "_кайма.svg"),
             encoding="utf-8").read().strip()


def blocks(md):
    """md-кусок -> список html-блоков (каждый .блок / .пример / .заглушка)."""
    res, cur = [], []
    надвое = [False]
    вовсю = [False]
    крупно = [False]

    def flush():
        if not cur:
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
            res.append('      <div class="%s">\n' % классы + "\n".join(cur)
                       + "\n      </div>")
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
            cur.append(f"        <h2>{inline(ln[3:])}</h2>")
            i += 1
            continue
        if ln.startswith("### "):
            if not (len(cur) == 1 and cur[0].lstrip().startswith("<h2>")):
                flush()
            cur.append(f"        <h3>{inline(ln[4:])}</h3>")
            i += 1
            continue
        if ln.startswith("> "):
            flush()
            q = []
            while i < len(lines) and lines[i].startswith(">"):
                q.append(lines[i][1:].strip())
                i += 1
            qt = " ".join(q)
            m = re.match(r"\*\*(.+?)\*\*\s*(.*)", qt)
            label, rest = (m.group(1).rstrip("."), m.group(2)) if m else ("Пример", qt)
            вид = {"Важно!": " важно", "Важно": " важно", "Совет": " совет"}.get(label, "")
            res.append(f'      <div class="пример{вид}">\n        <span class="метка">{inline(label)}</span>\n'
                       f"        <p>{inline(rest)}</p>\n      </div>")
            continue
        # ТРИ ФАЗЫ РАУНДА — сетка из трёх колонок (CSS .фазы). В разметке:
        #   :фазы:
        #   I. Обновление | стол готовится к новому раунду
        #   :конец:
        # :добор: подпись — заполнитель вынужденной пустоты на полосе.
        # Дизайнер вставит сюда художественную иллюстрацию в конце работы.
        # :надвое: — раздел идёт блоком во всю ширину: рисунки слева, текст справа.
        if ln.strip() == ":надвое:":
            надвое[0] = True
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
            фрагмент = open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                         "_" + ln.strip()[2:-2] + ".svg"),
                            encoding="utf-8").read()
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
            t = ['        <table>', "          <tr>" + "".join(f"<th>{inline(h.capitalize())}</th>" for h in head) + "</tr>"]
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
    html_pages.append(f'  <div class="стр {fons[n % 4]}">\n    {КАЙМА}\n{head}    <div class="две">\n{inner}\n    </div>\n\n'
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
