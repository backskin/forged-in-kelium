package kelium.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;

/**
 * НАМЕРЕНИЯ БОТА — то, чего он хочет ДОЛЬШЕ ОДНОГО РЕШЕНИЯ.
 *
 * <p>Жалоба дизайнера 07.09.2026: боты «принимают стратегию и тут же от неё
 * отказываются», «другой бот снёс первому здание — первому всё равно». Обе
 * беды от одного: ни у одного решения не было памяти. Здесь память есть, и она
 * трёх видов:
 * <ul>
 *   <li><b>противник</b> — кого бот считает своей целью. Выбирается из угрозы,
 *       близости и ОБИД; меняется только если другой кандидат заметно лучше
 *       (гистерезис), а не при каждом чихе;</li>
 *   <li><b>обиды</b> — кто и сколько раз сносил мои жетоны. Обида растёт от
 *       каждого удара по мне и гаснет с раундами, но не сразу;</li>
 *   <li><b>задание в фокусе</b> — карта из руки, которую бот решил довести до
 *       выполнения. Пока она в руке и не стала безнадёжной, оценка позиции
 *       платит за приближение именно к ней.</li>
 * </ul>
 *
 * <p>Всё здесь считается только по ОТКРЫТОЙ информации и собственной руке.
 */
public final class Intents {

    /** Сколько раз этот соперник сносил мои жетоны (с затуханием). */
    public final double[] grudge;
    /** Кого бью. −1 — пока никого. */
    public int targetSeat = -1;
    /**
     * КУДА БЬЮ — конкретный гекс, который бот выбрал целью удара и к которому
     * подводит войска ЗАРАНЕЕ, ещё до объявления боя ({@code null} — нет).
     *
     * <p>Жалоба дизайнера 22.09.2026: «бот видит всё поле, он может прицениться
     * за каждый жетон и тащить войска вокруг гекса, который хочет атаковать,
     * задолго до боя». Прежде намерение знало только ИГРОКА-цель, но не МЕСТО:
     * оценка платила за урон уже нанесённый и за жетоны, уже стоящие вплотную,
     * — а за путь к будущему штурму не платила ничего, и войска брели куда
     * придётся. Целевой гекс закрывает этот пробел: подведение нужного рода к
     * нему оценивается как прогресс, а не как пустой ход.
     */
    public String targetHex = null;
    /** Карта задания, которую довожу до выполнения ({@code null} — нет). */
    public String focusObjective = null;
    /** Насколько крепко держусь за противника (0..1): выше — реже меняю цель. */
    private final double commitment;
    private int lastRoundUpdated = -1;

    public Intents(int players, double commitment) {
        this.grudge = new double[players];
        this.commitment = commitment;
    }

    /** Мой жетон снесён игроком {@code by}: обида растёт. */
    public void hurtBy(int by, boolean cu) {
        if (by >= 0 && by < grudge.length) {
            grudge[by] += cu ? 3.0 : 1.0;
        }
    }

    /** Новый раунд: обиды остывают, цель пересматривается с гистерезисом. */
    public void newRound(GameState s, int seat, double leaderBias) {
        if (s.round == lastRoundUpdated) {
            return;
        }
        lastRoundUpdated = s.round;
        for (int i = 0; i < grudge.length; i++) {
            grudge[i] *= 0.7;
        }
        retarget(s, seat, leaderBias, false);
        retargetHex(s, seat, false);
    }

    /**
     * ВЫБРАТЬ (или подтвердить) ЦЕЛЕВОЙ ГЕКС.
     *
     * <p>Ценность гекса — сумма ценности стоящих на нём чужих жетонов (ЦУ и
     * здания дороже войск), с добавкой за жетон игрока-цели. Из этого вычитается
     * дальность: гекс, до которого мои войска не дотянутся и за партию, целью
     * быть не должен. Дальность — движковым BFS от ближайшего моего юнита, тем
     * же, каким ходит Манёвр, поэтому маршрут сквозь стенку в цель не запишется.
     *
     * <p>Гистерезис, как у игрока-цели: новый гекс должен быть заметно ценнее
     * прежнего, иначе бот метался бы между двумя соседними узлами и не доводил
     * ни один штурм до конца.
     *
     * @param force сменить цель даже без запаса превосходства (гекс опустел)
     */
    public void retargetHex(GameState s, int seat, boolean force) {
        PlayerState me = s.player(seat);
        java.util.List<UnitToken> myUnits = me.unitsOnField();
        // Собираем чужие жетоны по гексам.
        Map<String, Double> ценность = new HashMap<>();
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            double targetMult = p.seat == targetSeat ? 1.5 : 1.0;
            for (BuildingToken b : p.buildingsOnField()) {
                double v = b.type == BuildingType.COMMAND_CENTER ? 3.0
                    : b.type == BuildingType.AIRBASE || b.type == BuildingType.FACTORY ? 1.4
                    : 1.0;
                ценность.merge(b.hexId, v * targetMult, Double::sum);
            }
            for (UnitToken u : p.unitsOnField()) {
                ценность.merge(u.hexId, 0.6 * targetMult, Double::sum);
            }
        }
        if (ценность.isEmpty() || myUnits.isEmpty()) {
            targetHex = null;
            return;
        }
        // Дальность до каждого кандидата — от ближайшего моего юнита.
        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double curScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : ценность.entrySet()) {
            String hex = e.getKey();
            int dist = Integer.MAX_VALUE;
            for (UnitToken u : myUnits) {
                Integer d = kelium.engine.Movement.distance(s, u.hexId,
                    java.util.Set.of(hex));
                if (d != null && d < dist) {
                    dist = d;
                }
            }
            if (dist == Integer.MAX_VALUE) {
                continue;   // недостижим ни одним войском — не цель
            }
            // Дальность гасит ценность, но не обнуляет: дальний богатый узел
            // может остаться целью, к которой бот идёт несколько ходов.
            double score = e.getValue() - 0.4 * dist;
            if (score > bestScore) {
                bestScore = score;
                best = hex;
            }
            if (hex.equals(targetHex)) {
                curScore = score;
            }
        }
        if (best == null) {
            targetHex = null;
            return;
        }
        if (targetHex == null || force || !ценность.containsKey(targetHex)) {
            targetHex = best;
            return;
        }
        // ГИСТЕРЕЗИС: держусь за прежний гекс, пока новый не станет заметно лучше.
        if (bestScore > curScore + 0.5 + commitment) {
            targetHex = best;
        }
    }

    /**
     * Выбрать (или подтвердить) противника.
     *
     * @param force менять цель даже без запаса превосходства (цель исчезла)
     */
    public void retarget(GameState s, int seat, double leaderBias, boolean force) {
        Map<Integer, Double> score = new HashMap<>();
        Rivalry riv = new Rivalry(s, seat);
        int leader = riv.leader();
        PlayerState me = s.player(seat);
        for (PlayerState p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            double v = 1.0;
            v += grudge[p.seat] * 1.2;
            if (p.seat == leader) {
                v += leaderBias;
            }
            v += 0.8 * riv.threat(p.seat);
            // Близость: чем ближе его жетоны к моим, тем реальнее война с ним.
            int d = distance(s, me, p);
            v += d >= 99 ? 0 : Math.max(0, 3 - d) * 0.6;
            // Слабый соперник с раненым ЦУ — лёгкая добыча.
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER && b.damage > 0) {
                    v += b.damage * 0.7;
                }
            }
            if (!p.hasCommandCenter()) {
                v -= 1.0;   // ЦУ в запасе — бить пока нечего
            }
            score.put(p.seat, v);
        }
        if (score.isEmpty()) {
            targetSeat = -1;
            return;
        }
        int best = -1;
        double bestV = Double.NEGATIVE_INFINITY;
        for (var e : score.entrySet()) {
            if (e.getValue() > bestV) {
                bestV = e.getValue();
                best = e.getKey();
            }
        }
        if (targetSeat < 0 || force || !score.containsKey(targetSeat)) {
            targetSeat = best;
            return;
        }
        // ГИСТЕРЕЗИС: новая цель должна быть заметно лучше старой.
        double cur = score.getOrDefault(targetSeat, 0.0);
        if (bestV > cur * (1.0 + 0.4 * commitment) + 0.5) {
            targetSeat = best;
        }
    }

    /** Наименьшее расстояние в гексах между моими жетонами и жетонами {@code p}. */
    static int distance(GameState s, PlayerState me, PlayerState p) {
        java.util.Set<String> theirs = new java.util.HashSet<>();
        for (UnitToken u : p.unitsOnField()) {
            theirs.add(u.hexId);
        }
        for (BuildingToken b : p.buildingsOnField()) {
            theirs.add(b.hexId);
        }
        if (theirs.isEmpty()) {
            return 99;
        }
        java.util.Set<String> mine = new java.util.HashSet<>();
        for (UnitToken u : me.unitsOnField()) {
            mine.add(u.hexId);
        }
        for (BuildingToken b : me.buildingsOnField()) {
            mine.add(b.hexId);
        }
        if (mine.isEmpty()) {
            return 99;
        }
        // Поиск в ширину от всех моих гексов.
        java.util.Map<String, Integer> dist = new java.util.HashMap<>();
        java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
        for (String h : mine) {
            dist.put(h, 0);
            q.add(h);
        }
        while (!q.isEmpty()) {
            String h = q.poll();
            int d = dist.get(h);
            if (theirs.contains(h)) {
                return d;
            }
            if (d >= 8) {
                continue;
            }
            for (String nb : s.field.neighbors(h)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, d + 1);
                    q.add(nb);
                }
            }
        }
        return 99;
    }

    /**
     * Обновить задание в фокусе: держим прежнее, пока оно в руке и не стало
     * безнадёжным; иначе берём самое близкое по цене и прогрессу.
     */
    public void refocus(List<String> hand, Map<String, Double> progressByCard,
                        Map<String, Double> valueByCard) {
        if (focusObjective != null && hand.contains(focusObjective)
                && progressByCard.getOrDefault(focusObjective, 0.0) > 0.05) {
            return;
        }
        focusObjective = null;
        double best = Double.NEGATIVE_INFINITY;
        for (String cid : hand) {
            double p = progressByCard.getOrDefault(cid, 0.0);
            double v = valueByCard.getOrDefault(cid, 3.0);
            double score = v * (0.2 + p);
            if (score > best) {
                best = score;
                focusObjective = cid;
            }
        }
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("цель=").append(targetSeat < 0 ? "нет" : "игрок " + targetSeat);
        sb.append(" гекс=").append(targetHex == null ? "нет" : targetHex);
        sb.append(" фокус=").append(focusObjective == null ? "нет" : focusObjective);
        sb.append(" обиды=[");
        for (int i = 0; i < grudge.length; i++) {
            sb.append(String.format(java.util.Locale.ROOT, "%.1f", grudge[i]));
            if (i + 1 < grudge.length) {
                sb.append(' ');
            }
        }
        return sb.append(']').toString();
    }
}
