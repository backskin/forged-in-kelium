package kelium.report;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
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
 * <p>ПОЧЕМУ ПРОТЯЖКА, А НЕ СМЕЩЁННАЯ КОПИЯ. Сперва силуэт клался под жетон
 * ОДИН раз со сдвигом — и дизайнер сразу это забраковал (09.09.2026): «тень не
 * протягивается вектором от крайней точки текстуры, чтобы создать эффект
 * толщины, вместо этого она просто смещается, и получается плоская картинка на
 * фоне плоской тени». И верно: у настоящей картонки между верхней гранью и
 * столом есть БОКОВАЯ СТЕНКА, то есть силуэт, ПРОТЯНУТЫЙ вдоль вектора света.
 * Здесь стенка и строится — силуэт рисуется много раз с шагом меньше пикселя
 * на всём пути от жетона до конца сдвига, и получается сплошной торец.
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

    /**
     * СКОЛЬКО РАЗ перерисовать силуэт, чтобы торец получился сплошным.
     *
     * <p>Шаг должен быть меньше пикселя, иначе стенка распадается на полоски.
     * Потолок нужен на случай гигантского зума: полсотни проходов уже никак не
     * видны, а время едят.
     *
     * @param длина длина вектора протяжки в тех же единицах, в которых рисуют
     */
    public static int шагов(double длина) {
        return Math.max(3, Math.min(48, (int) Math.ceil(длина * 2)));
    }

    /**
     * ПРОТЯНУТЬ СИЛУЭТ-КАРТИНКУ вектором {@code (dx, dy)} — боковая стенка
     * картонки. Рисуется от дальнего конца к жетону, поэтому сверху ляжет сам
     * жетон и стык не виден.
     *
     * @param на    преобразование, которым кладётся сам жетон
     * @param dx dy сдвиг «на стол» в ЭКРАННЫХ осях: свет не поворачивается
     *              вместе с жетоном
     */
    public static void блок(Graphics2D g, BufferedImage силуэт, AffineTransform на,
                            double dx, double dy) {
        if (g == null || силуэт == null) {
            return;
        }
        int n = шагов(Math.hypot(dx, dy));
        for (int i = n; i >= 1; i--) {
            AffineTransform шаг = new AffineTransform(на);
            шаг.preConcatenate(AffineTransform.getTranslateInstance(dx * i / n, dy * i / n));
            g.drawImage(силуэт, шаг, null);
        }
    }

    /**
     * То же для нарисованной фигуры: цвет уже выставлен в {@code g}.
     * Заливки идут ОДНИМ непрозрачным цветом — накладываясь, они не темнеют.
     */
    public static void блок(Graphics2D g, Shape контур, double dx, double dy) {
        if (g == null || контур == null) {
            return;
        }
        int n = шагов(Math.hypot(dx, dy));
        for (int i = n; i >= 1; i--) {
            g.fill(AffineTransform.getTranslateInstance(dx * i / n, dy * i / n)
                .createTransformedShape(контур));
        }
    }

    /** Цвет строкой {@code #RRGGBB} — в таком виде силуэт принимает краску. */
    public static String краска(Color c) {
        return c == null ? null : String.format("#%02X%02X%02X",
            c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Забыть накопленное — вызывается вместе со сбросом текстур. */
    public static void forget() {
        КЭШ.clear();
    }
}
