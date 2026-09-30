package kelium.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.Token;

/**
 * CellGraph — НЕПРЕРЫВНОЕ СОЕДИНЕНИЕ ЖЕТОНОВ (правило дизайнера 12.08.2026).
 *
 * <p>Рисунок супер задания требует непрерывного соединения объектов по
 * секторам, а не по гексам. Само соседство — в {@link Соседство} (одно на весь
 * движок с 30.09.2026); здесь только то, что добавляет рисунок: АВИАЦИЯ в небе
 * гекса связывает между собой ВСЕ наземные жетоны этого гекса, даже стоящие
 * напротив друг друга, — жетон лежит в центре и касается всех секторов.
 *
 * <p>Допущение, требующее подтверждения дизайнера: небо связывает наземные
 * жетоны независимо от того, ЧЬЯ авиация в нём стоит (чей жетон — на
 * геометрию не влияет). Если связывать должна только СВОЯ авиация, поменять
 * {@link #bridgesAir}.
 */
public final class CellGraph {

    private CellGraph() {
    }

    /**
     * Связаны ли жетоны напрямую: они соседствуют, либо их связывает авиация в
     * небе общего гекса.
     */
    public static boolean linked(GameState s, Token x, Token y) {
        if (Соседство.соседствуют(s, x, y)) {
            return true;
        }
        for (Соседство.Место a : Соседство.места(s, x)) {
            for (Соседство.Место b : Соседство.места(s, y)) {
                if (!a.небо() && !b.небо() && a.hexId().equals(b.hexId())
                        && bridgesAir(s, a.hexId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Есть ли в небе гекса авиация (она связывает наземные жетоны гекса). */
    public static boolean bridgesAir(GameState s, String hexId) {
        return Соседство.авиацияВНебе(s, hexId);
    }

    /**
     * Образуют ли жетоны НЕПРЕРЫВНОЕ соединение — связный граф по
     * {@link #linked}. Пустой набор и одиночный жетон считаются связными.
     */
    public static boolean connected(GameState s, List<Token> tokens) {
        if (tokens.size() <= 1) {
            return true;
        }
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            adj.put(i, new ArrayList<>());
        }
        for (int i = 0; i < tokens.size(); i++) {
            for (int j = i + 1; j < tokens.size(); j++) {
                if (linked(s, tokens.get(i), tokens.get(j))) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        seen.add(0);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int nb : adj.get(cur)) {
                if (seen.add(nb)) {
                    queue.add(nb);
                }
            }
        }
        return seen.size() == tokens.size();
    }

    /** Все жетоны игрока на поле (здания и войска). */
    public static List<Token> ownTokens(GameState s, int seat) {
        return Соседство.жетоны(s, seat);
    }
}
