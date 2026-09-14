# -*- coding: utf-8 -*-
"""Перестиль книги правил: «командная панель» вместо чёрной рамки.

Что меняется — только оформление, текст и вёрстка страниц те же:
- рамка страницы: векторная кайма со скошенными углами, скобками по углам и
  насечками-рисками, у корешка открыта (левая и правая страницы — зеркально);
- заголовок главы: зелёная плашка с косым срезом «ГЛАВА N» и сам заголовок,
  за ним линия с ромбом;
- врезки Пример / Важно! / Совет: панель со скошенными углами и цветным
  ярлыком-плашкой; блоки текста — тонкий кант;
- подзаголовки: h2 с двойной линией (тонкая + короткая жирная), h3 с
  треугольной меткой; таблицы, колонцифра, оглавление, фазы — в том же языке;
- поверх фона страницы — едва заметная гекс-сетка.

Всё плоское и векторное: ни свечения, ни градиентов — так велит стильбиблия
для интерфейсной графики. Палитра прежняя: кремовая бумага, чернила, зелень
келемия, охра.
"""
import glob
import io
import os
import re

D = os.path.dirname(os.path.abspath(__file__))
КНИГА = glob.glob(r"C:\shared\forged-in-kelium\rules\Книга правил*\вёрстка\Книга правил.html")[0]


# ---------------------------------------------------------------- кайма

def кайма():
    """SVG-рамка страницы 220×220 мм. Стороны и хвосты — отдельными путями,
    чтобы CSS мог убрать сторону у корешка и дотянуть верх и низ до края."""
    o = 5.0          # внешняя линия
    c = 4.0          # скос угла
    i = 6.8          # внутренняя тонкая линия
    ci = 3.2
    L = 220.0
    верх = f"M{o+c},{o} H{L-o-c}"
    низ = f"M{o+c},{L-o} H{L-o-c}"
    лев = f"M{o+c},{o} L{o},{o+c} V{L-o-c} L{o+c},{L-o}"
    прав = f"M{L-o-c},{o} L{L-o},{o+c} V{L-o-c} L{L-o-c},{L-o}"
    хв_л = f"M{o+c},{o} H0 M{o+c},{L-o} H0"
    хв_п = f"M{L-o-c},{o} H{L} M{L-o-c},{L-o} H{L}"
    вн_верх = f"M{i+ci},{i} H{L-i-ci}"
    вн_низ = f"M{i+ci},{L-i} H{L-i-ci}"
    вн_лев = f"M{i+ci},{i} L{i},{i+ci} V{L-i-ci} L{i+ci},{L-i}"
    вн_прав = f"M{L-i-ci},{i} L{L-i},{i+ci} V{L-i-ci} L{L-i-ci},{L-i}"
    вн_хв_л = f"M{i+ci},{i} H0 M{i+ci},{L-i} H0"
    вн_хв_п = f"M{L-i-ci},{i} H{L} M{L-i-ci},{L-i} H{L}"
    # риски по верху и низу: каждые 10 мм, длинная — каждые 50
    риски = []
    for x in range(20, 201, 10):
        h = 1.9 if x % 50 == 0 else 1.0
        риски.append(f"M{x},{o} v{h} M{x},{L-o} v-{h}")
    # скобки по углам — жирные, келемий
    b = 2.6
    s = 11.0
    скобки = {
        "л": f"M{b},{b+s} V{b} H{b+s} M{b},{L-b-s} V{L-b} H{b+s}",
        "п": f"M{L-b-s},{b} H{L-b} V{b+s} M{L-b-s},{L-b} H{L-b} V{L-b-s}",
    }
    return f'''<svg class="кайма" viewBox="0 0 220 220" xmlns="http://www.w3.org/2000/svg">
<path class="к-осн" d="{верх} {низ}"/><path class="к-осн к-лев" d="{лев}"/><path class="к-осн к-прав" d="{прав}"/>
<path class="к-осн к-хвл" d="{хв_л}"/><path class="к-осн к-хвп" d="{хв_п}"/>
<path class="к-тонк" d="{вн_верх} {вн_низ}"/><path class="к-тонк к-лев" d="{вн_лев}"/><path class="к-тонк к-прав" d="{вн_прав}"/>
<path class="к-тонк к-хвл" d="{вн_хв_л}"/><path class="к-тонк к-хвп" d="{вн_хв_п}"/>
<path class="к-риски" d="{' '.join(риски)}"/>
<path class="к-скобка к-лев" d="{скобки['л']}"/><path class="к-скобка к-прав" d="{скобки['п']}"/>
</svg>'''


# ---------------------------------------------------------------- CSS

СКОС = "polygon({c} 0, 100% 0, 100% calc(100% - {c}), calc(100% - {c}) 100%, 0 100%, 0 {c})"
ГЕКС_СЕТКА = ("url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='52' height='90' "
              "viewBox='0 0 52 90'><path d='M26 1 L50 15 L50 45 L26 59 L2 45 L2 15 Z M26 61 L50 75 L50 105 "
              "M26 61 L2 75 L2 105' fill='none' stroke='%232A2318' stroke-opacity='.075' stroke-width='1.1'/></svg>\")")

CSS = r"""
  /* Оформление книг игры: квадрат 22×22 см, две колонки, Tektur, охра
     страницы, зелень келемия. РЕДАКЦИЯ «КОМАНДНАЯ ПАНЕЛЬ» (14.09.2026):
     векторная кайма со скосами и скобками, плашки с косым срезом, панели
     врезок со скошенными углами — тот же язык, что на картах приказов и на
     задней обложке. Всё плоское, без свечения и градиентов. */
  :root {
    --страница: #F7F1E1;
    --подложка: rgba(247, 241, 225, .90);
    --чернила: #2A2318;
    --охра: #6B4413;
    --келемий: #186C24;
    --келемий-ярк: #3FBF62;
    --серый: #5E5139;
    --рамка: #D8CCAE;
    --кант: rgba(42, 35, 24, .28);
    --выноска: #E7F0E2;
    --пример: #F2E4C4;
    --пример-кант: #A9761E;
    --важно: #F3DED4;
    --важно-кант: #9A2A12;
    --совет: #E4EFDF;
    --место-иконки: #EADFC6;
    --уточнить: #9A2A12;
  }
  * { box-sizing: border-box; }
  html { background: #4a463f; }
  body { margin: 0; padding: 20px 0 40px; font-family: "Tektur", sans-serif; color: var(--чернила); }

  .подпись-разворота { text-align: center; color: #e6dfd0; font: 13px "Tektur Med", "Tektur", sans-serif; margin: 0 0 8px; }
  .разворот { display: flex; justify-content: center; width: 440mm; margin: 0 auto 30px; }
  .разворот.одна { justify-content: flex-end; }

  .стр {
    flex: none; width: 220mm; height: 220mm; position: relative; overflow: hidden; isolation: isolate;
    background: var(--страница) center / cover no-repeat;
    padding: 15mm 15mm 16mm;
    box-shadow: 0 2px 10px rgba(0,0,0,.4);
  }
  ФОНЫ
  /* Гекс-сетка едва заметной разметкой поверх фона страницы, под текстом. */
  .стр:not(.обложка)::after {
    content: ""; position: absolute; inset: 0; z-index: -1; pointer-events: none;
    background-image: ГЕКС_СЕТКА; background-size: 13mm 22.5mm; opacity: .8;
  }

  /* ---- кайма страницы ---- */
  .кайма { position: absolute; inset: 0; width: 220mm; height: 220mm; z-index: 5; pointer-events: none; overflow: visible; }
  .кайма .к-осн { fill: none; stroke: var(--чернила); stroke-width: .5; stroke-linejoin: round; }
  .кайма .к-тонк { fill: none; stroke: var(--чернила); stroke-width: .16; opacity: .7; }
  .кайма .к-риски { fill: none; stroke: var(--чернила); stroke-width: .25; }
  .кайма .к-скобка { fill: none; stroke: var(--келемий); stroke-width: 1.05; stroke-linecap: square; }
  .кайма .к-хвл, .кайма .к-хвп { display: none; }
  /* У корешка рамка открыта: левая страница теряет правую сторону, правая — левую,
     а верх и низ дотягиваются до самого края. */
  .разворот:not(.одна):not(.левая) > .стр:first-child .к-прав { display: none; }
  .разворот:not(.одна):not(.левая) > .стр:first-child .к-хвп { display: block; }
  .разворот:not(.одна):not(.левая) > .стр:last-child .к-лев { display: none; }
  .разворот:not(.одна):not(.левая) > .стр:last-child .к-хвл { display: block; }
  .разворот.одна > .стр .к-лев { display: none; }
  .разворот.одна > .стр .к-хвл { display: block; }
  .разворот.левая > .стр .к-прав { display: none; }
  .разворот.левая > .стр .к-хвп { display: block; }
  .стр.обложка { padding: 0; background: #222; }
  .стр.обложка img { width: 100%; height: 100%; object-fit: cover; display: block; }

  .колонцифра {
    position: absolute; bottom: 5.8mm; left: 0; right: 0; text-align: center; z-index: 6;
    font: 700 10pt/1 "Tektur", sans-serif; color: var(--страница);
  }
  .колонцифра span { display: inline-block; background: var(--чернила); padding: .9mm 3.8mm .75mm;
    clip-path: polygon(1.6mm 0, 100% 0, calc(100% - 1.6mm) 100%, 0 100%); }
  .подготовка.левая-подг .колонцифра { text-align: left; left: 13mm; }
  .подготовка.правая-подг .колонцифра { text-align: right; right: 13mm; }

  /* ПОДЛОЖКА ПОД ТЕКСТОМ. Три правила, все три — просьба дизайнера
     14.09.2026: воздух от края текста, одинаковые поля слева и справа (никаких
     отрицательных отступов, из-за которых колонки выглядели по-разному) и
     заливка полупрозрачная, чтобы бумага и гекс-сетка проступали. */
  .блок { background: rgba(247, 241, 225, .66); padding: 1.9mm 2.7mm 1.6mm;
    margin: 0 0 2.1mm; outline: .18mm solid var(--кант); outline-offset: -.18mm; }
  .блок > :last-child { margin-bottom: 0; }

  /* ---- заголовок главы: плашка с номером, заголовок, линия с ромбом ---- */
  .глава {
    display: flex; align-items: center; gap: 3mm; position: relative;
    font: 700 17pt/1.15 "Tektur", sans-serif; color: var(--охра);
    padding: 0 0 1.6mm; margin: 0 0 3.6mm;
  }
  .глава::after { content: ""; flex: 1; height: .5mm; background: var(--келемий); position: relative; }
  .глава .гл-н { flex: none; background: var(--келемий); color: var(--страница); font: 800 9pt/1 "Tektur", sans-serif;
    text-transform: uppercase; letter-spacing: .1em; padding: 2.1mm 3.6mm 1.9mm 3mm; align-self: stretch; display: flex; align-items: center;
    clip-path: polygon(0 0, 100% 0, calc(100% - 2.4mm) 100%, 0 100%); }
  .глава .гл-т { flex: none; padding-left: .4mm; }
  .глава:not(:has(.гл-н)) { padding-left: 2.4mm; }
  .глава .гл-т::after { content: ""; display: inline-block; width: 2mm; height: 2mm; background: var(--охра); transform: rotate(45deg) translateY(-.2mm); margin-left: 3.2mm; vertical-align: middle; }

  h2 { font: 700 11.5pt/1.2 "Tektur", sans-serif; color: var(--охра); margin: 3mm 0 1.4mm; padding-bottom: .7mm; position: relative;
    border-bottom: .22mm solid var(--келемий); }
  h2::after { content: ""; position: absolute; left: 0; bottom: -.62mm; width: 9mm; height: 1mm; background: var(--келемий); }
  h2:first-child { margin-top: 0; }
  h3 { font: 700 10pt/1.2 "Tektur", sans-serif; color: var(--келемий); margin: 2.5mm 0 1mm; }
  h3::before { content: ""; display: inline-block; width: 1.7mm; height: 1.9mm; background: var(--келемий);
    clip-path: polygon(0 0, 100% 50%, 0 100%); margin-right: 1.5mm; vertical-align: -.05mm; }
  p, li { font: 9.5pt/1.3 "Tektur", sans-serif; margin: 0 0 1.4mm; hyphens: auto; }
  /* ВВОДНЫЙ АБЗАЦ ГЛАВЫ — крупнее и без подложки: он задаёт тон полосе, а не
     спорит с блоками правил (просьба дизайнера 14.09.2026). */
  .вводка { background: none; outline: 0; padding: 0 1mm 1mm; margin: 0 0 3.4mm; column-span: all; }
  .вводка p { font: 10.5pt/1.34 "Tektur", sans-serif; color: var(--чернила); }
  .вводка p:last-child { margin-bottom: 0; }
  ol, ul { margin: 0 0 1.4mm; padding-left: 4.5mm; }
  li { margin-bottom: .8mm; }
  ul li::marker { color: var(--келемий); content: "▸ "; }
  ol li::marker { color: var(--охра); font-weight: 700; }
  b { font-weight: 700; }
  .ссылка { color: var(--серый); }

  .две { column-count: 2; column-gap: 6mm; column-fill: balance; }
  /* БЛОК НЕ РВЁТСЯ МЕЖДУ КОЛОНКАМИ. Прежде заголовок с одной фразой
     оставался внизу первой колонки, а продолжение уезжало во вторую
     отдельной рамкой и читалось как другой раздел. */
  .две .блок { break-inside: avoid; }
  .две .пример, .две h2, .две h3, .две table, .две .заглушка, .две svg { break-inside: avoid; }
  .две h2, .две h3 { break-after: avoid; }
  .звезда, .две li, .две p { break-inside: avoid; }
  .две:has(> .блок:only-child) { column-count: 1; }

  /* ---- таблицы ---- */
  table { width: 100%; border-collapse: collapse; font: 10pt/1.3 "Tektur Narrow", "Tektur", sans-serif; background: rgba(255,255,255,.55);
    outline: .18mm solid var(--кант); }
  th { background: var(--келемий); color: #fff; font-weight: 700; text-align: left; padding: 1.3mm 1.8mm; text-transform: uppercase; letter-spacing: .04em; font-size: 8.6pt; }
  th:first-child { padding-left: 2.6mm; }
  td:first-child:has(.ико) { white-space: nowrap; }
  td { padding: 1.2mm 1.8mm; border-bottom: .25mm dotted var(--пример-кант); vertical-align: middle; }
  tr:last-child td { border-bottom: 0; }

  /* ---- заглушки ---- */
  .ико {
    display: inline-block; min-width: 4.8mm; height: 3.9mm; padding: 0 .8mm;
    background: var(--место-иконки); border: .25mm dashed var(--пример-кант); border-radius: .8mm;
    font: 8pt/3.5mm "Tektur Narrow", sans-serif; color: var(--серый); text-align: center; vertical-align: -.8mm; white-space: nowrap;
  }
  .ико.крупно { min-width: 10mm; height: 10mm; font-size: 8pt; line-height: 9.6mm; vertical-align: middle; }
  .заглушка {
    background: var(--место-иконки); border: .35mm dashed var(--пример-кант);
    padding: 2.2mm 3mm; margin: 0 0 2.5mm; font: 9pt/1.3 "Tektur Narrow", "Tektur", sans-serif; color: var(--серый);
  }
  .заглушка .метка { display: block; font: 700 9pt "Tektur Narrow", sans-serif; color: var(--охра); margin-bottom: .8mm; letter-spacing: .02em; }
  .заглушка.рисунок { display: flex; flex-direction: column; justify-content: center; text-align: center; }
  /* ЗАПОЛНИТЕЛЬ ВЫНУЖДЕННОЙ ПУСТОТЫ: где полоса не набирается текстом, стоит
     место под художественную иллюстрацию — дизайнер вставит её в конце работы. */
  .добор { column-span: all; margin-top: 2mm; }
  .добор .заглушка { height: 100%; min-height: 34mm; margin: 0; }
  .уточнить { font: 700 9pt "Tektur Narrow", sans-serif; color: var(--уточнить); }
  .звезда { display: flex; gap: 2.5mm; align-items: center; margin: .5mm 0 1.4mm; }
  .звезда p { margin: 0; }

  /* ---- состав игры ---- */
  .сетка { display: grid; gap: 2.4mm 3mm; margin: 0 0 2.5mm; }
  .сетка.к3 { grid-template-columns: repeat(3, 1fr); }
  .сетка.к4 { grid-template-columns: repeat(4, 1fr); }
  .комп .фото {
    height: 15mm; background: var(--место-иконки); border: .3mm dashed var(--пример-кант);
    display: flex; align-items: center; justify-content: center;
    font: 8.5pt "Tektur Narrow", sans-serif; color: var(--серый); text-transform: uppercase; letter-spacing: .05em;
    clip-path: СКОС2;
  }
  .комп .фото.низ { height: 11mm; }
  .крупнее .комп .фото { height: 30mm; }
  .крупнее .комп .фото.низ { height: 22mm; }
  .комп .имя { font: 700 9.5pt/1.2 "Tektur Narrow", "Tektur", sans-serif; margin-top: .9mm; }
  .комп .сколько { font: 9.5pt/1.2 "Tektur Narrow", "Tektur", sans-serif; color: var(--серый); }
  .комп .сколько b { color: var(--чернила); }
  .набор { outline: .18mm solid var(--кант); outline-offset: -.18mm; padding: 2.2mm 2.4mm .6mm; background: var(--подложка); margin: 0 0 2.5mm; clip-path: СКОС2; }
  .набор > p { font-weight: 700; }

  /* ---- разворот подготовки: иллюстрация в центре, текст у внешних краёв ---- */
  .стр.подготовка { padding: 0; }
  .подготовка .схема { position: absolute; inset: 0; width: 220mm; height: 220mm; z-index: 1; }
  .подготовка .столбец { position: absolute; top: 13mm; bottom: 14mm; width: 80mm; z-index: 3; }
  .стр.левая-подг .столбец { left: 12mm; top: 29mm; }
  .стр.правая-подг .столбец { right: 11mm; width: 80mm; }
  .стр.левая-подг .глава { position: absolute; left: 13mm; right: 13mm; top: 12mm; margin: 0; z-index: 3; }
  .столбец .блок { margin: 0 0 1.1mm; padding: 1mm 1.8mm; }
  .столбец h3 { margin: 0 0 1mm; }
  .столбец p { font: 9.5pt/1.28 "Tektur Narrow", "Tektur", sans-serif; margin: 0; }
  .шаг { display: inline-flex; align-items: center; justify-content: center; width: 4.4mm; height: 4.4mm;
    border-radius: 50%; background: var(--келемий); color: #fff; font: 700 8.5pt "Tektur Narrow", sans-serif;
    margin-right: 1mm; vertical-align: middle; }
  .схема .номер-круг { fill: var(--келемий); stroke: #fff; stroke-width: .5; }
  .схема .номер-текст { fill: #fff; font: 700 4.2px "Tektur Narrow", sans-serif; text-anchor: middle; dominant-baseline: central; }
  .схема .выноска { fill: none; stroke: var(--келемий); stroke-width: .45; }
  .схема .точка-выноски { fill: var(--келемий); }
  .схема .макет { fill: rgba(255,255,255,.55); stroke: var(--пример-кант); stroke-width: .35; stroke-dasharray: 1.2 .8; }
  .схема .подпись { fill: var(--чернила); font: 3.6px "Tektur Narrow", sans-serif; text-anchor: middle; }
  .схема .метка-илл { fill: var(--охра); font: 700 3.8px "Tektur Narrow", sans-serif; text-anchor: middle; }

  /* ---- врезки: Пример, Важно!, Совет — панель со скосами и ярлык-плашка ---- */
  .пример { position: relative; background: var(--пример-кант); padding: .3mm; margin: 1.4mm 0 2.4mm; break-inside: avoid;
    clip-path: СКОС28; }
  .пример > * { position: relative; }
  .пример::before { content: ""; position: absolute; inset: .3mm; background: var(--пример); clip-path: СКОС25; }
  .пример .метка { display: inline-block; font: 800 8.4pt/1 "Tektur", sans-serif; color: var(--страница); text-transform: uppercase; letter-spacing: .08em;
    background: var(--пример-кант); padding: 1.2mm 3.2mm 1mm 2.6mm; margin: 0 0 1.2mm 0;
    clip-path: polygon(0 0, 100% 0, calc(100% - 2.2mm) 100%, 0 100%); }
  .пример p, .пример li { font-size: 9.5pt; padding: 0 2.9mm; }
  .пример > p:last-child, .пример > ul:last-child, .пример > ol:last-child { padding-bottom: 2mm; margin-bottom: 0; }
  .пример ul, .пример ol { padding-left: 6.6mm; }
  .пример ul li, .пример ol li { padding: 0 2.6mm 0 0; }
  .пример.важно { background: var(--важно-кант); }
  .пример.важно::before { background: var(--важно); }
  .пример.важно .метка { background: var(--важно-кант); }
  .пример.совет { background: var(--келемий); }
  .пример.совет::before { background: var(--совет); }
  .пример.совет .метка { background: var(--келемий); }

  .фазы { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin: 0 0 3mm;
    column-span: all; break-inside: avoid; }
  .фазы > div { position: relative; background: var(--подложка); border-top: 1mm solid var(--келемий); padding: 1.8mm 2.4mm;
    outline: .18mm solid var(--кант); outline-offset: -.18mm; clip-path: СКОС2; }
  .фазы > div { padding: 2.1mm 2.6mm; }
  .фазы b { display: block; font: 700 11pt "Tektur", sans-serif; color: var(--охра); margin-bottom: .8mm; }
  .фазы p { margin: 0; font: 9.8pt/1.28 "Tektur", sans-serif; }

  .разворот.левая { justify-content: flex-start; }
  .схема-гекса { display: block; width: 62mm; margin: 1mm auto 2mm; }

  .обзор td { font-size: 9pt; vertical-align: top; }
  .обзор td:nth-child(1) { width: 30%; }

  .сведения { display: flex; gap: 6mm; justify-content: center; margin: 2mm 0 0; }
  .сведения b { font: 700 13pt "Tektur", sans-serif; color: var(--охра); background: var(--подложка); padding: 2mm 4.6mm; outline: .18mm solid var(--кант); outline-offset: -.18mm;
    clip-path: polygon(2mm 0, 100% 0, calc(100% - 2mm) 100%, 0 100%); }
  .оглавление { list-style: none; margin: 0; padding: 3mm 5mm; background: var(--подложка); outline: .18mm solid var(--кант); outline-offset: -.18mm; clip-path: СКОС28; }
  .оглавление li { display: flex; align-items: center; gap: 3mm; font: 12pt/1.9 "Tektur", sans-serif; margin: 0; border-bottom: .25mm dotted var(--пример-кант); }
  .оглавление li:last-child { border-bottom: 0; }
  .оглавление .н { width: 10mm; flex: none; font: 800 9.5pt/1 "Tektur", sans-serif; color: var(--страница); background: var(--келемий); text-align: center; padding: 1.3mm 0 1.1mm;
    clip-path: polygon(0 0, 100% 0, calc(100% - 1.6mm) 100%, 0 100%); }
  .оглавление .т { flex: 1; }
  .оглавление .с { font-weight: 700; color: var(--охра); }
  .оглавление .будет { color: var(--серый); }
  .оглавление .будет .н { background: var(--серый); }

  .рисунок-во-всю { column-span: all; break-inside: avoid; }
  /* ЗАГОЛОВОК ПЕРЕД РИСУНКОМ НА ВСЮ ШИРИНУ — тоже на всю ширину: иначе
     подложка заголовка обрывается посреди полосы (замечание дизайнера). */
  .две .блок:has(+ .рисунок-во-всю) { column-span: all; break-after: avoid; }
  .рисунок-в-колонке { break-inside: avoid; }
  .две .блок:has(.рис) { break-inside: avoid; }
  .рис { position: relative; margin: 0 auto; }
  .рис img { display: block; width: 100%; height: auto; }
  .рис svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
  .рис .номер-круг { fill: #fff; stroke: var(--келемий); }
  .рис .номер-текст { fill: var(--келемий); font-family: "Tektur Narrow", sans-serif; font-weight: 700; text-anchor: middle; dominant-baseline: central; }
  .рис .черта { stroke: var(--келемий); fill: none; }
  .рис .выноска-л { stroke: var(--келемий); }
  .рис .выноска-т { fill: var(--келемий); }
  /* ЛЕГЕНДА ПОД РИСУНКОМ: две колонки. Список из девяти пунктов одной
     колонкой не помещается на полосу, а рисунок ужимать уже некуда. */
  .блок.легенда { column-count: 2; column-gap: 5mm; column-span: all; }
  .блок.легенда h3 { column-span: all; }
  .блок.легенда > ul, .блок.легенда > ol { margin: 0; }
  .блок.легенда li { break-inside: avoid; }
  .легенда-цифры { margin: 1mm 0 0; padding-left: 5mm; }
  .легенда-цифры li { font-size: 9pt; margin-bottom: .4mm; }

  @media print {
    html, body { background: none; padding: 0; }
    .подпись-разворота { display: none; }
    .разворот, .разворот.одна { display: block; width: auto; margin: 0; }
    .стр { box-shadow: none; break-after: page; }
    @page { size: 220mm 220mm; margin: 0; }
  }
"""


def собрать_css(старый):
    фоны = "\n  ".join(re.findall(r'\.стр\.фон\d \{ background-image: url\("data:[^"]+"\); \}', старый))
    задняя = ""
    i = старый.find("  /* ---- задняя сторона обложки")
    if i >= 0:
        задняя = старый[i:]
    css = (CSS.replace("ФОНЫ", фоны)
              .replace("ГЕКС_СЕТКА", ГЕКС_СЕТКА)
              .replace("СКОС28", СКОС.format(c="2.8mm"))
              .replace("СКОС25", СКОС.format(c="2.5mm"))
              .replace("СКОС2", СКОС.format(c="2mm")))
    return css + "\n" + задняя


def патч_разметки(t, кайма_svg):
    # заголовок главы двумя частями
    t = re.sub(r'<div class="глава">(Глава \d+)\.\s*([^<]+)</div>',
               r'<div class="глава"><span class="гл-н">\1</span><span class="гл-т">\2</span></div>', t)
    # классы врезок по метке
    t = re.sub(r'<div class="пример"((?:\s[^>]*)?)>(\s*<span class="метка">Важно!?</span>)',
               r'<div class="пример важно"\1>\2', t)
    t = re.sub(r'<div class="пример"((?:\s[^>]*)?)>(\s*<span class="метка">Совет</span>)',
               r'<div class="пример совет"\1>\2', t)
    # кайма на каждой странице, кроме обложек; старые каймы убрать
    t = re.sub(r'\s*<svg class="кайма".*?</svg>', "", t, flags=re.S)
    def вставить(m):
        cls = m.group(1)
        if "обложка" in cls:
            return m.group(0)
        return m.group(0) + "\n    " + кайма_svg
    t = re.sub(r'<div class="стр ([^"]*)">', вставить, t)
    return t


def main():
    t = io.open(КНИГА, encoding="utf-8").read()
    a = t.index("<style>") + len("<style>")
    b = t.index("</style>")
    t = t[:a] + "\n" + собрать_css(t[a:b]) + t[b:]
    svg = кайма()
    io.open(os.path.join(D, "_кайма.svg"), "w", encoding="utf-8").write(svg)
    t = патч_разметки(t, svg)
    io.open(КНИГА, "w", encoding="utf-8").write(t)
    print("перестиль применён; страниц с каймой:", t.count('<svg class="кайма"'))


if __name__ == "__main__":
    main()
