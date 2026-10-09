// ПЕЧАТЬ КНИГИ ПРАВИЛ В PDF — Chromium через Playwright, без Windows-путей.
// Запуск: node tools/книга/печать_книги.js <вёрстка.html> <куда.pdf>
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const exe = process.env.KELIUM_CHROME || undefined;
  const b = await chromium.launch(exe ? { executablePath: exe } : {});
  const p = await b.newPage();
  await p.emulateMedia({ media: 'print' });
  await p.goto('file://' + path.resolve(process.argv[2]), { waitUntil: 'load' });
  await p.evaluate(() => document.fonts.ready);
  await p.pdf({ path: process.argv[3], preferCSSPageSize: true, printBackground: true });
  await b.close();
})();
