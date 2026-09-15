# -*- coding: utf-8 -*-
"""ПОЛОСА 2 — МИР ИГРЫ: иллюстрация во всю ширину и текст под ней.

Это полоса за обложкой, где стояли две заглушки: под картинку и под текст
о мире. Обе заменены настоящими (просьба дизайнера 15.09.2026). Полоса
собирается отдельным сборщиком, потому что она не из главы, а из вёрстки —
как выходные данные и задняя обложка.

Запуск: python make_mir.py
"""
import base64
import glob
import io
import os

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*\вёрстка\Книга правил.html")[0]
КАРТИНКА = r"C:\shared\forged-in-kelium\rules\иллюстрации\мир.jpg"

ТЕКСТ = """
      <p>Сначала были динозавры, потом мы, потом — <b>келемий</b>. Кристалл
      полез из ядра Земли без объявления войны и без инструкции: зелёный,
      светится, режется плохо, продаётся отлично. Геологи сказали, что этого
      не может быть. Келемий ответил, что он уже.</p>
      <p>Дальше сюрприз: вернулись соседи. Венера, Марс и Юпитер — оказывается,
      они здесь давно бывали, просто вышли покурить на пару геологических эпох.
      Прилетели все трое, и у каждого с собой документы на кристалл. Переговоры
      заняли одиннадцать минут, из них десять — перевод.</p>
      <p>Теперь мы копаем. И стреляем. Копаем и стреляем. По вечерам разбираем
      чужие обломки: технологии у всех разные, а горит всё одинаково.</p>
      <p>А между нами: это настольная игра, и автору на лор, честно говоря,
      плевать. Важно, что здесь можно очень круто строить и очень круто мочить
      друг друга. Кристалл зелёный, соседи злые, правила вы уже открыли.
      Погнали.</p>
"""

CSS = r"""
  /* ---- полоса «мир игры» за обложкой ---- */
  .мир-рис { margin: 5mm 0 4.5mm; }
  .мир-рис img { display: block; width: 100%; height: auto;
    clip-path: polygon(7mm 0, 100% 0, 100% calc(100% - 7mm), calc(100% - 7mm) 100%, 0 100%, 0 7mm); }
  .мир-текст { column-count: 2; column-gap: 6mm; }
  .мир-текст p { font: 10.4pt/1.36 "Tektur Narrow", sans-serif; margin: 0 0 2.4mm;
    break-inside: avoid; }
  .мир-текст p:last-child { margin-bottom: 0; color: var(--охра); }
"""


def кадр(ширина=1700):
    im = Image.open(КАРТИНКА).convert("RGB")
    im = im.resize((ширина, round(im.height * ширина / im.width)), Image.LANCZOS)
    буфер = io.BytesIO()
    im.save(буфер, "JPEG", quality=88, optimize=True)
    return "data:image/jpeg;base64," + base64.b64encode(буфер.getvalue()).decode()


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    рис = ('    <div class="мир-рис"><img src="%s" alt=""></div>' % кадр())
    текст = '    <div class="мир-текст">%s    </div>' % ТЕКСТ
    старый_рис = ('    <div class="заглушка рисунок" style="height:70mm;'
                  'margin-top:4mm">[ИЛЛЮСТРАЦИЯ — МИР ИГРЫ]</div>')
    if старый_рис in t:
        нач = t.index(старый_рис)
        кон = t.index("</div>", t.index('[ХУДОЖЕСТВЕННЫЙ ТЕКСТ — МИР ИГРЫ]')) + len("</div>")
        кон = t.index("</div>", кон) + len("</div>")
        t = t[:нач] + рис + "\n" + текст + t[кон:]
    else:
        import re
        t = re.sub(r'    <div class="мир-рис">.*?</div>\n    <div class="мир-текст">.*?</div>',
                   рис + "\n" + текст, t, count=1, flags=re.S)
    if ".мир-рис" not in t.split("</style>")[0]:
        t = t.replace("\n</style>", CSS + "\n</style>", 1)
    for попытка in range(12):
        try:
            io.open(КНИГА, "w", encoding="utf-8", newline="\n").write(t)
            break
        except OSError:
            import time
            time.sleep(0.4)
    print("полоса «мир игры» собрана")


if __name__ == "__main__":
    main()
