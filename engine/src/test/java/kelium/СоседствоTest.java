package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.CellGraph;
import kelium.engine.Chains;
import kelium.engine.Shapes;
import kelium.engine.Соседство;
import kelium.engine.Соседство.Место;
import kelium.support.Fix;

/**
 * СОСЕДСТВО ОДНО НА ДВИЖОК (этап 0, 30.09.2026): {@link Соседство} и три
 * прежних места, которые теперь его зовут, отвечают одинаково.
 */
class СоседствоTest {

    /** Пустой обычный гекс, у которого сосед по стороне {@code side} тоже пуст. */
    private static Hex[] pair(GameState s, int side) {
        for (Hex h : s.field.hexes.values()) {
            Hex nb = h.neighborBySide[side] == null ? null : s.field.get(h.neighborBySide[side]);
            if (empty(s, h) && nb != null && empty(s, nb)) {
                return new Hex[]{h, nb};
            }
        }
        throw new AssertionError("нет пары пустых гексов");
    }

    private static boolean empty(GameState s, Hex h) {
        if (h.kind != HexKind.NORMAL || h.spawnTile != null || h.hasNeutral()) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (h.sideOwner[i] != null) {
                return false;
            }
        }
        for (var p : s.players) {
            for (UnitToken u : p.units) {
                if (h.id.equals(u.hexId)) {
                    return false;
                }
            }
            for (BuildingToken b : p.buildings) {
                if (h.id.equals(b.hexId)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void ребро_примыкает_напротив_нет_небо_со_всеми() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        Hex[] hx = pair(s, 0);
        Hex a = hx[0];
        Hex b = hx[1];
        int back = b.sidesFacing(a.id).get(0);

        assertTrue(Соседство.примыкают(s, new Место(a.id, 0), new Место(b.id, back)));
        assertFalse(Соседство.примыкают(s, new Место(a.id, 1), new Место(b.id, back)),
            "сектор не на общем ребре не примыкает");
        assertTrue(Соседство.соседствуют(s, new Место(a.id, 0), new Место(a.id, 1)));
        assertFalse(Соседство.соседствуют(s, new Место(a.id, 0), new Место(a.id, 3)),
            "напротив по кругу — не соседи");
        assertTrue(Соседство.соседствуют(s, new Место(a.id, Соседство.НЕБО), new Место(a.id, 3)));
        assertFalse(Соседство.соседствуют(s, new Место(a.id, Соседство.НЕБО),
            new Место(b.id, back)), "небо не тянется за пределы своего гекса");
        assertFalse(Соседство.примыкают(s, new Место(a.id, 0), new Место(a.id, 1)),
            "примыкание — только между разными гексами");
    }

    @Test
    void здания_стенка_к_стенке_и_войско_на_своём_секторе() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        Hex[] hx = pair(s, 0);
        Hex a = hx[0];
        Hex b = hx[1];
        int back = b.sidesFacing(a.id).get(0);

        BuildingToken x = Fix.building(s, 0, BuildingType.FACTORY, a.id, null);
        a.freeSidesByToken(x.uid);
        a.occupySides(x.uid, List.of(0));
        BuildingToken y = Fix.building(s, 0, BuildingType.FACTORY, b.id, null);
        b.freeSidesByToken(y.uid);
        b.occupySides(y.uid, List.of(back));

        assertTrue(Соседство.примыкают(s, x, y));
        assertTrue(Chains.abutsAcrossWall(s, x, y));
        assertEquals(2, Chains.largestWallChain(s, List.of(x, y)));

        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, a.id);
        inf.chooseSides(List.of(3));
        assertFalse(Соседство.соседствуют(s, x, inf), "пехота напротив завода — не сосед");
        inf.chooseSides(List.of(1));
        assertTrue(Соседство.соседствуют(s, x, inf));
        assertTrue(CellGraph.linked(s, x, inf));
        assertTrue(Shapes.ownNodes(s, 0).contains(new Shapes.Node(a.id, 1)));
    }

    @Test
    void войско_в_здании_мест_не_занимает() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        Hex a = pair(s, 0)[0];
        BuildingToken bar = Fix.building(s, 0, BuildingType.BARRACKS, a.id, null);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, a.id);
        inf.insideBuildingUid = bar.uid;
        assertTrue(Соседство.места(s, inf).isEmpty());
    }
}
