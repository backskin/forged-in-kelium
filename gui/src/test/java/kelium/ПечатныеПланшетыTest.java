package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.replay2.BoardAnchors;
import kelium.report.Textures;

/**
 * ПЕЧАТНЫЕ ПЛАНШЕТЫ СТОЛА — научный отдел и рынок.
 *
 * <p>На экране «наука и рынок» показываются САМИ картинки компонентов, а поверх
 * них кладутся кубики шагов и активная карта рынка. Промахнуться тут легко и
 * незаметно: кубик уедет на полсантиметра от напечатанной ячейки, и понять это
 * можно будет только глазом на снимке. Поэтому машинно проверяется то, что
 * машина проверить может:
 *
 * <ul>
 *   <li>ячеек по сетке ровно столько, сколько их в СВОДе ({@code tech.step_capacity});
 *   <li>каждая ячейка целиком лежит на картинке, а не свисает с края;
 *   <li>ячейки одного шага не наезжают друг на друга;
 *   <li>рамка карты рынка — арсенального формата (68×44), иначе карта в ней
 *       растянется.
 * </ul>
 *
 * <p>Сходятся ли якоря с ПЕЧАТЬЮ — проверяет генератор
 * {@code tools/gen_anchors.py}: он смотрит на сами пиксели.
 */
class ПечатныеПланшетыTest {

    private static BoardAnchors.Science science() {
        BoardAnchors.Science sc = BoardAnchors.science();
        assertNotNull(sc, "якоря планшета науки не прочитались");
        return sc;
    }

    @Test
    void картинкиПланшетовНаМесте() {
        assertNotNull(Textures.board("science"), "нет картинки планшета науки");
        assertNotNull(Textures.board("market"), "нет картинки планшета рынка");
    }

    @Test
    void размерЯкорейСовпадаетСКартинкой() {
        BoardAnchors.Science sc = science();
        BufferedImage art = Textures.board("science");
        assertEquals(art.getWidth(), sc.artW(), "ширина картинки не та, с которой сняты якоря");
        assertEquals(art.getHeight(), sc.artH(), "высота картинки не та, с которой сняты якоря");
    }

    @Test
    void ячеекСтолькоЖеСколькоВСводе() {
        BoardAnchors.Science sc = science();
        int[] cap = ёмкости();
        int steps = cap.length;
        assertEquals(3, sc.tracks().size(), "треков науки на планшете три");
        for (String track : sc.tracks().keySet()) {
            for (int step = 1; step <= steps; step++) {
                for (int cell = 0; cell < cap[step - 1]; cell++) {
                    assertNotNull(sc.cell(track, step, cell, steps),
                        "нет ячейки " + track + "/шаг " + step + "/№" + cell);
                }
            }
        }
    }

    /** Сколько ячеек на каждом шаге — по СВОДу. */
    private static int[] ёмкости() {
        var ruleset = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 0L, null, null).ruleset;
        Object raw = ruleset.get("tech.step_capacity", null);
        assertTrue(raw instanceof List<?>, "в своде нет tech.step_capacity");
        List<?> cap = (List<?>) raw;
        int[] out = new int[cap.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((Number) cap.get(i)).intValue();
        }
        return out;
    }

    @Test
    void ячейкиНеСвисаютСКраяИНеНаезжаютДругНаДруга() {
        BoardAnchors.Science sc = science();
        int[] cap = ёмкости();
        int steps = cap.length;
        double half = Math.hypot(sc.cellW(), sc.cellH()) / 2;
        java.util.List<double[]> все = new java.util.ArrayList<>();
        for (String track : sc.tracks().keySet()) {
            for (int step = 1; step <= steps; step++) {
                for (int cell = 0; cell < cap[step - 1]; cell++) {
                    double[] c = sc.cell(track, step, cell, steps);
                    assertTrue(c[0] - half > 0 && c[0] + half < sc.artW()
                            && c[1] - half > 0 && c[1] + half < sc.artH(),
                        "ячейка " + track + "/" + step + "/" + cell + " свисает с картинки");
                    все.add(c);
                }
            }
        }
        // Ячейки лежат рядом, но не одна на другой: центры не ближе половины
        // короткой стороны. Совпавшие центры — верный признак опечатки в якорях.
        for (int i = 0; i < все.size(); i++) {
            for (int j = i + 1; j < все.size(); j++) {
                double d = Math.hypot(все.get(i)[0] - все.get(j)[0],
                    все.get(i)[1] - все.get(j)[1]);
                assertTrue(d > Math.min(sc.cellW(), sc.cellH()) / 2.0,
                    "две ячейки почти в одной точке: расстояние " + Math.round(d));
            }
        }
    }

    @Test
    void рамкаКартыРынкаАрсенальногоФормата() {
        int[] slot = BoardAnchors.marketCard();
        assertNotNull(slot, "якоря планшета рынка не прочитались");
        BufferedImage art = Textures.board("market");
        assertTrue(slot[0] > 0 && slot[1] > 0
                && slot[0] + slot[2] <= art.getWidth()
                && slot[1] + slot[3] <= art.getHeight(),
            "рамка карты рынка вылезает за планшет");
        // Карта рынка того же формата, что арсенальная: 68×44 мм.
        double got = slot[2] / (double) slot[3];
        assertTrue(Math.abs(got - 68.0 / 44.0) < 0.08,
            "рамка карты не арсенального формата: " + String.format("%.2f", got));
    }
}
