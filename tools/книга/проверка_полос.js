// ПРОВЕРКА ПОЛОС КНИГИ: что вылезло за поле полосы (низ и бока) — блоки,
// картинки, строки. Печатная вёрстка, как в PDF.
// Запуск: node tools/книга/проверка_полос.js <вёрстка.html>
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const exe = process.env.KELIUM_CHROME || undefined;
  const b = await chromium.launch(exe ? { executablePath: exe } : {});
  const p = await b.newPage(); await p.emulateMedia({ media: 'print' });
  await p.goto('file://' + path.resolve(process.argv[2]), { waitUntil: 'load' });
  await p.evaluate(() => document.fonts.ready);
  const r = await p.evaluate(() => {
    const mm = v => (v / (96 / 25.4)).toFixed(1);
    const out = [];
    document.querySelectorAll('.стр').forEach((s, i) => {
      if (s.classList.contains('обложка') && !s.classList.contains('задняя')) return;
      const R = s.getBoundingClientRect(), cs = getComputedStyle(s);
      const низ = R.bottom - parseFloat(cs.paddingBottom) + 2;
      const лево = R.left + parseFloat(cs.paddingLeft) - 2, право = R.right - parseFloat(cs.paddingRight) + 2;
      const плохие = new Set();
      s.querySelectorAll('.блок, .пример, .рис, .рисунок-в-колонке, .рисунок-во-всю, table, li, p, img, .плитка, .комп').forEach(e => {
        if (e.closest('.колонцифра') || e.closest('svg')) return;
        const q = e.getBoundingClientRect();
        if (q.height < 1 || q.width < 1) return;
        if (q.bottom > низ || q.left < лево || q.right > право) {
          const t = (e.textContent || e.getAttribute('src') || e.className).replace(/\s+/g, ' ').trim().slice(0, 40);
          плохие.add(`${e.tagName.toLowerCase()}.${e.className.split(' ')[0]} «${t}» низ ${mm(q.bottom - низ)}мм`);
        }
      });
      if (плохие.size) out.push(`стр ${i + 1}: ` + [...плохие].slice(0, 4).join(' | '));
    });
    return out;
  });
  console.log(r.join('\n') || 'все полосы в поле');
  await b.close();
})();
