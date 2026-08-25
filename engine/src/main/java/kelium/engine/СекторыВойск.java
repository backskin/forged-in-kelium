package kelium.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;

/**
 * НА КАКИХ СЕКТОРАХ ГЕКСА СТОЯТ ВОЙСКА.
 *
 * <p>СВОД (термин СЕКТОР): «Жетон занимает секторы: пехота один, техника два
 * смежных, здание — по своему размеру; авиация встаёт только в небо на гексе».
 * Вместимость гекса движок считал и раньше ({@link Placement#groundLoad} и
 * {@code Hex.fitsWithRepack} умеют и пары под технику, и переупаковку), но
 * КОНКРЕТНЫЙ сектор жетона нигде не хранился: {@code Hex.occupySides} зовётся
 * только для зданий и нейтралов, а войско получает один {@code hexId}.
 *
 * <p>ЧЕМ ЭТО ВЫШЛО БОКОМ. Непрерывное соседство ({@link Shapes}) строится по
 * СЕКТОРАМ: два жетона в соседних гексах соприкасаются, только если заняли
 * секторы по общему ребру. Наземные войска секторов не имели — и в цепочку не
 * входили вовсе. Замер 25.08.2026: пять карт заданий (o50, o54, o56, o58, o62)
 * — 451 раздача за 200 партий, условие не выполнилось НИ РАЗУ.
 *
 * <p>ПОЧЕМУ РАСКЛАДКА ВЫВОДИТСЯ, А НЕ ХРАНИТСЯ. Хранить сектор жетона значило бы
 * поддерживать его в двух десятках мест: наём, движение, манёвр, десант,
 * выселение из здания, гибель, возврат в запас, откаты способностей. Один
 * пропущенный вызов — и раскладка молча разъезжается с полем. Здесь она
 * ВЫЧИСЛЯЕТСЯ из того же состояния, что уже определяет вместимость: свободные
 * секторы гекса плюс стоящие на нём войска. Разъехаться не с чем.
 *
 * <p>ПОРЯДОК РАСКЛАДКИ ОДИН И ТОТ ЖЕ ВСЕГДА (иначе цепочка мигала бы от вызова
 * к вызову): сначала техника — ей нужны две смежные, потом одиночные жетоны.
 * Внутри каждой очереди сектор выбирается так, как поставил бы человек,
 * держащий линию: сперва тот, что смотрит на соседний гекс со СВОИМИ жетонами.
 */
public final class СекторыВойск {

    private СекторыВойск() {
    }

    /** Сколько секторов земли занимает жетон этого рода. Авиация — небо, ноль. */
    public static int секторов(UnitType t) {
        return switch (t) {
            case AIRCRAFT -> 0;
            case VEHICLE -> 2;
            default -> 1;
        };
    }

    /**
     * Разложить войска гекса по секторам: uid жетона → занятые им секторы.
     *
     * <p>Авиации в ответе нет: она стоит в небе на гексе, а не на земле.
     * Жетон, которому места не хватило (такое возможно после способностей карт,
     * ставящих жетон в обход проверки вместимости), в ответ не попадает — врать
     * про сектор нельзя, лучше показать, что его на земле нет.
     */
    public static Map<Integer, List<Integer>> разложить(GameState s, String hexId) {
        Hex h = s.field.hexes.get(hexId);
        if (h == null) {
            return Map.of();
        }
        boolean[] свободно = new boolean[6];
        for (int i = 0; i < 6; i++) {
            свободно[i] = h.sideOwner[i] == null;
        }

        List<UnitToken> наземные = new ArrayList<>();
        for (PlayerState p : s.players) {
            for (UnitToken u : p.units) {
                // Войско ВНУТРИ здания сектора не занимает: его укрывает след
                // самого здания — тот же порядок, что в Placement.groundLoad.
                if (u.inside() || !hexId.equals(u.hexId) || !u.alive()) {
                    continue;
                }
                if (секторов(u.type) > 0) {
                    наземные.add(u);
                }
            }
        }
        // ТЕХНИКА ПЕРВОЙ: ей нужны две смежные, и после одиночек пары может уже
        // не остаться. Внутри очереди — по uid, чтобы порядок не зависел от
        // порядка обхода игроков.
        наземные.sort(Comparator
            .comparingInt((UnitToken u) -> -секторов(u.type))
            .thenComparingInt(u -> u.uid));

        Map<Integer, List<Integer>> итог = new LinkedHashMap<>();
        for (UnitToken u : наземные) {
            List<Integer> место = найтиМесто(s, h, свободно, u);
            if (место == null) {
                continue;
            }
            for (int i : место) {
                свободно[i] = false;
            }
            итог.put(u.uid, место);
        }
        return итог;
    }

    /** Секторы одного жетона, или null если он не на земле или места нет. */
    public static List<Integer> секторыЖетона(GameState s, UnitToken u) {
        if (u.hexId == null || секторов(u.type) == 0) {
            return null;
        }
        return разложить(s, u.hexId).get(u.uid);
    }

    private static List<Integer> найтиМесто(GameState s, Hex h, boolean[] свободно,
                                            UnitToken u) {
        int нужно = секторов(u.type);
        List<Integer> порядок = порядокСекторов(s, h, u.owner());
        if (нужно == 1) {
            for (int i : порядок) {
                if (свободно[i]) {
                    return List.of(i);
                }
            }
            return null;
        }
        // Технике — две СМЕЖНЫЕ. Начало пары берём в том же порядке
        // предпочтения, что и одиночный сектор.
        for (int i : порядок) {
            int j = (i + 1) % 6;
            if (свободно[i] && свободно[j]) {
                return List.of(i, j);
            }
            int k = (i + 5) % 6;
            if (свободно[i] && свободно[k]) {
                return List.of(k, i);
            }
        }
        return null;
    }

    /**
     * Порядок предпочтения секторов: сперва смотрящие на соседний гекс, где у
     * этого же игрока уже есть жетоны, — так линия смыкается сама, как её
     * выстроил бы человек. Остальные — по номеру, чтобы ответ был однозначным.
     */
    private static List<Integer> порядокСекторов(GameState s, Hex h, int seat) {
        List<Integer> свои = new ArrayList<>();
        List<Integer> прочие = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String сосед = h.neighborBySide[i];
            if (сосед != null && естьСвои(s, сосед, seat)) {
                свои.add(i);
            } else {
                прочие.add(i);
            }
        }
        свои.addAll(прочие);
        return свои;
    }

    private static boolean естьСвои(GameState s, String hexId, int seat) {
        PlayerState p = s.player(seat);
        for (UnitToken u : p.units) {
            if (hexId.equals(u.hexId) && u.alive()) {
                return true;
            }
        }
        for (kelium.core.BuildingToken b : p.buildings) {
            if (hexId.equals(b.hexId) && b.alive()) {
                return true;
            }
        }
        return false;
    }
}
