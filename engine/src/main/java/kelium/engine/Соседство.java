package kelium.engine;

import java.util.ArrayList;
import java.util.List;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * СОСЕДСТВО НА ПОЛЕ — одно на весь движок (этап 0 плана «фигуры и синергии»,
 * 30.09.2026).
 *
 * <p>Прежде соседство считали три места ({@link CellGraph}, {@link Shapes},
 * {@link Chains}) и расходились: небо было то ячейкой 6, то −1; войско без
 * выбранного сектора в одном месте стояло «на всех свободных секторах сразу», в
 * другом — там, куда его кладёт {@link СекторыВойск}; примыкание стенкой было
 * написано дважды. Карта, спросившая одно и то же разными путями, получала
 * разные ответы. Теперь все три зовут этот класс.
 *
 * <p>ТЕРМИНЫ (канон 12–13.08.2026, книга гл. 4):
 * <ul>
 *   <li><b>место</b> — сектор гекса (0..5, та же нумерация, что стороны
 *       {@code Hex.neighborBySide}) или небо гекса ({@link #НЕБО});</li>
 *   <li><b>соседствует</b> — соседний сектор того же гекса (рядом по кругу;
 *       напротив — нет) или примыкающий сектор соседнего гекса; небо
 *       соседствует со всеми секторами своего гекса и больше ни с чем;</li>
 *   <li><b>примыкает</b> — только через общее ребро двух РАЗНЫХ гексов:
 *       сектор стороны i гекса A и сектор стороны, смотрящей обратно на A, у
 *       соседа B.</li>
 * </ul>
 *
 * <p>ГДЕ СТОИТ ЖЕТОН. Здание — на секторах своего следа ({@code Hex.sideOwner}).
 * Наземное войско — на своих секторах из {@link СекторыВойск} (по своду 1.46.0
 * войска держат секторы, как здания). Авиация — в небе. Войско внутри здания
 * мест не занимает: его укрывает здание.
 */
public final class Соседство {

    private Соседство() {
    }

    /** Номер неба гекса. Наземные секторы — 0..5. */
    public static final int НЕБО = -1;

    /** Место на поле: гекс и сектор (0..5) или небо ({@link #НЕБО}). */
    public record Место(String hexId, int сектор) {
        public boolean небо() {
            return сектор == НЕБО;
        }
    }

    /** Места, которые занимает жетон. Пусто — жетон не на поле или в здании. */
    public static List<Место> места(GameState s, Token t) {
        List<Место> out = new ArrayList<>();
        String hexId = t.hexId();
        if (hexId == null) {
            return out;
        }
        if (t instanceof UnitToken u) {
            if (!u.alive() || u.inside()) {
                return out;
            }
            if (u.type == UnitType.AIRCRAFT) {
                out.add(new Место(hexId, НЕБО));
                return out;
            }
            List<Integer> секторы = СекторыВойск.секторыЖетона(s, u);
            if (секторы != null) {
                for (int i : секторы) {
                    out.add(new Место(hexId, i));
                }
            }
            return out;
        }
        Hex h = s.field.hexes.get(hexId);
        if (h == null) {
            return out;
        }
        for (int i = 0; i < 6; i++) {
            if (h.sideOwner[i] != null && h.sideOwner[i] == t.uid()) {
                out.add(new Место(hexId, i));
            }
        }
        return out;
    }

    /** ПРИМЫКАЮТ ли места: общее ребро двух разных гексов. Небо не примыкает. */
    public static boolean примыкают(GameState s, Место a, Место b) {
        if (a.небо() || b.небо() || a.hexId().equals(b.hexId())) {
            return false;
        }
        Hex ha = s.field.hexes.get(a.hexId());
        Hex hb = s.field.hexes.get(b.hexId());
        if (ha == null || hb == null) {
            return false;
        }
        return b.hexId().equals(ha.neighborBySide[a.сектор()])
            && a.hexId().equals(hb.neighborBySide[b.сектор()]);
    }

    /** СОСЕДСТВУЮТ ли места (см. описание класса). */
    public static boolean соседствуют(GameState s, Место a, Место b) {
        if (a.equals(b)) {
            return false;
        }
        if (a.hexId().equals(b.hexId())) {
            if (a.небо() || b.небо()) {
                return true;
            }
            int d = Math.floorMod(a.сектор() - b.сектор(), 6);
            return d == 1 || d == 5;
        }
        return примыкают(s, a, b);
    }

    /** Соседствуют ли жетоны: хоть одна пара их мест соседствует. */
    public static boolean соседствуют(GameState s, Token x, Token y) {
        List<Место> mx = места(s, x);
        List<Место> my = места(s, y);
        for (Место a : mx) {
            for (Место b : my) {
                if (соседствуют(s, a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Примыкают ли жетоны: хоть одна пара их мест на общем ребре двух гексов. */
    public static boolean примыкают(GameState s, Token x, Token y) {
        List<Место> mx = места(s, x);
        List<Место> my = места(s, y);
        for (Место a : mx) {
            for (Место b : my) {
                if (примыкают(s, a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Стоит ли в небе гекса авиация (любого игрока). */
    public static boolean авиацияВНебе(GameState s, String hexId) {
        for (PlayerState p : s.players) {
            for (UnitToken u : p.unitsOnField()) {
                if (u.type == UnitType.AIRCRAFT && hexId.equals(u.hexId) && !u.inside()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Соседние ли гексы (общая сторона). */
    public static boolean соседниеГексы(GameState s, String a, String b) {
        Hex h = s.field.hexes.get(a);
        if (h == null || a.equals(b)) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (b.equals(h.neighborBySide[i])) {
                return true;
            }
        }
        return false;
    }

    /** Все жетоны игрока на поле (здания и войска). */
    public static List<Token> жетоны(GameState s, int seat) {
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
