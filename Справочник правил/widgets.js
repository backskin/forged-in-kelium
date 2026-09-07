/* Интерактивные элементы справочника.
   Каждый элемент — функция mount(root, ctx), которая сама рисует и оживляет
   свою разметку. Ничего не грузится извне, все числа берутся из KR.D. */

(function () {
  'use strict';

  var D = window.KR_DATA;
  var W = {};

  function h(html) { var d = document.createElement('div'); d.innerHTML = html; return d; }
  function on(root, sel, ev, fn) {
    Array.prototype.forEach.call(root.querySelectorAll(sel), function (n) {
      n.addEventListener(ev, fn);
    });
  }

  /* ================================================================ §7 ГЕКС
     Конструктор гекса: кладёшь жетоны, программа честно говорит, влезет ли
     техника, и показывает почему. Логика посадки — порт kelium/core/Hex.java. */

  W['hex-builder'] = function (root) {
    var R = 118, CX = 150, CY = 132;

    var st = {
      buildings: [],      // {kind, sides: [i,...]}
      vehicles: 0,
      singles: 0,         // пехота и вышки
      aircraft: 0,
      spawn: false
    };

    var BUILDS = [
      ['cu', 'ЦУ', 2], ['barracks', 'Казармы', 2], ['factory', 'Завод', 2],
      ['airbase', 'Авиабаза', 3], ['miner', 'Добытчик', 1], ['plant', 'Энергостанция', 1]
    ];

    root.innerHTML =
      '<div class="row">' +
      '<div class="col" style="flex:0 0 320px"><div id="hexart"></div>' +
      '<div class="legend">' +
      '<span><i style="background:var(--p2)"></i>здание (жёстко)</span>' +
      '<span><i style="background:var(--p3)"></i>войско (двигается)</span>' +
      '<span><i style="background:var(--bg-3);border:1px solid var(--line)"></i>свободно</span>' +
      '</div></div>' +
      '<div class="col">' +
      '<div class="field"><span class="field__label">Поставить здание</span>' +
      '<div class="filters">' + BUILDS.map(function (b) {
        return '<button class="btn" data-build="' + b[0] + '">' + b[1] +
          ' <span style="color:var(--ink-faint)">' + b[2] + '</span></button>';
      }).join('') + '</div></div>' +
      '<div class="field"><span class="field__label">Войска в гексе</span>' +
      '<div class="filters">' +
      '<button class="btn" data-unit="vehicle">+ Техника</button>' +
      '<button class="btn" data-unit="single">+ Пехота / вышка</button>' +
      '<button class="btn" data-unit="aircraft">+ Авиация</button>' +
      '</div></div>' +
      '<div class="field"><div class="filters">' +
      '<button class="btn" id="hx-spawn">Положить тайл зарождения</button>' +
      '<button class="btn" id="hx-clear">Очистить</button>' +
      '</div></div>' +
      '<div id="hx-state"></div>' +
      '<div class="field" style="margin-top:14px"><span class="field__label">Проверка входа</span>' +
      '<div class="filters">' +
      '<button class="btn on" data-try="vehicle">Войдёт ли техника?</button>' +
      '<button class="btn" data-try="infantry">Пехота</button>' +
      '<button class="btn" data-try="factory">Завод</button>' +
      '<button class="btn" data-try="airbase">Авиабаза</button>' +
      '</div></div>' +
      '<div id="hx-verdict"></div>' +
      '</div></div>';

    var art = root.querySelector('#hexart');
    var verdictBox = root.querySelector('#hx-verdict');
    var stateBox = root.querySelector('#hx-state');
    var probe = 'vehicle';

    function freeMask() {
      var free = [true, true, true, true, true, true];
      if (st.spawn) return [false, false, false, false, false, false];
      st.buildings.forEach(function (b) { b.sides.forEach(function (i) { free[i] = false; }); });
      return free;
    }

    function draw() {
      var free = freeMask();
      var s = '<svg class="diagram" viewBox="0 0 300 300" width="300" height="300">';
      s += '<path d="' + KR.hexPath(CX, CY, R) + '" fill="var(--bg-3)" stroke="var(--line)" stroke-width="2"/>';

      /* шесть наземных ячеек */
      for (var i = 0; i < 6; i++) {
        var c = KR.sideCenter(CX, CY, R, i, 0.66);
        var owner = null;
        st.buildings.forEach(function (b) { if (b.sides.indexOf(i) >= 0) owner = b; });
        var fill = owner ? 'var(--p2)' : (st.spawn ? 'var(--neutral)' : 'var(--bg-2)');
        s += '<circle cx="' + c[0].toFixed(1) + '" cy="' + c[1].toFixed(1) + '" r="21" fill="' + fill +
          '" stroke="' + (free[i] ? 'var(--line)' : 'none') + '" stroke-width="1.5"/>';
        s += '<text x="' + c[0].toFixed(1) + '" y="' + (c[1] + 4).toFixed(1) +
          '" text-anchor="middle" font-size="12" fill="' +
          (owner ? '#fff' : 'var(--ink-faint)') + '">' + i + '</text>';
      }

      /* воздушная ячейка — в центре */
      s += '<circle cx="' + CX + '" cy="' + CY + '" r="24" fill="none" stroke="var(--line)" ' +
        'stroke-dasharray="4 3" stroke-width="1.5"/>';
      s += '<text x="' + CX + '" y="' + (CY + 4) + '" text-anchor="middle" font-size="10.5" ' +
        'fill="var(--ink-faint)">' + (st.aircraft ? '✈ ×' + st.aircraft : 'воздух') + '</text>';

      /* войска — они не приколочены, поэтому показаны кучкой под гексом */
      var ux = 24;
      if (st.vehicles || st.singles) {
        s += '<text x="10" y="272" font-size="10.5" fill="var(--ink-faint)">войска (двигаются внутри гекса):</text>';
        for (var v = 0; v < st.vehicles; v++) {
          s += '<rect x="' + ux + '" y="278" width="26" height="15" rx="3" fill="var(--p3)"/>';
          ux += 32;
        }
        for (var u = 0; u < st.singles; u++) {
          s += '<rect x="' + ux + '" y="278" width="14" height="15" rx="3" fill="var(--p3)" opacity=".7"/>';
          ux += 20;
        }
      }
      s += '</svg>';
      art.innerHTML = s;

      var used = 6 - free.filter(Boolean).length;
      stateBox.innerHTML = '<div class="note"><div class="note__title">Что в гексе</div>' +
        'Занято зданиями: <b>' + used + '</b> из 6 · техники <b>' + st.vehicles +
        '</b> · одиночных жетонов <b>' + st.singles + '</b> · авиации <b>' + st.aircraft + '</b>' +
        (st.spawn ? '<br><b>Лежит тайл зарождения</b> — он занимает все наземные ячейки и воздушную.' : '') +
        '</div>';
      verdict();
    }

    function verdict() {
      var free = freeMask();
      var fp = KR.FOOTPRINT[probe];
      var names = {
        vehicle: 'Техника', infantry: 'Пехота', factory: 'Завод', airbase: 'Авиабаза'
      };
      var name = names[probe];
      var ok, why;

      if (st.spawn) {
        ok = false;
        why = 'На гексе лежит тайл зарождения — пока он там, гекс занят целиком. ' +
          'Авиация может пролететь сквозь, но остановиться не может.';
      } else if (probe === 'infantry') {
        ok = KR.chooseFootprint(free, 1, st.vehicles, st.singles) !== null;
        why = ok
          ? 'Одиночному жетону нужна одна свободная ячейка — и она находится после перестановки войск.'
          : 'Свободной ячейки не остаётся даже после того, как войска подвинутся.';
      } else {
        var pick = KR.chooseFootprint(free, fp, st.vehicles, st.singles);
        ok = pick !== null;
        if (ok) {
          why = (probe === 'vehicle'
            ? 'Технике всегда нужны <b>две смежные</b> ячейки. '
            : 'След этого здания — <b>' + fp + '</b> смежных ' +
              KR.plural(fp, 'ячейка', 'ячейки', 'ячеек') + '. ') +
            'Место находится: ячейки <b>' + pick.join(', ') + '</b>. ' +
            'Стоящие войска при этом переставляются внутри гекса — они не приколочены.';
        } else {
          var freeCount = free.filter(Boolean).length;
          why = 'Нужно <b>' + fp + '</b> смежных свободных ' +
            KR.plural(fp, 'ячейка', 'ячейки', 'ячеек') + ', а свободных всего <b>' +
            freeCount + '</b>' +
            (freeCount >= fp ? ', и подряд они не идут либо после посадки уже не умещаются стоящие войска.' : '.');
        }
      }

      verdictBox.innerHTML = '<div class="verdict ' + (ok ? 'yes' : 'no') + '">' +
        (ok ? '✓ ' + name + ' войдёт' : '✗ ' + name + ' не войдёт') +
        '<small>' + why + '</small></div>';
    }

    on(root, '[data-build]', 'click', function () {
      if (st.spawn) return;
      var kind = this.getAttribute('data-build');
      var fp = KR.FOOTPRINT[kind];
      var pick = KR.chooseFootprint(freeMask(), fp, st.vehicles, st.singles);
      if (!pick) {
        verdictBox.innerHTML = '<div class="verdict no">✗ Это здание уже не помещается' +
          '<small>Нет ' + fp + ' смежных свободных ячеек с учётом стоящих войск.</small></div>';
        return;
      }
      st.buildings.push({ kind: kind, sides: pick });
      draw();
    });

    on(root, '[data-unit]', 'click', function () {
      var kind = this.getAttribute('data-unit');
      if (st.spawn && kind !== 'aircraft') return;
      if (kind === 'aircraft') { st.aircraft++; draw(); return; }
      var v = st.vehicles + (kind === 'vehicle' ? 1 : 0);
      var s = st.singles + (kind === 'single' ? 1 : 0);
      if (!KR.feasible(freeMask(), v, s)) {
        verdictBox.innerHTML = '<div class="verdict no">✗ Это войско уже не помещается' +
          '<small>После перестановки всех стоящих жетонов места не остаётся.</small></div>';
        return;
      }
      st.vehicles = v; st.singles = s;
      draw();
    });

    on(root, '[data-try]', 'click', function () {
      probe = this.getAttribute('data-try');
      Array.prototype.forEach.call(root.querySelectorAll('[data-try]'), function (b) {
        b.classList.toggle('on', b === this);
      }, this);
      var self = this;
      Array.prototype.forEach.call(root.querySelectorAll('[data-try]'), function (b) {
        b.classList.toggle('on', b === self);
      });
      verdict();
    });

    root.querySelector('#hx-spawn').addEventListener('click', function () {
      st.spawn = !st.spawn;
      this.classList.toggle('on', st.spawn);
      if (st.spawn) { st.buildings = []; st.vehicles = 0; st.singles = 0; }
      draw();
    });

    root.querySelector('#hx-clear').addEventListener('click', function () {
      st.buildings = []; st.vehicles = 0; st.singles = 0; st.aircraft = 0; st.spawn = false;
      root.querySelector('#hx-spawn').classList.remove('on');
      draw();
    });

    draw();
  };

  /* ============================================================== §8 БОЙ */

  W['combat-steps'] = function (root) {
    var STEPS = [
      {
        t: 'Выбор гекса',
        d: 'Объявляешь <b>один</b> гекс, в котором идёт бой. Все атаки этого боя ' +
          'наносят только жетоны, стоящие в этом гексе или граничащие с ним по правилам ' +
          'своего рода. Бить из двух гексов одним действием нельзя — это и есть причина, ' +
          'по которой армия, размазанная по полю, стреляет хуже собранной.'
      },
      {
        t: 'Выбор целей',
        d: 'Смотришь на планшет войск: у каждого твоего рода нарисованы <b>две цели</b> — ' +
          'основная (верхний ряд) и второстепенная (нижний). По основной цели атака стоит ' +
          '<b>1 боеприпас</b>, по второстепенной — <b>2</b>. Целей, которых нет на планшете, ' +
          'этот род не бьёт вообще, сколько боеприпасов ни плати.'
      },
      {
        t: 'Назначение атак',
        d: 'Каждый твой жетон в бою может совершить <b>одну</b> атаку. Платишь боеприпасы ' +
          'за каждую атаку отдельно, до броска — бросков в этой игре нет, атака всегда ' +
          'попадает. Назначить атаку на цель, которой нет на планшете, нельзя.'
      },
      {
        t: 'Урон',
        d: 'Любая атака наносит <b>1 урон</b>. Урон кладётся кубиком на жетон и остаётся ' +
          'на нём до этапа Обновления. Когда урона накопилось столько же, сколько у жетона ' +
          'прочности, жетон уничтожен и уходит с поля.'
      },
      {
        t: 'Трофеи и компенсации',
        d: 'Каждый уничтоженный чужой жетон ложится <b>трофеем</b> на твоё место для трофеев ' +
          'с напечатанной ценностью в трофеях. Владельцу уничтоженного <b>здания</b> идёт компенсация ' +
          'контейнерами. Уничтожение <b>ЦУ</b> считается отдельно — см. раздел о конце партии.'
      },
      {
        t: 'Ответный бой',
        d: 'Обороняющийся немедленно проводит <b>ответный бой</b> в том же гексе — ' +
          '<b>бесплатно</b>, не тратя ни действия, ни боеприпасов. Отвечают только те его ' +
          'жетоны, которые уцелели. <b>Ответ на ответ не проводится</b> — иначе бой не ' +
          'кончался бы никогда.'
      },
      {
        t: 'Второй бой',
        d: 'В одном действии Бой можно открыть <b>второй</b> бой — в другом гексе. ' +
          'За право открыть его платится надбавка <b>+1 боеприпас</b>. Ответный бой ' +
          'обороняющегося при этом проводится за каждый бой отдельно.'
      }
    ];

    var i = 0;
    root.innerHTML = '<div class="steps" id="cb-steps"></div><div id="cb-body"></div>' +
      '<div class="filters" style="margin-top:12px">' +
      '<button class="btn" id="cb-prev">← Назад</button>' +
      '<button class="btn on" id="cb-next">Дальше →</button>' +
      '<button class="btn" id="cb-reset">Сначала</button></div>';

    function render() {
      root.querySelector('#cb-steps').innerHTML = STEPS.map(function (s, k) {
        return '<span class="step ' + (k === i ? 'on' : (k < i ? 'done' : '')) + '">' +
          (k + 1) + '. ' + s.t + '</span>';
      }).join('');
      root.querySelector('#cb-body').innerHTML =
        '<h3 style="margin-top:6px">Шаг ' + (i + 1) + '. ' + STEPS[i].t + '</h3>' +
        '<p>' + STEPS[i].d + '</p>';
      root.querySelector('#cb-prev').disabled = i === 0;
      root.querySelector('#cb-next').disabled = i === STEPS.length - 1;
    }

    root.querySelector('#cb-next').addEventListener('click', function () {
      if (i < STEPS.length - 1) { i++; render(); }
    });
    root.querySelector('#cb-prev').addEventListener('click', function () {
      if (i > 0) { i--; render(); }
    });
    root.querySelector('#cb-reset').addEventListener('click', function () { i = 0; render(); });
    render();
  };

  /* ==================================================== §8 ТАБЛИЦА АТАК */

  W['attack-table'] = function (root) {
    var RU = {
      infantry: 'Пехота', vehicle: 'Техника', aircraft: 'Авиация', tower: 'Вышка',
      buildings_towers: 'Здания и вышки'
    };
    var sides = Object.keys(D.troopSides);
    root.innerHTML = '<div class="filters">' + sides.map(function (s, i) {
      var b = D.troopSides[s];
      return '<button class="btn' + (i === 0 ? ' on' : '') + '" data-side="' + s + '">' +
        (s === 'A' ? 'Сторона А' : s.replace('B', 'Б') + (b.name ? ' · ' + b.name : '')) +
        '</button>';
    }).join('') + '</div><div id="at-body"></div>';

    function render(side) {
      var b = D.troopSides[side];
      var rows = ['infantry', 'vehicle', 'aircraft', 'tower'].map(function (u) {
        var a = b.attacks[u];
        return [
          KR.token(u, 26, 'var(--ink-dim)') + ' <b>' + RU[u] + '</b>',
          RU[a[0]] + ' <span style="color:var(--ink-faint)">1 БПР</span>',
          RU[a[1]] + ' <span style="color:var(--ink-faint)">2 БПР</span>',
          b.speeds ? b.speeds[u] : '—',
          D.tokens.units[u].hp
        ];
      });
      var extra = '';
      if (b.name) {
        extra += '<p><b>' + b.name + '.</b> Опора стороны — ' +
          (RU[b.pillar] || b.pillar).toLowerCase() +
          (b.counters ? '; отвечает на сторону ' + b.counters.replace('B', 'Б') : '') + '.';
        if (b.mirror_side) extra += ' Зеркальная сторона: каждый род бьёт свой же род основной целью.';
        if (b.towers_move) extra += ' <b>Вышки здесь двигаются</b> — скорость 1.';
        if (b.tower_hits_buildings) extra += ' Вышка бьёт здания.';
        extra += '</p>';
      } else {
        extra = '<p>Сторона А одинакова у всех игроков — это симметричная, обучающая ' +
          'раскладка. Вышка на ней <b>по зданиям не бьёт</b>.</p>';
      }
      var prices = b.building_prices;
      extra += KR.table(['Военное здание', 'Цена, МОН'], [
        ['Казармы', prices.barracks], ['Завод', prices.factory], ['Авиабаза', prices.airbase]
      ], { num: [1] });

      root.querySelector('#at-body').innerHTML = extra + KR.table(
        ['Кто бьёт', 'Основная цель', 'Второстепенная', 'Скорость', 'Прочность'],
        rows, { num: [3, 4] }
      );
    }

    on(root, '[data-side]', 'click', function () {
      var self = this;
      Array.prototype.forEach.call(root.querySelectorAll('[data-side]'), function (b) {
        b.classList.toggle('on', b === self);
      });
      render(this.getAttribute('data-side'));
    });
    render(sides[0]);
  };

  /* ================================================== §12 КАЛЬКУЛЯТОР ОЧКОВ */

  W['vp-calc'] = function (root) {
    var e = D.ruleset.economy;
    var tech = D.ruleset.tech;

    var LINES = [
      { id: 'kelium', label: 'Келемий в хранилище', rate: 'за каждые ' + e.kelium_per_vp,
        vp: function (n) { return Math.floor(n / e.kelium_per_vp); } },
      { id: 'coins', label: 'Монеты', rate: 'за каждые ' + e.coins_per_vp,
        vp: function (n) { return Math.floor(n / e.coins_per_vp); } },
      { id: 'trophy', label: 'Трофеи в хранилище', rate: 'за каждые ' + e.trophy_per_vp,
        vp: function (n) { return Math.floor(n / e.trophy_per_vp); } },
      { id: 'buildings', label: 'Здания на поле', rate: 'за каждые ' + e.buildings_per_vp,
        vp: function (n) { return Math.floor(n / e.buildings_per_vp); } },
      { id: 'units', label: 'Войска на поле', rate: 'за каждые ' + e.units_per_vp,
        vp: function (n) { return Math.floor(n / e.units_per_vp); } },
      { id: 'steps1', label: 'Шаги 1 треков (пройдено)', rate: '+' + tech.step_vp_cumulative[0] + ' за шаг',
        vp: function (n) { return n * tech.step_vp_cumulative[0]; }, max: tech.tracks },
      { id: 'steps2', label: 'Шаги 2 треков', rate: '+' + tech.step_vp_cumulative[1] + ' за шаг',
        vp: function (n) { return n * tech.step_vp_cumulative[1]; }, max: tech.tracks },
      { id: 'steps3', label: 'Шаги 3 треков', rate: '+' + tech.step_vp_cumulative[2] + ' за шаг',
        vp: function (n) { return n * tech.step_vp_cumulative[2]; }, max: tech.tracks },
      { id: 'steps4', label: 'Шаги 4 треков (вершины)', rate: '+' + tech.step_vp_cumulative[3] + ' за шаг',
        vp: function (n) { return n * tech.step_vp_cumulative[3]; }, max: tech.tracks },
      { id: 'flipStart', label: 'Обороты стартовых тайлов зарождения',
        rate: '+' + e.spawn_flip_start_vp + ' за оборот',
        vp: function (n) { return n * e.spawn_flip_start_vp; } },
      { id: 'flipNormal', label: 'Обороты обычных тайлов зарождения',
        rate: '+' + e.spawn_flip_normal_vp + ' за оборот',
        vp: function (n) { return n * e.spawn_flip_normal_vp; } },
      { id: 'cuTokens', label: 'Чужие жетоны уничтожения ЦУ',
        rate: '+' + D.ruleset.command_center.destruction_token_vp + ' за жетон',
        vp: function (n) { return n * D.ruleset.command_center.destruction_token_vp; } },
      { id: 'ownCu', label: 'Своё ЦУ ни разу не уничтожено (0 или 1)',
        rate: '+' + D.ruleset.command_center.own_token_vp_if_cu_never_destroyed,
        vp: function (n) { return Math.min(1, n) * D.ruleset.command_center.own_token_vp_if_cu_never_destroyed; },
        max: 1 },
      { id: 'star', label: 'Звёздные жетоны на поле (добытчик/станция №4)', rate: '+1 за жетон',
        vp: function (n) { return n; } }
    ];

    root.innerHTML =
      '<div class="calcgrid">' +
      '<div class="lbl"><b>Источник</b></div><div class="val"><b>Сколько</b></div><div class="vp"><b>ПО</b></div>' +
      LINES.map(function (l) {
        return '<div class="lbl">' + l.label + '<br><small style="color:var(--ink-faint)">' +
          l.rate + '</small></div>' +
          '<div class="val"><input type="number" min="0" ' +
          (l.max ? 'max="' + l.max + '"' : '') + ' value="0" data-vp="' + l.id + '"></div>' +
          '<div class="vp" data-out="' + l.id + '">0</div>';
      }).join('') +
      '</div>' +
      '<div class="total"><span>Победные очки всего</span><b id="vp-total">0</b></div>' +
      '<div class="note" style="margin-top:14px"><div class="note__title">Как читать</div>' +
      'Дробное не округляется вверх: остаток от деления просто пропадает. ' +
      'Три монеты при курсе ' + e.coins_per_vp + ' за очко — это ноль очков, а не «почти очко». ' +
      'Это и есть причина, по которой в конце партии деньги надо во что-то превращать.</div>';

    function recalc() {
      var total = 0;
      LINES.forEach(function (l) {
        var input = root.querySelector('[data-vp="' + l.id + '"]');
        var n = Math.max(0, Number(input.value) || 0);
        var v = l.vp(n);
        total += v;
        root.querySelector('[data-out="' + l.id + '"]').textContent = v;
      });
      root.querySelector('#vp-total').textContent = total;
    }

    on(root, 'input[type=number]', 'input', recalc);
    recalc();
  };

  /* ========================================== §4 СКОЛЬКО У МЕНЯ ДЕЙСТВИЙ */

  W['how-many-actions'] = function (root) {
    var ORDERS = {
      development: 'Разработка', infrastructure: 'Инфраструктура',
      operation: 'Операция', acquisitions: 'Приобретения'
    };
    var ACTIONS = {
      development: ['Сборка', 'Добыча'],
      infrastructure: ['Стройка', 'Смена энергии'],
      operation: ['Движение', 'Бой'],
      acquisitions: ['Рынок', 'Наука']
    };
    var cards = D.cards.orders.orders.filter(function (o) { return !o.joker; });
    var DECKS = { blue: 'Голубая', scarlet: 'Алая', green: 'Зелёная', yellow: 'Жёлтая' };

    root.innerHTML =
      '<div class="row"><div class="col">' +
      '<div class="field"><span class="field__label">Твоя карта</span>' +
      '<select id="hm-card"><option value="security">БЕЗОПАСНОСТЬ (джокер)</option>' +
      cards.map(function (c) {
        return '<option value="' + c.id + '">' + DECKS[c.deck] + ' · ' + ORDERS[c.top] +
          ' (низ: ' + ORDERS[c.bottom] + ')' + (c.maneuver ? ' · манёвр' : '') + '</option>';
      }).join('') + '</select></div></div>' +
      '<div class="col"><div class="field">' +
      '<span class="field__label">Какие верхние приказы вскрыли соседи</span>' +
      '<div class="filters" id="hm-others">' +
      Object.keys(ORDERS).map(function (k) {
        return '<button class="btn" data-o="' + k + '">' + ORDERS[k] + '</button>';
      }).join('') + '</div></div></div></div>' +
      '<div id="hm-out"></div>';

    var others = {};

    function render() {
      var id = root.querySelector('#hm-card').value;
      var out = '';
      if (id === 'security') {
        out = '<div class="verdict yes">Два разных действия из восьми — и никакого правила совпадения' +
          '<small>БЕЗОПАСНОСТЬ не подавляется совпадением и у соперников не считается вскрытым ' +
          'приказом. Плюс одно СПЕЦ. действие, как всегда.</small></div>' +
          KR.table(['Что доступно', 'Сколько'], [
            ['Любые два <b>разных</b> действия из всех восьми', '2'],
            ['СПЕЦ. действие', '1'],
            ['Действие с нижнего приказа', 'нижнего приказа у неё нет']
          ]);
      } else {
        var c = cards.filter(function (x) { return x.id === id; })[0];
        var clash = !!others[c.top];
        var topN = clash ? 1 : 2;
        var bottomOpen = !!others[c.bottom];

        var rows = [
          ['Верхний приказ — <b>' + ORDERS[c.top] + '</b><br><small style="color:var(--ink-faint)">' +
            ACTIONS[c.top].join(' · ') + '</small>',
            clash
              ? '<b>1</b> действие <span style="color:var(--danger)">(этот приказ вскрыл кто-то ещё)</span>'
              : '<b>2 разных</b> действия <span style="color:var(--ok)">(ты единственный)</span>'],
          ['СПЕЦ. действие', '<b>1</b> — задание, карта арсенала, вскрытие контейнера в свой ход и т. п.'],
          ['Нижний приказ — <b>' + ORDERS[c.bottom] + '</b><br><small style="color:var(--ink-faint)">' +
            ACTIONS[c.bottom].join(' · ') + '</small>',
            bottomOpen
              ? '<b>1</b> действие <span style="color:var(--ok)">(этот приказ вскрыт сверху кем-то ещё)</span>'
              : '— <span style="color:var(--ink-faint)">никто не вскрыл этот приказ сверху</span>']
        ];
        if (c.maneuver) {
          rows.push(['Плашка манёвра',
            '<b>СПЕЦ.:</b> перемести один свой жетон войска на его скорость. Бесплатно, одно перемещение.']);
        }

        var total = topN + (bottomOpen ? 1 : 0);
        out = '<div class="verdict ' + (total >= 2 ? 'yes' : 'no') + '">' +
          'Основных действий в этот ход: <b>' + total + '</b>' +
          '<small>' + (clash
            ? 'Совпадение сработало: приказ вскрыл кто-то ещё, и верх даёт одно действие вместо двух.'
            : 'Совпадения нет — верх отдаёт оба своих действия.') +
          (bottomOpen ? ' Плюс одно с нижней половины.' : '') +
          ' Сверх этого — одно СПЕЦ. действие' + (c.maneuver ? ' и плашка манёвра' : '') + '.</small></div>' +
          KR.table(['Откуда', 'Что получаешь'], rows);
      }
      root.querySelector('#hm-out').innerHTML = out;
    }

    on(root, '#hm-others [data-o]', 'click', function () {
      var k = this.getAttribute('data-o');
      others[k] = !others[k];
      this.classList.toggle('on', others[k]);
      render();
    });
    root.querySelector('#hm-card').addEventListener('change', render);
    render();
  };

  /* ================================================ §9 ТРЕК ТЕХНОЛОГИЙ */

  W['tech-track'] = function (root) {
    var tech = D.ruleset.tech;
    var board = D.techBoard;
    var TRACKS = {
      left: { name: 'Левый трек', mod: 'красные модули (атака)', ability: '+1 боеприпас в бою' },
      middle: { name: 'Средний трек', mod: 'жетоны хранилища', ability: '+1 келемий за Добычу' },
      right: { name: 'Правый трек', mod: 'синие модули (сборка)', ability: '+1 лишнее перемещение' }
    };
    var REWARD = ['приз-кубик у трека', 'модуль', 'модуль', 'карта супер арсенала'];

    root.innerHTML = board.tracks.map(function (tr) {
      var meta = TRACKS[tr.id];
      return '<div class="trackrow"><span class="tracklabel">' + meta.name + '</span>' +
        tech.step_cost_trophy.map(function (cost, i) {
          return '<button class="tstep" data-track="' + tr.id + '" data-step="' + i + '">' +
            '<b>' + (i + 1) + '</b><span>' + cost + ' ТО</span></button>';
        }).join('') + '</div>';
    }).join('') + '<div id="tt-out"></div>';

    function show(trackId, i) {
      var meta = TRACKS[trackId];
      var cost = tech.step_cost_trophy[i];
      var spent = tech.step_cost_trophy.slice(0, i + 1).reduce(function (a, b) { return a + b; }, 0);
      var vp = tech.step_vp_cumulative[i];
      var vpSum = tech.step_vp_cumulative.slice(0, i + 1).reduce(function (a, b) { return a + b; }, 0);
      var prize = tech.step1_prize[trackId];

      var rows = [
        ['Цена этого шага', '<b>' + cost + '</b> ' + KR.plural(cost, 'трофей', 'трофея', 'трофеев')],
        ['Потрачено с начала трека', '<b>' + spent + '</b> ОБЛ'],
        ['Победные очки за шаг', '<b>+' + vp + '</b>'],
        ['Победные очки накопленным итогом', '<b>' + vpSum + '</b>'],
        ['Награда шага', '<b>' + REWARD[i] + '</b>'],
        ['Мест на шаге', '<b>' + tech.step_capacity[i] + '</b>' +
          (tech.step_capacity[i] === 1 ? ' — вершина, занимает один игрок' : '')]
      ];
      if (i === 0 && prize) {
        var k1 = Object.keys(prize.first)[0];
        var RU = { ammo: 'боеприпаса', kelium: 'келемия', coin: 'монеты' };
        rows.push(['Лежащий приз шага 1',
          'первому: <b>' + prize.first[k1] + '</b> ' + RU[k1] +
          ' · второму: <b>' + prize.second[Object.keys(prize.second)[0]] + '</b> · третьему ничего']);
      }
      if (i === tech.steps_per_track - 1) {
        rows.push(['Вершина', 'Занятие <b>всех трёх</b> вершин — одно из условий конца партии']);
      }
      root.querySelector('#tt-out').innerHTML =
        '<h3>' + meta.name + ', шаг ' + (i + 1) + '</h3>' +
        '<p>Трек выдаёт <b>' + meta.mod + '</b>. Способность трека: ' + meta.ability + '.</p>' +
        KR.table(['Что', 'Сколько'], rows);
    }

    on(root, '.tstep', 'click', function () {
      var self = this;
      Array.prototype.forEach.call(root.querySelectorAll('.tstep'), function (b) {
        b.classList.toggle('on', b === self);
      });
      show(this.getAttribute('data-track'), Number(this.getAttribute('data-step')));
    });
    show('left', 0);
  };

  /* ============================================== §3 ШКАЛА РАУНДА */

  W['round-timeline'] = function (root) {
    var r = D.ruleset.rounds;
    var PHASES = [
      { t: 'Обновление', s: 'начало раунда', d:
        '<p>Раунд открывается служебным этапом. В нём снимается урон, на каждый ' +
        '<b>пустой гекс</b> кладётся контейнер рубашкой вверх и выкладывается новая карта ' +
        'рынка взамен прошлой.</p>' +
        '<p><b>В первом раунде Обновления нет</b> — всё это уже сделано при подготовке.</p>' },
      { t: 'Слепой сброс', s: 'одна карта из пяти', d:
        '<p>У каждого игрока на руках <b>' + r.order_hand_size + '</b> карт приказов. ' +
        'Одна из них откладывается рубашкой вверх горизонтально — на неё в этот раунд ' +
        'кладутся трофеи. Оставшиеся четыре игрок берёт в руку и только тогда видит, чего лишился.</p>' +
        (D.ruleset.rounds.blind_discard_choice
          ? '<p>По действующему правилу игрок <b>сам выбирает</b>, какой приказ отложить.</p>'
          : '<p>Откладывается случайная карта.</p>') +
        '<p>Следствие: каждый раунд один приказ выпадает из игры. Выпала Операция — раунд без войны, ' +
        'кроме как через нижние приказы и карты.</p>' },
      { t: 'Круг 1', s: 'одновременный выбор', d: circleText(1) },
      { t: 'Круг 2', s: '', d: circleText(2) },
      { t: 'Круг 3', s: '', d: circleText(3) },
      { t: 'Круг 4', s: '', d: circleText(4) },
      { t: 'Возврат', s: 'конец раунда', d:
        '<p>Все <b>' + r.order_hand_size + '</b> карт приказов возвращаются в руку, включая отложенную. ' +
        (D.ruleset.return_step.return_destroyed_tokens
          ? 'Уничтоженные жетоны возвращаются владельцам в запас. ' : '') +
        (D.ruleset.return_step.refill_objectives_to_limit
          ? 'Рука заданий добирается до лимита (<b>' + r.objective_hand_limit + '</b>). ' : '') +
        'Раунд закончен.</p>' +
        (D.ruleset.return_step.trophy_to_upgrade_exchange_enabled
          ? ''
          : '<p>Обмена трофеев на улучшения в конце раунда <b>нет</b> — он убран решением дизайнера.</p>') }
    ];

    function circleText(n) {
      return '<p>Круг устроен одинаково все четыре раза:</p><ol>' +
        '<li><b>Одновременный слепой выбор.</b> Все кладут по одной карте приказа рубашкой вверх. ' +
        'Никто не видит чужого выбора.</li>' +
        '<li><b>Вскрытие.</b> Карты переворачиваются разом. Здесь и решается <b>правило совпадения</b>: ' +
        'вскрыл приказ один — два разных действия, вскрыли несколько — по одному.</li>' +
        '<li><b>Розыгрыш по часовой</b> от первого игрока. Каждый по очереди отыгрывает свой ход целиком.</li>' +
        '</ol><p>Всего таких кругов в раунде — <b>' + D.ruleset.rounds.circles_per_round + '</b>' +
        (n === 4 ? ', этот последний' : '') + '.</p>';
    }

    root.innerHTML = '<div class="timeline">' + PHASES.map(function (p, i) {
      return '<button class="tl' + (i === 0 ? ' on' : '') + '" data-i="' + i + '">' + p.t +
        (p.s ? '<small>' + p.s + '</small>' : '') + '</button>';
    }).join('') + '</div><div id="rt-out" style="margin-top:14px"></div>';

    function show(i) {
      root.querySelector('#rt-out').innerHTML = '<h3>' + PHASES[i].t + '</h3>' + PHASES[i].d;
    }
    on(root, '.tl', 'click', function () {
      var self = this;
      Array.prototype.forEach.call(root.querySelectorAll('.tl'), function (b) {
        b.classList.toggle('on', b === self);
      });
      show(Number(this.getAttribute('data-i')));
    });
    show(0);
  };

  /* ============================================== §6 ЭНЕРГИЯ И ЗАПИТАННОСТЬ */

  W['energy-toggle'] = function (root) {
    var B = D.tokens.buildings;
    var LIST = [
      { id: 'barracks', name: 'Казармы', slots: B.barracks.energy_slots,
        can: 'Сборка: пехота либо боеприпасы', shape: 'barracks' },
      { id: 'factory', name: 'Завод', slots: B.factory.energy_slots,
        can: 'Сборка: техника либо боеприпасы', shape: 'factory' },
      { id: 'airbase', name: 'Авиабаза', slots: B.airbase.energy_slots,
        can: 'Сборка: авиация либо боеприпасы', shape: 'airbase' },
      { id: 'cu', name: 'ЦУ', slots: B.command_center.energy_slots,
        can: 'Сборка: вышка либо боеприпасы; само даёт ' + B.command_center.energy_gives + ' энергии',
        shape: 'cu' },
      { id: 'miner', name: 'Добытчик №1', slots: D.tokens.miners[0].energy_slots,
        can: 'Добыча: ' + D.tokens.miners[0].yield_kelium + ' келемий либо контейнер', shape: 'miner' }
    ];

    var powered = {};
    LIST.forEach(function (b) { powered[b.id] = true; });

    root.innerHTML = '<div id="en-list"></div>' +
      '<div class="note" style="margin-top:12px"><div class="note__title">Правило одной строкой</div>' +
      'Энергия — не ресурс, а <b>разрешение</b>. Запитанное здание работает, незапитанное ' +
      'стоит мёртвым картоном. В Разработке пустую ячейку можно заткнуть монетой: <b>' +
      D.ruleset.actions.empty_energy_slot_coin_cost +
      ' МОН за ячейку</b>, но только на это одно действие.</div>';

    function render() {
      root.querySelector('#en-list').innerHTML = LIST.map(function (b) {
        var on = powered[b.id];
        var cubes = '';
        for (var i = 0; i < b.slots; i++) {
          cubes += '<span style="display:inline-block;width:15px;height:15px;border-radius:3px;' +
            'margin-right:4px;border:1px solid var(--line);background:' +
            (on ? 'var(--accent)' : 'transparent') + '"></span>';
        }
        return '<div class="row" style="align-items:center;padding:8px 0;border-bottom:1px solid var(--line-soft)">' +
          '<div style="flex:0 0 46px">' + KR.token(b.shape, 34, on ? 'var(--p2)' : 'var(--ink-faint)') + '</div>' +
          '<div style="flex:1 1 160px"><b>' + b.name + '</b><br>' +
          '<small style="color:var(--ink-faint)">' + b.slots + ' ' +
          KR.plural(b.slots, 'ячейка', 'ячейки', 'ячеек') + ' энергии</small></div>' +
          '<div style="flex:0 0 110px">' + cubes + '</div>' +
          '<div style="flex:1 1 200px;color:' + (on ? 'var(--ok)' : 'var(--danger)') + '">' +
          (on ? b.can : 'Не работает. Ничего не производит и не добывает') + '</div>' +
          '<div><button class="btn' + (on ? ' on' : '') + '" data-en="' + b.id + '">' +
          (on ? 'Запитано' : 'Обесточено') + '</button></div></div>';
      }).join('');
      on(root, '[data-en]', 'click', function () {
        var k = this.getAttribute('data-en');
        powered[k] = !powered[k];
        render();
      });
    }
    render();
  };

  /* ============================================ §10 МОДУЛЬ ПОВЕРХ ЧИСЛА */

  W['module-overlay'] = function (root) {
    var st = { blue: false, gold: false };
    root.innerHTML = '<div class="row"><div class="col" style="flex:0 0 250px" id="mo-art"></div>' +
      '<div class="col"><div class="filters">' +
      '<button class="btn" id="mo-blue">Поставить синий модуль</button>' +
      '<button class="btn" id="mo-gold">Перевернуть на золото</button>' +
      '</div><div id="mo-out"></div></div></div>';

    function render() {
      var val = st.blue ? (st.gold ? 3 : 2) : 1;
      root.querySelector('#mo-art').innerHTML =
        '<svg class="diagram" viewBox="0 0 220 150" width="220" height="150">' +
        '<rect x="10" y="10" width="200" height="130" rx="10" fill="var(--bg-3)" stroke="var(--line)"/>' +
        '<text x="24" y="36" font-size="11" fill="var(--ink-faint)">ячейка сборки на планшете</text>' +
        '<rect x="70" y="52" width="80" height="66" rx="8" fill="var(--bg-2)" stroke="var(--line)"/>' +
        '<text x="110" y="98" text-anchor="middle" font-size="40" ' +
        'fill="' + (st.blue ? 'var(--ink-faint)' : 'var(--ink)') + '"' +
        (st.blue ? ' opacity=".25"' : '') + '>1</text>' +
        (st.blue
          ? '<rect x="78" y="58" width="64" height="54" rx="7" fill="' +
            (st.gold ? 'var(--accent)' : 'var(--p2)') + '"/>' +
            '<text x="110" y="98" text-anchor="middle" font-size="34" fill="#fff">' + val + '</text>'
          : '') +
        '</svg>';
      root.querySelector('#mo-out').innerHTML =
        '<div class="verdict ' + (st.blue ? 'yes' : '') + '">Здание производит <b>' + val + '</b> ' +
        KR.plural(val, 'жетон', 'жетона', 'жетонов') + ' за Сборку' +
        '<small>' + (st.blue
          ? 'Модуль <b>физически перекрывает</b> напечатанное число. Никакой арифметики: ' +
            'игрок читает то, что видит.' + (st.gold ? ' Золотая сторона — предел, дальше улучшать нечего.' : '')
          : 'Напечатанное на планшете значение — единица.') + '</small></div>';
    }

    root.querySelector('#mo-blue').addEventListener('click', function () {
      st.blue = !st.blue;
      if (!st.blue) st.gold = false;
      this.classList.toggle('on', st.blue);
      root.querySelector('#mo-gold').classList.toggle('on', st.gold);
      render();
    });
    root.querySelector('#mo-gold').addEventListener('click', function () {
      if (!st.blue) return;
      st.gold = !st.gold;
      this.classList.toggle('on', st.gold);
      render();
    });
    render();
  };

  /* ================================================= §2 ПОЛЕ ПО СЦЕНАРИЯМ */

  W['field-preview'] = function (root, ctx) {
    var n = String(ctx.players || 4);

    root.innerHTML = '<div class="filters">' +
      [2, 3, 4].map(function (k) {
        return '<button class="btn' + (String(k) === n ? ' on' : '') + '" data-n="' + k + '">' +
          k + ' игрока</button>';
      }).join('') + '<span class="count" id="fp-name"></span></div>' +
      '<div id="fp-art"></div>';

    function draw(players) {
      var list = D.scenarios[String(players)] || [];
      var sc = list[0];
      if (!sc) { root.querySelector('#fp-art').innerHTML = '<p>Раскладка не задана.</p>'; return; }

      var SIZE = 27, cells = [];
      sc.shape.forEach(function (row, ri) {
        var off = (typeof row === 'number') ? 0 : (row.offset || 0);
        var cnt = (typeof row === 'number') ? row : row.count;
        for (var k = 0; k < cnt; k++) {
          var col = off + k + 1;
          var ax = KR.rowColToAxial(ri + 1, col);
          var px = KR.axialToPixel(ax[0], ax[1], SIZE);
          cells.push({ row: ri + 1, col: col, x: px[0], y: px[1] });
        }
      });

      var minx = Math.min.apply(null, cells.map(function (c) { return c.x; })) - SIZE * 1.1;
      var miny = Math.min.apply(null, cells.map(function (c) { return c.y; })) - SIZE * 1.2;
      var maxx = Math.max.apply(null, cells.map(function (c) { return c.x; })) + SIZE * 1.1;
      var maxy = Math.max.apply(null, cells.map(function (c) { return c.y; })) + SIZE * 1.2;

      var special = {};
      (sc.special || []).forEach(function (s) { special[s.row + ':' + s.col] = s; });
      var neutrals = {};
      (sc.neutrals || []).forEach(function (nb) {
        var k = nb.row + ':' + nb.col;
        (neutrals[k] = neutrals[k] || []).push(nb);
      });

      var PCOL = ['var(--p1)', 'var(--p2)', 'var(--p3)', 'var(--p4)'];
      var s = '<svg class="diagram" viewBox="' + minx.toFixed(0) + ' ' + miny.toFixed(0) + ' ' +
        (maxx - minx).toFixed(0) + ' ' + (maxy - miny).toFixed(0) + '" width="100%">';

      cells.forEach(function (c) {
        var sp = special[c.row + ':' + c.col];
        var fill = 'var(--bg-3)';
        if (sp && sp.content === 'player_start') fill = PCOL[sp.seat || 0];
        else if (sp && (sp.content === 'spawn_start' || sp.content === 'kelium_tile')) fill = 'var(--accent-soft)';
        s += '<path d="' + KR.hexPath(c.x, c.y, SIZE - 1.5) + '" fill="' + fill +
          '" stroke="var(--line)" stroke-width="1.2"/>';

        if (sp) {
          if (sp.content === 'player_start') {
            s += '<text x="' + c.x.toFixed(1) + '" y="' + (c.y + 5).toFixed(1) +
              '" text-anchor="middle" font-size="13" font-weight="700" fill="#fff">' +
              ((sp.seat || 0) + 1) + '</text>';
          } else if (sp.content === 'spawn_start' || sp.content === 'kelium_tile') {
            s += '<circle cx="' + c.x.toFixed(1) + '" cy="' + c.y.toFixed(1) +
              '" r="12" fill="var(--accent)" opacity=".85"/>';
            s += '<text x="' + c.x.toFixed(1) + '" y="' + (c.y + 4).toFixed(1) +
              '" text-anchor="middle" font-size="10" fill="#1a1206">' +
              (sp.content === 'spawn_start' ? 'С' : 'З') + '</text>';
          } else if (sp.content === 'container') {
            s += '<rect x="' + (c.x - 7).toFixed(1) + '" y="' + (c.y - 7).toFixed(1) +
              '" width="14" height="14" rx="3" fill="var(--ink-faint)"/>';
          }
        }
        /* нейтралы: стенки на рёбрах между указанными углами */
        (neutrals[c.row + ':' + c.col] || []).forEach(function (nb) {
          var corners = KR.hexCorners(c.x, c.y, SIZE - 1.5);
          var cs = nb.corners || [];
          for (var i = 0; i < cs.length - 1; i++) {
            var a = corners[(cs[i] - 1) % 6], b = corners[cs[i + 1] % 6 === 0 ? 5 : (cs[i + 1] - 1) % 6];
            s += '<line x1="' + a[0].toFixed(1) + '" y1="' + a[1].toFixed(1) + '" x2="' +
              b[0].toFixed(1) + '" y2="' + b[1].toFixed(1) +
              '" stroke="var(--neutral)" stroke-width="5" stroke-linecap="round"/>';
          }
        });
      });
      s += '</svg>';

      root.querySelector('#fp-art').innerHTML = s +
        '<div class="legend">' +
        '<span><i style="background:var(--p1)"></i>старт игрока</span>' +
        '<span><i style="background:var(--accent)"></i>тайл зарождения (С — стартовый)</span>' +
        '<span><i style="background:var(--ink-faint)"></i>контейнер</span>' +
        '<span><i style="background:var(--neutral)"></i>стенка нейтрального здания</span>' +
        '</div>' +
        '<p style="margin-top:10px;color:var(--ink-faint);font-size:13.5px">Гексов на поле: <b>' +
        cells.length + '</b> · тайлов зарождения: <b>' +
        (sc.special || []).filter(function (x) {
          return x.content === 'spawn_start' || x.content === 'kelium_tile';
        }).length + '</b> · нейтральных зданий: <b>' + (sc.neutrals || []).length + '</b></p>';

      root.querySelector('#fp-name').textContent = 'раскладка ' + sc.id;
    }

    on(root, '[data-n]', 'click', function () {
      var self = this;
      Array.prototype.forEach.call(root.querySelectorAll('[data-n]'), function (b) {
        b.classList.toggle('on', b === self);
      });
      draw(Number(this.getAttribute('data-n')));
    });
    draw(Number(n));
  };

  /* ==================================================== §4 АНАТОМИЯ КАРТЫ */

  W['order-anatomy'] = function (root) {
    var ZONES = {
      top: ['Шапка приказа', 'Стрелка-нашивка с названием приказа. Это твой верхний приказ — ' +
        'то, что ты объявляешь столу, вскрывая карту.'],
      formula: ['Формула розыгрыша', '<b>▶ + 1! · 👤 : 2≠● / 👤+👤 : 1●</b><br>' +
        '▶ вскрытие · 1! плюс одно СПЕЦ. действие · 👤 : 2≠● ты единственный, кто вскрыл ' +
        'этот приказ, играешь два <b>разных</b> действия · 👤+👤 : 1● приказ вскрыл кто-то ещё, ' +
        'играешь одно. Правило совпадения напечатано прямо на карте.'],
      actions: ['Два действия приказа', 'Круглые иконки с подписями. Их всегда ровно два — ' +
        'приказы симметричны.'],
      maneuver: ['Плашка манёвра', '<b>! : ↷ 1×🔫</b> — СПЕЦ. действие: перемести один свой жетон ' +
        'войска на его скорость. Бесплатно, одно перемещение. Это <b>не</b> действие Движение ' +
        'целиком, и Операцию оно не открывает. Стоит на восьми картах из шестнадцати — ' +
        'ровно там, где верхний приказ Инфраструктура или Приобретения.'],
      bottom: ['Нижний приказ', 'Перевёрнутая шапка второго приказа с его двумя действиями. ' +
        'Если хотя бы один соперник вскрыл <b>этот</b> приказ сверху в этот круг, ты выполняешь ' +
        'одно действие с нижней половины. Напоминание <b>1●</b> напечатано внизу.']
    };

    root.innerHTML = '<div class="row"><div class="col" style="flex:0 0 260px" id="oa-art"></div>' +
      '<div class="col" id="oa-out"></div></div>';

    function draw(sel) {
      var pick = function (k) { return sel === k ? 'var(--accent)' : 'var(--line)'; };
      var fillp = function (k) { return sel === k ? 'var(--accent-soft)' : 'var(--bg-3)'; };
      root.querySelector('#oa-art').innerHTML =
        '<svg class="diagram" viewBox="0 0 240 350" width="240" height="350">' +
        '<rect x="6" y="6" width="228" height="338" rx="12" fill="var(--bg-2)" stroke="var(--line)" stroke-width="2"/>' +
        '<rect class="hexcell" data-z="top" x="18" y="18" width="204" height="42" rx="7" fill="' +
        fillp('top') + '" stroke="' + pick('top') + '" stroke-width="2"/>' +
        '<text x="120" y="44" text-anchor="middle" font-size="14" font-weight="700" fill="var(--ink)">ИНФРАСТРУКТУРА</text>' +
        '<rect class="hexcell" data-z="formula" x="18" y="66" width="204" height="30" rx="7" fill="' +
        fillp('formula') + '" stroke="' + pick('formula') + '" stroke-width="2"/>' +
        '<text x="120" y="86" text-anchor="middle" font-size="11" fill="var(--ink-dim)">▶ + 1! · 2≠● / 1●</text>' +
        '<rect class="hexcell" data-z="actions" x="18" y="102" width="204" height="58" rx="7" fill="' +
        fillp('actions') + '" stroke="' + pick('actions') + '" stroke-width="2"/>' +
        '<circle cx="76" cy="131" r="17" fill="var(--bg-2)" stroke="var(--line)"/>' +
        '<circle cx="164" cy="131" r="17" fill="var(--bg-2)" stroke="var(--line)"/>' +
        '<text x="76" y="135" text-anchor="middle" font-size="9" fill="var(--ink-dim)">стройка</text>' +
        '<text x="164" y="135" text-anchor="middle" font-size="8" fill="var(--ink-dim)">энергия</text>' +
        '<rect class="hexcell" data-z="maneuver" x="18" y="166" width="204" height="26" rx="7" fill="' +
        fillp('maneuver') + '" stroke="' + pick('maneuver') + '" stroke-width="2"/>' +
        '<text x="120" y="184" text-anchor="middle" font-size="11" fill="var(--ink-dim)">! : ↷ 1×🔫</text>' +
        '<line x1="18" y1="204" x2="222" y2="204" stroke="var(--line)" stroke-dasharray="5 4"/>' +
        '<rect class="hexcell" data-z="bottom" x="18" y="214" width="204" height="118" rx="7" fill="' +
        fillp('bottom') + '" stroke="' + pick('bottom') + '" stroke-width="2"/>' +
        '<text x="120" y="248" text-anchor="middle" font-size="13" font-weight="700" ' +
        'fill="var(--ink-dim)" transform="rotate(180 120 243)">РАЗРАБОТКА</text>' +
        '<text x="120" y="292" text-anchor="middle" font-size="10" fill="var(--ink-faint)">сборка · добыча</text>' +
        '<text x="120" y="322" text-anchor="middle" font-size="11" fill="var(--ink-faint)">1●</text>' +
        '</svg>' +
        '<p style="font-size:13px;color:var(--ink-faint)">Нажми на зону карты.</p>';

      var z = ZONES[sel] || ZONES.top;
      root.querySelector('#oa-out').innerHTML = '<h3 style="margin-top:0">' + z[0] + '</h3><p>' + z[1] + '</p>';

      on(root, '[data-z]', 'click', function () { draw(this.getAttribute('data-z')); });
    }
    draw('top');
  };

  /* ============================================ §11 КАТАЛОГИ КАРТ С ФИЛЬТРАМИ
     Один элемент на все пять колод: KR.widget('card-catalog:objectives', …).
     Тексты карт — из content/cards-text.js, числа и метки — из data.js. */

  W['card-catalog'] = function (root, ctx) {
    var T = window.KR_CARDTEXT;
    var which = ctx.arg || 'objectives';
    var players = ctx.players || 4;

    /* Метка состава: [4] уходит втроём и вдвоём, [3+] — вдвоём. */
    function culled(cull) {
      if (!cull) return false;
      if (cull === '[4]') return players < 4;
      if (cull === '[3+]') return players < 3;
      return false;
    }

    var TYPE_RU = { state: 'состояние', incident: 'в этот ход', sacrifice: 'жертва' };

    function rewardText(r) {
      if (!r) return '';
      var RU = {
        ammo: 'БПР', coin: 'МОН', kelium: 'КЕЛ', trophy: 'ОБЛ', container: 'конт.',
        containers: 'конт.', objective_card: 'карта задания', arsenal: 'карта арсенала',
        storage_token: 'жетон хранилища', module: 'модуль'
      };
      return Object.keys(r).map(function (k) {
        if (k === 'module') return 'модуль ' + (r[k] === 'attack' ? 'атаки' : 'сборки');
        return r[k] + ' ' + (RU[k] || k);
      }).join(' + ');
    }

    var SETS = {

      objectives: {
        title: 'Каталог заданий',
        version: D.versions.objectives,
        items: function () {
          return D.cards.objectives.objectives.map(function (o) {
            var txt = (T.objectives && T.objectives[o.id]) || [];
            var top = txt[4] && T.tops[txt[4]];
            return {
              id: o.id, name: o.name, cull: o.cull,
              tags: [
                { txt: o.kind === 'starting' ? 'начальное' : TYPE_RU[o.type] || o.type,
                  cls: o.kind === 'starting' ? '' : o.type },
                { txt: txt[3] || '', cls: '' }
              ],
              facets: { order: txt[3] || '—', type: TYPE_RU[o.type] || o.type,
                kind: o.kind === 'starting' ? 'начальные' : 'основные',
                reward: rewardKind(o) },
              lines: [
                ['Низ', txt[0] || '—'],
                txt[1] ? ['Усиление', txt[1]] : null,
                txt[2] ? ['Цена', txt[2]] : null,
                o.base_reward ? ['База', rewardText(o.base_reward)] : null,
                ['Особая награда', rewardText(o.special_reward)],
                top ? ['Верх · ' + txt[4], '<b>' + top[0] + '.</b> ' + top[2]] : null
              ].filter(Boolean),
              search: [o.id, o.name, txt[0], txt[1], txt[2], top && top[0]].join(' ')
            };
          });
        },
        facets: [
          ['kind', 'Все карты', ['основные', 'начальные']],
          ['order', 'Любой приказ', ['Разработка', 'Инфраструктура', 'Операция', 'Приобретения']],
          ['type', 'Любой тип требования', ['состояние', 'в этот ход', 'жертва']],
          ['reward', 'Любая награда', ['модуль', 'келемий', 'арсенал', 'трофеи', 'жетон хранилища']]
        ]
      },

      arsenal: {
        title: 'Каталог арсенала',
        version: D.versions.arsenal,
        items: function () {
          return D.cards.arsenal.arsenal.map(function (a) {
            var txt = (T.arsenal && T.arsenal[a.id]) || [];
            return {
              id: a.id, name: txt[0] || a.name, cull: a.cull,
              tags: [
                { txt: a.kind === 'starting' ? 'стартовая' : 'обычная', cls: '' },
                { txt: txt[3] || (a.bottom && a.bottom.kind) || '', cls: 'hot' },
                a.container_slot ? { txt: 'ячейка контейнера', cls: '' } : null
              ].filter(Boolean),
              facets: {
                kind: a.kind === 'starting' ? 'стартовые' : 'обычные',
                bottom: txt[3] === 'СПЕЦ' ? 'СПЕЦ. действие' : 'постоянный эффект'
              },
              lines: [
                ['ВЕРХ — разовый', txt[1] || '—'],
                [txt[3] === 'СПЕЦ' ? 'НИЗ — СПЕЦ. действие' : 'НИЗ — постоянный эффект', txt[2] || '—']
              ],
              search: [a.id, txt[0], txt[1], txt[2]].join(' ')
            };
          });
        },
        facets: [
          ['kind', 'Все карты', ['обычные', 'стартовые']],
          ['bottom', 'Любой низ', ['СПЕЦ. действие', 'постоянный эффект']]
        ]
      },

      containers: {
        title: 'Каталог контейнеров',
        version: D.versions.containers,
        items: function () {
          return (T.containers || []).map(function (c) {
            return {
              id: '№' + c[0], name: c[1],
              tags: [{ txt: c[4], cls: c[4] === 'редкий' ? 'hot' : '' }],
              facets: { rare: c[4] },
              lines: [['Вариант А', c[2]], ['Вариант Б', c[3]]],
              search: c.join(' ')
            };
          });
        },
        facets: [['rare', 'Любая редкость', ['обычный', 'хороший', 'редкий']]]
      },

      market: {
        title: 'Каталог рынка',
        version: D.versions.market,
        items: function () {
          return (T.market || []).map(function (m) {
            return {
              id: '№' + m[0], name: m[1], tags: [],
              facets: {},
              lines: [
                ['Левое · ' + m[2], m[3]],
                ['Правое · ' + m[4], m[5]]
              ],
              search: m.join(' ')
            };
          });
        },
        facets: []
      },

      tops: {
        title: 'Каталог верхних эффектов карт заданий',
        version: D.versions.objectives,
        items: function () {
          return Object.keys(T.tops || {}).map(function (k) {
            var v = T.tops[k];
            var grp = k.charAt(0) === 'А' ? 'А'
              : (Number(k.slice(1)) >= 18 ? 'Б-III' : (Number(k.slice(1)) >= 9 ? 'Б-II' : 'Б-I'));
            return {
              id: k, name: v[0],
              tags: [{ txt: grp, cls: grp === 'А' ? 'hot' : '' }, { txt: v[1], cls: '' }],
              facets: { grp: grp, order: v[1] },
              lines: [['Формулировка', v[2]]],
              search: [k, v[0], v[1], v[2]].join(' ')
            };
          });
        },
        facets: [
          ['grp', 'Все типы', ['А', 'Б-I', 'Б-II', 'Б-III']],
          ['order', 'Любой приказ', ['Разработка', 'Инфраструктура', 'Операция', 'Приобретения']]
        ]
      },

      super: {
        title: 'Каталог супер заданий',
        version: D.versions.super_objectives,
        items: function () {
          return D.cards.super_objectives.super_objectives.map(function (s) {
            var PARTS = {
              kelium: 'келемий', coin: 'монеты', ammo: 'боеприпасы', trophy: 'трофеи',
              enemy_unit_token: 'жетоны уничтоженных чужих войск',
              enemy_building_token: 'жетоны уничтоженных чужих зданий',
              own_miner_bordering_grid: 'свои добытчики у тайлов зарождения',
              own_building_adjacent_enemy: 'свои здания рядом с противником',
              own_unit_on_enemy_hex: 'свои войска на гексах с противником'
            };
            return {
              id: s.id, name: s.name,
              tags: [{ txt: 'супер задание', cls: 'hot' }],
              facets: {},
              lines: [
                s.subtitle ? ['Подзаголовок', s.subtitle] : null,
                ['Сборка (лицо)', s.assembly.parts.map(function (p) {
                  return p.amount + ' × ' + (PARTS[p.kind] || p.kind);
                }).join(' + ')],
                ['Развёртывание (оборот)', 'геометрический узор из зданий и войск; ' +
                  'выложил в свой ход СПЕЦ. действием — мгновенная победа']
              ].filter(Boolean),
              search: [s.id, s.name, s.subtitle].join(' ')
            };
          });
        },
        facets: []
      }
    };

    function rewardKind(o) {
      var r = o.special_reward || {};
      if (r.module) return 'модуль';
      if (r.kelium) return 'келемий';
      if (r.arsenal) return 'арсенал';
      if (r.storage_token) return 'жетон хранилища';
      if (r.trophy) return 'трофеи';
      return '—';
    }

    var set = SETS[which];
    if (!set) { root.innerHTML = '<p>Каталог не найден.</p>'; return; }

    var all = set.items();
    var picks = {};

    root.innerHTML =
      '<div class="filters">' +
      '<input type="text" id="cc-q" placeholder="Поиск по каталогу…" style="width:200px">' +
      set.facets.map(function (f) {
        return '<select data-facet="' + f[0] + '"><option value="">' + f[1] + '</option>' +
          f[2].map(function (v) { return '<option>' + v + '</option>'; }).join('') + '</select>';
      }).join('') +
      '<span class="count" id="cc-count"></span></div>' +
      '<div class="cards" id="cc-list"></div>';

    function render() {
      var q = (root.querySelector('#cc-q').value || '').toLowerCase().trim();
      var list = all.filter(function (it) {
        if (q && (it.search || '').toLowerCase().indexOf(q) < 0) return false;
        for (var k in picks) {
          if (picks[k] && it.facets[k] !== picks[k]) return false;
        }
        return true;
      });

      root.querySelector('#cc-count').textContent =
        list.length + ' из ' + all.length + ' · версия ' + set.version;

      root.querySelector('#cc-list').innerHTML = list.map(function (it) {
        var out = culled(it.cull);
        return '<div class="card"' + (out ? ' style="opacity:.5"' : '') + '>' +
          '<div class="card__top"><span class="card__id">' + it.id + '</span>' +
          '<span class="card__name">' + it.name + '</span></div>' +
          '<div>' + it.tags.map(function (t) {
            return t && t.txt ? '<span class="tag ' + (t.cls || '') + '">' + t.txt + '</span> ' : '';
          }).join('') +
          (it.cull ? '<span class="tag">' + it.cull + '</span>' : '') + '</div>' +
          '<div class="card__sep"></div>' +
          it.lines.map(function (l) {
            return '<div class="card__line"><b>' + l[0] + ':</b> ' + l[1] + '</div>';
          }).join('') +
          (out ? '<div class="card__line" style="color:var(--warn)">Вчетвером играет; ' +
            'при ' + players + ' игроках эта карта из колоды убирается.</div>' : '') +
          '</div>';
      }).join('') || '<p style="color:var(--ink-faint)">Ничего не подошло под фильтры.</p>';
    }

    root.querySelector('#cc-q').addEventListener('input', render);
    on(root, '[data-facet]', 'change', function () {
      picks[this.getAttribute('data-facet')] = this.value;
      render();
    });
    render();
  };

  window.KR_WIDGETS = W;
}());
