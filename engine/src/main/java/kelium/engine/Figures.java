package kelium.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;

/**
 * Figures — РИСУНОК ИЗ ЖЕТОНОВ НА ПОЛЕ как условие карты.
 *
 * <p>Задания и вторая часть супер заданий требуют не «столько-то жетонов», а
 * ФИГУРУ: линию из трёх, треугольник, «уголок», ромб. Фигура задана списком
 * смещений в осевых координатах от опорного гекса.
 *
 * <p><b>ФИГУРУ МОЖНО ТОЛЬКО ПОВОРАЧИВАТЬ (правило дизайнера 13.08.2026).</b>
 * Отражать нельзя. Это важное различие: на гексовой сетке зеркальное отражение
 * «уголка» — это ДРУГАЯ фигура, и живой игрок за столом видит разницу, потому что
 * карту он крутит, а вот перевернуть её лицом вниз не может. Поэтому проверяются
 * ровно шесть положений — шесть поворотов на 60°.
 *
 * <p><b>Как фигура записана.</b> У гекса в движке нет координат — поле связано
 * ПО СТОРОНАМ (0..5, те же направления, что {@code Field.AXIAL_DIRS}). Поэтому
 * фигура — это набор ПУТЕЙ от опорного гекса: каждый путь список номеров сторон.
 * Пустой путь — сам опорный гекс. Тогда поворот фигуры на 60° — это просто
 * прибавка единицы к каждому номеру стороны, и шесть прибавок дают полный набор
 * положений. Отражение потребовало бы смены знака направлений, и его здесь нет
 * намеренно.
 *
 * <p>Формат в данных карты:
 * <pre>
 *   figure:
 *     cells: [[], [0], [0,0]]      # линия из трёх: опора, шаг в сторону 0, ещё шаг
 *     what: "unit:any"             # что должно стоять в каждом гексе фигуры
 *     name: "линия из трёх"        # для описания в отчётах и в проигрывателе
 * </pre>
 *
 * <p>Примеры: треугольник — {@code [[], [0], [1]]}; уголок — {@code [[], [0], [0,1]]};
 * линия из четырёх — {@code [[], [0], [0,0], [0,0,0]]}.
 *
 * <p>Проверка полная: перебираются все гексы поля как опора и все шесть поворотов.
 * Поле маленькое (20–30 гексов), фигуры короткие (3–5 гексов), поэтому перебор
 * дешевле любой эвристики и не может «почти найти» решение.
 */
public final class Figures {

    private Figures() {
    }

    /**
     * Повернуть фигуру на {@code turn} шагов по 60°: каждый номер стороны в каждом
     * пути сдвигается на turn. Отражения нет — знаки направлений не меняются.
     */
    public static List<int[]> turned(List<int[]> paths, int turn) {
        List<int[]> out = new ArrayList<>(paths.size());
        for (int[] path : paths) {
            int[] t = new int[path.length];
            for (int i = 0; i < path.length; i++) {
                t[i] = Math.floorMod(path[i] + turn, 6);
            }
            out.add(t);
        }
        return out;
    }

    /** Пути фигуры из данных карты (каждый путь — список номеров сторон). */
    public static List<int[]> cellsOf(Map<String, Object> figure) {
        List<int[]> out = new ArrayList<>();
        if (figure == null || !(figure.get("cells") instanceof List<?> raw)) {
            return out;
        }
        for (Object o : raw) {
            if (!(o instanceof List<?> path)) {
                continue;
            }
            int[] steps = new int[path.size()];
            for (int i = 0; i < path.size(); i++) {
                steps[i] = path.get(i) instanceof Number n ? n.intValue() : 0;
            }
            out.add(steps);
        }
        return out;
    }

    /**
     * Выполнена ли фигура у игрока {@code seat}.
     *
     * <p>«Выполнена» = нашлись опорный гекс и поворот, при которых В КАЖДОМ гексе
     * фигуры стоит подходящий жетон этого игрока. Гексов вне поля быть не должно:
     * фигура, часть которой свисает за край, не считается выполненной — за столом
     * там просто нет картона.
     */
    public static boolean satisfied(GameState s, int seat, Map<String, Object> figure) {
        List<int[]> cells = cellsOf(figure);
        if (cells.isEmpty()) {
            return false;   // фигуры нет — это ошибка данных, а не «победа даром»
        }
        String what = figure.get("what") == null ? "unit:any"
            : String.valueOf(figure.get("what"));
        Set<String> owned = hexesWith(s, seat, what);
        if (owned.size() < cells.size()) {
            return false;
        }
        // Опора — только гекс, где уже стоит подходящий жетон: остальные заведомо
        // не дадут фигуры, и перебор от них — пустая работа.
        for (String anchor : owned) {
            for (int turn = 0; turn < 6; turn++) {
                if (fits(s, anchor, turned(cells, turn), owned)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ложится ли фигура на поле из этой опоры при этом повороте. */
    private static boolean fits(GameState s, String anchor, List<int[]> paths,
                                Set<String> owned) {
        for (int[] path : paths) {
            String cur = anchor;
            for (int side : path) {
                Hex h = s.field.get(cur);
                cur = h == null ? null : h.neighborBySide[side];
                if (cur == null) {
                    return false;   // путь свисает за край поля — фигуры нет
                }
            }
            if (!owned.contains(cur)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Гексы, где у игрока стоит подходящий жетон. Виды {@code what}:
     * {@code unit:any}, {@code unit:<род>}, {@code building:any},
     * {@code building:<тип>}, {@code any} (любой свой жетон).
     */
    private static Set<String> hexesWith(GameState s, int seat, String what) {
        Set<String> out = new LinkedHashSet<>();
        PlayerState p = s.player(seat);
        String kind = what.contains(":") ? what.substring(0, what.indexOf(':')) : what;
        String sub = what.contains(":") ? what.substring(what.indexOf(':') + 1) : "any";
        boolean anyKind = "any".equals(kind);
        if (anyKind || "unit".equals(kind)) {
            for (UnitToken u : p.unitsOnField()) {
                if ("any".equals(sub) || sub.equalsIgnoreCase(u.type.code)) {
                    out.add(u.hexId);
                }
            }
        }
        if (anyKind || "building".equals(kind)) {
            for (BuildingToken b : p.buildingsOnField()) {
                if ("any".equals(sub) || sub.equalsIgnoreCase(b.type.code)) {
                    out.add(b.hexId);
                }
            }
        }
        return out;
    }

    /** Человеческое описание фигуры — для отчётов и проигрывателя. */
    public static String describe(Map<String, Object> figure) {
        if (figure == null) {
            return "фигуры нет";
        }
        String name = figure.get("name") == null ? null : String.valueOf(figure.get("name"));
        int n = cellsOf(figure).size();
        String what = figure.get("what") == null ? "unit:any"
            : String.valueOf(figure.get("what"));
        return (name != null ? name : "фигура из " + n + " гексов")
            + " (" + what + ", можно поворачивать, отражать нельзя)";
    }

    // ==================================================================
    //  УЗОР ИЗ СЕКТОРОВ (дизайнер 03.10.2026)
    // ==================================================================

    /**
     * Сколько секторов узора занято жетонами игрока при лучшем положении; −1 —
     * узор не ложится на поле ни при одной опоре и повороте.
     *
     * <p>Запись узора — как у узла языка карт «узор»: {@code клетки: [{путь:
     * [стороны…], секторы: [0..5]}]}. Поворот на 60° — прибавка к номерам и
     * сторон пути, и секторов; отражения нет.
     */
    public static int sectorsBest(GameState s, int seat, Map<?, ?> узор) {
        List<int[]> пути = new ArrayList<>();
        List<int[]> секторы = new ArrayList<>();
        if (узор.get("клетки") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> к) {
                    пути.add(ints(к.get("путь")));
                    секторы.add(ints(к.get("секторы")));
                }
            }
        }
        if (пути.isEmpty()) {
            return -1;
        }
        Set<Integer> мои = new java.util.HashSet<>();
        PlayerState p = s.player(seat);
        for (BuildingToken b : p.buildingsOnField()) {
            мои.add(b.uid);
        }
        for (UnitToken u : p.unitsOnField()) {
            мои.add(u.uid);
        }
        int best = -1;
        for (String опора : s.field.hexes.keySet()) {
            for (int поворот = 0; поворот < 6; поворот++) {
                int n = sectorsAt(s, опора, поворот, пути, секторы, мои);
                best = Math.max(best, n);
            }
        }
        return best;
    }

    /** Всего секторов в узоре. */
    public static int sectorsTotal(Map<?, ?> узор) {
        int n = 0;
        if (узор.get("клетки") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> к) {
                    n += ints(к.get("секторы")).length;
                }
            }
        }
        return n;
    }

    /** Узор выложен целиком. */
    public static boolean sectorsSatisfied(GameState s, int seat, Map<?, ?> узор) {
        int total = sectorsTotal(узор);
        return total > 0 && sectorsBest(s, seat, узор) >= total;
    }

    private static int sectorsAt(GameState s, String опора, int поворот, List<int[]> пути,
                                 List<int[]> секторы, Set<Integer> мои) {
        int n = 0;
        for (int i = 0; i < пути.size(); i++) {
            String cur = опора;
            for (int side : пути.get(i)) {
                Hex h = s.field.get(cur);
                cur = h == null ? null : h.neighborBySide[Math.floorMod(side + поворот, 6)];
                if (cur == null) {
                    return -1;      // узор свисает за край поля
                }
            }
            Hex h = s.field.get(cur);
            for (int сектор : секторы.get(i)) {
                Integer uid = h.sideOwner[Math.floorMod(сектор + поворот, 6)];
                if (uid != null && мои.contains(uid)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int[] ints(Object o) {
        if (!(o instanceof List<?> l)) {
            return new int[0];
        }
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = l.get(i) instanceof Number n ? n.intValue() : 0;
        }
        return out;
    }
}
