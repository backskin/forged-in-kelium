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
        // ВЫБРАННЫЕ ИГРОКОМ СЕКТОРЫ — ПЕРВЫМИ (26.09.2026): жетон стоит там, куда
        // его поставили. Выбор, который с тех пор накрыло здание, не действует —
        // такой жетон садится сам вместе с остальными.
        List<UnitToken> сами = new ArrayList<>();
        for (UnitToken u : наземные) {
            List<Integer> выбор = u.chosenSides();
            boolean годится = выбор != null && выбор.size() == секторов(u.type);
            if (годится) {
                for (int i : выбор) {
                    годится &= свободно[i];
                }
            }
            if (!годится) {
                сами.add(u);
                continue;
            }
            for (int i : выбор) {
                свободно[i] = false;
            }
            итог.put(u.uid, выбор);
        }
        for (UnitToken u : сами) {
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

    /** Войско, стоящее на секторе {@code side} гекса, или null. */
    public static UnitToken наСекторе(GameState s, Hex h, int side) {
        if (h == null || side < 0 || side > 5) {
            return null;
        }
        boolean есть = false;
        for (PlayerState p : s.players) {
            for (UnitToken u : p.units) {
                if (h.id.equals(u.hexId)) {
                    есть = true;
                }
            }
        }
        if (!есть) {
            return null;
        }
        for (var e : разложить(s, h.id).entrySet()) {
            if (e.getValue().contains(side)) {
                for (PlayerState p : s.players) {
                    for (UnitToken u : p.units) {
                        if (u.uid == e.getKey()) {
                            return u;
                        }
                    }
                }
            }
        }
        return null;
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

    // ======================================================================
    //  ВЫБОР СЕКТОРА ИГРОКОМ (решение дизайнера 26.09.2026)
    // ======================================================================

    /**
     * Куда можно поставить жетон {@code u} на его гексе: одиночный сектор или
     * пара смежных у техники. Годится место, свободное от зданий, стенок и
     * уже поставленных войск, при котором на гексе умещаются и все остальные
     * войска (жетоны без выбора подвинутся сами).
     */
    public static List<List<Integer>> варианты(GameState s, UnitToken u) {
        List<List<Integer>> out = new ArrayList<>();
        Hex h = u.hexId == null ? null : s.field.hexes.get(u.hexId);
        int нужно = секторов(u.type);
        if (h == null || нужно == 0 || u.inside()) {
            return out;
        }
        int былаМаска = u.sideMask;
        String былГекс = u.sidesHex;
        try {
            u.sideMask = 0;
            int разместилось = разложить(s, u.hexId).size();
            for (int i = 0; i < 6; i++) {
                List<Integer> место = нужно == 1 ? List.of(i) : List.of(i, (i + 1) % 6);
                boolean свободно = true;
                for (int k : место) {
                    свободно &= h.sideOwner[k] == null;
                }
                if (!свободно) {
                    continue;
                }
                u.chooseSides(место);
                Map<Integer, List<Integer>> р = разложить(s, u.hexId);
                if (место.equals(р.get(u.uid)) && р.size() >= разместилось) {
                    out.add(место);
                }
            }
        } finally {
            u.sideMask = былаМаска;
            u.sidesHex = былГекс;
        }
        return out;
    }

    /**
     * Войско встало на гекс: поставить его на секторы. Живой игрок выбирает сам
     * ({@link kelium.core.Agent#choosesSectors}); бот — как поставил бы человек,
     * берущий карту: на печатный контейнер, если он свободен, иначе к своим.
     * Авиация и войско в здании секторов не занимают — вопроса нет.
     */
    public static void поставить(GameState s, kelium.core.Agent agent, UnitToken u) {
        List<List<Integer>> вар = варианты(s, u);
        if (вар.isEmpty()) {
            return;
        }
        Hex h = s.field.hexes.get(u.hexId);
        List<Integer> лучший = поУмолчанию(s, h, u, вар);
        if (agent == null || !agent.choosesSectors() || вар.size() == 1) {
            u.chooseSides(лучший);
            return;
        }
        // лучший — первым: пропуск вопроса и робот-тесты берут его
        List<List<Integer>> порядок = new ArrayList<>(вар);
        порядок.remove(лучший);
        порядок.add(0, лучший);
        List<kelium.core.Choice> opts = new ArrayList<>();
        for (List<Integer> м : порядок) {
            boolean контейнер = h.containerCell >= 0 && м.contains(h.containerCell);
            opts.add(new kelium.core.Choice("unit_sector", м,
                (м.size() == 1 ? "сектор " + м.get(0) : "секторы " + м.get(0) + "-" + м.get(1))
                    + (контейнер ? " (контейнер)" : "")));
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("kind", "unit_sector");
        ctx.put("hex", u.hexId);
        ctx.put("uid", u.uid);
        ctx.put("utype", u.type.code);
        kelium.core.Choice pick = agent.choose(s, opts, ctx);
        List<Integer> м = new ArrayList<>();
        if (pick != null && pick.payload() instanceof List<?> l) {
            for (Object o : l) {
                м.add(((Number) o).intValue());
            }
        }
        u.chooseSides(вар.contains(м) ? м : лучший);
    }

    /** Место по умолчанию: печатный контейнер, если он в вариантах, иначе к своим. */
    private static List<Integer> поУмолчанию(GameState s, Hex h, UnitToken u,
                                             List<List<Integer>> вар) {
        if (h.containerCell >= 0 && h.containerCell < 6) {
            for (List<Integer> м : вар) {
                if (м.contains(h.containerCell)) {
                    return м;
                }
            }
        }
        for (int i : порядокСекторов(s, h, u.owner())) {
            for (List<Integer> м : вар) {
                if (м.get(0) == i) {
                    return м;
                }
            }
        }
        return вар.get(0);
    }
}
