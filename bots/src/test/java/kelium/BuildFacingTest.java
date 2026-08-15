package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.agents.HeuristicAgent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.SpawnTile;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * ПОВОРОТ ЗДАНИЯ — осмысленное решение бота. Дизайнер 12.08.2026: «все боты
 * ставят добытчики рандомно, не думают что их надо строить примыкая к стенке
 * с соседним гексом где лежит тайл зарождения. и поэтому нихуя не добывают».
 */
class BuildFacingTest {

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null));
    }

    /** Гекс с живой грядкой у одной из сторон целевого гекса. */
    private static String[] hexWithGridNeighbour(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.hasNeutral() || h.spawnTile != null) {
                continue;
            }
            for (int side = 0; side < 6; side++) {
                String nb = h.neighborBySide[side];
                Hex n = nb == null ? null : s.field.get(nb);
                if (n != null && n.spawnTile != null && n.spawnTile.kelium > 0
                        && !n.hasNeutral()) {
                    return new String[]{h.id, String.valueOf(side)};
                }
            }
        }
        return null;
    }

    @Test
    void minerTurnsItsWallTowardsLiveVein() {
        GameState s = game();
        String[] spot = hexWithGridNeighbour(s);
        if (spot == null) {
            // на этом поле нет свободного гекса у живой жилы — соберём вручную
            Hex any = null;
            for (Hex h : s.field.hexes.values()) {
                if (h.neighborBySide[0] != null) {
                    any = h;
                    break;
                }
            }
            assertTrue(any != null, "нужен гекс с соседом по стороне 0");
            Hex n = s.field.get(any.neighborBySide[0]);
            n.neutrals.clear();
            n.spawnTile = new SpawnTile(false, 3, 3, 1);
            any.neutrals.clear();
            any.spawnTile = null;
            spot = new String[]{any.id, "0"};
        }
        String hexId = spot[0];
        int veinSide = Integer.parseInt(spot[1]);

        HeuristicAgent agent = new HeuristicAgent(0, new java.util.Random(1), "balanced");
        List<Choice> opts = new ArrayList<>();
        for (int start = 0; start < 6; start++) {
            List<Integer> run = s.field.get(hexId).footprintAt(start, 2, 0, 0);
            if (run != null) {
                opts.add(new Choice("build_facing", run, "поворот " + start));
            }
        }
        assertTrue(opts.size() > 1, "должно быть из чего выбирать: " + opts.size());

        Choice pick = agent.choose(s, opts, Map.of("kind", "build_facing",
            "btype", "miner", "hex", hexId));
        @SuppressWarnings("unchecked")
        List<Integer> sides = (List<Integer>) pick.payload();
        assertTrue(sides.contains(veinSide),
            "добытчик должен встать стенкой " + veinSide + " к жиле, а встал " + sides);
    }

    @Test
    void anyBuildingPrefersWallsInsideTheField() {
        GameState s = game();
        // гекс на краю: часть сторон смотрит в пустоту
        Hex edge = null;
        for (Hex h : s.field.hexes.values()) {
            int nulls = 0;
            for (int i = 0; i < 6; i++) {
                if (h.neighborBySide[i] == null) {
                    nulls++;
                }
            }
            if (nulls < 2 || h.hasNeutral() || h.spawnTile != null) {
                continue;
            }
            // рядом не должно быть живой грядки: окно к ней важнее роста зоны,
            // и не-добытчик правильно откажется его занимать
            boolean vein = false;
            for (int i = 0; i < 6; i++) {
                String nb = h.neighborBySide[i];
                Hex n = nb == null ? null : s.field.get(nb);
                if (n != null && n.spawnTile != null && n.spawnTile.kelium > 0) {
                    vein = true;
                }
            }
            if (!vein) {
                edge = h;
                break;
            }
        }
        if (edge == null) {
            return;   // компактное поле без краевых гексов — правило не проверить
        }
        HeuristicAgent agent = new HeuristicAgent(0, new java.util.Random(1), "balanced");
        List<Choice> opts = new ArrayList<>();
        for (int start = 0; start < 6; start++) {
            List<Integer> run = edge.footprintAt(start, 2, 0, 0);
            if (run != null) {
                opts.add(new Choice("build_facing", run, "поворот " + start));
            }
        }
        Choice pick = agent.choose(s, opts, Map.of("kind", "build_facing",
            "btype", "power_plant", "hex", edge.id));
        @SuppressWarnings("unchecked")
        List<Integer> sides = (List<Integer>) pick.payload();
        int inField = 0;
        for (int side : sides) {
            if (edge.neighborBySide[side] != null) {
                inField++;
            }
        }
        assertEquals(sides.size(), inField,
            "стенки должны смотреть в поле, а не за край: " + sides);
    }
}
