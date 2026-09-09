package kelium.report;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Сглаживание — ОДИН НАБОР ПОДСКАЗОК РИСОВАНИЯ НА ВСЮ ПРОГРАММУ.
 *
 * <p>ЗАЧЕМ. Java2D по умолчанию рисует грубо: линии ступеньками, а картинку
 * при сжатии берёт «ближайшим соседом» — от печатного планшета остаётся рябь.
 * Раньше каждое окно включало сглаживание само, и получалось вразнобой: где-то
 * стояли две подсказки из пяти, где-то ни одной. Дизайнер это видел прямо на
 * планшете игрока (замечание 09.09.2026: «на планшете игрока и других окнах
 * сглаживание не применяется, а хотелось бы чтобы везде было на всей графике»).
 *
 * <p>ПОЭТОМУ подсказки собраны сюда, и любое рисование начинается с вызова
 * {@link #включить(Graphics2D)}. Правится в одном месте — меняется везде.
 *
 * <p>Что именно включается:
 * <ul>
 *   <li>сглаживание фигур и текста — края без ступенек;
 *   <li>билинейный фильтр картинок — при сжатии пиксели усредняются, а не
 *       выбрасываются (сильное сжатие всё равно лучше вести через
 *       {@link Mips}: билинейный берёт лишь четыре соседних пикселя);
 *   <li>качество вместо скорости в общем режиме и в смешивании прозрачности;
 *   <li>честные координаты линий (STROKE_PURE) — иначе тонкие рамки прыгают
 *       на полпикселя и выглядят разной толщины.
 * </ul>
 */
public final class Сглаживание {

    private Сглаживание() {
    }

    /** Включить полный набор подсказок на этом холсте. */
    public static void включить(Graphics2D g) {
        if (g == null) {
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
            RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
            RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
            RenderingHints.VALUE_STROKE_PURE);
    }
}
