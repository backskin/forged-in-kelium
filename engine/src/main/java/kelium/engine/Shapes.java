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
            addUnitCells(s, u, out);
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
     * СЕКТОРЫ ОДНОГО ВОЙСКА. Берутся из раскладки {@link СекторыВойск}: разметка
     * гекса ({@code sideOwner}) хранит только здания и стенки нейтралов, а
     * наземные войска раскладываются по свободным секторам выводом из состояния.
     *
     * <p>ПОЧЕМУ ОТДЕЛЬНЫЙ ПУТЬ ДЛЯ ВОЙСК. До 25.08.2026 войска шли тем же
     * {@code addCellsOf}, что и здания, — то есть искали свой uid в sideOwner,
     * куда их никто не писал. Узлов не появлялось вовсе, и все пять карт про
     * непрерывное соседство были невыполнимы: 451 раздача, ноль выполнений.
     */
    private static void addUnitCells(GameState s, UnitToken u, Set<Node> out) {
        List<Integer> секторы = СекторыВойск.секторыЖетона(s, u);
        if (секторы == null) {
            return;
        }
        for (int i : секторы) {
            out.add(new Node(u.hexId, i));
        }
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

    // ======================================================================
    //  НЕПРЕРЫВНОЕ СОСЕДСТВО, СВЯЗЫВАЮЩЕЕ ЗАДАННЫЕ ГЕКСЫ (ревью 17.08.2026)
    // ======================================================================
    //  Дизайнер: считать надо не жетоны, а СВЯЗЬ. «Линия из четырёх» ничего не
    //  значит — четыре жетона могут стоять кучей у себя дома. Правильная форма
    //  требования — «твои жетоны образуют непрерывное соседство, СВЯЗЫВАЮЩЕЕ вот
    //  эти два (три) гекса»: тогда фигура обязана куда-то тянуться, и её длина
    //  задаётся полем, а не числом на карте.
    //
    //  Что задаёт карта:
    //   what          — из чего строится соседство: any | unit:any | building:any
    //   anchors       — ЧТО связывать: own_miner_hexes | straight_line |
    //                   opposite_around_spawn | field_edge_cells
    //   anchor_count  — сколько опорных гексов должно попасть в одну группу
    //   forbid_kinds  — рода войск, которым в этой группе быть нельзя
    //   require_types — типы зданий, которые в группе быть обязаны
    //   require_count — сколько их должно быть (по умолчанию 1)
    //   require_unit_kinds — рода войск, которые в группе быть обязаны

    /** Проверка предиката {@code chain_connects} — см. комментарий выше. */
    public static boolean chainConnects(GameState s, int seat, Map<String, Object> p) {
        String what = String.valueOf(p.getOrDefault("what", "any"));
        Set<Node> nodes = filterNodes(s, seat, what);
        List<Set<String>> groups = componentsAsHexSets(s, nodes);
        if (groups.isEmpty()) {
            return false;
        }
        int needAnchors = p.get("anchor_count") instanceof Number n ? n.intValue() : 2;
        String kind = String.valueOf(p.getOrDefault("anchors", "own_miner_hexes"));
        for (Set<String> group : groups) {
            if (anchorsHit(s, seat, kind, needAnchors, group)
                    && groupSatisfiesExtras(s, seat, group, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * НАКРЫВАЕТ ЛИ ГРУППА НУЖНЫЕ ОПОРЫ — СЧЁТОМ, А НЕ ПЕРЕБОРОМ НАБОРОВ.
     *
     * <p>БАГ-ФИКС 20.08.2026 (дизайнер: «бесконечная анимация „считаю партию“»).
     * Прежде опоры разворачивались во ВСЕ СОЧЕТАНИЯ нужного размера, и группа
     * сверялась с каждым. Для «двух гексов у края поля» это ещё терпимо, но у
     * новой карты o61 требуется ТРИ края: на поле их около тридцати, значит
     * C(30,3) — четыре тысячи наборов. А спрашивают это не раз в ход, а на КАЖДЫЙ
     * рассматриваемый выбор бота (наведение по заданиям зовёт progress у каждой
     * карты руки дважды), то есть миллионы проверок за партию — расчёт вставал
     * насмерть.
     *
     * <p>Перебор был не нужен: «группа накрывает хотя бы один набор из k опор»
     * равносильно «в группе есть хотя бы k опорных гексов». Считаем — и получаем
     * тот же ответ за один проход по группе.
     *
     * <p>Два вида опор считать простым числом НЕЛЬЗЯ, и они разобраны отдельно:
     * у «своё здание и чужое» опоры берутся из ДВУХ разных множеств (две своих
     * базы не годятся), а «прямая» и «вокруг зарождения» — это не количество, а
     * взаимное расположение.
     */
    private static boolean anchorsHit(GameState s, int seat, String kind, int need,
                                      Set<String> group) {
        switch (kind) {
            case "own_miner_hexes" -> {
                Set<String> mine = new java.util.LinkedHashSet<>(minerHexes(s, seat));
                mine.retainAll(group);
                return mine.size() >= need;
            }
            case "field_edge_cells" -> {
                int n = 0;
                for (String id : group) {
                    Hex h = s.field.hexes.get(id);
                    if (h == null) {
                        continue;
                    }
                    for (int side = 0; side < 6; side++) {
                        if (h.neighborBySide[side] == null) {
                            n++;
                            break;
                        }
                    }
                    if (n >= need) {
                        return true;
                    }
                }
                return n >= need;
            }
            case "start_spawn_adjacent" -> {
                Set<String> near = new java.util.LinkedHashSet<>();
                for (Hex h : s.field.hexes.values()) {
                    if (h.spawnTile == null || !h.spawnTile.isStart) {
                        continue;
                    }
                    for (int side = 0; side < 6; side++) {
                        String nb = h.neighborBySide[side];
                        if (nb != null) {
                            near.add(nb);
                        }
                    }
                }
                near.retainAll(group);
                return near.size() >= need;
            }
            case "own_and_enemy_building" -> {
                boolean mine = false;
                boolean theirs = false;
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    mine |= group.contains(b.hexId);
                }
                for (PlayerState other : s.players) {
                    if (other.seat == seat) {
                        continue;
                    }
                    for (BuildingToken b : other.buildingsOnField()) {
                        theirs |= group.contains(b.hexId);
                    }
                }
                return mine && theirs;
            }
            case "straight_line" -> {
                // РАСПОЛОЖЕНИЕ, А НЕ КОЛИЧЕСТВО: ищем прямую из need гексов,
                // целиком лежащую в группе. Перебор идёт по гексам ГРУППЫ, а не
                // по всему полю, поэтому он мал.
                for (String id : group) {
                    Hex h = s.field.hexes.get(id);
                    if (h == null) {
                        continue;
                    }
                    for (int dir = 0; dir < 6; dir++) {
                        int len = 0;
                        Hex at = h;
                        while (at != null && group.contains(at.id) && len < need) {
                            len++;
                            String nb = at.neighborBySide[dir];
                            at = nb == null ? null : s.field.get(nb);
                        }
                        if (len >= need) {
                            return true;
                        }
                    }
                }
                return false;
            }
            case "opposite_around_spawn" -> {
                for (Hex h : s.field.hexes.values()) {
                    if (!h.hasSpawnTile()) {
                        continue;
                    }
                    for (int dir = 0; dir < 3; dir++) {
                        String a = h.neighborBySide[dir];
                        String b = h.neighborBySide[(dir + 3) % 6];
                        if (a != null && b != null
                                && group.contains(a) && group.contains(b)) {
                            return true;
                        }
                    }
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** Узлы игрока, отфильтрованные по {@code what} с карты. */
    private static Set<Node> filterNodes(GameState s, int seat, String what) {
        if ("any".equals(what)) {
            return ownNodes(s, seat);
        }
        boolean unitsOnly = what.startsWith("unit");
        Set<Node> out = new HashSet<>();
        PlayerState p = s.player(seat);
        if (unitsOnly) {
            for (UnitToken u : p.units) {
                if (u.hexId == null || !u.alive()) {
                    continue;
                }
                if (u.type == UnitType.AIRCRAFT) {
                    out.add(new Node(u.hexId, -1));
                } else {
                    addUnitCells(s, u, out);
                }
            }
        } else {
            for (BuildingToken b : p.buildings) {
                if (b.hexId != null && b.alive()) {
                    addCellsOf(s, b.hexId, b.uid, out);
                }
            }
        }
        return out;
    }

    /** Связные компоненты подграфа, приведённые к множествам гексов. */
    private static List<Set<String>> componentsAsHexSets(GameState s, Set<Node> nodes) {
        List<Node> list = new ArrayList<>(nodes);
        Map<Node, List<Node>> g = new HashMap<>();
        for (Node n : list) {
            g.put(n, new ArrayList<>());
        }
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (neighbours(s, list.get(i), list.get(j))) {
                    g.get(list.get(i)).add(list.get(j));
                    g.get(list.get(j)).add(list.get(i));
                }
            }
        }
        List<Set<String>> out = new ArrayList<>();
        Set<Node> seen = new HashSet<>();
        for (Node start : list) {
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
            out.add(hexes);
        }
        return out;
    }

    /**
     * ВАРИАНТЫ ОПОРНЫХ НАБОРОВ: карта называет ВИД опоры, а конкретных наборов на
     * поле бывает много (три гекса по прямой можно провести где угодно). Группа
     * засчитывается, если она накрывает хотя бы один набор целиком.
     */
    private static List<Set<String>> anchorSets(GameState s, int seat, String kind, int count) {
        List<Set<String>> out = new ArrayList<>();
        switch (kind) {
            case "own_miner_hexes" -> {
                // Два РАЗНЫХ гекса, где стоят твои добытчики.
                List<String> hexes = new ArrayList<>(new java.util.LinkedHashSet<>(
                    minerHexes(s, seat)));
                combinations(hexes, count, out);
            }
            case "straight_line" -> {
                // count гексов подряд в одном направлении: h, h+d, h+2d, …
                for (Hex h : s.field.hexes.values()) {
                    for (int dir = 0; dir < 6; dir++) {
                        Set<String> line = new java.util.LinkedHashSet<>();
                        Hex at = h;
                        while (at != null && line.size() < count) {
                            line.add(at.id);
                            String nb = at.neighborBySide[dir];
                            at = nb == null ? null : s.field.get(nb);
                        }
                        if (line.size() == count) {
                            out.add(line);
                        }
                    }
                }
            }
            case "opposite_around_spawn" -> {
                // Два гекса, лежащих по РАЗНЫЕ стороны одного тайла зарождения.
                for (Hex h : s.field.hexes.values()) {
                    if (!h.hasSpawnTile()) {
                        continue;
                    }
                    for (int dir = 0; dir < 3; dir++) {
                        String a = h.neighborBySide[dir];
                        String b = h.neighborBySide[(dir + 3) % 6];
                        if (a != null && b != null) {
                            out.add(new HashSet<>(List.of(a, b)));
                        }
                    }
                }
            }
            case "field_edge_cells" -> {
                // Гексы у края поля: хотя бы одно ребро без соседнего гекса.
                List<String> edge = new ArrayList<>();
                for (Hex h : s.field.hexes.values()) {
                    for (int side = 0; side < 6; side++) {
                        if (h.neighborBySide[side] == null) {
                            edge.add(h.id);
                            break;
                        }
                    }
                }
                combinations(edge, count, out);
            }
            case "start_spawn_adjacent" -> {
                // ГЕКСЫ, ПРИМЫКАЮЩИЕ К СТАРТОВЫМ ЗАРОЖДЕНИЯМ (заказ дизайнера
                // 19.08.2026). Именно к СТАРТОВЫМ, а не к любым: стартовые стоят
                // у баз игроков, и фигура через них — это дорога между чужими
                // дворами, а не случайная связка в середине поля.
                java.util.LinkedHashSet<String> near = new java.util.LinkedHashSet<>();
                for (Hex h : s.field.hexes.values()) {
                    if (h.spawnTile == null || !h.spawnTile.isStart) {
                        continue;
                    }
                    for (int side = 0; side < 6; side++) {
                        String nb = h.neighborBySide[side];
                        if (nb != null) {
                            near.add(nb);
                        }
                    }
                }
                combinations(new ArrayList<>(near), count, out);
            }
            case "own_and_enemy_building" -> {
                // ОДНО ИЗ СВОИХ ЗДАНИЙ И ЗДАНИЕ ПРОТИВНИКА (заказ дизайнера
                // 19.08.2026). Опора здесь не список одинаковых гексов, а ПАРА
                // из двух разных множеств, поэтому перебираются пары «свой гекс
                // × чужой гекс», а не сочетания из общего списка: сочетание
                // могло бы дать две свои базы и требование стало бы другим.
                List<String> mine = new ArrayList<>();
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    mine.add(b.hexId);
                }
                List<String> theirs = new ArrayList<>();
                for (PlayerState other : s.players) {
                    if (other.seat == seat) {
                        continue;
                    }
                    for (BuildingToken b : other.buildingsOnField()) {
                        theirs.add(b.hexId);
                    }
                }
                for (String a : mine) {
                    for (String b : theirs) {
                        if (!a.equals(b)) {
                            out.add(new java.util.LinkedHashSet<>(List.of(a, b)));
                        }
                    }
                }
            }
            default -> { }
        }
        return out;
    }

    private static List<String> minerHexes(GameState s, int seat) {
        List<String> out = new ArrayList<>();
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.type == kelium.core.BuildingType.MINER) {
                out.add(b.hexId);
            }
        }
        return out;
    }

    private static void combinations(List<String> pool, int k, List<Set<String>> out) {
        if (pool.size() < k) {
            return;
        }
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) {
            idx[i] = i;
        }
        while (true) {
            Set<String> pick = new HashSet<>();
            for (int i : idx) {
                pick.add(pool.get(i));
            }
            if (pick.size() == k) {
                out.add(pick);
            }
            int i = k - 1;
            while (i >= 0 && idx[i] == pool.size() - k + i) {
                i--;
            }
            if (i < 0) {
                return;
            }
            idx[i]++;
            for (int j = i + 1; j < k; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
    }

    /** Запреты и обязательные участники соседства (усиления карт). */
    private static boolean groupSatisfiesExtras(GameState s, int seat, Set<String> hexes,
                                                Map<String, Object> p) {
        PlayerState pl = s.player(seat);
        Set<String> forbid = codes(p.get("forbid_kinds"));
        Set<String> needUnits = codes(p.get("require_unit_kinds"));
        Set<String> needTypes = codes(p.get("require_types"));
        int needCount = p.get("require_count") instanceof Number n ? n.intValue() : 1;

        // ДЛИНА ФИГУРЫ — сколько гексов она охватывает (заказ дизайнера
        // 19.08.2026: «длина которого не менее 4», усиление «не менее 6»).
        // Меряется в ГЕКСАХ, а не в ячейках: за столом игрок считает клетки
        // поля, а не половинки гексов, и правило должно совпадать с тем, что он
        // видит.
        if (p.get("min_hexes") instanceof Number mh && hexes.size() < mh.intValue()) {
            return false;
        }

        Set<String> unitKinds = new HashSet<>();
        Map<String, Integer> buildingTypes = new HashMap<>();
        int unitsInside = 0;
        for (UnitToken u : pl.unitsOnField()) {
            if (hexes.contains(u.hexId)) {
                unitKinds.add(u.type.code);
                unitsInside++;
            }
        }
        // СКОЛЬКО ЖЕТОНОВ ВОЙСК УЧАСТВУЕТ (заказ дизайнера 19.08.2026: усиление
        // «в соседстве принимает участие не менее 3 жетонов войск»). Считаются
        // жетоны, а не рода: три пехоты — это три жетона, и требование про
        // количество, а не про разнообразие (для разнообразия есть
        // require_unit_kinds).
        if (p.get("min_units") instanceof Number mu && unitsInside < mu.intValue()) {
            return false;
        }
        for (BuildingToken b : pl.buildingsOnField()) {
            if (hexes.contains(b.hexId)) {
                buildingTypes.merge(b.type.code, 1, Integer::sum);
            }
        }
        for (String bad : forbid) {
            if (unitKinds.contains(bad)) {
                return false;
            }
        }
        if (!unitKinds.containsAll(needUnits)) {
            return false;
        }
        for (String t : needTypes) {
            if (buildingTypes.getOrDefault(t, 0) < needCount) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> codes(Object listObj) {
        Set<String> out = new HashSet<>();
        if (listObj instanceof List<?> l) {
            for (Object o : l) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /**
     * ПОЛОСА ИЗ ШЕСТИ ЯЧЕЕК НА ДВУХ ГЕКСАХ ВДОЛЬ ПРЯМОЙ (рисунок дизайнера
     * 19.08.2026): три смежные ячейки на одном гексе плюс три смежные на
     * соседнем, и переход между половинами идёт через ОБЩЕЕ РЕБРО этих гексов.
     *
     * <p>ПОЧЕМУ ЭТО НЕ ПАРАМЕТР К {@link #chainConnects}. Тот работает на уровне
     * ГЕКСОВ: он ищет связную группу и проверяет, накрыла ли она опорные гексы.
     * Здесь же важна точность до ЯЧЕЙКИ — какие именно стороны гекса заняты и в
     * каком порядке они идут, — поэтому требование считается своим кодом.
     *
     * <p>КАК ЗАДАНА ПРЯМИЗНА. У гекса A сторона {@code dA} смотрит на гекс B, у
     * B сторона {@code dB} — на A; только эти две ячейки и примыкают через общее
     * ребро (см. {@link #neighbours}). Полоса прямая, если на A три ячейки идут
     * подряд и ЗАКАНЧИВАЮТСЯ на {@code dA}, а на B — НАЧИНАЮТСЯ с {@code dB} и
     * продолжают вращение в ту же сторону. Обе стороны вращения проверяются, то
     * есть фигура распознаётся в любом повороте — но не в отражении, как и
     * просил дизайнер.
     *
     * @return true, если хотя бы одна такая полоса целиком занята жетонами игрока
     */
    public static boolean straightSixCells(GameState s, int seat) {
        return longestStraightBand(s, seat) >= 2;
    }

    /**
     * САМАЯ ДЛИННАЯ ПРЯМАЯ ПОЛОСА игрока, в ГЕКСАХ (каждый даёт три ячейки).
     *
     * <p>ОБЩЕЕ ОПРЕДЕЛЕНИЕ ПРЯМОЙ (заказ дизайнера 19.08.2026: «они проходят
     * через половины гексов и продолжаются на следующих через общую грань»).
     * Полоса — это цепочка ПОЛОВИН: в каждом гексе заняты три ячейки подряд,
     * полоса входит в гекс одной ячейкой и выходит той, что лежит ЧЕРЕЗ ДВЕ
     * стороны от входа в ту же сторону вращения; этой же стороной выбирается
     * следующий гекс, а в нём вход — ячейка, смотрящая назад. Направление
     * вращения по всей полосе одно: именно это и делает её прямой, а не
     * извивающейся.
     *
     * <p>Оба направления вращения проверяются, поэтому фигура распознаётся в
     * ЛЮБОМ повороте, но НЕ в отражении — как и просил дизайнер для карт-фигур.
     *
     * @return сколько гексов подряд накрыто половинами; 0, если нет ни одной
     *         полной половины
     */
    public static int longestStraightBand(GameState s, int seat) {
        Set<Node> mine = ownNodes(s, seat);
        if (mine.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (Hex start : s.field.hexes.values()) {
            for (int entry = 0; entry < 6; entry++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    best = Math.max(best, walkBand(s, mine, start, entry, dir, null));
                }
            }
        }
        return best;
    }

    /**
     * ЕСТЬ ЛИ ПРЯМАЯ ПОЛОСА нужной длины, В КОТОРОЙ УЧАСТВУЮТ нужные рода войск.
     *
     * <p>Усиления карт-фигур просят не «есть ли у меня пехота вообще», а «пехота
     * СТОИТ В ЭТОЙ ФИГУРЕ» (заказ дизайнера 19.08.2026: «принимают участие
     * жетоны пехоты и техники»). Поэтому рода проверяются по гексам ИМЕННО той
     * полосы, что дотянулась до нужной длины, а не по всему полю: иначе карта
     * выполнялась бы случайной пехотой в другом углу.
     *
     * @param minHexes    сколько половин должна накрыть полоса
     * @param needKinds   коды родов войск, которые обязаны стоять на полосе
     */
    public static boolean straightBand(GameState s, int seat, int minHexes,
                                       Set<String> needKinds) {
        Set<Node> mine = ownNodes(s, seat);
        if (mine.isEmpty()) {
            return false;
        }
        for (Hex start : s.field.hexes.values()) {
            for (int entry = 0; entry < 6; entry++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    java.util.Set<String> onBand = new java.util.LinkedHashSet<>();
                    int len = walkBand(s, mine, start, entry, dir, onBand);
                    if (len < minHexes) {
                        continue;
                    }
                    if (needKinds == null || needKinds.isEmpty()) {
                        return true;
                    }
                    java.util.Set<String> kinds = new java.util.HashSet<>();
                    for (UnitToken u : s.player(seat).unitsOnField()) {
                        if (onBand.contains(u.hexId)) {
                            kinds.add(u.type.code);
                        }
                    }
                    if (kinds.containsAll(needKinds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Пройти полосу от гекса {@code hex} со входом {@code entry}; вернуть длину.
     *
     * @param bandHexes куда сложить пройденные гексы (может быть null)
     */
    private static int walkBand(GameState s, Set<Node> mine, Hex hex, int entry, int dir,
                                java.util.Set<String> bandHexes) {
        int length = 0;
        java.util.Set<String> visited = new java.util.HashSet<>();
        Hex cur = hex;
        int in = entry;
        while (cur != null && visited.add(cur.id)) {
            // ПОЛОВИНА ГЕКСА — три ячейки подряд от входа в сторону вращения.
            for (int i = 0; i < 3; i++) {
                if (!mine.contains(new Node(cur.id, Math.floorMod(in + dir * i, 6)))) {
                    return length;
                }
            }
            length++;
            if (bandHexes != null) {
                bandHexes.add(cur.id);
            }
            // ВЫХОД — через две стороны от входа: только так половина остаётся
            // прямым коридором, а не поворотом внутри гекса.
            int exit = Math.floorMod(in + dir * 2, 6);
            String nextId = cur.neighborBySide[exit];
            if (nextId == null) {
                return length;
            }
            Hex next = s.field.hexes.get(nextId);
            if (next == null) {
                return length;
            }
            Integer back = null;
            for (int k = 0; k < 6; k++) {
                if (cur.id.equals(next.neighborBySide[k])) {
                    back = k;
                    break;
                }
            }
            if (back == null) {
                return length;
            }
            cur = next;
            in = back;
        }
        return length;
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
