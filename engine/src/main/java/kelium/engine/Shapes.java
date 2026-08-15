package kelium.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * ФИГУРЫ ИЗ ЖЕТОНОВ НА ПОЛЕ — распознавание рисунков для карт заданий и арсенала.
 *
 * <p>Заказ дизайнера 12.08.2026: на карте задания нарисована гексовая сетка и
 * линиями показано, через какие ячейки должна проходить фигура. Фигуру можно
 * ВРАЩАТЬ и СДВИГАТЬ, но НЕЛЬЗЯ ОТРАЖАТЬ. Задача движка — понять, лежит ли на поле
 * такая непрерывная цепочка своих жетонов.
 *
 * <p>ЧТО СЧИТАЕТСЯ СОСЕДСТВОМ. Термины дизайнера (13.08.2026):
 * <ul>
 *   <li><b>соседствует</b> — жетон на любой соседней ЯЧЕЙКЕ: и внутри того же
 *       гекса, и на примыкающей ячейке соседнего гекса;</li>
 *   <li><b>примыкает</b> — только соседняя ячейка ЧУЖОГО гекса, имеющая с данной
 *       общее ребро двух гексов.</li>
 * </ul>
 * Фигуры чертятся по СОСЕДСТВУ.
 *
 * <p>ЧЕМ ЭТО СЛОЖНЕЕ ОБЫЧНОГО ПОИСКА ПУТИ. Три особенности жетонов:
 * <ul>
 *   <li>техника занимает ДВЕ смежные ячейки — один жетон закрывает два узла;</li>
 *   <li>авиация стоит в центральной воздушной ячейке и соседствует со ВСЕМИ
 *       наземными ячейками своего гекса, даже не смежными между собой;</li>
 *   <li>здания занимают свой след из ячеек, как и техника.</li>
 * </ul>
 * Поэтому фигура ищется не по гексам, а по ЯЧЕЙКАМ, и авиация работает
 * «перемычкой» внутри гекса.
 *
 * <p>ЧТО ЗДЕСЬ ЕСТЬ СЕЙЧАС: длина непрерывной цепочки ({@link #longestChain}) и
 * замкнутое кольцо ({@link #hasClosedRing}). Этого достаточно для первых карт
 * («линия из 5-6 ячеек», «замкнутая фигура»); совмещение с КОНКРЕТНЫМ рисунком с
 * карты будет добавлено, когда дизайнер нарисует сами карты — формат рисунка
 * тогда и определится.
 */
public final class Shapes {

    private Shapes() {
    }

    /**
     * Узел фигуры: гекс и номер ЯЧЕЙКИ внутри него.
     *
     * <p>Ячейка гекса — это его сторона (их шесть, {@code Hex.sideOwner}); ячейка
     * с номером −1 — центральная воздушная, где стоит авиация.
     */
    public record Node(String hexId, int cell) {
    }

    /**
     * ВСЕ ЯЧЕЙКИ, ЗАНЯТЫЕ ЖЕТОНАМИ ИГРОКА. Техника и здания дают несколько узлов,
     * авиация — воздушный узел.
     */
    public static Set<Node> ownNodes(GameState s, int seat) {
        Set<Node> out = new HashSet<>();
        PlayerState p = s.player(seat);
        for (UnitToken u : p.units) {
            if (u.hexId == null || !u.alive()) {
                continue;
            }
            if (u.type == UnitType.AIRCRAFT) {
                out.add(new Node(u.hexId, -1));
                continue;
            }
            addCellsOf(s, u.hexId, u.uid, out);
        }
        for (BuildingToken b : p.buildings) {
            if (b.hexId == null || !b.alive()) {
                continue;
            }
            addCellsOf(s, b.hexId, b.uid, out);
        }
        return out;
    }

    /**
     * Ячейки, занятые ОДНИМ жетоном. Берём из разметки гекса ({@code sideOwner}):
     * это единственная модель размещения в движке, второй быть не должно. Техника и
     * здания занимают несколько ячеек и дают несколько узлов.
     */
    private static void addCellsOf(GameState s, String hexId, int uid, Set<Node> out) {
        Hex h = s.field.hexes.get(hexId);
        if (h == null) {
            return;
        }
        for (int i = 0; i < h.sideOwner.length; i++) {
            if (h.sideOwner[i] != null && h.sideOwner[i] == uid) {
                out.add(new Node(hexId, i));
            }
        }
    }

    /** СОСЕДСТВО двух узлов — по правилу дизайнера (см. описание класса). */
    public static boolean neighbours(GameState s, Node a, Node b) {
        if (a.equals(b)) {
            return false;
        }
        if (a.hexId().equals(b.hexId())) {
            // Внутри гекса: воздушная ячейка соседствует со всеми наземными,
            // наземные — со смежными по разметке гекса.
            if (a.cell() < 0 || b.cell() < 0) {
                return true;
            }
            // Шесть ячеек по кругу: смежные — те, что стоят рядом по кольцу.
            int d = Math.abs(a.cell() - b.cell());
            return d == 1 || d == 5;
        }
        // Разные гексы: нужна ПРИМЫКАЮЩАЯ пара ячеек через общее ребро.
        if (!s.field.neighbors(a.hexId()).contains(b.hexId())) {
            return false;
        }
        if (a.cell() < 0 || b.cell() < 0) {
            return false;      // воздух за пределы своего гекса не тянется
        }
        Hex ha = s.field.hexes.get(a.hexId());
        Hex hb = s.field.hexes.get(b.hexId());
        if (ha == null || hb == null) {
            return false;
        }
        // ПРИМЫКАНИЕ: ячейка-сторона смотрит ровно на один соседний гекс. Значит
        // ячейки примыкают, если сторона A смотрит на гекс B, а сторона B — на A:
        // это и есть общее ребро двух гексов.
        return ha.sidesFacing(b.hexId()).contains(a.cell())
            && hb.sidesFacing(a.hexId()).contains(b.cell());
    }

    /** Граф соседства по занятым ячейкам игрока. */
    private static Map<Node, List<Node>> graph(GameState s, int seat) {
        List<Node> nodes = new ArrayList<>(ownNodes(s, seat));
        Map<Node, List<Node>> g = new HashMap<>();
        for (Node n : nodes) {
            g.put(n, new ArrayList<>());
        }
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (neighbours(s, nodes.get(i), nodes.get(j))) {
                    g.get(nodes.get(i)).add(nodes.get(j));
                    g.get(nodes.get(j)).add(nodes.get(i));
                }
            }
        }
        return g;
    }

    /**
     * САМАЯ ДЛИННАЯ НЕПРЕРЫВНАЯ ЦЕПОЧКА своих жетонов (в ячейках).
     *
     * <p>Это простой путь без повторов — то, что на карте задания нарисовано
     * линией. Перебор полный с отсечением по достигнутому максимуму: узлов у
     * игрока в этой игре десятки, а не тысячи, поэтому точный ответ дешевле, чем
     * приближённый и спорный.
     */
    public static int longestChain(GameState s, int seat) {
        Map<Node, List<Node>> g = graph(s, seat);
        int best = 0;
        for (Node start : g.keySet()) {
            best = Math.max(best, walk(g, start, new HashSet<>()));
        }
        return best;
    }

    private static int walk(Map<Node, List<Node>> g, Node at, Set<Node> seen) {
        seen.add(at);
        int best = seen.size();
        for (Node next : g.getOrDefault(at, List.of())) {
            if (!seen.contains(next)) {
                best = Math.max(best, walk(g, next, seen));
            }
        }
        seen.remove(at);
        return best;
    }

    /**
     * ЕСТЬ ЛИ ЗАМКНУТОЕ КОЛЬЦО длиной не меньше {@code minLength}.
     *
     * <p>Кольцо — цикл в графе соседства: цепочка, вернувшаяся в начало. Именно
     * это дизайнер называет «замкнутой фигурой» на карте арсенала.
     */
    public static boolean hasClosedRing(GameState s, int seat, int minLength) {
        Map<Node, List<Node>> g = graph(s, seat);
        for (Node start : g.keySet()) {
            if (ring(g, start, start, new HashSet<>(), 0, minLength)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ring(Map<Node, List<Node>> g, Node start, Node at,
                                Set<Node> seen, int depth, int minLength) {
        seen.add(at);
        for (Node next : g.getOrDefault(at, List.of())) {
            if (next.equals(start) && depth + 1 >= minLength) {
                seen.remove(at);
                return true;
            }
            if (!seen.contains(next)
                    && ring(g, start, next, seen, depth + 1, minLength)) {
                seen.remove(at);
                return true;
            }
        }
        seen.remove(at);
        return false;
    }

    /**
     * СКОЛЬКО ГЕКСОВ занимает связная группа жетонов игрока — грубая мера
     * «фронта», нужная заданиям вида «твои жетоны стоят непрерывно от края до
     * края». Считается по тем же узлам, но с выходом на уровень гексов.
     */
    public static int largestConnectedHexes(GameState s, int seat) {
        Map<Node, List<Node>> g = graph(s, seat);
        Set<Node> seen = new HashSet<>();
        int best = 0;
        for (Node start : g.keySet()) {
            if (seen.contains(start)) {
                continue;
            }
            Set<String> hexes = new HashSet<>();
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                Node at = queue.poll();
                hexes.add(at.hexId());
                for (Node next : g.getOrDefault(at, List.of())) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            best = Math.max(best, hexes.size());
        }
        return best;
    }
}
