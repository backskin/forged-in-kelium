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
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * CellGraph — СВЯЗНОСТЬ ЖЕТОНОВ ПО ЯЧЕЙКАМ (правило дизайнера 12.08.2026).
 *
 * <p>Рисунок супер задания требует НЕПРЕРЫВНОГО соединения объектов, причём
 * связь считается по ЯЧЕЙКАМ, а не по гексам:
 *
 * <ul>
 *   <li>внутри гекса соседними считаются ячейки, стоящие РЯДОМ по кругу
 *       (i и i±1). Два жетона НАПРОТИВ друг друга (i и i+3) НЕ связаны;</li>
 *   <li>между гексами связь идёт по ОБЩЕМУ РЕБРУ: ячейка стороны i гекса A и
 *       ячейка стороны (i+3) соседнего гекса B лежат на одном ребре и потому
 *       соседние;</li>
 *   <li>АВИАЦИЯ в воздушной ячейке гекса связывает между собой ВСЕ наземные
 *       жетоны этого гекса, даже стоящие напротив друг друга: жетон авиации
 *       лежит в центре и касается всех наземных ячеек.</li>
 * </ul>
 *
 * <p>Допущение, требующее подтверждения дизайнера: воздушная ячейка связывает
 * наземные жетоны независимо от того, ЧЬЯ авиация в ней стоит (жетон физически
 * лежит в центре гекса, чей он — на геометрию не влияет). Если по правилам
 * связывать должна только СВОЯ авиация, поменять {@link #bridgesAir}.
 */
public final class CellGraph {

    private CellGraph() {
    }

    /** Ячейка поля: гекс + номер ячейки (0..5 наземные, 6 воздушная). */
    public record Cell(String hexId, int index) {
        public boolean air() {
            return index == AIR;
        }
    }

    public static final int AIR = 6;

    /** Ячейки, занятые жетоном (наземный жетон может занимать 1–3 ячейки). */
    public static List<Cell> cellsOf(GameState s, Token t) {
        List<Cell> out = new ArrayList<>();
        String hexId = t.hexId();
        if (hexId == null) {
            return out;
        }
        if (t instanceof UnitToken u && u.type == UnitType.AIRCRAFT) {
            out.add(new Cell(hexId, AIR));
            return out;
        }
        Hex h = s.field.get(hexId);
        if (h == null) {
            return out;
        }
        for (int i = 0; i < 6; i++) {
            if (h.sideOwner[i] != null && h.sideOwner[i] == t.uid()) {
                out.add(new Cell(hexId, i));
            }
        }
        // Жетон стоит на гексе, но ячейку за ним никто не записал (так бывает у
        // войск, которых движок не «прибивает» к ячейке). Тогда считаем, что он
        // занимает любую свободную ячейку: связность по гексу сохраняем, но не
        // придумываем ему конкретную сторону — берём все свободные.
        if (out.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                if (h.sideOwner[i] == null) {
                    out.add(new Cell(hexId, i));
                }
            }
        }
        return out;
    }

    /** Соседние ли ячейки: по кругу внутри гекса или по общему ребру гексов. */
    public static boolean adjacent(GameState s, Cell a, Cell b) {
        if (a.equals(b)) {
            return false;
        }
        if (a.hexId.equals(b.hexId)) {
            if (a.air() || b.air()) {
                return true;              // центр касается всех ячеек своего гекса
            }
            int d = Math.floorMod(a.index - b.index, 6);
            return d == 1 || d == 5;      // рядом по кругу; напротив (d == 3) — нет
        }
        if (a.air() || b.air()) {
            return false;                 // воздух связывает только свой гекс
        }
        Hex ha = s.field.get(a.hexId);
        if (ha == null) {
            return false;
        }
        // общее ребро: ячейка стороны i гекса A и ячейка (i+3) соседа B
        return b.hexId.equals(ha.neighborBySide[a.index])
            && b.index == (a.index + 3) % 6;
    }

    /**
     * Связаны ли жетоны напрямую: хоть одна пара их ячеек соседняя, либо их
     * связывает авиация в воздушной ячейке общего гекса.
     */
    public static boolean linked(GameState s, Token x, Token y) {
        List<Cell> cx = cellsOf(s, x);
        List<Cell> cy = cellsOf(s, y);
        for (Cell a : cx) {
            for (Cell b : cy) {
                if (adjacent(s, a, b)) {
                    return true;
                }
            }
        }
        // авиация в центре гекса: все наземные жетоны этого гекса связаны
        for (Cell a : cx) {
            for (Cell b : cy) {
                if (!a.air() && !b.air() && a.hexId.equals(b.hexId)
                        && bridgesAir(s, a.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Есть ли в воздушной ячейке гекса жетон авиации (он связывает наземку). */
    public static boolean bridgesAir(GameState s, String hexId) {
        for (PlayerState p : s.players) {
            for (UnitToken u : p.unitsOnField()) {
                if (u.type == UnitType.AIRCRAFT && hexId.equals(u.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Образуют ли жетоны НЕПРЕРЫВНОЕ соединение — то есть связный граф по
     * правилам соседства ячеек. Пустой набор и одиночный жетон считаются
     * связными.
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

    /** Все жетоны игрока на поле (здания и войска) — удобно для проверок. */
    public static List<Token> ownTokens(GameState s, int seat) {
        List<Token> out = new ArrayList<>();
        PlayerState p = s.player(seat);
        for (BuildingToken b : p.buildingsOnField()) {
            out.add(b);
        }
        for (UnitToken u : p.unitsOnField()) {
            out.add(u);
        }
        return out;
    }
}
