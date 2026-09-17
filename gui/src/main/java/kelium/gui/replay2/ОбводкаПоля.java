package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import kelium.core.Field;
import kelium.report.FieldGeometry;

/**
 * ЖИРНАЯ ОБВОДКА СЛОЖЕННОГО ПОЛЯ — по внешнему краю, а не по каждому гексу.
 *
 * <p>Зачем. На картинках книги кусок поля сливался с фоном полосы: гексы
 * светлые, подложка страницы светлая, и где кончается стол — не видно вовсе
 * (замечание дизайнера 16.09.2026: «нихуя не видно границ у поля»). На столе
 * границу видно, потому что там кончается картон; на бумаге её надо нарисовать.
 *
 * <p>Обводится ТОЛЬКО внешний контур: ребро, у которого нет соседнего гекса
 * сцены. Внутренние стыки не трогаются — на столе их не видно, блоки лежат
 * встык.
 */
public final class ОбводкаПоля {

    private ОбводкаПоля() {
    }

    /** Цвет кромки — та же тёмная охра, что у кайм и подписей книги. */
    private static final Color КРОМКА = new Color(0x2A, 0x23, 0x18);

    /**
     * Обвести внешний край поля.
     *
     * @param клетки осевые координаты гексов сцены, по паре {q, r}
     * @param size   радиус гекса в точках
     * @param cx0    сдвиг начала координат по x
     * @param cy0    сдвиг по y
     */
    public static void нарисовать(Graphics2D g, Collection<int[]> клетки, double size,
                                  double cx0, double cy0) {
        Set<String> есть = new LinkedHashSet<>();
        for (int[] qr : клетки) {
            есть.add(qr[0] + "_" + qr[1]);
        }
        Path2D контур = new Path2D.Double();
        for (int[] qr : клетки) {
            double[] c = FieldGeometry.hexCenter(qr[0], qr[1], size);
            for (int s = 0; s < 6; s++) {
                int[] d = Field.AXIAL_DIRS[s];
                if (есть.contains((qr[0] + d[0]) + "_" + (qr[1] + d[1]))) {
                    continue;                       // внутренний стык — не кромка
                }
                double a = FieldGeometry.edgeAngle(s);
                double[] p1 = FieldGeometry.polar(cx0 + c[0], cy0 + c[1], size, a - 30);
                double[] p2 = FieldGeometry.polar(cx0 + c[0], cy0 + c[1], size, a + 30);
                контур.moveTo(p1[0], p1[1]);
                контур.lineTo(p2[0], p2[1]);
            }
        }
        g.setColor(КРОМКА);
        g.setStroke(new BasicStroke((float) Math.max(2.0, size * 0.035),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(контур);
    }
}
