package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.report.FieldCanvas;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.ReplayRecord;

/**
 * ЭНЕРГИЯ НА ЖЕТОНЕ (просьба дизайнера 12.08.2026): ячейки — чёрные квадраты,
 * кубики стоят ровно в них, и НИЧЕГО не вылезает за жетон.
 *
 * <p>Проверяем главное, что раньше ломалось: все ячейки, кубики и площадка
 * хранения лежат внутри гекса с отступом от края. Ловим это подставным
 * холстом, который просто запоминает нарисованные прямоугольники.
 */
class EnergyOnTokenTest {

    /** Холст-соглядатай: запоминает все точки, которых коснулся рисовальщик. */
    private static final class Spy implements FieldCanvas {
        final List<double[]> points = new ArrayList<>();

        private void mark(double x, double y) {
            points.add(new double[]{x, y});
        }

        // ЗАГЛУШКА: рисование картинки-текстуры в этих тестах не проверяется.

        @Override public void image(java.awt.image.BufferedImage img, double cx, double cy,

                double rotDeg, double k, double ax, double ay) { }


        @Override public void polygon(double[][] pts, String fill, String stroke, double w) {
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < pts.length; i++) {
                if (i == 0) {
                    p.moveTo(pts[i][0], pts[i][1]);
                } else {
                    p.lineTo(pts[i][0], pts[i][1]);
                }
            }
            p.closePath();
            markPainted(p);
        }

        @Override public void roundRect(double x, double y, double w, double h, double r,
                                       String fill, String stroke, double sw) {
            mark(x, y);
            mark(x + w, y + h);
        }

        @Override public void circle(double x, double y, double r, String f, String s, double w) {
            mark(x - r, y - r);
            mark(x + r, y + r);
        }

        @Override public void text(String t, double cx, double by, double size,
                                   boolean bold, String fill) {
            mark(cx, by);
        }

        @Override public void outlinedText(String t, double cx, double by, double size,
                                           String fill, String outline) {
            mark(cx, by);
        }

        @Override public void outlinedTextRotated(String t, double cx, double cy, double size,
                                                  String fill, String outline, double rot) {
            mark(cx, cy);
        }

        /** Силуэт жетона — по нему проверяем, что напечатанное с него не сползло. */
        java.awt.geom.Area token;

        @Override public void shape(FieldGeometry.Shape sh, double cx, double cy, double rot,
                                    double scale, double ax, double ay, String fill,
                                    String stroke, double sw) {
            mark(cx, cy);
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(cx, cy);
            at.rotate(Math.toRadians(rot));
            at.scale(scale, scale);
            at.translate(-ax, -ay);
            token = new java.awt.geom.Area(at.createTransformedShape(sh.path()));
        }

        @Override public void alpha(double a) {
        }

        /**
         * ОБРЕЗКА УЧИТЫВАЕТСЯ ПО-НАСТОЯЩЕМУ. Зона свободной энергии и штриховка
         * рисуются с запасом и обрезаются силуэтом жетона, поэтому проверять надо
         * не исходные фигуры, а то, что от них осталось: холст пересекает каждую
         * фигуру с действующей обрезкой и запоминает только видимые точки.
         */
        private final java.util.Deque<java.awt.geom.Area> clips = new java.util.ArrayDeque<>();

        private void markPainted(java.awt.geom.Path2D p) {
            java.awt.geom.Area area = new java.awt.geom.Area(p);
            if (!clips.isEmpty()) {
                area.intersect(clips.peek());
            }
            double[] c = new double[6];
            for (java.awt.geom.PathIterator it = area.getPathIterator(null);
                    !it.isDone(); it.next()) {
                int kind = it.currentSegment(c);
                if (kind != java.awt.geom.PathIterator.SEG_CLOSE) {
                    mark(c[0], c[1]);
                }
            }
        }

        private void push(java.awt.geom.Area a) {
            if (!clips.isEmpty()) {
                a.intersect(clips.peek());
            }
            clips.push(a);
        }

        @Override public void clipTo(double[][] pts) {
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            for (int i = 0; i < pts.length; i++) {
                if (i == 0) {
                    p.moveTo(pts[i][0], pts[i][1]);
                } else {
                    p.lineTo(pts[i][0], pts[i][1]);
                }
            }
            p.closePath();
            push(new java.awt.geom.Area(p));
        }

        @Override public void clipToShape(FieldGeometry.Shape sh, double cx, double cy,
                                          double rot, double k, double ax, double ay) {
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(cx, cy);
            at.rotate(Math.toRadians(rot));
            at.scale(k, k);
            at.translate(-ax, -ay);
            push(new java.awt.geom.Area(at.createTransformedShape(sh.path())));
        }

        @Override public void clipOff() {
            if (!clips.isEmpty()) {
                clips.pop();
            }
        }
    }

    /**
     * Точка внутри гекса? Проверяем по всем шести сторонам: для выпуклой фигуры
     * это точно. Прежний тест сравнивал расстояние с апофемой, то есть требовал
     * попадания во ВПИСАННУЮ окружность, — но у жетона, прижатого к кромке, углы
     * законно лежат дальше неё, ближе к вершинам гекса.
     */
    private static boolean insideHex(double x, double y, double size) {
        double apothem = kelium.report.FieldGeometry.apothem(size);
        for (int s = 0; s < 6; s++) {
            double a = Math.toRadians(kelium.report.FieldGeometry.edgeAngle(s));
            if (x * Math.cos(a) + y * Math.sin(a) > apothem) {
                return false;
            }
        }
        return true;
    }

    private static ReplayRecord.Tok building(String type, int slots, int placed, int idle) {
        ReplayRecord.Tok t = new ReplayRecord.Tok();
        t.building = true;
        t.type = type;
        t.owner = 0;
        t.hp = 2;
        t.energySlots = slots;
        t.energyPlaced = placed;
        t.energyIdle = idle;
        return t;
    }

    /** Самая дальняя точка ТОЛЬКО слоя энергии: разница «с ним» и «без него». */
    private static double[] energyLayerWorst(ReplayRecord.Tok b, int span, double size) {
        FieldPainter.showEnergy = false;
        Spy without = new Spy();
        paint(without, b, span, size);
        int base = without.points.size();

        FieldPainter.showEnergy = true;
        Spy with = new Spy();
        paint(with, b, span, size);

        double[] worst = new double[]{0, 0};
        for (int i = base; i < with.points.size(); i++) {
            double[] p = with.points.get(i);
            // Точка внутри силуэта законна по определению: там и печатают. Берём
            // не contains, а пересечение с крошечным квадратом: после обрезки
            // вершины ЛЕЖАТ РОВНО НА кромке, а для contains граница — уже «снаружи».
            if (with.token != null && with.token.intersects(p[0] - 0.5, p[1] - 0.5, 1, 1)) {
                continue;
            }
            if (Math.hypot(p[0], p[1]) > Math.hypot(worst[0], worst[1])) {
                worst = p;
            }
        }
        return worst;
    }

    private static void paint(Spy spy, ReplayRecord.Tok b, int span, double size) {
        ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
        hi.id = "h0_0";
        hi.kind = "NORMAL";
        ReplayRecord.HexState st = new ReplayRecord.HexState();
        st.id = "h0_0";
        for (int i = 0; i < span; i++) {
            st.sideOwner[i] = b.uid;
        }
        FieldPainter.paintHex(spy, size, hi, st, List.of(b), 0, 0, false);
    }

    @Test
    void energySlotsAndCubesStayInsideTheToken() {
        double size = 60;
        // Ячейки, кубики и зона свободной энергии обязаны лежать ВНУТРИ ГЕКСА:
        // это и есть «на поверхности жетона, не за краем».
        for (int span = 1; span <= 3; span++) {
            for (ReplayRecord.Tok b : List.of(
                    building("barracks", 1, 1, 0),
                    building("factory", 2, 1, 0),
                    building("airbase", 3, 0, 0),
                    building("miner", 2, 2, 0),
                    building("power_plant", 0, 0, 3),
                    building("power_plant", 0, 0, 5),
                    building("command_center", 1, 1, 1))) {
                double[] worst = energyLayerWorst(b, span, size);
                assertTrue(insideHex(worst[0], worst[1], size),
                    b.type + " (клин " + span + ", ячеек " + b.energySlots
                        + ", запас " + b.energyIdle + "): энергия вылезла за гекс в точке "
                        + Math.round(worst[0]) + "," + Math.round(worst[1]));
            }
        }
    }

    @Test
    void everythingRendersInsideTheHex() {
        double size = 60;
        for (int span = 1; span <= 3; span++) {
            Spy spy = new Spy();
            paint(spy, building("command_center", 1, 1, 1), span, size);
            for (double[] p : spy.points) {
                assertTrue(Math.hypot(p[0], p[1]) <= size * 1.02,
                    "рисование вышло за пределы гекса");
            }
        }
    }

    @Test
    void sourcesShowTheirIdleCubes() {
        // У станции без ячеек рисовать нечего, кроме зоны хранения: если
        // простаивающие кубики не показывать, картинка молчит о запасе энергии.
        Spy empty = new Spy();
        ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
        hi.id = "h0_0";
        hi.kind = "NORMAL";
        ReplayRecord.HexState st = new ReplayRecord.HexState();
        st.id = "h0_0";
        st.sideOwner[0] = 0;
        FieldPainter.paintHex(empty, 60, hi, st, List.of(building("power_plant", 0, 0, 0)),
            0, 0, false);
        int withoutIdle = empty.points.size();

        Spy full = new Spy();
        FieldPainter.paintHex(full, 60, hi, st, List.of(building("power_plant", 0, 0, 3)),
            0, 0, false);
        assertTrue(full.points.size() > withoutIdle,
            "простаивающие кубики обязаны появляться на жетоне источника");
    }
}
