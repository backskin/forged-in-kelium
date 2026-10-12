/* Общие помощники: разметка, геометрия гекса, силуэты жетонов, глоссарий.
   Ничего внешнего — всё считается на месте. */

(function () {
  'use strict';

  var D = window.KR_DATA;

  /* --------------------------------------------------------------- утилиты */

  function esc(s) {
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  /** Склонение по числу: plural(2, 'гекс','гекса','гексов'). */
  function plural(n, one, few, many) {
    var a = Math.abs(n) % 100, b = a % 10;
    if (a > 10 && a < 20) return many;
    if (b > 1 && b < 5) return few;
    if (b === 1) return one;
    return many;
  }

  function nplural(n, one, few, many) { return n + ' ' + plural(n, one, few, many); }

  /** Таблица из массива заголовков и массива строк. */
  function table(head, rows, opts) {
    opts = opts || {};
    var h = '<div class="tablewrap"><table><thead><tr>';
    head.forEach(function (c, i) {
      h += '<th' + (opts.num && opts.num.indexOf(i) >= 0 ? ' class="num"' : '') + '>' + c + '</th>';
    });
    h += '</tr></thead><tbody>';
    rows.forEach(function (r) {
      h += '<tr>';
      r.forEach(function (c, i) {
        h += '<td' + (opts.num && opts.num.indexOf(i) >= 0 ? ' class="num"' : '') + '>' +
          (c === null || c === undefined ? '—' : c) + '</td>';
      });
      h += '</tr>';
    });
    return h + '</tbody></table></div>';
  }

  /** Сворачиваемый блок «Точные правила и числа». */
  function exact(title, html) {
    return '<details class="exact"><summary>' + (title || 'Точные правила и числа') +
      '</summary><div class="exact__body">' + html + '</div></details>';
  }

  function note(title, html) {
    return '<div class="note"><div class="note__title">' + title + '</div>' + html + '</div>';
  }

  function warn(title, html) {
    return '<div class="warnbox"><div class="warnbox__title">' + title + '</div>' + html + '</div>';
  }

  function example(html, title) {
    return '<div class="example"><div class="example__title">' +
      (title || 'Пример') + '</div>' + html + '</div>';
  }

  function mistake(html) {
    return '<div class="mistake"><b>Частая ошибка.</b> ' + html + '</div>';
  }

  /** Контейнер интерактивного элемента: заголовок + пустое тело под виджет. */
  function widget(id, title, sub) {
    return '<div class="widget" data-widget="' + id + '">' +
      '<div class="widget__head"><b>' + title + '</b>' +
      (sub ? '<span>' + sub + '</span>' : '') + '</div>' +
      '<div class="widget__body" id="w-' + id + '"></div></div>';
  }

  /* --------------------------------------------------------------- термины */

  var GLOSSARY = {};

  function defineTerms(map) {
    Object.keys(map).forEach(function (k) { GLOSSARY[k.toLowerCase()] = map[k]; });
  }

  /** Обёртка термина: подчёркнутое слово с пояснением по наведению. */
  function t(word, key) {
    var k = (key || word).toLowerCase();
    var def = GLOSSARY[k];
    return '<span class="term" data-term="' + esc(k) + '" title="' +
      esc(def ? def.short : '') + '">' + word + '</span>';
  }

  function glossaryAll() { return GLOSSARY; }

  /* -------------------------------------------------------- силуэты жетонов */

  var COLORS = {
    p1: 'var(--p1)', p2: 'var(--p2)', p3: 'var(--p3)', p4: 'var(--p4)',
    neutral: 'var(--neutral)'
  };

  /**
   * Силуэт жетона как самостоятельный <svg>. Формы — авторские, из
   * «Общие компоненты/svg/», перерисованы вектором и красятся цветом игрока.
   */
  function token(kind, size, color, opts) {
    opts = opts || {};
    var s = window.KR_SHAPES[kind];
    if (!s) return '';
    var vb = s.viewBox.split(' ').map(Number);
    var w = vb[2], h = vb[3];
    var k = (size || 40) / Math.max(w, h);
    var fill = color || 'var(--ink-dim)';
    var shape = s.d
      ? '<path d="' + s.d + '" fill="' + fill + '"/>'
      : '<polygon points="' + s.points + '" fill="' + fill + '"/>';
    return '<svg class="token" viewBox="' + s.viewBox + '" width="' + Math.round(w * k) +
      '" height="' + Math.round(h * k) + '" role="img" aria-label="' + esc(s.name) + '"' +
      (opts.style ? ' style="' + opts.style + '"' : '') + '>' + shape + '</svg>';
  }

  /* ------------------------------------------------------- геометрия гекса */

  /* Гексы «остриём вверх». Направление к соседу номер i — угол −60·i градусов,
     как в SvgFieldRenderer симулятора. Стороны нумеруются 0..5 в том же порядке,
     что и осевые направления. */
  var AXIAL_DIRS = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];

  /** Вершины гекса радиуса r с центром (cx, cy), остриём вверх. */
  function hexCorners(cx, cy, r) {
    var pts = [];
    for (var k = 0; k < 6; k++) {
      var a = Math.PI / 180 * (60 * k - 90);
      pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
    }
    return pts;
  }

  function hexPath(cx, cy, r) {
    return hexCorners(cx, cy, r).map(function (p, i) {
      return (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1);
    }).join(' ') + ' Z';
  }

  /** Центр наземной ячейки (стороны) i — на полпути к соседу i. */
  function sideCenter(cx, cy, r, i, k) {
    var a = Math.PI / 180 * (-60 * i);
    var d = r * (k === undefined ? 0.62 : k);
    return [cx + d * Math.cos(a), cy + d * Math.sin(a)];
  }

  /** Ряд-колонка сценария → осевые координаты (pointy-top, even-r). */
  function rowColToAxial(row, col) {
    var r0 = row - 1, c0 = col - 1;
    return [c0 - ((r0 + (r0 & 1)) >> 1), r0];
  }

  /** Осевые → пиксели (остриём вверх). */
  function axialToPixel(q, r, size) {
    return [size * Math.sqrt(3) * (q + r / 2), size * 1.5 * r];
  }

  /* -------------------------------------- посадка жетонов в гекс (из ядра) */

  /* Порт логики kelium/core/Hex.java. Войска не приколочены: они занимают
     место, но всегда могут подвинуться внутри гекса. Жёсткие только здания и
     стенки нейтралов. Технике всегда нужны две смежные ячейки. */

  /** Умещаются ли v единиц техники (по 2 смежные) и s одиночных на маске free. */
  function feasible(free, v, s) {
    var total = 0, i;
    for (i = 0; i < 6; i++) if (free[i]) total++;
    if (total < 2 * v + s) return false;
    if (v === 0) return true;
    if (total === 6) return v <= 3;           // полный цикл: три пары
    var anchor = -1;
    for (i = 0; i < 6; i++) if (!free[i]) { anchor = i; break; }
    var cap = 0, run = 0;
    for (var k = 1; k <= 6; k++) {
      var j = (anchor + k) % 6;
      if (free[j]) run++; else { cap += run >> 1; run = 0; }
    }
    cap += run >> 1;
    return cap >= v;
  }

  /**
   * Выбрать fp смежных свободных ячеек так, чтобы стоящие войска после этого
   * всё ещё умещались переупаковкой. null — не влезает никак.
   */
  function chooseFootprint(free, fp, vehicles, singles) {
    if (fp <= 0) return null;
    for (var start = 0; start < 6; start++) {
      var run = [], ok = true;
      for (var k = 0; k < fp; k++) {
        var i = (start + k) % 6;
        run.push(i);
        if (!free[i]) { ok = false; break; }
      }
      if (!ok) continue;
      var f2 = free.slice();
      run.forEach(function (i) { f2[i] = false; });
      if (feasible(f2, vehicles, singles)) return run;
    }
    return null;
  }

  /* След здания в наземных ячейках — по «simulator/ПРАВИЛА.md» §17.
     Осторожно: движок (kelium/engine/Actions.FOOTPRINT) даёт казарме след 2.
     Расхождение вынесено в раздел «Спорные места», здесь стоит документная
     цифра. */
  var FOOTPRINT = {
    barracks: 1, factory: 2, airbase: 3, cu: 2, miner: 1, plant: 1,
    infantry: 1, vehicle: 2, aircraft: 0, tower: 1
  };

  /* --------------------------------------------------------------- экспорт */

  window.KR = {
    esc: esc, plural: plural, nplural: nplural,
    table: table, exact: exact, note: note, warn: warn,
    example: example, mistake: mistake, widget: widget,
    t: t, defineTerms: defineTerms, glossary: glossaryAll,
    token: token, COLORS: COLORS,
    AXIAL_DIRS: AXIAL_DIRS, hexCorners: hexCorners, hexPath: hexPath,
    sideCenter: sideCenter, rowColToAxial: rowColToAxial, axialToPixel: axialToPixel,
    feasible: feasible, chooseFootprint: chooseFootprint, FOOTPRINT: FOOTPRINT,
    D: D
  };
}());
