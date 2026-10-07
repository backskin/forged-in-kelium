// ПРОВЕРКА КАРТИНОК КНИГИ: у каждой картинки — сколько её точек приходится на
// дюйм при печати. Меньше 200 — в PDF она выйдет мыльной (замечание дизайнера
// 02.10.2026: «иконки страшно пожаты»). Ещё ловит пропавшие файлы и картинки,
// растянутые не в своих пропорциях.
// Запуск: node tools/книга/проверка_картинок.js <вёрстка.html>
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const exe = process.env.KELIUM_CHROME || undefined;
  const b = await chromium.launch(exe ? { executablePath: exe } : {});
  const p = await b.newPage(); await p.emulateMedia({ media: 'print' });
  await p.goto('file://' + path.resolve(process.argv[2]), { waitUntil: 'load' });
  const r = await p.evaluate(() => {
    const out = [];
    document.querySelectorAll('.стр').forEach((s, i) => s.querySelectorAll('img').forEach(im => {
      const q = im.getBoundingClientRect();
      if (q.width < 2) return;
      const src = (im.getAttribute('src') || '').slice(0, 40);
      if (!im.naturalWidth) { out.push(`стр ${i + 1}: НЕТ ФАЙЛА ${src}`); return; }
      const dpi = im.naturalWidth / (q.width / 96);
      const искаж = Math.abs((q.width / q.height) / (im.naturalWidth / im.naturalHeight) - 1);
      const cs = getComputedStyle(im);
      if (dpi < 200) out.push(`стр ${i + 1}: ${Math.round(dpi)} dpi ${im.naturalWidth}px→${(q.width / 96 * 25.4).toFixed(1)}мм ${src}`);
      if (искаж > 0.04 && cs.objectFit !== 'contain' && cs.objectFit !== 'cover') out.push(`стр ${i + 1}: ИСКАЖЕНА на ${Math.round(искаж * 100)}% ${src}`);
    }));
    return out;
  });
  console.log(r.join('\n') || 'все картинки в порядке');
  await b.close();
})();
