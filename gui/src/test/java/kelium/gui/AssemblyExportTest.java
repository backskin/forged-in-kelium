package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/**
 * ЛЕГЕНДА СБОРКИ СЛУШАЕТСЯ ТЕХ ЖЕ НАСТРОЕК ЭКСПОРТА, ЧТО И ЛЕГЕНДА РАСКЛАДКИ
 * (просьба дизайнера 14.08.2026): раньше чекбокс «Общие обозначения» в окне
 * настроек экспорта действовал только на картинку поля, у картинки сборки
 * блоков легенда печаталась всегда без разбора.
 */
class AssemblyExportTest {

    private static LayoutEditor.Model smallField() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        for (int r = 0; r < 3; r++) {
            for (int q = 0; q < 3; q++) {
                m.hexes.put(LayoutEditor.Model.key(q, r), new LayoutEditor.LHex(q, r));
            }
        }
        m.get(0, 0).content = "player_start";
        m.get(0, 0).seat = 0;
        m.get(2, 2).content = "player_start";
        m.get(2, 2).seat = 1;
        return m;
    }

    private static AssemblyWindow assembledWindow() throws InterruptedException {
        AssemblyWindow w = new AssemblyWindow(smallField());
        w.refresh();
        for (int i = 0; i < 100 && !w.hasResult(); i++) {
            Thread.sleep(50);
        }
        assertTrue(w.hasResult(), "маленькое поле должно собраться быстро");
        return w;
    }

    @Test
    void legendCheckboxHidesAssemblyLegendToo() throws InterruptedException {
        AssemblyWindow w = assembledWindow();
        BufferedImage withLegend =
            w.exportImage(new PngExport.Options(true, true, true, PngExport.Layout.SEPARATE));
        BufferedImage withoutLegend =
            w.exportImage(new PngExport.Options(false, true, true, PngExport.Layout.SEPARATE));
        assertTrue(withLegend.getHeight() > withoutLegend.getHeight(),
            "без легенды картинка сборки должна быть короче");
    }

    /**
     * ЧЁРНЫЕ НАКЛАДКИ НЕ ОБРЕЗАЮТСЯ КРАЕМ КАРТИНКИ (баг дизайнера 14.08.2026:
     * «картинка на экспорте не вписывается»). Физический блок — фигура из 5–6
     * гексов; его лишние ячейки, торчащие за пределы нарисованного поля,
     * закрываются чёрной накладкой «недоступно» — а она лежит ЗА пределами
     * игровых гексов, и рамка картинки раньше её не учитывала.
     *
     * <p>Проверяем не геометрию подбора (это дело {@link BlockAssembler}), а то,
     * что если чёрные накладки ЕСТЬ, они попадают в кадр: у самого края картинки
     * (внешнее кольцо в несколько пикселей) не должно быть тёмных пикселей.
     */
    @Test
    void blackOverlayCellsStayInsideTheImageFrame() throws InterruptedException {
        AssemblyWindow w = assembledWindow();
        if (w.currentBlackCells().isEmpty()) {
            return;   // этому полю чёрные накладки не понадобились — нечего проверять
        }
        BufferedImage img = w.renderField(500, 400);
        java.awt.Color dark = new java.awt.Color(0x14171A);
        assertTrue(borderIsFreeOf(img, dark, 3),
            "чёрная накладка задевает самый край картинки — рамка её не учла");
    }

    private static boolean borderIsFreeOf(BufferedImage img, java.awt.Color target, int ring) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < ring; y++) {
                if (close(img.getRGB(x, y), target.getRGB())
                        || close(img.getRGB(x, h - 1 - y), target.getRGB())) {
                    return false;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < ring; x++) {
                if (close(img.getRGB(x, y), target.getRGB())
                        || close(img.getRGB(w - 1 - x, y), target.getRGB())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Совпадение с запасом в пару градаций — сглаживание может чуть смешать цвет. */
    private static boolean close(int rgb, int targetRgb) {
        java.awt.Color a = new java.awt.Color(rgb);
        java.awt.Color b = new java.awt.Color(targetRgb);
        return Math.abs(a.getRed() - b.getRed()) < 12
            && Math.abs(a.getGreen() - b.getGreen()) < 12
            && Math.abs(a.getBlue() - b.getBlue()) < 12;
    }

    @Test
    void fieldRendersAndHasContent() throws InterruptedException {
        AssemblyWindow w = assembledWindow();
        BufferedImage img = w.renderField(400, 300);
        int painted = 0;
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                if (img.getRGB(x, y) != img.getRGB(0, 0)) {
                    painted++;
                }
            }
        }
        assertTrue(painted > 20, "сборка должна быть нарисована, а не пустым фоном");
    }
}
