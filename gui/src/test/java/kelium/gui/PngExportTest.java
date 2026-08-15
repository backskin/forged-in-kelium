package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.gui.LayoutEditor.LHex;
import kelium.gui.LayoutEditor.Model;

/**
 * Выгрузка картинок из конструктора (просьба дизайнера 12.08.2026): раскладка
 * с ЛЕГЕНДОЙ под полем и сборка из блоков — обе в PNG.
 *
 * <p>Проверяем не «красиво ли», а что картинка действительно нарисована: поле
 * попало в кадр (есть непустые пиксели), легенда занимает место под полем,
 * и рисование не портит текущий вид на экране (зум и панорама сохраняются).
 */
class PngExportTest {

    private static Model smallField() {
        Model m = new Model();
        for (int r = 0; r < 3; r++) {
            for (int q = 0; q < 4; q++) {
                m.hexes.put(Model.key(q, r), new LHex(q, r));
            }
        }
        m.get(0, 0).content = "player_start";
        m.get(0, 0).seat = 0;
        m.get(3, 2).content = "player_start";
        m.get(3, 2).seat = 1;
        m.get(1, 1).content = "kelium_tile";
        return m;
    }

    /** Сколько пикселей отличается от фонового цвета. */
    private static int painted(BufferedImage img, Color bg) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                if (img.getRGB(x, y) != bg.getRGB()) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void layoutIsRenderedAndViewStateIsRestored() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = smallField();
        double size = canvas.size;
        double panX = canvas.panX;
        double panY = canvas.panY;

        BufferedImage img = canvas.render(600, 420);
        assertEquals(600, img.getWidth());
        assertEquals(420, img.getHeight());
        assertTrue(painted(img, canvas.getBackground()) > 500,
            "поле должно быть нарисовано, а не остаться пустым фоном");

        assertEquals(size, canvas.size, 1e-9, "экранный масштаб не должен меняться");
        assertEquals(panX, canvas.panX, 1e-9, "экранная панорама не должна меняться");
        assertEquals(panY, canvas.panY, 1e-9, "экранная панорама не должна меняться");
    }

    @Test
    void legendIsPlacedUnderTheField() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = smallField();
        BufferedImage field = canvas.render(600, 420);

        List<PngExport.Item> legend = List.of(
            PngExport.Item.hex(new Color(0xEFEDE4), "обычный гекс"),
            PngExport.Item.circle(new Color(0x3b82d0), "старт игрока"),
            PngExport.Item.square(new Color(0x9AA0A6), "нейтральное здание"));

        BufferedImage img = PngExport.compose("Раскладка", "подпись", field,
            PngExport.Content.legendOnly(legend));
        assertTrue(img.getHeight() > field.getHeight() + 80,
            "под полем обязано остаться место под заголовок и легенду");
        assertTrue(img.getWidth() >= field.getWidth());

        // нижняя полоса — это легенда, она не может быть чистым фоном
        BufferedImage tail = img.getSubimage(0, img.getHeight() - 70, img.getWidth(), 70);
        assertTrue(painted(tail, Color.WHITE) > 30, "легенда должна быть нарисована");
    }

    @Test
    void composeWithoutLegendStillWorks() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = smallField();
        BufferedImage field = canvas.render(300, 240);
        BufferedImage img = PngExport.compose("Без легенды", null, field,
            PngExport.Content.legendOnly(List.of()));
        assertTrue(img.getHeight() >= field.getHeight());
    }

    @Test
    void playersAndStatsSectionsGrowTheImageAndArePainted() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        Model m = smallField();
        canvas.model = m;
        BufferedImage field = canvas.render(600, 420);

        PngExport.Content withExtras = new PngExport.Content(
            List.of(), LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));
        PngExport.Content legendOnly = PngExport.Content.legendOnly(List.of());

        BufferedImage full = PngExport.compose("Раскладка", null, field, withExtras);
        BufferedImage bare = PngExport.compose("Раскладка", null, field, legendOnly);

        assertTrue(full.getHeight() > bare.getHeight(),
            "игроки и статистика должны занимать место, которого нет без них");
        BufferedImage tail = full.getSubimage(0, bare.getHeight(),
            full.getWidth(), full.getHeight() - bare.getHeight());
        assertTrue(painted(tail, Color.WHITE) > 30,
            "добавленная область должна быть нарисована, а не пустым фоном");
    }

    @Test
    void playerBlocksHaveOneEntryPerSeatWithDistinctColors() {
        Model m = smallField();
        List<PngExport.PlayerBlock> blocks = LayoutEditor.playerBlocks(m);
        assertEquals(2, blocks.size(), "два старта на карте — два блока игроков");
        assertTrue(blocks.get(0).lines().size() >= 1, "у блока есть хотя бы один показатель");
        org.junit.jupiter.api.Assertions.assertNotEquals(
            blocks.get(0).color(), blocks.get(1).color(), "цвета разных мест не совпадают");
    }

    @Test
    void mapStatsListsHexCountAndStartDistance() {
        Model m = smallField();
        List<String> stats = LayoutEditor.mapStats(m);
        assertTrue(stats.stream().anyMatch(s -> s.startsWith("гексов: " + m.hexes.size())));
        assertTrue(stats.stream().anyMatch(s -> s.contains("расстояние между стартами")),
            "при двух и более стартах должно быть расстояние между ними");
    }

    @Test
    void longLegendTextWrapsInsteadOfOverflowingTheImage() {
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = smallField();
        BufferedImage field = canvas.render(600, 420);

        // Заведомо длинная фраза без переносов вручную — раньше drawString
        // просто рисовал её одной строкой, и она вылезала за правый край
        // картинки (жалоба дизайнера 14.08.2026).
        String longText = "очень длинное описание обозначения, которое ни при "
            + "каких условиях не должно уместиться в одну строку данной ширины";
        List<PngExport.Item> legend = List.of(PngExport.Item.hex(Color.WHITE, longText));

        BufferedImage narrow = PngExport.compose("Раскладка", null, field,
            PngExport.Content.legendOnly(legend));

        // Ничего не нарисовано за правым краем картинки — если бы строка
        // вылезала, обрезка Graphics2D всё равно её бы не показала, поэтому
        // проверяем то, что реально доступно снаружи: высота обязана вырасти
        // под многострочный текст (одна строка такой длины заведомо не влезла
        // бы даже в широкую картинку).
        BufferedImage oneShortLine = PngExport.compose("Раскладка", null, field,
            PngExport.Content.legendOnly(List.of(PngExport.Item.hex(Color.WHITE, "гекс"))));
        assertTrue(narrow.getHeight() > oneShortLine.getHeight(),
            "перенесённый по словам длинный текст занимает больше одной строки");
    }

    @Test
    void stackVerticalPutsSecondImageBelowTheFirst() {
        BufferedImage top = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage bottom = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = PngExport.stack(top, bottom, true);
        assertEquals(300, out.getWidth(), "ширина — по более широкой картинке");
        assertTrue(out.getHeight() > top.getHeight() + bottom.getHeight(),
            "высота — сумма обеих плюс разделитель");
    }

    @Test
    void stackHorizontalPutsSecondImageBesideTheFirst() {
        BufferedImage left = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage right = new BufferedImage(150, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = PngExport.stack(left, right, false);
        assertEquals(300, out.getHeight(), "высота — по более высокой картинке");
        assertTrue(out.getWidth() > left.getWidth() + right.getWidth(),
            "ширина — сумма обеих плюс разделитель");
    }

    @Test
    void optionsDefaultLayoutIsSeparateForBackwardCompatibleConstructor() {
        PngExport.Options o = new PngExport.Options(true, false, true);
        assertEquals(PngExport.Layout.SEPARATE, o.layout(),
            "старый трёхпольный конструктор — раскладка по умолчанию «раздельно»");
    }

    @Test
    void filteredOptionsDropExactlyTheUncheckedSections() {
        Model m = smallField();
        PngExport.Content all = new PngExport.Content(
            List.of(PngExport.Item.hex(Color.WHITE, "x")),
            LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));

        PngExport.Content onlyPlayers = all.filtered(new PngExport.Options(false, true, false));
        assertTrue(onlyPlayers.legend().isEmpty());
        assertEquals(all.players(), onlyPlayers.players());
        assertTrue(onlyPlayers.mapStats().isEmpty());
    }
}
