package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.report.SvgFieldRenderer;

/**
 * Нейтральные постройки ЗАНИМАЮТ ячейки гекса — как и всё остальное.
 *
 * <p>Дизайнер 12.08.2026: «жетоны пехоты находились на тех же самых ячейках,
 * что и жетоны нейтральных зданий. Так нельзя». Причина была в рендере: он
 * собирал занятость только по зданиям ИГРОКОВ, а стенки нейтралов лежат в
 * {@code sideOwner} с отрицательным uid. Тест закрывает обе стороны: и модель
 * (стенки реально заняты), и рендер (свободными их не считает).
 */
class NeutralOccupancyTest {

    private static GameState game(int players) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, players, 11L, null, null));
    }

    @Test
    void neutralWallsOccupyHexSides() {
        GameState s = game(4);
        int hexesWithNeutrals = 0;
        for (Hex h : s.field.hexes.values()) {
            if (h.neutrals.isEmpty()) {
                continue;
            }
            hexesWithNeutrals++;
            for (Hex.NeutralBuilding nb : h.neutrals) {
                for (int side : nb.wallSides()) {
                    assertEquals(nb.uid, h.sideOwner[side],
                        "стенка нейтрала должна ЗАНИМАТЬ ячейку " + side + " гекса " + h.id);
                }
                // малое здание — одна стенка, большое — две
                assertEquals(nb.big ? 2 : 1, nb.wallSides().size(),
                    "малый нейтрал занимает 1 ячейку, большой — 2");
            }
        }
        assertTrue(hexesWithNeutrals > 0, "в авторской раскладке 4p нейтралы есть");
    }

    @Test
    void rendererNeverTreatsNeutralCellAsFree() {
        GameState s = game(4);
        boolean checkedAny = false;
        for (Hex h : s.field.hexes.values()) {
            if (h.neutrals.isEmpty()) {
                continue;
            }
            List<Integer> occupied = SvgFieldRenderer.occupiedSides(h);
            for (Hex.NeutralBuilding nb : h.neutrals) {
                for (int side : nb.wallSides()) {
                    checkedAny = true;
                    assertTrue(occupied.contains(side),
                        "рендер обязан считать ячейку нейтрала занятой (гекс " + h.id
                            + ", ячейка " + side + ") — иначе войска рисуются поверх него");
                }
            }
            // и наоборот: свободные ячейки гекса действительно свободны
            for (int i = 0; i < 6; i++) {
                if (!occupied.contains(i)) {
                    assertFalse(h.sideOwner[i] != null,
                        "свободной названа занятая ячейка " + i + " гекса " + h.id);
                }
            }
        }
        assertTrue(checkedAny, "нашлись нейтралы со стенками");
    }
}
