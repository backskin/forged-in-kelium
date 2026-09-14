# -*- coding: utf-8 -*-
"""Выходные данные — предпоследняя страница книги, перед задней обложкой.

Наполнение — по обычаю печатных рулбуков (Gaia Project, Maracaibo, Игра
престолов): создатели игры, тестирование, благодарности, версия правил,
копирайт и куда писать с вопросами. Имён я не знаю, поэтому там, где их должен
вписать дизайнер, стоит обычная для книги пометка «уточнить».
"""
import base64
import glob
import io
import os

from PIL import Image

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*\вёрстка\Книга правил.html")[0]
МАРКЕР = "<!-- ============ ВЫХОДНЫЕ ДАННЫЕ ============ -->"
# Задняя обложка своей шапки-комментария не имеет — ищем её по самой странице.
ЗАД = '<p class="подпись-разворота">Задняя сторона обложки · памятка</p>' 

КАЙМА = io.open(os.path.join(D, "_кайма.svg"), encoding="utf-8").read().strip()
ЛОГО = os.path.join(D, "icons", "логотип.png")


def лого():
    im = Image.open(ЛОГО).convert("RGBA")
    im = im.resize((900, round(im.height * 900 / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def уточнить(что):
    return f'<span class="уточнить">[уточнить: {что}]</span>'


СОЗДАТЕЛИ = [
    ("Автор игры", уточнить("имя")),
    ("Правила и вёрстка книги", уточнить("имя")),
    ("Иллюстрации", уточнить("имена художников")),
    ("Графическое оформление", уточнить("имя")),
    ("Редактура и корректура", уточнить("имя")),
]

ТЕСТ = уточнить("список тестировщиков")
БЛАГОДАРНОСТИ = уточнить("кому и за что")
ИЗДАТЕЛЬ = уточнить("издатель и год")
СВЯЗЬ = уточнить("почта или сайт для вопросов по правилам")


ЗАДНЯЯ = '  <div class="стр обложка задняя">'
КОНЕЦ = "<!-- ============ /ВЫХОДНЫЕ ДАННЫЕ ============ -->"


def страница():
    строки = "".join(
        f'<div class="кто"><span class="роль">{роль}</span>'
        f'<span class="имя">{имя}</span></div>' for роль, имя in СОЗДАТЕЛИ)
    знак = лого()
    return f"""{МАРКЕР}
  <div class="стр фон2 выходные">
    {КАЙМА}
    <div class="глава"><span class="гл-т">Выходные данные</span></div>

    <div class="две">
      <div class="блок">
        <h2>Игра</h2>
        <div class="строки">
          <div class="кто"><span class="роль">Название</span><span class="имя"><b>Келемий. Кристалл раздора</b></span></div>
          <div class="кто"><span class="роль">Игроков</span><span class="имя">2–4</span></div>
          <div class="кто"><span class="роль">Партия</span><span class="имя">120–180 минут</span></div>
          <div class="кто"><span class="роль">Возраст</span><span class="имя">12+</span></div>
          <div class="кто"><span class="роль">Редакция правил</span><span class="имя">черновик, сентябрь 2026</span></div>
        </div>
      </div>

      <div class="блок">
        <h2>Создатели</h2>
        <div class="строки">{строки}</div>
      </div>

      <div class="блок">
        <h2>Тестирование</h2>
        <p>Игру собирали и ломали за столом десятки партий. Спасибо всем, кто
        играл в неё недоделанной и говорил, что не работает: {ТЕСТ}</p>
      </div>

      <div class="блок">
        <h2>Благодарности</h2>
        <p>{БЛАГОДАРНОСТИ}</p>
      </div>

      <div class="пример совет">
        <span class="метка">Остались вопросы по правилам?</span>
        <p>Правила старались написать так, чтобы ответ находился в них самих:
        сначала посмотрите памятку на задней стороне обложки и оглавление.
        Если ответа нет — напишите нам: {СВЯЗЬ}</p>
      </div>

      <div class="блок права">
        <p>{ИЗДАТЕЛЬ}</p>
        <p>Перепечатка и публикация правил, компонентов и иллюстраций игры
        без разрешения правообладателя запрещены.</p>
      </div>
    </div>

    <div class="марка"><img src="{знак}" alt=""></div>

    <div class="колонцифра"><span>0</span></div>
  </div>
</div>

<p class="подпись-разворота">Задняя сторона обложки · памятка</p>
<div class="разворот одна">
{КОНЕЦ}
"""


CSS = r"""
  /* ---- выходные данные ---- */
  .выходные .строки { margin: .6mm 0 0; }
  .выходные .кто { display: flex; align-items: baseline; gap: 2.4mm; padding: .9mm 0;
    border-bottom: .22mm dotted var(--пример-кант); }
  .выходные .кто:last-child { border-bottom: 0; }
  .выходные .роль { flex: none; width: 34mm; font: 700 8.4pt "Tektur Narrow", sans-serif;
    color: var(--келемий); text-transform: uppercase; letter-spacing: .03em; }
  .выходные .имя { flex: 1; font: 9.5pt/1.25 "Tektur", sans-serif; }
  .выходные .марка { position: absolute; left: 0; right: 0; bottom: 22mm; text-align: center; }
  .выходные .марка img { width: 74mm; height: auto; opacity: .92; }
  .выходные .права p { font: 8.6pt/1.25 "Tektur Narrow", sans-serif; color: var(--серый); }
"""


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    if МАРКЕР in t:
        a = t.index(МАРКЕР)
        b = t.index(ЗАД, a)
        t = t[:a] + t[b:]
    if ".выходные .строки" not in t:
        t = t.replace("\n</style>", CSS + "\n</style>", 1)
    j = t.index(ЗАДНЯЯ)
    # разворот перед выходными данными кончается — страница идёт своей полосой
    t = t[:j] + страница() + t[j:]
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("выходные данные вставлены")


if __name__ == "__main__":
    main()
