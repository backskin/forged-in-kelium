package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.engine.Scenario;
import kelium.gui.LayoutEditor;

/**
 * Несколько нейтральных зданий одного типа на одном гексе: углы не должны
 * пересекаться, и всё это обязано доезжать до загрузчика сценариев.
 */
class NeutralPlacementTest {

    @Test
    void neutralsMayStandOnAdjacentEdges() {
        // Два малых здания на СОСЕДНИХ рёбрах делят только угловую точку —
        // это законно (раньше конструктор считал такое пересечением).
        LayoutEditor.Model m = new LayoutEditor.Model();
        LayoutEditor.LHex h = new LayoutEditor.LHex(0, 0);
        h.neutrals.add(new LayoutEditor.Neutral(false, 1));   // ребро углов 1-2
        h.neutrals.add(new LayoutEditor.Neutral(false, 2));   // ребро углов 2-3, вплотную
        m.hexes.put(LayoutEditor.Model.key(0, 0), h);

        var hex = Scenario.buildFieldFromScenario(LayoutEditor.toScenarioMap(m, "t"))
            .field().get("h0_0");
        assertEquals(2, hex.neutrals.size(), "оба здания на гексе");
        Set<Integer> walls = new HashSet<>();
        for (var nb : hex.neutrals) {
            for (int s : nb.wallSides()) {
                assertTrue(walls.add(s), "стенка " + s + " занята дважды");
            }
        }
        assertEquals(2, walls.size(), "занято две РАЗНЫЕ стенки");
    }

    @Test
    void threeSmallNeutralsFitOnOneHexWithoutOverlap() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        LayoutEditor.LHex h = new LayoutEditor.LHex(0, 0);
        // малый занимает 2 угла: 1-2, 3-4, 5-6 — ровно три штуки на шесть углов
        h.neutrals.add(new LayoutEditor.Neutral(false, 1));
        h.neutrals.add(new LayoutEditor.Neutral(false, 3));
        h.neutrals.add(new LayoutEditor.Neutral(false, 5));
        m.hexes.put(LayoutEditor.Model.key(0, 0), h);

        Set<Integer> used = new HashSet<>();
        for (LayoutEditor.Neutral n : h.neutrals) {
            for (int c : n.corners()) {
                assertTrue(used.add(c), "угол " + c + " занят дважды");
            }
        }
        assertEquals(6, used.size(), "три малых здания занимают все шесть углов");

        var field = Scenario.buildFieldFromScenario(LayoutEditor.toScenarioMap(m, "t")).field();
        assertEquals(3, field.get("h0_0").neutrals.size(),
            "все три здания дошли до поля симулятора");
    }

    @Test
    void twoBigNeutralsFitOnOneHex() {
        LayoutEditor.Model m = new LayoutEditor.Model();
        LayoutEditor.LHex h = new LayoutEditor.LHex(0, 0);
        h.neutrals.add(new LayoutEditor.Neutral(true, 1));   // углы 1,2,3
        h.neutrals.add(new LayoutEditor.Neutral(true, 4));   // углы 4,5,6
        m.hexes.put(LayoutEditor.Model.key(0, 0), h);

        var hex = Scenario.buildFieldFromScenario(LayoutEditor.toScenarioMap(m, "t"))
            .field().get("h0_0");
        assertEquals(2, hex.neutrals.size(), "оба больших здания на гексе");
        assertTrue(hex.anyNeutralBig(), "распознаны как большие");
        // стенки обоих зданий занимают РАЗНЫЕ стороны гекса
        Set<Integer> sides = new HashSet<>();
        for (var nb : hex.neutrals) {
            for (int s : nb.wallSides()) {
                assertTrue(sides.add(s), "сторона " + s + " занята дважды");
            }
        }
    }
}
