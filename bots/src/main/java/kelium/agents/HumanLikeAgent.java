package kelium.agents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Token;
import kelium.core.UnitToken;

/**
 * HumanLikeAgent — бот, который играет КАК ЧЕЛОВЕК, а не как счётная машина.
 *
 * <p>Зачем такая семья. Оптимизатор нужен, чтобы измерять потолок игры, но партия
 * живых людей выглядит иначе, и баланс надо проверять именно на ней. Люди помнят
 * обиды, отвечают ударом на удар, защищают то, во что вложились, злятся, когда
 * проигрывают, не пересчитывают план каждый ход и вообще редко выбирают лучший из
 * восьми вариантов — они выбирают ХОРОШИЙ из двух-трёх, которые заметили.
 *
 * <p>Что именно вложено (каждая черта — отдельная человеческая слабость, а не
 * общая «случайность»):
 * <ol>
 *   <li><b>Память обид.</b> Кто меня ударил, снёс здание, убил жетон — тому
 *       начисляется обида. Обида ЗАБЫВАЕТСЯ медленно (затухает по раундам), и
 *       цели выбираются с оглядкой на неё, а не только на выгоду. Отдельно
 *       считается обида за снос ЦУ — она самая тяжёлая.</li>
 *   <li><b>Ответ ударом на удар.</b> Свежая обида весит больше старой: человек
 *       мстит СЕЙЧАС, а не когда выгодно.</li>
 *   <li><b>Задетость (tilt).</b> Копится от потерь и от того, что кто-то ушёл в
 *       отрыв. Поднимает агрессию и снижает терпение — человек «заводится» и
 *       начинает играть резче, чем следовало бы.</li>
 *   <li><b>Своё дороже.</b> Вложенное переоценивается (издержки, которые уже
 *       понесены, человек считает частью ценности) — свои здания защищаются
 *       упрямее, чем стоило бы по расчёту.</li>
 *   <li><b>Узкое внимание.</b> Из всех вариантов рассматриваются лишь несколько
 *       лучших «на глаз», и среди них выбор с шумом — человек не перебирает
 *       восемь действий, он берёт то, что первым показалось хорошим.</li>
 *   <li><b>Упрямство плана.</b> План держится несколько раундов, даже если
 *       обстановка изменилась: люди не пересматривают замысел каждый ход.</li>
 * </ol>
 *
 * <p>Такой бот ЗАВЕДОМО СЛАБЕЕ просчитывающего — и это не изъян, а смысл: он
 * показывает, как игра ощущается за столом, где ошибаются и мстят.
 */
public class HumanLikeAgent extends StrategicAgent {

    /**
     * Решения, на которых человек РЕАЛЬНО отличается от машины: замысел (какой
     * приказ вскрыть), что делать, что строить, кого бить, куда идти. Всё
     * остальное — рутина, и в ней человек надёжен.
     */
    private static final java.util.Set<String> HUMAN_KINDS = java.util.Set.of(
        "reveal_order", "action", "build_pick", "combat_target", "combat_victim",
        "move", "maneuver_unit", "spec");

    /** Насколько сильно помнит обиды (0 — не помнит вовсе). */
    private final double grudgeWeight;
    /** Как быстро обида забывается за раунд (0.75 = четверть забывается). */
    private final double grudgeDecay;
    /** Сколько вариантов вообще рассматривает (узкое внимание). */
    private final int attention;
    /** Сила шума в выборе среди рассмотренных. */
    private final double noise;
    /** Сколько раундов держится план, прежде чем пересматривать. */
    private final int planStickiness;

    /** Обида на каждое место: сколько мне сделали плохого. */
    private final double[] grudge;
    /** Задетость: копится от потерь, поднимает агрессию. */
    private double tilt = 0.0;
    /** Раунд, в котором план пересчитывался последний раз. */
    private int planRound = -99;
    /** Раунд последнего затухания обид — чтобы затухать раз в раунд. */
    private int decayedRound = -1;
    private final Random human;

    // Для отчёта: сколько раз ударил именно обидчика и сколько раз вообще.
    private int revengeHits = 0;
    private int totalHits = 0;

    public HumanLikeAgent(int seat, Random rng, Genome genome, String character,
                          double grudgeWeight, double grudgeDecay, int attention,
                          double noise, int planStickiness, int players) {
        super(seat, rng, genome, character);
        this.grudgeWeight = grudgeWeight;
        this.grudgeDecay = grudgeDecay;
        this.attention = Math.max(2, attention);
        this.noise = noise;
        this.planStickiness = Math.max(1, planStickiness);
        this.grudge = new double[Math.max(4, players)];
        this.human = new Random(rng == null ? seat : rng.nextLong());
    }

    /** «Обычный человек»: помнит обиды, заводится, смотрит на 4 варианта. */
    public static HumanLikeAgent normal(int seat, Random rng, Genome g, int players) {
        return new HumanLikeAgent(seat, rng, g, "человек", 1.0, 0.75, 4, 0.5, 2, players);
    }

    /** «Злопамятный»: мстит долго и охотно, выгоду считает плохо. */
    public static HumanLikeAgent vengeful(int seat, Random rng, Genome g, int players) {
        return new HumanLikeAgent(seat, rng, g, "злопамятный", 2.5, 0.9, 3, 0.7, 3, players);
    }

    /**
     * «Хладнокровный»: обид почти не держит, внимателен — ближе всех к машине.
     * Служит проверкой самой семьи: если он НЕ сильнее обычного человека, значит
     * человеческие черты сделаны неправильно (шумят, а не моделируют слабость).
     */
    public static HumanLikeAgent coolHeaded(int seat, Random rng, Genome g, int players) {
        return new HumanLikeAgent(seat, rng, g, "хладнокровный", 0.2, 0.4, 6, 0.15, 1, players);
    }

    /** Доля ударов, пришедшихся по обидчику — мера мстительности для отчёта. */
    public double revengeShare() {
        return totalHits == 0 ? 0 : (double) revengeHits / totalHits;
    }

    /** Текущая задетость (для отчётов и рассказа). */
    public double tilt() {
        return tilt;
    }

    /** Обиды по местам — для отчётов. */
    public double[] grudges() {
        return grudge.clone();
    }

    // ==================================================================
    //  ПАМЯТЬ: кто что мне сделал
    // ==================================================================

    @Override
    public void observePublicEvent(Map<String, Object> event) {
        String type = String.valueOf(event.get("type"));
        if ("combat_hit".equals(type)) {
            noteHit(event);
        } else if ("refresh".equals(type)) {
            forgetALittle(event);
        }
    }

    /**
     * Удар по столу: если досталось МНЕ — запоминаю обидчика. Обида тем тяжелее,
     * чем дороже потеря: жетон убит хуже, чем поцарапан, ЦУ хуже всего.
     */
    private void noteHit(Map<String, Object> event) {
        Object attackerObj = event.get("seat");
        Object victimObj = event.get("victim_owner");
        if (!(attackerObj instanceof Number an)) {
            return;
        }
        int attacker = an.intValue();
        // Своим ударам радуемся, но обиду они не создают.
        if (attacker == seat) {
            return;
        }
        int victim = victimObj instanceof Number vn ? vn.intValue() : -1;
        if (victim != seat) {
            return;   // били не меня — мне всё равно (человек тоже так)
        }
        boolean destroyed = Boolean.TRUE.equals(event.get("destroyed"));
        String what = String.valueOf(event.get("victim"));
        double weight = destroyed ? 2.0 : 0.6;
        if (what.contains("command_center")) {
            weight += 4.0;   // по ЦУ — самое тяжёлое
        }
        if (attacker >= 0 && attacker < grudge.length) {
            grudge[attacker] += weight;
        }
        // ЗАДЕТОСТЬ: человек заводится от потерь, а не от расчёта.
        tilt = Math.min(3.0, tilt + (destroyed ? 0.5 : 0.15));
    }

    /** Обиды забываются медленно — раз в раунд, на Обновлении. */
    private void forgetALittle(Map<String, Object> event) {
        int round = event.get("round") instanceof Number n ? n.intValue() : -1;
        if (round == decayedRound) {
            return;
        }
        decayedRound = round;
        for (int i = 0; i < grudge.length; i++) {
            grudge[i] *= grudgeDecay;
        }
        tilt *= 0.8;
    }

    // ==================================================================
    //  РЕШЕНИЯ: узкое внимание, шум, обида вместо чистой выгоды
    // ==================================================================

    @Override
    protected Choice pick(GameState state, List<Choice> options,
                          Map<String, Object> context, String kind) {
        if (options.size() <= 1) {
            return options.get(0);
        }
        // РЕШЕНИЯ БЕЗ СВОЕЙ ОЦЕНКИ отдаём базовой логике. Иначе все варианты имеют
        // счёт 0, и «человеческий» выбор с равной охотой садится на ПАС — бот
        // отказывался от решений, где отказываться нечего. Это стоило живым
        // ботам около 6 очков за партию, а хладнокровный (у него шума меньше)
        // проваливался сильнее всех, потому что стабильно брал первый вариант
        // списка. Ошибка была в механике выбора, а не в «человечности».
        if (scorerFor(kind, context) == null) {
            return super.pick(state, options, context, kind);
        }
        // ЧЕЛОВЕЧНОСТЬ — ТОЛЬКО НА ЗНАЧИМЫХ РЕШЕНИЯХ. Человек небрежен в замысле
        // (какой приказ вскрыть, что построить, кого ударить), но рутину делает
        // надёжно: кубик энергии он положит куда надо, келемий продаст, когда
        // пусто в кошельке. Пока шум и узкое внимание применялись к КАЖДОМУ
        // решению (а их за партию около сотни), мелкие потери складывались, и
        // живые проваливались вдвое по очкам — это была не человечность, а
        // просто испорченная игра.
        if (!HUMAN_KINDS.contains(kind)) {
            return super.pick(state, options, context, kind);
        }
        // УЗКОЕ ВНИМАНИЕ: человек не перебирает всё. Берём несколько лучших «на
        // глаз» и выбираем среди них с шумом — так появляются живые ошибки,
        // при этом ход остаётся осмысленным, а не случайным.
        List<Choice> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparingDouble(
            (Choice o) -> -(priorScore(state, o, kind, context)
                + humanBonus(state, o, kind))));
        int width = Math.min(attention, sorted.size());
        // ОШИБКА ЧЕЛОВЕКА СОРАЗМЕРНА БЛИЗОСТИ ВАРИАНТОВ. Человек путается в
        // близких по смыслу ходах, а очевидно лучший видит и берёт. Поэтому шум
        // не постоянный, а в долях РАЗБРОСА оценок рассмотренных вариантов.
        //
        // Постоянный шум оказался разрушительным: живые проваливались с 16 очков
        // до 10, потому что сбивались и там, где выбор был ясен. Контрольный
        // прогон с выключенной человечностью это доказал — механика выбора была
        // ни при чём.
        double[] scores = new double[width];
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = 0; i < width; i++) {
            scores[i] = priorScore(state, sorted.get(i), kind, context)
                + humanBonus(state, sorted.get(i), kind);
            hi = Math.max(hi, scores[i]);
            lo = Math.min(lo, scores[i]);
        }
        double spread = Math.max(0.0, hi - lo);
        Choice best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < width; i++) {
            double sc = scores[i] + human.nextGaussian() * noise * spread * 0.5;
            if (sc > bestScore) {
                bestScore = sc;
                best = sorted.get(i);
            }
        }
        if (best != null && "combat_target".equals(kind)) {
            totalHits++;
            if (targetsOffender(state, best)) {
                revengeHits++;
            }
        }
        return best != null ? best : sorted.get(0);
    }

    /**
     * УПРЯМСТВО ПЛАНА: человек не пересматривает замысел каждый ход. План
     * пересчитывается не чаще раза в {@code planStickiness} раундов.
     */
    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
        // УПРЯМСТВО КАСАЕТСЯ ТОЛЬКО ЗАМЫСЛА (выбора приказа), а не тактики.
        // Сначала оно распространялось и на выбор действия — и живые боты
        // проваливались втрое по очкам: они играли ходы по плану от прошлого
        // раунда, то есть по устаревшей обстановке. Человек так не делает: он
        // держится замысла, но на конкретный ход смотрит свежими глазами.
        boolean wouldReplan = "reveal_order".equals(kind);
        if (wouldReplan && state.round - planRound < planStickiness
                && currentPlan() != null) {
            // держим прежний план: подменяем вид решения, чтобы базовый класс
            // не пересчитывал его, но всё остальное делал как обычно
            Map<String, Object> ctx = new java.util.HashMap<>(
                context == null ? Map.of() : context);
            ctx.put("kind", kind);
            return pickKeepingPlan(state, options, ctx, kind);
        }
        if (wouldReplan) {
            planRound = state.round;
        }
        return super.choose(state, options, context);
    }

    /** Выбор без пересчёта плана (план держится по упрямству). */
    private Choice pickKeepingPlan(GameState state, List<Choice> options,
                                   Map<String, Object> ctx, String kind) {
        return pick(state, options, ctx, kind);
    }

    /**
     * ЧЕЛОВЕЧЕСКАЯ НАДБАВКА к оценке варианта: обида, задетость и «своё дороже».
     * Именно она отличает живого игрока от счётной машины.
     */
    private double humanBonus(GameState state, Choice o, String kind) {
        double bonus = 0.0;
        switch (kind) {
            case "combat_target", "combat_victim" -> {
                double g = grudgeOfTarget(state, o);
                bonus += grudgeWeight * g;
                bonus += tilt * 0.8;   // на нервах бьём охотнее
            }
            case "action" -> {
                String name = String.valueOf(o.payload());
                if ("combat".equals(name) || "movement".equals(name)) {
                    // Есть на кого злиться — тянет в драку сильнее расчёта.
                    bonus += grudgeWeight * 0.5 * maxGrudge() + tilt * 1.2;
                }
                if ("science".equals(name) || "market".equals(name)) {
                    bonus -= tilt * 0.8;   // «не до торговли сейчас»
                }
            }
            case "build_pick" -> {
                // СВОЁ ДОРОЖЕ: когда задет, хочется укрепляться, а не считать.
                if (o.payload() instanceof Map<?, ?> m
                        && String.valueOf(((Map<String, Object>) m).get("label"))
                            .startsWith("barracks")) {
                    bonus += tilt * 0.6;
                }
            }
            default -> {
                // прочие решения человек делает как обычно
            }
        }
        return bonus;
    }

    private double maxGrudge() {
        double m = 0;
        for (double g : grudge) {
            m = Math.max(m, g);
        }
        return m;
    }

    /** Обида на владельца жетонов в цели этого варианта. */
    private double grudgeOfTarget(GameState state, Choice o) {
        Integer owner = ownerOfTarget(state, o);
        if (owner == null || owner < 0 || owner >= grudge.length) {
            return 0;
        }
        return grudge[owner];
    }

    private boolean targetsOffender(GameState state, Choice o) {
        Integer owner = ownerOfTarget(state, o);
        return owner != null && owner >= 0 && owner < grudge.length
            && grudge[owner] > 0.5;
    }

    /** Чей жетон стоит в цели: для гекса берём владельца первого чужого жетона. */
    private Integer ownerOfTarget(GameState state, Choice o) {
        if (o.payload() instanceof Token t) {
            return t.owner();
        }
        if (!(o.payload() instanceof String hex)) {
            return null;
        }
        for (var p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                if (hex.equals(u.hexId)) {
                    return p.seat;
                }
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (hex.equals(b.hexId)) {
                    return p.seat;
                }
            }
        }
        return null;
    }

    /** Короткая сводка состояния «души» — для рассказа и отчётов. */
    public String moodLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "задетость %.1f", tilt));
        for (int i = 0; i < grudge.length; i++) {
            if (grudge[i] > 0.5) {
                sb.append(String.format(Locale.ROOT, ", обида на игрока %d: %.1f",
                    i, grudge[i]));
            }
        }
        return sb.toString();
    }

    /** Тип здания цели — нужен подклассам и отчётам. */
    protected static boolean isCommandCenter(Token t) {
        return t instanceof BuildingToken b && b.type == BuildingType.COMMAND_CENTER;
    }
}
