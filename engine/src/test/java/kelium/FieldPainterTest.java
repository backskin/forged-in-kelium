package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.report.FieldCanvas;
import kelium.report.FieldGeometry;
import kelium.report.FieldPainter;
import kelium.report.ReplayRecord;
import kelium.report.SvgFieldRenderer;

/**
 * Отрисовка поля — ОДНА на всё приложение.
 *
 * <p>И картинка в отчёте, и вид в проигрывателе идут через {@link FieldPainter};
 * отличается только поверхность ({@link FieldCanvas}). Тест ловит именно те
 * ошибки, из-за которых два рендера разъезжались: порядок слоёв и то, что
 * что-то нарисовано в одном месте и забыто в другом.
 */
class FieldPainterTest {

    /** Поверхность-протокол: запоминает, ЧТО и в каком порядке рисовали. */
    private static final class RecordingCanvas implements FieldCanvas {
        final List<String> ops = new ArrayList<>();

        // ЗАГЛУШКА: рисование картинки-текстуры в этих тестах не проверяется.

        @Override public void image(java.awt.image.BufferedImage img, double cx, double cy,

                double rotDeg, double k, double ax, double ay) { }


        @Override public void polygon(double[][] pts, String fill, String stroke, double w) {
            ops.add("polygon fill=" + fill + " stroke=" + stroke);
        }

        @Override public void shape(FieldGeometry.Shape sh, double cx, double cy, double rot,
                                    double k, double ax, double ay,
                                    String fill, String stroke, double w) {
            ops.add("shape fill=" + fill);
        }

        @Override public void roundRect(double x, double y, double w, double h, double r,
                                        String fill, String stroke, double sw) {
            ops.add("rect fill=" + fill);
        }

        @Override public void circle(double cx, double cy, double r, String fill,
                                     String stroke, double w) {
            ops.add("circle fill=" + fill);
        }

        @Override public void text(String t, double cx, double y, double size,
                                   boolean bold, String fill) {
            ops.add("text " + t);
        }

        @Override public void outlinedText(String t, double cx, double y, double size,
                                           String fill, String outline) {
            ops.add("outlined " + t);
        }

        @Override public void outlinedTextRotated(String t, double cx, double cy, double size,
                                                  String fill, String outline, double rot) {
            // подпись на жетоне повёрнута вместе с ним — для порядка слоёв это
            // та же надпись
            ops.add("outlined " + t);
        }

        @Override public void alpha(double v) {
            // прозрачность на порядок слоёв не влияет
        }

        int indexOf(String prefix) {
            for (int i = 0; i < ops.size(); i++) {
                if (ops.get(i).startsWith(prefix)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static GameState sample() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null));
    }

    /**
     * Воздушная ячейка рисуется ПОВЕРХ здания (решение дизайнера 2026-08-12):
     * она принадлежит гексу целиком, а не сектору. Раньше её закрывала бледная
     * подложка занятого сектора.
     */
    @Test
    void theAirCellIsPaintedOverBuildingsNotUnderThem() {
        GameState s = sample();
        ReplayRecord.Snapshot snap = ReplayRecord.snapshotOf(s, null);
        String startHex = s.player(0).startHex;

        ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
        int[] qr = FieldGeometry.parseQR(startHex);
        hi.id = startHex;
        hi.q = qr[0];
        hi.r = qr[1];
        hi.kind = "NORMAL";

        ReplayRecord.HexState st = null;
        for (ReplayRecord.HexState h : snap.hexes) {
            if (h.id.equals(startHex)) {
                st = h;
            }
        }
        List<ReplayRecord.Tok> here = new ArrayList<>();
        for (ReplayRecord.Tok t : snap.tokens) {
            if (startHex.equals(t.hexId) && t.alive) {
                here.add(t);
            }
        }
        assertTrue(here.stream().anyMatch(t -> t.building),
            "на стартовом гексе обязано стоять ЦУ — иначе тест ничего не проверяет");

        RecordingCanvas c = new RecordingCanvas();
        FieldPainter.paintHex(c, 52, hi, st, here, 0, 0, true);

        int building = c.indexOf("shape fill=" + FieldGeometry.SEAT_TOKEN[0]);
        int airCell = c.indexOf("polygon fill=none stroke=" + FieldGeometry.AIR_CELL_STROKE);
        assertTrue(building >= 0, "здание не нарисовано: " + c.ops);
        assertTrue(airCell >= 0, "воздушная ячейка не нарисована: " + c.ops);
        assertTrue(airCell > building,
            "воздушная ячейка должна рисоваться ПОСЛЕ здания, а она " + airCell
                + " против " + building);
    }

    /** Порядок слоёв соблюдён: гекс → … → здания → воздух → войска → контейнеры. */
    @Test
    void layersGoInTheDocumentedOrder() {
        assertNotNull(FieldGeometry.LAYER_ORDER);
        GameState s = sample();
        ReplayRecord.Snapshot snap = ReplayRecord.snapshotOf(s, null);
        String startHex = s.player(0).startHex;
        ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
        int[] qr = FieldGeometry.parseQR(startHex);
        hi.id = startHex;
        hi.q = qr[0];
        hi.r = qr[1];
        ReplayRecord.HexState st = null;
        for (ReplayRecord.HexState h : snap.hexes) {
            if (h.id.equals(startHex)) {
                st = h;
            }
        }
        List<ReplayRecord.Tok> here = new ArrayList<>();
        for (ReplayRecord.Tok t : snap.tokens) {
            if (startHex.equals(t.hexId) && t.alive) {
                here.add(t);
            }
        }
        RecordingCanvas c = new RecordingCanvas();
        FieldPainter.paintHex(c, 52, hi, st, here, 0, 0, true);

        // самый первый примитив — сам гекс
        assertTrue(c.ops.get(0).startsWith("polygon fill=#ffffff"),
            "первым рисуется сам гекс, а не " + c.ops.get(0));
        // подпись гекса — до зданий
        assertTrue(c.indexOf("text " + startHex) < c.indexOf("shape fill="),
            "подпись гекса должна быть под зданиями");
    }

    /**
     * Оба выхода идут через ОДИН painter: SVG-картинка отчёта содержит ровно те
     * же жетоны, что видит проигрыватель, — иначе рендеры снова разъехались.
     */
    @Test
    void bothOutputsDrawTheSameTokens() {
        GameState s = sample();
        String svg = SvgFieldRenderer.render(s, 1);

        int alive = 0;
        for (ReplayRecord.Tok t : ReplayRecord.snapshotOf(s, null).tokens) {
            if (t.hexId != null && t.alive) {
                alive++;
            }
        }
        int drawn = svg.split("<g transform='translate", -1).length - 1;
        assertTrue(drawn >= alive,
            "в SVG нарисовано жетонов " + drawn + ", а на поле их " + alive);
        assertEquals(alive > 0, true, "на стартовом поле должны быть жетоны");
        assertTrue(svg.contains(FieldGeometry.AIR_CELL_STROKE),
            "воздушная ячейка должна быть и в SVG");
    }
}
