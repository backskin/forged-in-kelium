/* Оболочка справочника: оглавление, переходы, поиск, темы, состав игроков. */

(function () {
  'use strict';

  var SECTIONS = window.KR_SECTIONS || [];
  var state = {
    current: null,
    players: Number(localStorage.getItem('kr.players') || 4),
    theme: localStorage.getItem('kr.theme') || 'dark'
  };

  var elMain, elToc, elSearch, elResults;

  /* ---------------------------------------------------------------- запуск */

  function boot() {
    document.documentElement.setAttribute('data-theme', state.theme);
    elMain = document.getElementById('main');
    elToc = document.getElementById('toc');
    elSearch = document.getElementById('q');
    elResults = document.getElementById('results');

    buildToc();
    bindTools();
    bindSearch();

    var first = (location.hash || '').replace(/^#/, '');
    go(SECTIONS.some(function (s) { return s.id === first; }) ? first : SECTIONS[0].id, true);

    window.addEventListener('hashchange', function () {
      var id = (location.hash || '').replace(/^#/, '');
      if (id && id !== state.current) go(id, true);
    });
  }

  /* ------------------------------------------------------------ оглавление */

  function buildToc() {
    var html = '', group = null;
    SECTIONS.forEach(function (s) {
      if (s.group && s.group !== group) {
        group = s.group;
        html += '<div class="toc__group">' + group + '</div>';
      }
      html += '<button class="toc__item" data-go="' + s.id + '">' +
        '<span class="toc__num">' + (s.num === undefined ? '' : s.num) + '</span>' +
        '<span>' + s.title + '</span></button>';
    });
    elToc.innerHTML = html;
  }

  function markToc() {
    Array.prototype.forEach.call(elToc.querySelectorAll('.toc__item'), function (b) {
      b.classList.toggle('active', b.getAttribute('data-go') === state.current);
    });
  }

  /* ------------------------------------------------------------- переходы */

  function section(id) {
    for (var i = 0; i < SECTIONS.length; i++) if (SECTIONS[i].id === id) return SECTIONS[i];
    return null;
  }

  function go(id, replace, anchor) {
    var s = section(id);
    if (!s) return;
    state.current = id;

    var ctx = { players: state.players, go: go };
    var body = typeof s.body === 'function' ? s.body(ctx) : (s.body || '');

    elMain.innerHTML =
      '<article class="page" id="page">' +
      (s.kicker ? '<div class="page__kicker">' + s.kicker + '</div>' : '') +
      '<h1>' + (s.num !== undefined ? s.num + '. ' : '') + s.title + '</h1>' +
      (s.lede ? '<p class="lede">' + s.lede + '</p>' : '') +
      body +
      seeAlso(s) +
      '</article>';

    /* Интерактивные элементы поднимаются после вставки разметки. */
    Array.prototype.forEach.call(elMain.querySelectorAll('[data-widget]'), function (node) {
      /* Имя вида «card-catalog:objectives» — часть после двоеточия уходит
         элементу как параметр. */
      var name = node.getAttribute('data-widget');
      var parts = name.split(':');
      var mount = window.KR_WIDGETS && window.KR_WIDGETS[parts[0]];
      if (mount) {
        try {
          mount(node.querySelector('.widget__body'),
            { players: state.players, go: go, arg: parts[1] });
        } catch (e) {
          node.querySelector('.widget__body').innerHTML =
            '<div class="verdict no">Элемент не собрался: ' + e.message + '</div>';
        }
      }
    });

    if (!replace) history.pushState(null, '', '#' + id);
    else if (location.hash !== '#' + id) history.replaceState(null, '', '#' + id);

    markToc();
    elToc.classList.remove('open');

    if (anchor) {
      var target = findText(anchor);
      if (target) { target.scrollIntoView({ block: 'center' }); flash(target); return; }
    }
    elMain.scrollTop = 0;
  }

  function seeAlso(s) {
    if (!s.see || !s.see.length) return '';
    var chips = s.see.map(function (id) {
      var o = section(id);
      return o ? '<button class="chip" data-go="' + id + '">' +
        (o.num !== undefined ? o.num + '. ' : '') + o.title + '</button>' : '';
    }).join('');
    return '<div class="seealso"><div class="seealso__title">Смотри также</div>' +
      '<div class="seealso__list">' + chips + '</div></div>';
  }

  function findText(needle) {
    var low = needle.toLowerCase();
    var nodes = elMain.querySelectorAll('h2, h3, h4, p, li, td, summary');
    for (var i = 0; i < nodes.length; i++) {
      if ((nodes[i].textContent || '').toLowerCase().indexOf(low) >= 0) return nodes[i];
    }
    return null;
  }

  function flash(node) {
    var old = node.style.background;
    node.style.transition = 'background .35s';
    node.style.background = 'var(--accent-soft)';
    setTimeout(function () { node.style.background = old; }, 1400);
  }

  /* ---------------------------------------------------------------- поиск */

  var INDEX = null;

  /** Индекс строится один раз, из отрисованного текста всех разделов. */
  function buildIndex() {
    if (INDEX) return INDEX;
    INDEX = [];
    var sandbox = document.createElement('div');
    SECTIONS.forEach(function (s) {
      var body;
      try {
        body = typeof s.body === 'function' ? s.body({ players: state.players, go: go }) : (s.body || '');
      } catch (e) { body = ''; }
      sandbox.innerHTML = body;
      var title = (s.num !== undefined ? s.num + '. ' : '') + s.title;
      INDEX.push({ sec: s.id, secTitle: title, head: title, text: s.lede || '' });
      Array.prototype.forEach.call(
        sandbox.querySelectorAll('h2, h3, h4, p, li, td, th, summary'),
        function (n) {
          var txt = (n.textContent || '').replace(/\s+/g, ' ').trim();
          if (txt.length < 4) return;
          INDEX.push({ sec: s.id, secTitle: title, head: null, text: txt });
        }
      );
    });
    return INDEX;
  }

  function search(q) {
    var idx = buildIndex();
    var terms = q.toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return [];
    var hits = [];
    for (var i = 0; i < idx.length && hits.length < 400; i++) {
      var rec = idx[i];
      var hay = ((rec.head || '') + ' ' + rec.text).toLowerCase();
      var score = 0, ok = true;
      for (var j = 0; j < terms.length; j++) {
        var p = hay.indexOf(terms[j]);
        if (p < 0) { ok = false; break; }
        score += rec.head ? 40 : 10;
        if (p === 0 || /[\s(«"]/.test(hay.charAt(p - 1))) score += 6;
      }
      if (ok) hits.push({ rec: rec, score: score - Math.min(20, rec.text.length / 60) });
    }
    hits.sort(function (a, b) { return b.score - a.score; });
    return hits.slice(0, 30);
  }

  function highlight(text, q) {
    var out = KR.esc(text);
    q.toLowerCase().split(/\s+/).filter(Boolean).forEach(function (term) {
      var re = new RegExp('(' + term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi');
      out = out.replace(re, '<mark>$1</mark>');
    });
    return out;
  }

  function bindSearch() {
    var timer = null;
    elSearch.addEventListener('input', function () {
      clearTimeout(timer);
      timer = setTimeout(function () { runSearch(elSearch.value.trim()); }, 110);
    });
    elSearch.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { elSearch.value = ''; closeResults(); elSearch.blur(); }
      if (e.key === 'Enter') {
        var first = elResults.querySelector('.results__item');
        if (first) first.click();
      }
    });
    document.addEventListener('click', function (e) {
      if (!elResults.contains(e.target) && e.target !== elSearch) closeResults();
    });
    document.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'f') { e.preventDefault(); elSearch.focus(); elSearch.select(); }
      if (e.key === '/' && document.activeElement !== elSearch) { e.preventDefault(); elSearch.focus(); }
    });
  }

  function runSearch(q) {
    if (q.length < 2) return closeResults();
    var hits = search(q);
    if (!hits.length) {
      elResults.innerHTML = '<div class="results__empty">Ничего не нашлось. ' +
        'Попробуй одно слово — например «надбавка», «вышка», «трофей».</div>';
      elResults.classList.add('open');
      return;
    }
    elResults.innerHTML = hits.map(function (h) {
      return '<button class="results__item" data-sec="' + h.rec.sec +
        '" data-anchor="' + KR.esc(h.rec.text.slice(0, 60)) + '">' +
        '<div class="results__sec">' + KR.esc(h.rec.secTitle) + '</div>' +
        '<div class="results__txt">' + highlight(h.rec.text || h.rec.head, q) + '</div>' +
        '</button>';
    }).join('');
    elResults.classList.add('open');
  }

  function closeResults() { elResults.classList.remove('open'); }

  /* --------------------------------------------------------------- кнопки */

  function bindTools() {
    document.getElementById('theme').addEventListener('click', function () {
      state.theme = state.theme === 'dark' ? 'light' : 'dark';
      localStorage.setItem('kr.theme', state.theme);
      document.documentElement.setAttribute('data-theme', state.theme);
      this.textContent = state.theme === 'dark' ? 'Светлая тема' : 'Тёмная тема';
    });
    document.getElementById('theme').textContent =
      state.theme === 'dark' ? 'Светлая тема' : 'Тёмная тема';

    document.getElementById('print').addEventListener('click', function () { window.print(); });

    var seg = document.getElementById('players');
    Array.prototype.forEach.call(seg.querySelectorAll('button'), function (b) {
      b.classList.toggle('on', Number(b.getAttribute('data-n')) === state.players);
      b.addEventListener('click', function () {
        state.players = Number(b.getAttribute('data-n'));
        localStorage.setItem('kr.players', state.players);
        Array.prototype.forEach.call(seg.querySelectorAll('button'), function (x) {
          x.classList.toggle('on', x === b);
        });
        INDEX = null;
        go(state.current, true);
      });
    });

    document.getElementById('burger').addEventListener('click', function () {
      elToc.classList.toggle('open');
    });

    /* Один обработчик на все переходы: оглавление, «смотри также», ссылки в тексте. */
    document.addEventListener('click', function (e) {
      var b = e.target.closest ? e.target.closest('[data-go]') : null;
      if (b) { e.preventDefault(); go(b.getAttribute('data-go'), false, b.getAttribute('data-anchor')); return; }
      var r = e.target.closest ? e.target.closest('.results__item') : null;
      if (r) {
        closeResults();
        elSearch.value = '';
        go(r.getAttribute('data-sec'), false, r.getAttribute('data-anchor'));
      }
    });
  }

  window.KR_GO = function (id, anchor) { go(id, false, anchor); };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
}());
