package kelium.agents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.Ctx;
import kelium.engine.CombatResolver;
import kelium.engine.Movement;
import kelium.engine.Scoring;
import kelium.engine.Speed;

/**
 * УГРОЗЫ — что за столом видно про каждого соперника (заказ дизайнера
 * 27.09.2026: «боты играют против соперников, а не против таблицы очков»).
 *
 * <p>Прежние боты оценивали только СВОЮ позицию. Соперник входил в оценку
 * одним числом — очками лидера. Отсюда ходы, которые со стороны непонятны:
 * бот сносил своё ЦУ и дарил соседу 3 очка, потому что в его счёте чужие очки
 * почти ничего не весили. Здесь собрано то, что игрок за столом видит про
 * соседей, и только оно:
 * <ul>
 *   <li>очки на столе (подсчёт {@link Scoring});</li>
 *   <li>выполненные задания — число открыто;</li>
 *   <li>трек науки: где стоят кубики, есть ли на следующей ступени свободная
 *       ячейка и хватает ли соседу трофеев и келемия её оплатить;</li>
 *   <li>войска, которые за один ход дотягиваются до чужих зданий и ЦУ;</li>
 *   <li>склад: трофеи, келемий, боеприпасы.</li>
 * </ul>
 *
 * <p>ЧЕГО ЗДЕСЬ НЕТ НАРОЧНО: руки соперников (задания, арсенал, контейнеры) и
 * порядка колод. Это закрыто, и бот, который это читает, подсматривает.
 * Известно только, СКОЛЬКО у соседа карт.
 */
public final class Угрозы {

    private Угрозы() {
    }

    // ======================================================================
    //  ЦУ ПОД УГРОЗОЙ
    // ======================================================================

    /** Гекс моего ЦУ на поле, или null (ЦУ в запасе). */
    public static String гексЦу(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b.hexId;
            }
        }
        return null;
    }

    private static BuildingToken цу(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        return null;
    }

    /**
     * ДОТЯГИВАЕТСЯ ЛИ ЧУЖОЕ ВОЙСКО ДО ЖЕТОНА за один ход: может дойти до гекса
     * жетона или до соседнего (бой идёт с соседнего гекса) и печатная таблица
     * атак рода бьёт такую цель.
     */
    public static boolean дотягивается(GameState s, UnitToken u, int владелецВойска,
                                       String гекс, kelium.core.Token цель) {
        if (u.hexId == null || гекс == null) {
            return false;
        }
        if (!CombatResolver.canHit(s, владелецВойска, u, цель)) {
            return false;
        }
        Set<String> рядом = new HashSet<>(s.field.neighbors(гекс));
        рядом.add(гекс);
        if (рядом.contains(u.hexId)) {
            return true;
        }
        int скорость = Speed.of(s, владелецВойска, u);
        if (скорость <= 0) {
            return false;
        }
        return вПределах(s, u.hexId, рядом, скорость);
    }

    /**
     * Дойдёт ли жетон с гекса {@code от} до одного из {@code цели} за
     * {@code шагов} шагов по проходимым гексам. Поиск обрезан по дальности:
     * скорость войск — один-два шага, и обходить всё поле незачем.
     */
    static boolean вПределах(GameState s, String от, Set<String> цели, int шагов) {
        Set<String> видели = new HashSet<>();
        List<String> фронт = new ArrayList<>();
        фронт.add(от);
        видели.add(от);
        for (int d = 0; d < шагов; d++) {
            List<String> дальше = new ArrayList<>();
            for (String h : фронт) {
                for (String nb : s.field.neighborsView(h)) {
                    if (!видели.add(nb)) {
                        continue;
                    }
                    if (цели.contains(nb)) {
                        return true;
                    }
                    if (Movement.passable(s, nb)) {
                        дальше.add(nb);
                    }
                }
            }
            фронт = дальше;
        }
        return false;
    }

    /**
     * ЦУ ПОД РЕАЛЬНОЙ УГРОЗОЙ: на нём уже есть урон, или чужие войска за один
     * ход дотягиваются до его гекса. Без этого ни переносить, ни сносить своё
     * ЦУ боту незачем — такой ход ничего не меняет, кроме подарка соседу.
     */
    public static boolean цуПодУгрозой(GameState s, int seat) {
        PlayerState me = s.player(seat);
        BuildingToken цу = цу(me);
        if (цу == null) {
            return false;
        }
        if (цу.damage > 0) {
            return true;
        }
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                if (дотягивается(s, u, p.seat, цу.hexId, цу)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ГРОЗИТ ЛИ ВОЕННАЯ ПОБЕДА СОПЕРНИКА через снос МОЕГО ЦУ: по своду 1.45.0
     * второе уничтожение ЦУ за партию (втроём и вчетвером) — мгновенная победа.
     * Значит опасен сосед, у которого уже есть одно уничтожение и чьи войска
     * дотягиваются до моего ЦУ.
     */
    public static boolean грозитВоеннаяПобеда(GameState s, int seat) {
        PlayerState me = s.player(seat);
        BuildingToken цу = цу(me);
        if (цу == null) {
            return false;
        }
        var rs = Ctx.rules(s);
        int нужно = ((Number) rs.get("command_center.cu_tokens_for_military_win", 2)).intValue();
        boolean поСносам = rs.getBool("command_center.military_win_counts_kills", false);
        int минИгроков = rs.getInt("command_center.military_win_min_players", 2);
        if (s.numPlayers() < минИгроков) {
            return false;
        }
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            boolean уПорога = поСносам ? p.cuKills + 1 >= нужно
                : p.cuDestructionTokens + 1 >= нужно;
            if (!уПорога) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                if (дотягивается(s, u, p.seat, цу.hexId, цу)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ======================================================================
    //  ХОДЫ БЕЗ ЦЕЛИ — СНОС И ПЕРЕНОС СВОЕГО ЦУ
    // ======================================================================

    /** Вариант — снос или перенос МОЕГО ЦУ. */
    public static boolean трогаетСвоёЦу(GameState s, int seat, Choice o) {
        String k = o.kind();
        Integer uid = null;
        if ("demolish_pick".equals(k) && o.payload() instanceof Number n) {
            uid = n.intValue();
        } else if ("move_pick".equals(k) && o.payload() instanceof Map<?, ?> m
                && m.get("uid") instanceof Number n) {
            uid = n.intValue();
        }
        if (uid == null) {
            return false;
        }
        for (BuildingToken b : s.player(seat).buildings) {
            if (b.uid == uid && b.type == BuildingType.COMMAND_CENTER) {
                return true;
            }
        }
        return false;
    }

    /**
     * ДОПУСТИМ ЛИ СНОС ИЛИ ПЕРЕНОС СВОЕГО ЦУ ПРЯМО СЕЙЧАС.
     *
     * <p>Перенос — только при реальной угрозе ЦУ. Снос по своду 1.45.0 отдаёт
     * соседу жетон на 3 очка ({@code actions.build.demolish_cu_gives_token}),
     * поэтому он допустим, только если без него сосед берёт военную победу.
     */
    public static boolean трогатьЦуМожно(GameState s, int seat, Choice o) {
        if (!цуПодУгрозой(s, seat)) {
            return false;
        }
        if ("demolish_pick".equals(o.kind())
                && Ctx.rules(s).getBool("actions.build.demolish_cu_gives_token", false)) {
            return грозитВоеннаяПобеда(s, seat);
        }
        return true;
    }

    /**
     * ОТСЕВ ХОДОВ БЕЗ ЦЕЛИ — одна точка для всех ботов и для всех их прогонов на
     * копии стола. Возвращает тот же список, если отсеивать нечего.
     *
     * <p>ПОЧЕМУ ЗДЕСЬ, А НЕ В ОЦЕНКЕ. Планировщик перебирает сценарии хода, и
     * часть прогонов идёт с ошибками новичка — случайным выбором из меню. Меню
     * Стройки содержит «снести своё ЦУ», и такой прогон иногда выигрывал: ЦУ,
     * поставленное заново, даёт вышку и накрывает контейнер, а подарок соседу в
     * три очка своя оценка почти не видела. Вариант, которого бот не должен
     * делать никогда, надо не взвешивать, а не предлагать.
     */
    public static List<Choice> отсеять(GameState s, int seat, List<Choice> options) {
        if (options == null || options.size() < 2) {
            return options;
        }
        List<Choice> out = null;
        for (int i = 0; i < options.size(); i++) {
            Choice o = options.get(i);
            boolean долой = трогаетСвоёЦу(s, seat, o) && !трогатьЦуМожно(s, seat, o);
            if (долой && out == null) {
                out = new ArrayList<>(options.subList(0, i));
            } else if (!долой && out != null) {
                out.add(o);
            }
        }
        return out == null || out.isEmpty() ? options : out;
    }

    // ======================================================================
    //  СОПЕРНИК ГЛАЗАМИ ИГРОКА ЗА СТОЛОМ
    // ======================================================================

    /**
     * Что видно про одного соперника.
     *
     * @param очки          очки на столе сейчас
     * @param наукаВершин   сколько вершин треков он занял
     * @param наукаДоступно сколько треков, где следующая ступень свободна и по
     *                      карману ему прямо сейчас
     * @param близостьНауки 0..1 — насколько он близок к победе наукой (все три
     *                      вершины)
     * @param войскДоЦу     сколько его войск за ход дотягиваются до чужих ЦУ
     * @param войскДоЗданий сколько его войск дотягиваются до чужих зданий
     * @param грозитВойной  у порога военной победы и дотягивается до чьего-то ЦУ
     * @param заданий       выполнено заданий (открыто)
     * @param сила          оценка силы в очках: очки плюс то, что он вот-вот
     *                      возьмёт по открытым признакам
     */
    public record Соперник(int место, int очки, int наукаВершин, int наукаДоступно,
                           double близостьНауки, int войскДоЦу, int войскДоЗданий,
                           boolean грозитВойной, int заданий, double сила) {
    }

    /** Очки игрока по подсчёту. */
    public static int очки(GameState s, int seat) {
        return Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
    }

    /** Сколько трофеев и келемия игрок может отдать за шаг науки (открыто). */
    static int карманНауки(GameState s, PlayerState p) {
        int трофеи = p.resources.trophy() + p.destroyedValue();
        boolean келемием = Ctx.rules(s).getBool("tech.pay_with_kelium", false);
        return трофеи + (келемием ? p.resources.kelium() : 0);
    }

    /**
     * Свободных ячеек на СЛЕДУЮЩЕЙ ступени трека для игрока {@code p}
     * (−1 — вершина без предела или трек пройден).
     */
    static int свободноНаСледующей(GameState s, PlayerState p, String трек) {
        int шаг = p.techSteps.getOrDefault(трек, 0);
        if (шаг >= s.tech.steps) {
            return 0;
        }
        List<Integer> ёмкость = Ctx.rules(s).stepCapacity(s.numPlayers());
        Integer ёмк = ёмкость == null || шаг >= ёмкость.size() ? null : ёмкость.get(шаг);
        if (ёмк == null) {
            return 99;
        }
        List<List<Integer>> поШагам = s.tech.occupancy.get(трек);
        int занято = поШагам == null ? 0 : поШагам.get(шаг).size();
        return Math.max(0, ёмк - занято);
    }

    /** Цена следующей ступени трека для игрока (в трофеях/келемии). */
    static int ценаСледующей(GameState s, PlayerState p, String трек) {
        int шаг = p.techSteps.getOrDefault(трек, 0);
        List<Integer> цены = Ctx.rules(s).getIntList("tech.step_cost_trophy");
        if (шаг >= цены.size()) {
            return Integer.MAX_VALUE;
        }
        return цены.get(шаг);
    }

    /** Доступных ему ступеней: свободна ячейка, хватает оплаты и кубиков. */
    public static int наукаДоступно(GameState s, PlayerState p) {
        if (p.techCubesLeft == 0) {
            return 0;
        }
        int карман = карманНауки(s, p);
        int n = 0;
        for (String t : s.tech.tracks) {
            if (свободноНаСледующей(s, p, t) > 0 && ценаСледующей(s, p, t) <= карман) {
                n++;
            }
        }
        return n;
    }

    /** Взгляд на одного соперника. */
    public static Соперник соперник(GameState s, int r) {
        PlayerState p = s.player(r);
        int очки = очки(s, r);
        int вершин = 0;
        double высота = 0;
        for (String t : s.tech.tracks) {
            int шаг = p.techSteps.getOrDefault(t, 0);
            if (шаг >= s.tech.steps) {
                вершин++;
            }
            высота += Math.min(1.0, (double) шаг / Math.max(1, s.tech.steps));
        }
        double близость = s.tech.tracks.isEmpty() ? 0 : высота / s.tech.tracks.size();
        int доступно = наукаДоступно(s, p);
        int доЦу = 0;
        int доЗданий = 0;
        boolean война = false;
        var rs = Ctx.rules(s);
        int нужно = ((Number) rs.get("command_center.cu_tokens_for_military_win", 2)).intValue();
        boolean поСносам = rs.getBool("command_center.military_win_counts_kills", false);
        boolean уПорога = s.numPlayers() >= rs.getInt("command_center.military_win_min_players", 2)
            && (поСносам ? p.cuKills + 1 >= нужно : p.cuDestructionTokens + 1 >= нужно);
        for (UnitToken u : p.unitsOnField()) {
            boolean цуДостал = false;
            boolean зданиеДостал = false;
            for (PlayerState о : s.players) {
                if (о.seat == r) {
                    continue;
                }
                for (BuildingToken b : о.buildingsOnField()) {
                    if (цуДостал && зданиеДостал) {
                        break;
                    }
                    boolean этоЦу = b.type == BuildingType.COMMAND_CENTER;
                    if ((этоЦу ? цуДостал : зданиеДостал)) {
                        continue;
                    }
                    if (дотягивается(s, u, r, b.hexId, b)) {
                        if (этоЦу) {
                            цуДостал = true;
                        } else {
                            зданиеДостал = true;
                        }
                    }
                }
            }
            if (цуДостал) {
                доЦу++;
                if (уПорога) {
                    война = true;
                }
            }
            if (зданиеДостал) {
                доЗданий++;
            }
        }
        // СИЛА — В ОЧКАХ. Очки на столе плюс то, что он вот-вот возьмёт по
        // открытым признакам: свободная и оплачиваемая ступень науки — это почти
        // верное очко-два; каждая вершина приближает победу наукой; войска у
        // чужого ЦУ — это жетон на 3 очка; у порога военной победы — сама победа.
        double сила = очки
            + 1.5 * доступно
            + 1.2 * вершин + (вершин >= 2 ? 3.0 * (вершин - 1) : 0)
            + 0.6 * Math.min(3, доЦу) + 0.2 * Math.min(4, доЗданий)
            + (война ? 6.0 : 0)
            + 0.3 * p.objectivesCompleted;
        return new Соперник(r, очки, вершин, доступно, близость, доЦу, доЗданий, война,
            p.objectivesCompleted, сила);
    }

    /** Все соперники места {@code seat}. */
    public static List<Соперник> соперники(GameState s, int seat) {
        List<Соперник> out = new ArrayList<>();
        for (PlayerState p : s.players) {
            if (p.seat != seat) {
                out.add(соперник(s, p.seat));
            }
        }
        return out;
    }

    /**
     * ЛИДЕР ГЛАЗАМИ МЕСТА {@code seat} — соперник, который ближе всех к победе:
     * по очкам на столе с поправкой на то, что он вот-вот возьмёт. −1 — соперников
     * нет.
     */
    public static int лидер(GameState s, int seat) {
        int best = -1;
        double bestV = Double.NEGATIVE_INFINITY;
        for (Соперник r : соперники(s, seat)) {
            // Очки на столе видят все, и лидера за столом называют по ним;
            // открытые угрозы только поправляют: вот-вот взятое — треть веса.
            double v = r.очки() + 0.35 * (r.сила() - r.очки());
            if (v > bestV) {
                bestV = v;
                best = r.место();
            }
        }
        return best;
    }

    /** Лидер по одним очкам на столе (для замера; ничья — меньшее место). */
    public static int лидерПоОчкам(GameState s, int seat) {
        int best = -1;
        int bestV = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            int v = очки(s, p.seat);
            if (v > bestV) {
                bestV = v;
                best = p.seat;
            }
        }
        return best;
    }
}
