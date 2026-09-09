package kelium.report;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ТОЛЩИНА КАРТОНКИ — силуэт жетона одним цветом, чтобы положить его под жетон.
 *
 * <p>Заказ дизайнера 08.09.2026: «добавь жетонам войск эффект блока с тенью —
 * это такой эффект толщины, что это объёмный жетон, а не наклейка. Отступ
 * небольшой, и точно в цвет рамки самого жетона».
 *
 * <p>ПОЧЕМУ СИЛУЭТ КАРТИНКИ, А НЕ ФИГУРА РОДА ВОЙСК. Сперва бортик рисовался
 * силуэтом танка или самолёта — той же фигурой, которой рисуется жетон без
 * текстуры. Но печатный жетон это КАРТОНКА со скруглёнными углами, и из-под
 * почти квадратной картинки торчал цветной пятиугольник: читалось как брак
 * печати, а не как толщина. Здесь силуэт берётся у самой картинки — по её
 * непрозрачности, — поэтому бортик повторяет ровно ту форму, которая лежит
 * сверху, и правильно едет при повороте.
 *
 * <p>Силуэты кэшируются: одна и та же картинка с одним и тем же цветом рисуется
 * десятки раз за кадр.
 */
public final class ТеньЖетона {

    private ТеньЖетона() {
    }

    /** Порог непрозрачности: всё, что заметнее этого, считается телом жетона. */
    private static final int ПОРОГ = 24;

    private static final Map<String, BufferedImage> КЭШ = new ConcurrentHashMap<>();

    /**
     * Силуэт картинки, залитый цветом {@code rgb} (формата {@code #RRGGBB}).
     *
     * @return новая картинка того же размера; {@code null}, если нечего красить
     */
    public static BufferedImage силуэт(BufferedImage src, String rgb) {
        if (src == null || rgb == null || rgb.isBlank()) {
            return null;
        }
        String ключ = System.identityHashCode(src) + "@" + rgb;
        BufferedImage готов = КЭШ.get(ключ);
        if (готов != null) {
            return готов;
        }
        int цвет;
        try {
            цвет = Integer.parseInt(rgb.startsWith("#") ? rgb.substring(1) : rgb, 16);
        } catch (NumberFormatException неЦвет) {
            return null;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] строка = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, строка, 0, w);
            for (int x = 0; x < w; x++) {
                int a = (строка[x] >>> 24) & 0xFF;
                // ПОЛУПРОЗРАЧНЫЙ КРАЙ ОСТАЁТСЯ ПОЛУПРОЗРАЧНЫМ: иначе у бортика
                // появляется зубчатая кромка, и весь смысл (аккуратная толщина)
                // пропадает.
                строка[x] = a < ПОРОГ ? 0 : (a << 24) | цвет;
            }
            out.setRGB(0, y, w, 1, строка, 0, w);
        }
        // Кэш не растёт бесконечно: картинок жетонов десятки, цветов мест
        // четыре, и пересоздаются они только при смене набора текстур.
        if (КЭШ.size() > 256) {
            КЭШ.clear();
        }
        КЭШ.put(ключ, out);
        return out;
    }

    /** Забыть накопленное — вызывается вместе со сбросом текстур. */
    public static void forget() {
        КЭШ.clear();
    }
}
