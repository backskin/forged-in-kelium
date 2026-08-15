package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;

/**
 * ЗОНА СТРОЙКИ (правило уточнено дизайнером 12.08.2026).
 *
 * <p>Строить можно на своих гексах и на тех, к которым твоё здание примыкает
 * ИМЕННО ТОЙ СТЕНКОЙ, что смотрит на этот гекс. Раньше в зону попадали все
 * соседи своих гексов — поворот здания ничего не решал, а зона расползалась
 * кольцом. Чужое или нейтральное здание, занявшее ту же общую стенку с другой
 * стороны, работает стенкой и прохода не даёт.
 */
class BuildZoneTest {

    private static GameState game(long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
    }

    /** Соседи гекса, к которым здание игрока НЕ повёрнуто стенкой. */
    private static List<String> notFacing(GameState s, PlayerState p, String hexId) {
        Hex h = s.field.get(hexId);
        List<String> out = new java.util.ArrayList<>();
        for (int side = 0; side < 6; side++) {
            String nb = h.neighborBySide[side];
            if (nb == null) {
                continue;
            }
            Integer owner = h.sideOwner[side];
            boolean mine = false;
            if (owner != null) {
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.uid == owner) {
                        mine = true;
                        break;
                    }
                }
            }
            if (!mine) {
                out.add(nb);
            }
        }
        return out;
    }

    @Test
    void zoneGrowsOnlyThroughWallsOwnBuildingsActuallyOccupy() {
        GameState s = game(31L);
        PlayerState p = s.player(0);
        List<String> zone = Actions.buildableHexes(s, 0);

        assertTrue(zone.contains(p.startHex), "свой гекс всегда в зоне");

        // Ни один сосед, к которому мы не повёрнуты стенкой, в зону попасть
        // не должен — если только он не наш собственный гекс.
        java.util.Set<String> own = new java.util.HashSet<>();
        for (BuildingToken b : p.buildingsOnField()) {
            own.add(b.hexId);
        }
        for (String nb : notFacing(s, p, p.startHex)) {
            if (!own.contains(nb)) {
                assertFalse(zone.contains(nb),
                    "гекс " + nb + " не должен быть в зоне: здание к нему не повёрнуто");
            }
        }
    }

    @Test
    void zoneIsSmallerThanTheRingOfAllNeighbours() {
        // Проверка «правило вообще работает»: старая версия брала всех соседей,
        // новая — только те стороны, что заняты своими зданиями.
        GameState s = game(32L);
        for (int seat = 0; seat < 4; seat++) {
            PlayerState p = s.player(seat);
            java.util.Set<String> ring = new java.util.LinkedHashSet<>();
            for (BuildingToken b : p.buildingsOnField()) {
                ring.add(b.hexId);
                ring.addAll(s.field.neighbors(b.hexId));
            }
            List<String> zone = Actions.buildableHexes(s, seat);
            assertTrue(zone.size() < ring.size(),
                "место " + (seat + 1) + ": зона (" + zone.size()
                    + ") обязана быть уже кольца соседей (" + ring.size() + ")");
        }
    }

    @Test
    void enemyWallOnTheFarSideBlocksTheExpansion() {
        // Ищем по сидам первую пригодную позицию: сторона ЦУ, за которой сосед
        // реально попал в зону (не тайл зарождения и не запретный гекс).
        boolean checked = false;
        for (long seed = 33; seed < 60 && !checked; seed++) {
            GameState s = game(seed);
            PlayerState me = s.player(0);
            List<String> zone = Actions.buildableHexes(s, 0);
            for (BuildingToken own : me.buildingsOnField()) {
                Hex from = s.field.get(own.hexId);
                for (int side = 0; side < 6; side++) {
                    String nbId = from.neighborBySide[side];
                    if (from.sideOwner[side] == null || from.sideOwner[side] != own.uid
                            || nbId == null || !zone.contains(nbId)) {
                        continue;
                    }
                    Hex nb = s.field.get(nbId);
                    if (nb.sideOwner[(side + 3) % 6] != null) {
                        continue;   // стенка уже занята — не наш случай
                    }
                    // ставим ЧУЖОЕ здание ровно на общую стенку с той стороны
                    PlayerState enemy = s.player(1);
                    BuildingToken wall =
                        s.tokenStats.makeBuilding(BuildingType.BARRACKS, 1, 7777, null);
                    wall.hexId = nbId;
                    enemy.buildings.add(wall);
                    nb.occupySides(wall.uid, List.of((side + 3) % 6));

                    assertFalse(Actions.buildableHexes(s, 0).contains(nbId),
                        "чужое здание на общей стенке закрывает расширение зоны");
                    checked = true;
                    break;
                }
                if (checked) {
                    break;
                }
            }
        }
        assertTrue(checked, "нашлась позиция для проверки стенки");
    }
}
