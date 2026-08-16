package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.engine.Scenario;
import kelium.gui.LayoutEditor;

/**
 * Конструктор раскладок: модель редактора сериализуется в формат, который
 * принимает НАСТОЯЩИЙ загрузчик сценариев (Scenario.buildFieldFromScenario),
 * с сохранением стартов, грядок, нейтралов и контейнеров.
 */
class LayoutEditorTest {

    private LayoutEditor.Model tinyLayout() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        // линия из 5 гексов: старт P1 — грядка-старт — обычный — грядка-старт — старт P2
        int[][] coords = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}};
        for (int[] c : coords) {
            m.hexes.put(LayoutEditor.Model.key(c[0], c[1]),
                new LayoutEditor.LHex(c[0], c[1]));
        }
        m.get(0, 0).content = "player_start";
        m.get(0, 0).seat = 0;
        m.get(1, 0).content = "spawn_start";
        m.get(2, 0).neutrals.add(new LayoutEditor.Neutral(true, 2));
        m.get(3, 0).content = "spawn_start";
        m.get(4, 0).content = "player_start";
        m.get(4, 0).seat = 1;
        return m;
    }

    /**
     * Координаты в записи ЦЕНТРИРУЮТСЯ: (0,0) достаётся самому центральному
     * гексу карты (правило дизайнера 16.08.2026). Линия из пяти гексов
     * q = 0..4 записывается как q = −2..+2, поэтому средний гекс здесь —
     * «h0_0», а не «h2_0».
     */
    @Test
    void editorModelRoundTripsThroughSimLoader() {
        Map<String, Object> scn = LayoutEditor.toScenarioMap(tinyLayout(), "t");
        Scenario.FieldWithStarts fw = Scenario.buildFieldFromScenario(scn);
        assertEquals(5, fw.field().size(), "все гексы дошли до поля");
        assertEquals(2, fw.starts().size(), "оба старта распознаны");
        var mid = fw.field().get("h0_0");
        assertEquals(1, mid.neutrals.size(), "нейтрал на месте");
        assertTrue(mid.neutrals.get(0).big, "нейтрал большой");
        assertEquals(3, fw.field().get("h-1_0").spawnTile.kelium, "стартовая грядка: лицо 3");
    }

    /** Самый центральный гекс карты получает координаты (0,0). */
    @Test
    void theMostCentralHexBecomesTheOrigin() {
        Map<String, Object> scn = LayoutEditor.toScenarioMap(tinyLayout(), "t");
        Scenario.FieldWithStarts fw = Scenario.buildFieldFromScenario(scn);
        assertTrue(fw.field().get("h0_0") != null,
            "в центре линии из пяти гексов обязан оказаться h0_0: " + fw.field().hexes.keySet());
        assertTrue(fw.field().get("h-2_0") != null && fw.field().get("h2_0") != null,
            "края линии — на равном удалении от нуля");
    }

    @Test
    void validatorCatchesTypicalProblems() {
        LayoutEditor.Model m = tinyLayout();
        // отклеим половину карты: удалим средний гекс — две компоненты
        m.hexes.remove(LayoutEditor.Model.key(2, 0));
        var issues = LayoutEditor.validate(m);
        boolean split = issues.stream().anyMatch(i -> i.toString().contains("куска"));
        assertTrue(split, "журнал видит развал карты на компоненты");
    }

    @Test
    void validatorPassesOnRealScenario() {
        // настоящий сценарий 2p из данных должен грузиться загрузчиком без ошибок
        var scn = Scenario.loadScenario(2, "1.0.0",
            kelium.dataio.GameConfig.build(2, 0L).dataRoot);
        var fw = Scenario.buildFieldFromScenario(scn);
        assertFalse(fw.starts().isEmpty(), "в реальном сценарии есть старты");
    }
}
