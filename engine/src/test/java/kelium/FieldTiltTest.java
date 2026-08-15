package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.report.FieldGeometry;

/**
 * ПОВОРОТ ПОЛЯ НА 30° — СТРАХОВКА. Поле развёрнуто (гексы «плашмя вверх»), и
 * важно, чтобы разворот был согласованным: центры гексов, углы сторон и вершины
 * повёрнуты на один и тот же угол. Если кто-то поправит одно и забудет другое,
 * жетоны начнут смотреть не на своих соседей — этот тест ловит именно такое.
 */
final class FieldTiltTest {

    /** Те же шесть направлений, что в движке ({@code Field.AXIAL_DIRS}). */
    private static final int[][] DIRS = {
        {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    @Test
    void сторона_смотрит_ровно_на_своего_соседа() {
        double size = 40;
        for (int s = 0; s < 6; s++) {
            double[] me = FieldGeometry.hexCenter(0, 0, size);
            double[] nb = FieldGeometry.hexCenter(DIRS[s][0], DIRS[s][1], size);
            double toNeighbour = Math.toDegrees(
                Math.atan2(nb[1] - me[1], nb[0] - me[0]));
            double diff = FieldGeometry.norm180(toNeighbour - FieldGeometry.edgeAngle(s));
            assertTrue(Math.abs(diff) < 1e-6,
                "сторона " + s + ": на соседа " + toNeighbour
                    + "°, а edgeAngle даёт " + FieldGeometry.edgeAngle(s) + "°");
        }
    }

    @Test
    void гексы_стоят_плашмя_вверх() {
        // «плашмя вверх» = есть вершина точно справа (угол 0°), а не сверху
        double[][] pts = FieldGeometry.hexCorners(0, 0, 10);
        boolean right = false;
        for (double[] p : pts) {
            if (Math.abs(p[0] - 10) < 1e-6 && Math.abs(p[1]) < 1e-6) {
                right = true;
            }
        }
        assertTrue(right, "у гекса нет вершины справа — значит он не «плашмя вверх»");
        assertEquals(30, FieldGeometry.TILT, 1e-9);
    }

    @Test
    void соседи_стоят_на_расстоянии_двух_апофем() {
        double size = 33;
        double[] me = FieldGeometry.hexCenter(3, -2, size);
        for (int[] d : DIRS) {
            double[] nb = FieldGeometry.hexCenter(3 + d[0], -2 + d[1], size);
            double dist = Math.hypot(nb[0] - me[0], nb[1] - me[1]);
            assertEquals(2 * FieldGeometry.apothem(size), dist, 1e-6);
        }
    }

    @Test
    void точка_под_курсором_возвращает_свой_гекс() {
        double size = 47;
        for (int q = -3; q <= 3; q++) {
            for (int r = -3; r <= 3; r++) {
                double[] c = FieldGeometry.hexCenter(q, r, size);
                int[] back = FieldGeometry.hexAt(c[0], c[1], size);
                assertEquals(q, back[0], "q для " + q + "," + r);
                assertEquals(r, back[1], "r для " + q + "," + r);
                // и точка у самой кромки тоже должна остаться в этом же гексе
                double[] near = FieldGeometry.polar(c[0], c[1],
                    FieldGeometry.apothem(size) * 0.9, 17);
                int[] back2 = FieldGeometry.hexAt(near[0], near[1], size);
                assertEquals(q, back2[0], "q у кромки " + q + "," + r);
                assertEquals(r, back2[1], "r у кромки " + q + "," + r);
            }
        }
    }
}
