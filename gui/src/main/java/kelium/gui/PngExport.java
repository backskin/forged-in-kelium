package kelium.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

/**
 * PngExport — выгрузка картинок из конструктора.
 *
 * <p>Две вещи, которые дизайнер уносит со стола в работу: <b>раскладка</b>
 * (поле со всеми обозначениями, легендой игроков и статистикой под ним) и
 * <b>сборка из блоков</b> (как сложить это поле из физических кусков картона).
 * Обе сохраняются одним PNG через привычный {@link PathDialog}.
 *
 * <p>Здесь только общая часть: рисование заголовка, легенды, статистики и сама
 * запись файла. Само поле рисуют те же холсты, что и на экране, — чтобы
 * картинка не могла разойтись с тем, что видно в программе.
 */
public final class PngExport {

    private PngExport() {
    }

    private static final Color BG = new Color(0xFFFFFF);
    private static final Color INK = new Color(0x22201B);
    private static final Color MUTED = new Color(0x6d6a5e);
    private static final Color RULE = new Color(0xDDDAD0);

    /** Одна строка легенды: цветной образец + подпись. */
    public record Item(Color fill, Color edge, String shape, String text, String letter) {
        public static Item hex(Color fill, String text) {
            return new Item(fill, MUTED, "hex", text, null);
        }

        public static Item circle(Color fill, String text) {
            return new Item(fill, MUTED, "circle", text, null);
        }

        public static Item square(Color fill, String text) {
            return new Item(fill, MUTED, "square", text, null);
        }

        /** Кружок с буквой места (P1, P2…) — для персональной легенды игрока. */
        public static Item seat(Color fill, String letter, String text) {
            return new Item(fill, MUTED, "seat", text, letter);
        }
    }

    /**
     * Блок одного игрока: цвет места, заголовок («Игрок 1») и произвольные
     * строки показателей под ним («келемий поблизости: 12», «соседей у старта: 4»).
     */
    public record PlayerBlock(Color color, String title, List<String> lines) { }

    /**
     * КАК ПОКАЗАТЬ ПОЛЕ И СБОРКУ ИЗ БЛОКОВ ВМЕСТЕ (просьба дизайнера 14.08.2026:
     * незачем выгружать их отдельными действиями, если под столом они всё равно
     * нужны рядом). Сборка одна на все выгрузки — переключается в окне настроек
     * экспорта, а не выбирается заново при каждом сохранении.
     */
    public enum Layout {
        /** Два отдельных файла: «…-поле.png» и «…-сборка.png». */
        SEPARATE("Раздельно — два файла"),
        /** Один файл: поле сверху, сборка снизу. */
        VERTICAL("Один файл — поле над сборкой"),
        /** Один файл: поле и сборка бок о бок. */
        HORIZONTAL("Один файл — поле рядом со сборкой"),
        /**
         * ОДНА КАРТИНКА: сама раскладка блоков (приглушённые гексы, жирные
         * границы блоков, чёрные накладки недоступных гексов) — как главное
         * изображение, а под ним полная легенда и статистика РАСКЛАДКИ (не
         * куцая легенда сборки). Так на одной картинке видно и «что где лежит на
         * поле», и «из каких блоков это сложено» разом.
         */
        FUSION("Слияние — блоки под содержимым поля");

        public final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    /** Что класть в картинку — управляется окном «Настройки экспорта». */
    public record Options(boolean generalLegend, boolean players, boolean mapStats,
                          Layout layout) {
        public static final Options ALL = new Options(true, true, true, Layout.SEPARATE);

        /** Совместимость со старым трёхпольным вызовом — раскладка «раздельно». */
        public Options(boolean generalLegend, boolean players, boolean mapStats) {
            this(generalLegend, players, mapStats, Layout.SEPARATE);
        }
    }

    /**
     * Всё, что уходит ПОД поле: общая легенда, блоки игроков, общая статистика
     * карты. Пустой список/карта — раздел просто не рисуется.
     */
    public record Content(List<Item> legend, List<PlayerBlock> players,
                          List<String> mapStats) {
        public static Content legendOnly(List<Item> legend) {
            return new Content(legend, List.of(), List.of());
        }

        /** То же содержимое, но урезанное чекбоксами окна настроек экспорта. */
        public Content filtered(Options o) {
            return new Content(
                o.generalLegend() ? legend : List.of(),
                o.players() ? players : List.of(),
                o.mapStats() ? mapStats : List.of());
        }
    }

    // ==================== геометрия текста ====================
    // Дизайнер 14.08.2026: «описание под картинкой почти нечитаемо» — кегль
    // легенды был 13px при печатном экспорте на весь разворот стола. Подняты
    // все размеры блока под полем и раздвинуты отступы.
    private static final int TITLE_SIZE = 26;
    private static final int SUBTITLE_SIZE = 15;
    private static final int SECTION_SIZE = 18;
    private static final int LEGEND_SIZE = 16;
    private static final int PLAYER_TITLE_SIZE = 17;
    private static final int PLAYER_LINE_SIZE = 14;
    private static final int STAT_SIZE = 15;
    private static final int SAMPLE = 26;

    /**
     * Собрать итоговую картинку: заголовок, поле, легенда, игроки, статистика.
     *
     * @param title    крупная надпись сверху
     * @param subtitle строка помельче под заголовком (может быть null)
     * @param field    уже нарисованное поле
     * @param content  что показать под полем (легенда/игроки/статистика)
     */
    public static BufferedImage compose(String title, String subtitle,
                                        BufferedImage field, Content content) {
        int w = Math.max(field.getWidth(), 1000);
        int headH = subtitle == null || subtitle.isBlank() ? 60 : 90;

        List<Item> legend = content.legend();
        List<PlayerBlock> players = content.players();
        List<String> stats = content.mapStats();

        int legendCols = w >= 1300 ? 3 : 2;
        int statCols = w >= 1300 ? 2 : 1;

        // ДВА ПРОХОДА: сперва меряем на одноразовой картинке (у неё те же
        // FontMetrics, что у настоящей — размер шрифта от неё не зависит), потом
        // рисуем начисто на итоговой. Так итоговая высота картинки известна ДО
        // того, как её вообще можно создать.
        Graphics2D measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
            .createGraphics();
        int legendH = legend.isEmpty() ? 0
            : legendSection(measure, legend, w, 0, legendCols, false);
        int playerH = players.isEmpty() ? 0
            : playersSection(measure, players, w, 0, false);
        int statsH = stats.isEmpty() ? 0
            : statsSection(measure, stats, w, 0, statCols, false);
        measure.dispose();

        int h = headH + field.getHeight() + legendH + playerH + statsH + 24;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);

        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, TITLE_SIZE));
        g.drawString(title, 28, 40);
        if (subtitle != null && !subtitle.isBlank()) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, SUBTITLE_SIZE));
            g.drawString(subtitle, 28, 66);
        }

        g.drawImage(field, (w - field.getWidth()) / 2, headH, null);

        int y = headH + field.getHeight() + 10;
        if (!legend.isEmpty()) {
            y = legendSection(g, legend, w, y, legendCols, true);
        }
        if (!players.isEmpty()) {
            y = playersSection(g, players, w, y, true);
        }
        if (!stats.isEmpty()) {
            statsSection(g, stats, w, y, statCols, true);
        }
        g.dispose();
        return out;
    }

    /**
     * РАЗБИТЬ ТЕКСТ ПО СЛОВАМ так, чтобы каждая строка укладывалась в
     * {@code maxWidth}. Раньше подпись легенды писалась одной строкой
     * {@code drawString} и на длинных фразах вылезала за край картинки —
     * жалоба дизайнера 14.08.2026. Если ОДНО слово само шире {@code maxWidth}
     * (длинный код без пробелов), режем его по буквам — иначе цикл не сдвинется
     * с места.
     */
    private static List<String> wrap(java.awt.FontMetrics fm, String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth || line.isEmpty()) {
                if (fm.stringWidth(word) > maxWidth && line.isEmpty()) {
                    // слово само по себе не влезает ни на какой строке — режем
                    int cut = word.length();
                    while (cut > 1 && fm.stringWidth(word.substring(0, cut)) > maxWidth) {
                        cut--;
                    }
                    lines.add(word.substring(0, cut));
                    line = new StringBuilder(word.substring(cut));
                } else {
                    line = new StringBuilder(candidate);
                }
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static int sectionHeader(Graphics2D g, String text, int w, int y, boolean draw) {
        if (draw) {
            g.setColor(RULE);
            g.fillRect(28, y, w - 56, 1);
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, SECTION_SIZE));
            g.drawString(text, 28, y + 26);
        }
        return y + 26 + 12;
    }

    /**
     * ОБЩАЯ ЛЕГЕНДА: цветной образец + подпись, подпись переносится по словам,
     * если не влезает в колонку. Раскладка по колонкам БАЛАНСИРУЕТ высоту —
     * следующая карточка всегда идёт в самую короткую колонку на этот момент,
     * а не в жёстко назначенную по индексу: у карточек теперь разная высота
     * (одна строка текста или три), и фиксированная сетка строк рассыпалась бы.
     */
    private static int legendSection(Graphics2D g, List<Item> legend, int w, int y0,
                                      int cols, boolean draw) {
        int y = sectionHeader(g, "Обозначения", w, y0, draw);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, LEGEND_SIZE);
        java.awt.FontMetrics fm = g.getFontMetrics(font);
        int colW = (w - 56) / cols;
        int textW = colW - SAMPLE - 12 - 10;
        int lineH = LEGEND_SIZE + 6;
        int gap = 10;
        int[] colY = new int[cols];
        for (Item it : legend) {
            List<String> lines = wrap(fm, it.text(), textW);
            int itemH = Math.max(SAMPLE, lines.size() * lineH);
            int col = 0;
            for (int c = 1; c < cols; c++) {
                if (colY[c] < colY[col]) {
                    col = c;
                }
            }
            int x = 28 + col * colW;
            int ry = y + colY[col];
            if (draw) {
                sample(g, it, x, ry + (itemH - SAMPLE) / 2);
                g.setColor(INK);
                g.setFont(font);
                int ty = ry + fm.getAscent();
                for (String line : lines) {
                    g.drawString(line, x + SAMPLE + 12, ty);
                    ty += lineH;
                }
            }
            colY[col] += itemH + gap;
        }
        int maxColY = 0;
        for (int cy : colY) {
            maxColY = Math.max(maxColY, cy);
        }
        return y + maxColY + 10;
    }

    /**
     * ЛИЧНАЯ ЛЕГЕНДА КАЖДОГО ИГРОКА: цветной кружок с местом, заголовок и его
     * собственные показатели — колонка на каждого (просьба дизайнера
     * 14.08.2026: раньше в легенде был один ОБЩИЙ образец «старт игрока P1» на
     * всех, будто игрок один). Заголовок и строки переносятся по словам.
     */
    private static int playersSection(Graphics2D g, List<PlayerBlock> players, int w, int y0,
                                      boolean draw) {
        int y = sectionHeader(g, "Игроки", w, y0, draw);
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, PLAYER_TITLE_SIZE);
        Font lineFont = new Font(Font.SANS_SERIF, Font.PLAIN, PLAYER_LINE_SIZE);
        java.awt.FontMetrics titleFm = g.getFontMetrics(titleFont);
        java.awt.FontMetrics lineFm = g.getFontMetrics(lineFont);
        int colW = (w - 56) / players.size();
        int titleTextW = colW - SAMPLE - 10;
        int titleLineH = PLAYER_TITLE_SIZE + 4;
        int statLineH = PLAYER_LINE_SIZE + 8;
        int maxBlockH = 0;
        for (int i = 0; i < players.size(); i++) {
            PlayerBlock pb = players.get(i);
            int x = 28 + i * colW;
            List<String> title = wrap(titleFm, pb.title(), titleTextW);
            if (draw) {
                g.setColor(pb.color());
                g.fillOval(x, y, SAMPLE, SAMPLE);
                g.setColor(MUTED);
                g.drawOval(x, y, SAMPLE, SAMPLE);
                g.setColor(INK);
                g.setFont(titleFont);
            }
            int ty = y + titleFm.getAscent();
            for (String line : title) {
                if (draw) {
                    g.drawString(line, x + SAMPLE + 10, ty);
                }
                ty += titleLineH;
            }
            int blockH = Math.max(SAMPLE, title.size() * titleLineH) + 16;
            if (draw) {
                g.setFont(lineFont);
                g.setColor(MUTED);
            }
            int ly = y + blockH + lineFm.getAscent();
            for (String stat : pb.lines()) {
                for (String line : wrap(lineFm, stat, colW - 10)) {
                    if (draw) {
                        g.drawString(line, x, ly);
                    }
                    ly += statLineH;
                    blockH += statLineH;
                }
            }
            maxBlockH = Math.max(maxBlockH, blockH);
        }
        return y + maxBlockH + 20;
    }

    /**
     * ОБЩАЯ СТАТИСТИКА ПОЛЯ: то, что дизайнер сверяет глазами при балансировке
     * раскладки — размер, число зарождений, расстояния между стартами. Та же
     * балансировка по колонкам, что и в общей легенде.
     */
    private static int statsSection(Graphics2D g, List<String> stats, int w, int y0,
                                    int cols, boolean draw) {
        int y = sectionHeader(g, "Статистика поля", w, y0, draw);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, STAT_SIZE);
        java.awt.FontMetrics fm = g.getFontMetrics(font);
        int colW = (w - 56) / cols;
        int textW = colW - 16;
        int lineH = STAT_SIZE + 8;
        int gap = 6;
        int[] colY = new int[cols];
        for (String stat : stats) {
            List<String> lines = wrap(fm, stat, textW);
            int itemH = lines.size() * lineH;
            int col = 0;
            for (int c = 1; c < cols; c++) {
                if (colY[c] < colY[col]) {
                    col = c;
                }
            }
            int x = 28 + col * colW;
            int ry = y + colY[col];
            if (draw) {
                g.setColor(MUTED);
                g.drawString("•", x, ry + fm.getAscent());
                g.setColor(INK);
                g.setFont(font);
                int ty = ry + fm.getAscent();
                for (String line : lines) {
                    g.drawString(line, x + 16, ty);
                    ty += lineH;
                }
            }
            colY[col] += itemH + gap;
        }
        int maxColY = 0;
        for (int cy : colY) {
            maxColY = Math.max(maxColY, cy);
        }
        return y + maxColY + 10;
    }

    private static void sample(Graphics2D g, Item it, int x, int y) {
        g.setColor(it.fill());
        switch (it.shape()) {
            case "circle", "seat" -> g.fillOval(x, y, SAMPLE, SAMPLE);
            case "square" -> g.fillRect(x, y, SAMPLE, SAMPLE);
            default -> {
                java.awt.Polygon p = new java.awt.Polygon();
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(60 * k - 90
                        + kelium.report.FieldGeometry.TILT);
                    p.addPoint((int) (x + SAMPLE / 2.0 + SAMPLE / 2.0 * Math.cos(a)),
                        (int) (y + SAMPLE / 2.0 + SAMPLE / 2.0 * Math.sin(a)));
                }
                g.fillPolygon(p);
                g.setColor(it.edge());
                g.drawPolygon(p);
                return;
            }
        }
        g.setColor(it.edge());
        switch (it.shape()) {
            case "circle", "seat" -> g.drawOval(x, y, SAMPLE, SAMPLE);
            case "square" -> g.drawRect(x, y, SAMPLE, SAMPLE);
            default -> { }
        }
        if ("seat".equals(it.shape()) && it.letter() != null) {
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            var fm = g.getFontMetrics();
            int tx = x + (SAMPLE - fm.stringWidth(it.letter())) / 2;
            int ty = y + (SAMPLE + fm.getAscent()) / 2 - 2;
            g.drawString(it.letter(), tx, ty);
        }
    }

    /**
     * СЛОЖИТЬ ДВЕ ГОТОВЫЕ КАРТИНКИ в одну — поле и сборка друг над другом или
     * бок о бок ({@link Layout#VERTICAL}/{@link Layout#HORIZONTAL}). Обе уже
     * несут свои заголовки и легенды — здесь только общий фон и разделитель.
     */
    public static BufferedImage stack(BufferedImage first, BufferedImage second,
                                      boolean vertical) {
        int gap = 28;
        int w;
        int h;
        if (vertical) {
            w = Math.max(first.getWidth(), second.getWidth());
            h = first.getHeight() + gap + second.getHeight();
        } else {
            w = first.getWidth() + gap + second.getWidth();
            h = Math.max(first.getHeight(), second.getHeight());
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setColor(RULE);
        if (vertical) {
            g.drawImage(first, (w - first.getWidth()) / 2, 0, null);
            g.fillRect(28, first.getHeight() + gap / 2 - 1, w - 56, 2);
            g.drawImage(second, (w - second.getWidth()) / 2, first.getHeight() + gap, null);
        } else {
            g.drawImage(first, 0, (h - first.getHeight()) / 2, null);
            g.fillRect(first.getWidth() + gap / 2 - 1, 28, 2, h - 56);
            g.drawImage(second, first.getWidth() + gap, (h - second.getHeight()) / 2, null);
        }
        g.dispose();
        return out;
    }

    /**
     * Спросить путь и записать PNG. Возвращает сохранённый файл или null,
     * если пользователь передумал.
     */
    public static Path save(Component parent, String defaultName, BufferedImage img) {
        // ВАЖНО: диалог сам ищет окно по этому компоненту. Раньше сюда
        // передавался результат getWindowAncestor(frame) — для самого окна это
        // null, и диалог падал с NPE до появления окна. Теперь parent передаётся
        // как есть.
        Path start = Paths.get(System.getProperty("user.dir")).resolve(defaultName);
        Path path = PathDialog.choose(parent, "Сохранить картинку", start, true, "png");
        if (path == null) {
            return null;
        }
        if (!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            path = path.resolveSibling(path.getFileName() + ".png");
        }
        try {
            ImageIO.write(img, "png", path.toFile());
            return path;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                Ui.text("Не удалось сохранить картинку.\n\n" + e.getMessage(), 420),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Сообщить об удачном сохранении (одинаково для обеих вкладок). */
    public static void done(Component parent, Path path) {
        if (path == null) {
            return;
        }
        JOptionPane.showMessageDialog(parent,
            Ui.text("Картинка сохранена:\n" + path.toAbsolutePath(), 460),
            "Готово", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * СОХРАНИТЬ ДВА ФАЙЛА ОДНИМ ДЕЙСТВИЕМ ({@link Layout#SEPARATE}): путь
     * спрашивается один раз, второе имя выводится из первого приставкой перед
     * расширением («…-поле.png», «…-сборка.png»). Возвращает оба пути или null,
     * если пользователь передумал уже на первом диалоге.
     */
    public static Path[] saveTwo(Component parent, String defaultName,
                                 BufferedImage first, String firstSuffix,
                                 BufferedImage second, String secondSuffix) {
        Path start = Paths.get(System.getProperty("user.dir")).resolve(defaultName);
        Path chosen = PathDialog.choose(parent, "Сохранить картинки (поле и сборка)",
            start, true, "png");
        if (chosen == null) {
            return null;
        }
        String base = chosen.getFileName().toString();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            base = base.substring(0, base.length() - 4);
        }
        Path p1 = chosen.resolveSibling(base + firstSuffix + ".png");
        Path p2 = chosen.resolveSibling(base + secondSuffix + ".png");
        try {
            ImageIO.write(first, "png", p1.toFile());
            ImageIO.write(second, "png", p2.toFile());
            return new Path[]{p1, p2};
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                Ui.text("Не удалось сохранить картинки.\n\n" + e.getMessage(), 420),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Сообщить об удачном сохранении двух файлов сразу. */
    public static void doneTwo(Component parent, Path[] paths) {
        if (paths == null) {
            return;
        }
        JOptionPane.showMessageDialog(parent,
            Ui.text("Картинки сохранены:\n" + paths[0].toAbsolutePath()
                + "\n" + paths[1].toAbsolutePath(), 460),
            "Готово", JOptionPane.INFORMATION_MESSAGE);
    }
}
