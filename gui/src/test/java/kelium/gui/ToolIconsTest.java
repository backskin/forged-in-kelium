package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;

/**
 * РИСОВАННЫЕ ИКОНКИ ИНСТРУМЕНТОВ вместо эмодзи (замечание дизайнера 12.08.2026:
 * «у меня в интерфейсе эти иконки не видятся» — Swing показывал пустые квадраты,
 * потому что цветных шрифтов Java2D не поддерживает).
 *
 * <p>Проверяем то, ради чего иконки переделаны: каждая ЧТО-ТО рисует, все они
 * РАЗНЫЕ (иначе кнопки не различить), рисунок ЦВЕТНОЙ и не вылезает за коробку —
 * тогда подписи кнопок стоят ровным столбцом.
 */
class ToolIconsTest {

    private static final List<String> CODES = List.of(
        "ADD", "CLEAR_HEX", "PLAYER", "SPAWN_START", "KELIUM", "STACK",
        "KELIUM_DELTA", "FORBIDDEN", "NEUTRAL_SMALL", "NEUTRAL_BIG", "BLOCKS", "PNG");

    /** Отрисовать иконку на белом поле с запасом, вернуть картинку. */
    private static BufferedImage render(String code, int pad) {
        Icon icon = ToolIcons.of(code);
        int w = icon.getIconWidth() + pad * 2;
        int h = icon.getIconHeight() + pad * 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        icon.paintIcon(null, g, pad, pad);
        g.dispose();
        return img;
    }

    @Test
    void everyIconDrawsSomething() {
        for (String code : CODES) {
            BufferedImage img = render(code, 4);
            int ink = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                        ink++;
                    }
                }
            }
            assertTrue(ink > 30, "иконка " + code + " почти пустая: " + ink + " пикселей");
        }
    }

    @Test
    void iconsAreDistinguishableFromEachOther() {
        Map<Long, String> seen = new LinkedHashMap<>();
        for (String code : CODES) {
            long sig = signature(render(code, 4));
            String twin = seen.put(sig, code);
            assertTrue(twin == null,
                "иконки " + code + " и " + twin + " выглядят одинаково");
        }
        // самая важная пара: малое зарождение против старта игрока — именно их
        // дизайнер путал, пока это были эмодзи
        assertNotEquals(signature(render("SPAWN_START", 4)), signature(render("PLAYER", 4)),
            "малое зарождение и старт игрока обязаны различаться");
    }

    @Test
    void iconsAreColouredNotJustBlackAndWhite() {
        for (String code : List.of("PLAYER", "SPAWN_START", "KELIUM", "KELIUM_DELTA")) {
            BufferedImage img = render(code, 4);
            boolean colour = false;
            for (int y = 0; y < img.getHeight() && !colour; y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int gg = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (Math.abs(r - gg) > 24 || Math.abs(gg - b) > 24) {
                        colour = true;
                        break;
                    }
                }
            }
            assertTrue(colour, "иконка " + code + " должна быть цветной");
        }
    }

    @Test
    void nothingSpillsOutsideTheBox() {
        int pad = 6;
        for (String code : CODES) {
            BufferedImage img = render(code, pad);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    boolean inside = x >= pad && x < pad + ToolIcons.SIZE
                        && y >= pad && y < pad + ToolIcons.SIZE;
                    if (!inside) {
                        assertEquals(0xFFFFFF, img.getRGB(x, y) & 0xFFFFFF,
                            "иконка " + code + " вылезла за коробку в точке " + x + "," + y);
                    }
                }
            }
        }
    }

    /** Грубая подпись картинки: сумма цветов с позиционным весом. */
    private static long signature(BufferedImage img) {
        long sig = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                sig = sig * 31 + (img.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return sig;
    }
}
