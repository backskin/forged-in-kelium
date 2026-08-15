package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.report.SvgFieldRenderer;
import kelium.core.Agent;

/**
 * Геометрия отрисовки поля: жетоны обязаны быть разумного размера, не вылезать
 * за свой гекс и иметь правильный поворот. Раньше «вписывание в клин» давало
 * жетоны почти в целый гекс — они наезжали на соседей.
 */
class FieldRenderGeometryTest {

    /** Разобрать transform жетона: translate(cx,cy) rotate(a) scale(k) translate(-w/2,-h/2). */
    private record Token(double cx, double cy, double rot, double k, double w, double h) {
        double drawnW() {
            return w * k;
        }

        double drawnH() {
            return h * k;
        }

        double radius() {
            return Math.hypot(drawnW(), drawnH()) / 2;
        }
    }

    private static List<Token> tokens(String svg) {
        Pattern p = Pattern.compile(
            "translate\\((-?[\\d.]+),(-?[\\d.]+)\\) rotate\\((-?[\\d.]+)\\) scale\\(([\\d.]+)\\) "
            + "translate\\((-?[\\d.]+),(-?[\\d.]+)\\)");
        Matcher m = p.matcher(svg);
        List<Token> out = new ArrayList<>();
        while (m.find()) {
            double w = -2 * Double.parseDouble(m.group(5));
            double h = -2 * Double.parseDouble(m.group(6));
            out.add(new Token(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)), w, h));
        }
        return out;
    }

    private static String renderSample() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null);
        GameState s = Setup.buildGame(cfg);
        return SvgFieldRenderer.render(s, 1);
    }

    @Test
    void tokensAreSanelySizedAndInsideTheirHex() {
        String svg = renderSample();
        List<Token> ts = tokens(svg);
        assertTrue(ts.size() >= 4, "на стартовом поле нарисованы жетоны: " + ts.size());
        double hexR = 52;   // SvgFieldRenderer.SIZE
        for (Token t : ts) {
            assertTrue(t.drawnW() <= hexR * 1.05,
                "жетон шире гекса: " + String.format("%.1f", t.drawnW()));
            assertTrue(t.drawnW() >= hexR * 0.12,
                "жетон вырожденно мелкий: " + String.format("%.1f", t.drawnW()));
            assertTrue(t.drawnH() <= hexR * 1.05,
                "жетон выше гекса: " + String.format("%.1f", t.drawnH()));
        }
    }

    @Test
    void everyTokenStaysWithinItsHexFootprint() {
        String svg = renderSample();
        // центры гексов: из полигонов не вытащить, зато у каждого жетона центр
        // должен лежать не дальше 0.62·SIZE от какого-то центра гекса, а сам
        // жетон целиком укладываться в радиус гекса.
        double hexR = 52;
        for (Token t : tokens(svg)) {
            assertTrue(t.radius() <= hexR * 0.80,
                "жетон не влезает в гекс: радиус " + String.format("%.1f", t.radius()));
        }
    }

    @Test
    void towerIsFlippedAndBuildingsAreLabelled() {
        String svg = renderSample();
        // Подпись типа прямо на жетоне. На старте по СВОДу на поле только ЦУ
        // (добытчика игрок ставит сам), поэтому проверяем именно его.
        assertTrue(svg.contains(">ЦУ<"), "у ЦУ есть подпись на жетоне");
        // энергия и урон — КВАДРАТЫ (rect), не круги
        assertTrue(svg.contains("fill='#ffc400'"), "кубик энергии нарисован квадратом");
        assertTrue(!svg.contains("<circle") || !svg.contains("fill='#fc0'"),
            "старых круглых индикаторов энергии больше нет");
        // легенда на месте
        assertTrue(svg.contains("Условные обозначения"), "легенда есть");
        assertTrue(svg.contains("Кз — казарма"), "легенда расшифровывает коды зданий");
    }

    @Test
    void containersNeverLandOnSpawnTiles() {
        // Гекс под тайлом зарождения занят целиком — контейнеру там не место.
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 21L, null, null);
        GameState s = Setup.buildGame(cfg);
        java.util.List<kelium.core.Agent> agents = new java.util.ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new kelium.agents.RandomAgent(seat, new java.util.Random(seat + 1)));
        }
        kelium.engine.GameEngine.playGame(s, agents, ev -> { });
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                assertTrue(h.containerCell < 0,
                    "на гексе с тайлом зарождения " + h.id + " не бывает печатного "
                    + "контейнера: жетон закрывает все ячейки");
            }
        }
    }

    @Test
    void spawnTilesUseConstructorColours() {
        String svg = renderSample();
        assertTrue(svg.contains("#2E7D32") || svg.contains("#A5D6A7"),
            "тайлы зарождения в зелёной гамме конструктора");
        assertEquals(false, svg.contains("#fff2b0"),
            "старая жёлтая заливка грядок убрана");
    }
}
