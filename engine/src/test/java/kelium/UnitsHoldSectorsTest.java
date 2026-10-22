package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Placement;
import kelium.engine.СекторыВойск;
import kelium.support.Fix;

/**
 * ЖЕТОНЫ ДЕРЖАТ СВОИ СЕКТОРЫ (дизайнер 30.09.2026, свод 1.46.0): войско
 * занимает сектор так же, как здание. Внутри гекса ничто не сдвигается —
 * здание встаёт только на свободные секторы, войска ради него не переезжают.
 * В замороженном своде 1.45.0 войска по-прежнему переупаковываются.
 */
class UnitsHoldSectorsTest {

    /** Пустой обычный гекс без нейтралов, тайлов и чужих жетонов. */
    private static Hex emptyHex(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != HexKind.NORMAL || h.spawnTile != null || h.hasNeutral()) {
                continue;
            }
            boolean free = true;
            for (int i = 0; i < 6; i++) {
                free &= h.sideOwner[i] == null;
            }
            for (var p : s.players) {
                for (UnitToken u : p.units) {
                    free &= !h.id.equals(u.hexId);
                }
                for (var b : p.buildings) {
                    free &= !h.id.equals(b.hexId);
                }
            }
            if (free) {
                return h;
            }
        }
        throw new AssertionError("нет пустого гекса");
    }

    /**
     * Пехота через сектор друг от друга (0, 2, 4): свободные секторы есть,
     * но двух смежных нет. Раньше пехота подвинулась бы и техника встала;
     * теперь технике места нет.
     */
    @Test
    void vehicleDoesNotFitBetweenHeldSectors() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        assertTrue(Placement.unitsHoldSectors(s));
        Hex h = emptyHex(s);
        for (int side : List.of(0, 2, 4)) {
            Fix.unit(s, 0, UnitType.INFANTRY, h.id).chooseSides(List.of(side));
        }
        assertFalse(Placement.hasRoomOnHex(s, s.player(0), h.id, UnitType.VEHICLE),
            "технике нужны два смежных свободных сектора");
        assertTrue(Placement.hasRoomOnHex(s, s.player(0), h.id, UnitType.INFANTRY));
    }

    /** Здание встаёт только на секторы, не занятые войском, и войско не сдвигается. */
    @Test
    void buildingFootprintAvoidsUnitSectors() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        Hex h = emptyHex(s);
        UnitToken u = Fix.unit(s, 0, UnitType.INFANTRY, h.id);
        u.chooseSides(List.of(1));
        int[] ld = Placement.groundLoad(s, h.id, -1);
        assertEquals(1 << 1, ld[2]);
        for (int start = 0; start < 6; start++) {
            List<Integer> run = h.footprintAt(start, 3, ld[0], ld[1], ld[2]);
            if (run != null) {
                assertFalse(run.contains(1), "след здания на секторе войска: " + run);
            }
        }
        assertEquals(List.of(1), СекторыВойск.секторыЖетона(s, u));
    }

    /**
     * Жетон без выбора закрепляет движок — там, куда его положила раскладка.
     * Сам запрос раскладки стол не меняет.
     */
    @Test
    void unpinnedUnitGetsPinned() {
        GameState s = Fix.game("1.46.0", 2, 7L);
        Hex h = emptyHex(s);
        UnitToken u = Fix.unit(s, 0, UnitType.VEHICLE, h.id);
        List<Integer> first = СекторыВойск.секторыЖетона(s, u);
        assertNotNull(first);
        assertEquals(null, u.chosenSides(), "запрос не закрепляет");
        СекторыВойск.закрепитьВсе(s);
        assertEquals(first, u.chosenSides());
    }

    /** Свод 1.45.0: войска переупаковываются, технике место находится. */
    @Test
    void frozenRulesetStillRepacks() {
        GameState s = Fix.game("1.45.0", 2, 7L);
        assertFalse(Placement.unitsHoldSectors(s));
        Hex h = emptyHex(s);
        for (int side : List.of(0, 2, 4)) {
            Fix.unit(s, 0, UnitType.INFANTRY, h.id).chooseSides(List.of(side));
        }
        assertTrue(Placement.hasRoomOnHex(s, s.player(0), h.id, UnitType.VEHICLE));
    }
}
