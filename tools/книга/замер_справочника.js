// ЗАМЕР СПРАВОЧНИКА — настоящие высоты карточек и шапок в Chromium.
//
// Сборщик справочника раскладывает карточки по колонкам сам, а высоту
// карточки на глаз не угадать: перенос строк зависит от шрифта. Поэтому
// первый проход вёрстки открывается здесь, каждая карточка (data-k) и каждая
// шапка раздела (data-h) меряется, и второй проход раскладывает по этим
// числам. Заодно скрипт находит полосы, где что-то вылезло за поле.
//
// Запуск: node tools/книга/замер_справочника.js <Справочник.html> [куда.pdf]  → JSON в stdout
const path = require('path');
let chromium;
try { ({ chromium } = require('playwright')); }
catch (e) { ({ chromium } = require(path.join(process.execPath, '..', '..', 'lib', 'node_modules', 'playwright'))); }

(async () => {
  const exe = process.env.KELIUM_CHROME || undefined;
  const b = await chromium.launch(exe ? { executablePath: exe } : {});
  const p = await b.newPage();
  await p.emulateMedia({ media: 'print' });
  await p.goto('file://' + path.resolve(process.argv[2]));
  await p.evaluate(() => document.fonts.ready);
  const r = await p.evaluate(() => {
    const mm = v => v / (96 / 25.4);
    const out = { cards: {}, heads: {}, page: null, over: [] };
    document.querySelectorAll('.кк[data-k]').forEach(e => {
      const cs = getComputedStyle(e);
      out.cards[e.dataset.k] = mm(e.getBoundingClientRect().height + parseFloat(cs.marginBottom));
    });
    document.querySelectorAll('.стр').forEach((s, i) => {
      const cs = getComputedStyle(s);
      const R = s.getBoundingClientRect();
      const верх = R.top + parseFloat(cs.paddingTop);
      const низ = R.bottom - parseFloat(cs.paddingBottom);
      if (!out.page && s.classList.contains('спр')) out.page = mm(низ - верх);
      const h = s.querySelector('[data-h]');
      const кк = s.querySelector('.карточки');
      if (h && кк) out.heads[h.dataset.h] = mm(кк.getBoundingClientRect().top - верх);
      s.querySelectorAll('.кк, .ряд, .словарь > div, table, figure, .легенда-поля, .портрет').forEach(e => {
        if (e.getBoundingClientRect().bottom > низ + 1) out.over.push(i + 1);
      });
    });
    out.over = [...new Set(out.over)];
    return out;
  });
  // PDF — тем же браузером и той же печатной вёрсткой, что мерили: печать
  // chrome --print-to-pdf раскладывала колонки иначе, чем замер.
  if (process.argv[3]) {
    await p.pdf({ path: process.argv[3], preferCSSPageSize: true, printBackground: true });
  }
  console.log(JSON.stringify(r));
  await b.close();
})();
