package kelium.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.Token;

/**
 * ГРАФ ПРИМЫКАНИЯ СТЕНКОЙ — общий примитив для карт, спрашивающих про цепочки
 * зданий («здания примыкают друг к другу общей стенкой»).
 *
 * <p>Извлечено из {@link Predicates} 17.08.2026 при переезде карт заданий в код:
 * прежде этот перебор жил внутри одной строки {@code buildings_wall_chain}, и
 * карта, живущая в коде, до него не доставала. Поведение не изменено — только
 * вынесено в отдельное место, откуда его может позвать и старый предикат, и
 * новая карта.
 */
public final class Chains {

    private Chains() {
    }

    /**
     * Примыкают ли два здания на РАЗНЫХ гексах общей стенкой: {@code a} занимает
     * сторону, смотрящую на гекс {@code b}, и {@code b} занял противоположную
     * сторону той же грани.
     */
    public static boolean abutsAcrossWall(GameState s, BuildingToken a, BuildingToken b) {
        Hex ha = s.field.get(a.hexId);
        Hex hb = s.field.get(b.hexId);
        if (ha == null || hb == null) {
            return false;
        }
        for (int side = 0; side < 6; side++) {
            Integer owner = ha.sideOwner[side];
            if (owner == null || owner != a.uid) {
                continue;
            }
            if (!b.hexId.equals(ha.neighborBySide[side])) {
                continue;
            }
            Integer opp = hb.sideOwner[(side + 3) % 6];
            if (opp != null && opp == b.uid) {
                return true;
            }
        }
        return false;
    }

    /** Размер наибольшей связной компоненты в готовом графе смежности. */
    public static int largestComponent(Map<Integer, List<Integer>> g, Set<Integer> nodes) {
        Set<Integer> seen = new HashSet<>();
        int best = 0;
        for (Integer start : nodes) {
            if (seen.contains(start)) {
                continue;
            }
            int comp = 0;
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                Integer x = stack.pop();
                if (!seen.add(x)) {
                    continue;
                }
                comp++;
                for (Integer nb : g.getOrDefault(x, List.of())) {
                    if (!seen.contains(nb)) {
                        stack.push(nb);
                    }
                }
            }
            best = Math.max(best, comp);
        }
        return best;
    }

    /**
     * Наибольшая цепочка зданий из {@code pool}, примыкающих друг к другу
     * стенкой (на разных гексах). Здания на одном гексе в графе не участвуют —
     * примыкание им не касается: они и так соседи.
     */
    public static int largestWallChain(GameState s, List<BuildingToken> pool) {
        Map<Integer, List<Integer>> g = new HashMap<>();
        for (BuildingToken b : pool) {
            g.put(b.uid, new java.util.ArrayList<>());
        }
        for (int i = 0; i < pool.size(); i++) {
            for (int k = i + 1; k < pool.size(); k++) {
                BuildingToken a = pool.get(i);
                BuildingToken b = pool.get(k);
                if (!a.hexId.equals(b.hexId) && abutsAcrossWall(s, a, b)) {
                    g.get(a.uid).add(b.uid);
                    g.get(b.uid).add(a.uid);
                }
            }
        }
        return largestComponent(g, g.keySet());
    }

    /** Обход поля в глубину без повтора выбранных и соседних с уже выбранными гексов. */
    public static boolean chooseNonAdjacent(GameState s, List<String> pool, int from,
                                            List<String> picked, int need) {
        if (picked.size() >= need) {
            return true;
        }
        if (pool.size() - from < need - picked.size()) {
            return false;
        }
        for (int i = from; i < pool.size(); i++) {
            String cand = pool.get(i);
            boolean ok = true;
            for (String taken : picked) {
                if (taken.equals(cand) || s.field.neighbors(taken).contains(cand)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            picked.add(cand);
            if (chooseNonAdjacent(s, pool, i + 1, picked, need)) {
                return true;
            }
            picked.remove(picked.size() - 1);
        }
        return false;
    }
}
