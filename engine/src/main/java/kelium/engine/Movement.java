package kelium.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.UnitToken;

/**
 * Movement — ЕДИНСТВЕННЫЙ источник правды о том, куда войско может пройти.
 *
 * <p>Раньше проходимость была написана ЧЕТЫРЕ раза и по-разному: полная
 * проверка в действии Движения, два поиска пути в ботах (вообще без правил —
 * ни стенок, ни тайлов, ни вместимости) и третий вариант в предикатах заданий.
 * Из-за этого бот планировал ход в гекс, куда физически не может войти, а
 * задания мерили расстояние не так, как ходят войска.
 *
 * <p>Здесь одна проверка {@link #canEnter} и один поиск пути {@link #distance},
 * которым пользуются и движок, и боты, и предикаты.
 */
public final class Movement {

    private Movement() {
    }

    /**
     * Может ли {@code unit} игрока {@code seat} ВОЙТИ на гекс {@code hexId}
     * (и остановиться там). Правила: запретный гекс, воздушная ячейка, тайл
     * зарождения, по-сторонняя блокировка стенками (§12.3) и умная переупаковка
     * стоящих войск.
     */
    public static boolean canEnter(GameState state, UnitToken unit, String hexId, int seat) {
        return Actions.MovementAction.canEnterHex(state, unit, hexId, seat);
    }

    /**
     * Проходим ли гекс «вообще» — для грубых прикидок расстояния, где нет
     * конкретного жетона: не запретный и не занят тайлом зарождения.
     */
    public static boolean passable(GameState state, String hexId) {
        Hex h = state.field.get(hexId);
        return h != null && h.kind != HexKind.FORBIDDEN && !h.hasSpawnTile();
    }

    /**
     * Кратчайшее расстояние в шагах от {@code from} до ближайшего из
     * {@code targets}. Ходить можно только по проходимым гексам; САМИ цели
     * достижимы, даже если стоят на непроходимом гексе (по ним бьют, а не
     * входят). null — пути нет.
     *
     * <p>Это «прикидка на карте», а не проверка конкретного жетона: ей меряют
     * дистанцию боты и предикаты заданий. Точную возможность войти отвечает
     * {@link #canEnter}.
     */
    public static Integer distance(GameState state, String from, Collection<String> targets) {
        if (from == null || targets == null || targets.isEmpty()) {
            return null;
        }
        Set<String> goal = new HashSet<>(targets);
        if (goal.contains(from)) {
            return 0;
        }
        Map<String, Integer> seen = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = seen.get(cur);
            for (String nb : state.field.neighborsView(cur)) {
                if (seen.containsKey(nb)) {
                    continue;
                }
                if (goal.contains(nb)) {
                    return d + 1;
                }
                if (!passable(state, nb)) {
                    continue;
                }
                seen.put(nb, d + 1);
                queue.add(nb);
            }
        }
        return null;
    }

    /**
     * Куда этот жетон может шагнуть ПРЯМО СЕЙЧАС (один шаг). Список в порядке
     * обхода соседей — порядок важен для воспроизводимости партий по сиду.
     */
    public static List<String> stepsFrom(GameState state, UnitToken unit, int seat) {
        List<String> out = new ArrayList<>();
        if (unit.hexId == null) {
            return out;
        }
        for (String nb : state.field.neighbors(unit.hexId)) {
            if (canEnter(state, unit, nb, seat)) {
                out.add(nb);
            }
        }
        return out;
    }
}
