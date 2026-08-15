package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.engine.Scenario;
import kelium.gui.LayoutEditor;

/**
 * Новые возможности конструктора: двойные тайлы зарождения (стопка ×2) и правка
 * келемия ±4 должны доезжать до настоящего загрузчика сценариев, а проверки —
 * ловить «у старта мало обычных соседей».
 */
class LayoutEditorFeaturesTest {

    @Test
    void doubleTileAndKeliumDeltaReachSimLoader() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        for (int q = 0; q < 3; q++) {
            m.hexes.put(LayoutEditor.Model.key(q, 0), new LayoutEditor.LHex(q, 0));
        }
        LayoutEditor.LHex tile = m.get(1, 0);
        tile.content = "kelium_tile";
        tile.stack = 2;            // двойной тайл
        tile.keliumDelta = 2;      // лицо 4 + 2 = 6

        Map<String, Object> scn = LayoutEditor.toScenarioMap(m, "t");
        var field = Scenario.buildFieldFromScenario(scn).field();
        var hex = field.get("h1_0");
        assertTrue(hex.hasSpawnTile(), "тайл зарождения лежит на гексе");
        assertEquals(2, hex.spawnTile.stack, "стопка из двух тайлов");
        assertEquals(6, hex.spawnTile.kelium, "келемий на лице с правкой +2");
    }

    @Test
    void validatorRequiresThreePlainNeighboursAtStart() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        // старт в «кармане»: вокруг только тайлы зарождения — разворачиваться негде
        m.hexes.put(LayoutEditor.Model.key(0, 0), new LayoutEditor.LHex(0, 0));
        m.get(0, 0).content = "player_start";
        m.get(0, 0).seat = 0;
        int[][] around = {{1, 0}, {0, 1}, {-1, 1}};
        for (int[] c : around) {
            LayoutEditor.LHex h = new LayoutEditor.LHex(c[0], c[1]);
            h.content = "spawn_start";
            m.hexes.put(LayoutEditor.Model.key(c[0], c[1]), h);
        }
        boolean caught = LayoutEditor.validate(m).stream()
            .anyMatch(i -> i.level() == 2 && i.text().contains("обычных соседних"));
        assertTrue(caught, "журнал требует ≥3 обычных соседних гекса у старта");
    }
}
