package kelium.agents;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Target;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Order;
import kelium.engine.Passives;
import kelium.core.Choice;
import kelium.dataio.Ctx;

/**
 * StrategicAgent — обучаемый стратегический бот. Цель: максимум победных очков.
 *
 * <p>Наследует всю отлаженную ЭКОНОМИКУ {@link HeuristicAgent} (сборка, добыча,
 * стройка, рынок, наука, контейнеры, модули), но ПЕРЕОПРЕДЕЛЯЕТ военную часть,
 * делая её ОСМЫСЛЕННОЙ через {@link WorldView}:
 * <ul>
 *   <li>движение ведёт юнит к ближайшему врагу, которого этот юнит РЕАЛЬНО может
 *       убить (по таблице атак планшета), а не к произвольному ближайшему;
 *   <li>в бою приоритет целям, которые действительно уничтожаются (→ трофей →
 *       шаг трека → ПО), с бонусом за здания/ЦУ.
 * </ul>
 *
 * <p>Поведение задаётся {@link Genome} (веса) — это «память» бота, которую
 * настраивает эволюция ({@link EvoTrainer}). Оценочная функция позиции
 * {@link #evaluate} служит для сравнения исходов и обучения.
 */
public class StrategicAgent extends HeuristicAgent {

    private final Genome genome;
    /** Необязательный приёмник записей для архива партий (память об играх). */
    private final kelium.report.GameArchive archive;
    /** Необязательный «живой» рассказ: бот озвучивает решения от первого лица. */
    private kelium.report.NarrativeLog narrative;
    /**
     * Необязательный ПРИЁМНИК мыслей: те же фразы от первого лица, но не в файл,
     * а тому, кто слушает (проигрыватель партий кладёт их в лог рядом с ходами).
     * Работает независимо от {@link #narrative} — можно включить одно, другое
     * или оба сразу.
     */
    private java.util.function.BiConsumer<Integer, String> thoughts;

    /**
     * ТЕКУЩИЙ ПЛАН — промежуточная цель и цепочка шагов к ней (см. {@link Plan}).
     * Пересчитывается перед каждым выбором ДЕЙСТВИЯ и держится на всё, что
     * внутри этого действия решается: стройка, гекс, раскладка энергии. Без
     * него бот оценивал каждое решение в отрыве от остальных и мог построить
     * добытчик у жилы, а потом не запитать его — «забыв», зачем строил.
     */
    private Plan plan;
    /** План, о котором уже сказано вслух (чтобы не повторять одно и то же). */
    private String planSpoken;

    public StrategicAgent(int seat, Random rng, Genome genome) {
        this(seat, rng, genome, (kelium.report.GameArchive) null);
    }

    public StrategicAgent(int seat, Random rng, Genome genome, kelium.report.GameArchive archive) {
        super(seat, rng, "strategic#" + seat, "strategic",
              genome != null ? genome.weights : Genome.defaults().weights);
        this.genome = genome != null ? genome : Genome.defaults();
        this.archive = archive;
    }

    /**
     * Бот НАЗВАННОГО ХАРАКТЕРА: веса те же (геном), но и в логах, и в отчётах он
     * зовётся своим характером. С 12.08.2026 характеров вне геномов нет —
     * «Исследователь» и «Хаос» тоже линии эволюции, а не прошитые правила.
     */
    public StrategicAgent(int seat, Random rng, Genome genome, String character) {
        super(seat, rng, (character == null ? "strategic" : character) + "#" + seat,
              character == null ? "strategic" : character,
              genome != null ? genome.weights : Genome.defaults().weights);
        this.genome = genome != null ? genome : Genome.defaults();
        this.archive = null;
    }

    public Genome genome() {
        return genome;
    }

    /** Подключить «живой» рассказ — бот начнёт озвучивать свои решения. */
    public StrategicAgent withNarrative(kelium.report.NarrativeLog narrative) {
        this.narrative = narrative;
        return this;
    }

    /**
     * Подключить приёмник мыслей: бот будет отдавать те же объяснения от первого
     * лица (место, фраза) вызывающему — например проигрывателю партий.
     */
    public StrategicAgent withThoughts(java.util.function.BiConsumer<Integer, String> sink) {
        this.thoughts = sink;
        return this;
    }

    // ================= выбор + озвучка от первого лица ===================
    @Override
    public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
        String ctxKind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
        // ПЛАН ДЕРЖИТСЯ МЕЖДУ ХОДАМИ (15.08.2026). Раньше он пересоздавался с
        // нуля при каждом вскрытии и каждом действии: при двух близких по
        // ценности целях бот метался между ними и не доводил до конца НИ ОДНОЙ
        // цепочки — со стороны это выглядело как случайные приказы и отсутствие
        // тактики (разнос дизайнера, и разнос по делу). Теперь смена курса
        // должна ОПРАВДАТЬ свою цену: новый план обязан быть заметно лучше
        // свежей версии текущего (порог — ген plan.commit), иначе держим курс.
        if ("reveal_order".equals(ctxKind) || "action".equals(ctxKind)) {
            plan = reconsiderPlan(state);
            sayPlan();
        } else if (plan == null) {
            plan = Plan.best(state, seat, genome);
        }
        Choice pick = pick(state, options, context, ctxKind);
        boolean wantNarrative = narrative != null && narrative.isOpen();
        if (wantNarrative || thoughts != null) {
            String kind = context != null ? String.valueOf(context.getOrDefault("kind", "")) : "";
            String phrase = explain(state, kind, pick, context);
            if (phrase != null) {
                if (wantNarrative) {
                    narrative.say(seat, phrase);
                }
                if (thoughts != null) {
                    thoughts.accept(seat, phrase);
                }
            }
        }
        return pick;
    }

    /**
     * СОБСТВЕННО ВЫБОР варианта — точка расширения для ботов, которые думают
     * иначе. По умолчанию это оценка каждого варианта формулой (эвристика +
     * геном + план). Просчитывающий бот ({@link SearchAgent}) подменяет только
     * этот метод, а пересчёт плана и рассказ от первого лица остаются общими:
     * иначе каждый новый бот пришлось бы учить говорить заново.
     */
    protected Choice pick(GameState state, List<Choice> options,
                          Map<String, Object> context, String kind) {
        return super.choose(state, options, context);
    }

    /**
     * Оценка варианта штатной формулой — просчитывающему боту она нужна как
     * АПРИОРНАЯ: просчитывать все варианты дорого, поэтому сперва отбираются
     * несколько лучших «на глаз», и только они проверяются на деле.
     */
    protected double priorScore(GameState state, Choice o, String kind,
                                Map<String, Object> ctx) {
        var scorer = scorerFor(kind, ctx);
        return scorer == null ? 0.0 : scorer.apply(state, o);
    }

    /**
     * Русское объяснение выбора от ПЕРВОГО ЛИЦА (или null, если решение мелкое и
     * его не стоит озвучивать). Опирается на реальный вид решения и обстановку.
     */
    @SuppressWarnings("unchecked")
    private String explain(GameState state, String kind, Choice pick, Map<String, Object> ctx) {
        if (pick == null || "pass".equals(pick.kind())) {
            return switch (kind) {
                case "action" -> Phrasebook.pick("пас.действие", rng);
                case "combat_source", "combat_target" -> Phrasebook.pick("пас.бой", rng);
                case "build_pick", "build_hex" -> Phrasebook.pick("пас.стройка", rng);
                case "spec" -> null;   // пас в спец-фазе — молча
                default -> null;
            };
        }
        return switch (kind) {
            case "reveal_order" -> explainReveal(state, pick);
            case "action" -> explainAction(state, pick);
            case "move" -> explainMove(state, pick);
            case "maneuver_unit" -> explainManeuver(state, pick);
            case "combat_target" -> explainCombatTarget(state, pick);
            case "spec" -> explainSpec(state, pick);
            case "build_pick" -> explainBuildPick(state, pick);
            case "science", "sci_exchange" -> null;
            default -> null;
        };
    }

    // ================= реплики из словаря =================
    // Каждое объяснение — это КЛЮЧ СИТУАЦИИ («действие.добыча.сильно»), по
    // которому Phrasebook отдаёт случайную из нескольких заготовленных фраз.
    // Сам текст живёт в data/phrases/bot_phrases.*.yaml и правится без сборки.

    private static final String СИЛЬНО = "сильно";
    private static final String НОРМА = "норма";
    private static final String СЛАБО = "слабо";

    private String explainReveal(GameState state, Choice pick) {
        Map<String, Object> card = Ctx.cfg(state)
            .content.get("orders").byId((String) pick.payload());
        if (card == null) {
            return null;
        }
        if (Boolean.TRUE.equals(card.get("joker"))) {
            return Phrasebook.pick("приказ.безопасность." + jokerGrade(state), rng);
        }
        Order top = Order.fromCode((String) card.get("top"));
        String what = switch (top) {
            case DEVELOPMENT -> "разработка";
            case INFRASTRUCTURE -> "инфраструктура";
            case OPERATION -> "операция";
            case ACQUISITIONS -> "приобретения";
        };
        return Phrasebook.pick("приказ." + what + "." + orderGrade(state, top), rng);
    }

    /** Насколько приказ ложится в обстановку: совпал с планом — сильно. */
    private String orderGrade(GameState state, Order top) {
        String planned = plan == null || plan.nextStep == null
            ? null : plan.nextStep.action;
        List<String> actions = List.of(Order.ORDER_ACTIONS.get(top));
        if (planned != null && actions.contains(planned)) {
            return СИЛЬНО;
        }
        for (String a : actions) {
            if (!СЛАБО.equals(actionGrade(state, a))) {
                return НОРМА;
            }
        }
        return СЛАБО;
    }

    /** Джокер тем ценнее, чем больше действий сейчас реально полезны. */
    private String jokerGrade(GameState state) {
        int useful = 0;
        for (String a : Actions.ALL_NAMES) {
            if (СИЛЬНО.equals(actionGrade(state, a))) {
                useful++;
            }
        }
        return useful >= 2 ? СИЛЬНО : (useful == 1 ? НОРМА : СЛАБО);
    }

    private String explainAction(GameState state, Choice pick) {
        String name = (String) pick.payload();
        String what = switch (name) {
            case "assembly" -> "сборка";
            case "mining" -> "добыча";
            case "build" -> "стройка";
            case "energy_swap" -> "энергия";
            case "movement" -> "движение";
            case "combat" -> "бой";
            case "market" -> "рынок";
            case "science" -> "наука";
            default -> null;
        };
        if (what == null) {
            return null;
        }
        PlayerState me = state.player(seat);
        int pool = me.trophySpacePoints() + me.resources.debris();
        return Phrasebook.pick("действие." + what + "." + actionGrade(state, name), rng,
            "n", String.valueOf(pool), "келемий", String.valueOf(me.resources.kelium()));
    }

    /**
     * НАСКОЛЬКО ПОЛЕЗНО действие прямо сейчас. Оценка честная и дешёвая: она
     * смотрит на то же, на что смотрит сам бот, — запитанные здания, живые
     * жилы, деньги, трофеи и наличие целей, до которых реально дотянуться.
     */
    private String actionGrade(GameState state, String action) {
        PlayerState me = state.player(seat);
        return switch (action) {
            case "assembly" -> {
                boolean powered = false;
                boolean any = false;
                for (BuildingToken b : me.buildingsOnField()) {
                    if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                            || b.type == BuildingType.AIRBASE) {
                        any = true;
                        powered |= b.powered();
                    }
                }
                yield powered ? СИЛЬНО : (any ? СЛАБО : СЛАБО);
            }
            case "mining" -> {
                java.util.Set<String> live = Plan.liveTileHexes(state);
                boolean poweredNear = false;
                boolean anyNear = false;
                for (BuildingToken b : me.buildingsOnField()) {
                    if (b.type != BuildingType.MINER
                            || !Plan.touchesLiveTile(state, b.hexId, live)) {
                        continue;
                    }
                    anyNear = true;
                    poweredNear |= b.powered();
                }
                yield poweredNear ? СИЛЬНО : (anyNear ? НОРМА : СЛАБО);
            }
            case "build" -> {
                int coin = me.resources.coin();
                yield coin >= 5 ? СИЛЬНО : (coin >= 2 ? НОРМА : СЛАБО);
            }
            case "energy_swap" -> {
                int dark = 0;
                for (BuildingToken b : me.buildingsOnField()) {
                    if (b.energySlots > 0 && !b.powered()) {
                        dark++;
                    }
                }
                yield dark >= 2 ? СИЛЬНО : (dark == 1 ? НОРМА : СЛАБО);
            }
            case "movement", "combat" -> {
                WorldView wv = new WorldView(state, seat);
                if (wv.enemyTokens.isEmpty()) {
                    yield СЛАБО;
                }
                for (UnitToken u : me.unitsOnField()) {
                    if (!wv.killableEnemyHexes(u.type).isEmpty()) {
                        yield СИЛЬНО;
                    }
                }
                yield НОРМА;
            }
            case "market" -> {
                int kel = me.resources.kelium();
                yield kel >= 3 ? СИЛЬНО : (kel >= 1 ? НОРМА : СЛАБО);
            }
            case "science" -> {
                int pool = me.trophySpacePoints() + me.resources.debris();
                yield pool >= 3 ? СИЛЬНО : (pool >= 1 ? НОРМА : СЛАБО);
            }
            default -> НОРМА;
        };
    }

    @SuppressWarnings("unchecked")
    private String explainMove(GameState state, Choice pick) {
        Map<String, Object> pl = (Map<String, Object>) pick.payload();
        String dest = (String) pl.get("to");
        int uid = pl.get("uid") instanceof Number n ? n.intValue() : -1;
        UnitToken mover = findUnit(state, uid);
        if (mover == null) {
            return null;
        }
        WorldView wv = new WorldView(state, seat);
        var killable = wv.killableEnemyHexes(mover.type);
        String grade = "просто";
        if (!killable.isEmpty()) {
            Integer d = wv.bfsDistanceTo(dest, killable);
            if (d != null && d == 1) {
                grade = "удар";
            } else if (d != null) {
                grade = "сближение";
            }
        }
        if ("просто".equals(grade)) {
            Integer de = wv.distanceToNearestEnemy(dest);
            if (de != null && de <= 2) {
                grade = "давление";
            }
        }
        return Phrasebook.pick("движение." + unitKey(mover.type) + "." + grade, rng,
            "гекс", dest, "род", unitRu(mover.type));
    }

    private String explainCombatTarget(GameState state, Choice pick) {
        String hx = (String) pick.payload();
        WorldView wv = new WorldView(state, seat);
        for (Token t : wv.enemyTokens) {
            if (!hx.equals(t.hexId())) {
                continue;
            }
            String what;
            if (t instanceof BuildingToken b) {
                what = b.type == BuildingType.COMMAND_CENTER ? "цу" : "здание";
            } else if (t instanceof UnitToken u) {
                what = unitKey(u.type);
            } else {
                what = "цель";
            }
            String grade = wv.anyUnitCanKill(t) ? "убью" : "пораню";
            return Phrasebook.pick("бой." + what + "." + grade, rng, "гекс", hx);
        }
        // цели среди жетонов нет — значит бьём нейтральную постройку
        kelium.core.Hex h = state.field.get(hx);
        String grade = h != null && h.anyNeutralBig() ? "пораню" : "убью";
        return Phrasebook.pick("бой.нейтрал." + grade, rng, "гекс", hx);
    }

    private String explainSpec(GameState state, Choice pick) {
        String what = switch (pick.kind()) {
            case "spec_objective" -> "задание";
            case "spec_objective_burn" -> "сжечь_задание";
            case "spec_arsenal_burn" -> "сжечь_арсенал";
            case "spec_arsenal_install" -> "установить_арсенал";
            case "spec_super" -> "супер";
            case "spec_super_deploy" -> "развернуть_супер";
            case "spec_container" -> "контейнеры";
            default -> null;
        };
        return what == null ? null : Phrasebook.pick("спец." + what, rng);
    }

    /**
     * Что именно строим. Ключ учитывает не только тип здания, но и обстановку:
     * добытчик у живой жилы — «сильно», третья энергостанция при избытке
     * энергии — «слабо».
     */
    private String explainBuildPick(GameState state, Choice pick) {
        BuildingType type = buildTypeOf(pick);
        if (type == null) {
            return null;
        }
        String what = switch (type) {
            case MINER -> "добытчик";
            case POWER_PLANT -> "энергостанция";
            case BARRACKS -> "казарма";
            case FACTORY -> "завод";
            case AIRBASE -> "авиабаза";
            case COMMAND_CENTER -> "цу";
        };
        return Phrasebook.pick("стройка." + what + "." + buildGrade(state, type), rng,
            "здание", what);
    }

    /** Тип здания из выбора стройки — данные у разных вариантов лежат по-разному. */
    @SuppressWarnings("unchecked")
    private static BuildingType buildTypeOf(Choice pick) {
        Object payload = pick.payload();
        String code = null;
        if (payload instanceof Map<?, ?> m) {
            Object t = ((Map<String, Object>) m).get("btype");
            if (t == null) {
                t = ((Map<String, Object>) m).get("type");
            }
            code = t == null ? null : String.valueOf(t);
        } else if (payload instanceof String str) {
            code = str;
        }
        if (code == null) {
            return null;
        }
        try {
            return BuildingType.fromCode(code);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Насколько удачна стройка именно этого здания сейчас. */
    private String buildGrade(GameState state, BuildingType type) {
        PlayerState me = state.player(seat);
        int already = 0;
        int spareEnergy = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type == type) {
                already++;
            }
            if (b.type == BuildingType.POWER_PLANT || b.type == BuildingType.COMMAND_CENTER) {
                spareEnergy += b.energyIdle;
            }
        }
        return switch (type) {
            case MINER -> {
                boolean live = !Plan.liveTileHexes(state).isEmpty();
                yield live && already == 0 ? СИЛЬНО : (live ? НОРМА : СЛАБО);
            }
            case POWER_PLANT -> spareEnergy == 0 ? СИЛЬНО : (already == 0 ? НОРМА : СЛАБО);
            case BARRACKS, FACTORY, AIRBASE ->
                spareEnergy > 0 ? (already == 0 ? СИЛЬНО : НОРМА) : СЛАБО;
            case COMMAND_CENTER -> НОРМА;
        };
    }

    private String explainManeuver(GameState state, Choice pick) {
        int uid = pick.payload() instanceof Number n ? n.intValue() : -1;
        UnitToken u = findUnit(state, uid);
        if (u == null) {
            return null;
        }
        return Phrasebook.pick("манёвр." + unitKey(u.type), rng, "род", unitRu(u.type));
    }

    /** Ключ рода войск для словаря реплик. */
    private static String unitKey(UnitType t) {
        return switch (t) {
            case INFANTRY -> "пехота";
            case VEHICLE -> "техника";
            case AIRCRAFT -> "авиация";
            case TOWER -> "вышка";
        };
    }

    private static String unitRu(UnitType t) {
        return switch (t) {
            case INFANTRY -> "пехоту";
            case VEHICLE -> "технику";
            case AIRCRAFT -> "авиацию";
            case TOWER -> "вышку";
        };
    }

    private double g(String key) {
        return genome.get(key, wget(key));
    }

    // ================= переопределение военных решений ===================
    @Override
    protected BiFunction<GameState, Choice, Double> scorerFor(String kind, Map<String, Object> ctx) {
        BiFunction<GameState, Choice, Double> base = switch (kind) {
            case "reveal_order" -> (s, o) -> scoreRevealPlanned(s, o);
            case "move" -> (s, o) -> scoreMoveStrategic(s, o);
            case "combat_source" -> (s, o) -> scoreCombatSourceStrategic(s, o);
            case "combat_target" -> (s, o) -> scoreCombatTargetStrategic(s, o, ctx);
            case "maneuver_unit" -> (s, o) -> scoreManeuverUnit(s, o);
            case "build_pick" -> (s, o) -> scoreBuildPickStrategic(s, o, ctx);
            case "assemble" -> (s, o) -> scoreAssembleStrategic(s, o, ctx);
            default -> super.scorerFor(kind, ctx);   // экономика — от HeuristicAgent
        };
        if (base == null) {
            return null;
        }
        // ПОВЕРХ любой оценки — надбавка за то, что выбор двигает текущий план.
        return (s, o) -> base.apply(s, o) + planBonus(s, kind, o, ctx);
    }

    // ==================================================================
    //  Планирование: надбавка за шаг к промежуточной цели
    // ==================================================================

    /** Вес плановой надбавки: настраивается геномом (обучение может её двигать). */
    private double planWeight() {
        return genome.get("plan.focus", 1.0);
    }

    /**
     * Насколько выбор {@code o} приближает первый невыполненный шаг плана.
     *
     * <p>Это и есть «промежуточная цель» в действии: бот не просто считает
     * сиюминутную пользу, а спрашивает «нужно ли мне это для того, к чему я
     * иду». Надбавки крупные (сопоставимы с базовыми оценками) — иначе жадная
     * сиюминутная логика их перевешивает и цепочка снова рассыпается.
     */
    @SuppressWarnings("unchecked")
    private double planBonus(GameState s, String kind, Choice o, Map<String, Object> ctx) {
        if (plan == null || o == null) {
            return 0.0;
        }
        Plan.Step step = plan.nextStep;
        double k = planWeight();
        switch (kind) {
            case "reveal_order" -> {
                // ВЫБОР ПРИКАЗА ПОД ПЛАН. Раньше приказ выбирался по «сумме
                // весов действий вообще», из-за чего бот вскрывал Операцию без
                // единой цели на поле (82% боёв уходили впустую). Теперь приказ
                // оценивается по тому, содержит ли он действие, которым
                // закрывается ближайший шаг плана, или действие-развязку цели.
                String needNow = step != null ? step.action : finalAction(plan);
                String needLater = finalAction(plan);
                Object cardId = o.payload();
                if (cardId == null) {
                    return 0.0;
                }
                var card = Ctx.content(s)
                    .get("orders").byId(String.valueOf(cardId));
                if (card == null) {
                    return 0.0;
                }
                double bonus = 0.0;
                bonus += orderMatch(card.get("top"), needNow) * k * 7.0;
                bonus += orderMatch(card.get("top"), needLater) * k * 3.0;
                // нижняя половина срабатывает лишь при совпадении с соперником —
                // ценим её вдвое слабее
                bonus += orderMatch(card.get("bottom"), needNow) * k * 1.5;
                return bonus;
            }
            case "pay_power" -> {
                // Компенсация энергии монетами (только Разработка, только на это
                // действие). Платим охотно, если этим включается здание, которого
                // ждёт план: одна монета сейчас дешевле потерянного раунда.
                if (!Boolean.TRUE.equals(o.payload()) || step == null) {
                    return 0.0;
                }
                Object uid = ctx != null ? ctx.get("building") : null;
                if (step.needPowerUid != null && uid instanceof Number n
                        && n.intValue() == step.needPowerUid) {
                    return k * 5.0;
                }
                return 0.0;
            }
            case "action" -> {
                if (o.payload() == null) {
                    return 0.0;
                }
                String name = String.valueOf(o.payload());
                // действие, которым закрывается ближайший шаг
                if (step != null && name.equals(step.action)) {
                    return k * 6.0;
                }
                // цель уже собрана — играем действие-развязку (Добыча/Маркет/...)
                if (step == null && name.equals(finalAction(plan))) {
                    return k * 8.0;
                }
                return 0.0;
            }
            case "build_pick" -> {
                if (step == null || step.needBuild == null || !(o.payload() instanceof Map)) {
                    return 0.0;
                }
                Map<String, Object> spec = (Map<String, Object>) o.payload();
                Object bt = spec.get("btype");
                if (bt == step.needBuild) {
                    return k * 5.0;
                }
                // энергостанция закрывает шаг «запитать»: она приносит кубики
                if (bt == BuildingType.POWER_PLANT && step.needPowerUid != null) {
                    return k * 4.0;
                }
                return 0.0;
            }
            case "build_hex" -> {
                if (!(o.payload() instanceof String hid)) {
                    return 0.0;
                }
                String bt = ctx != null ? String.valueOf(ctx.get("btype")) : "";
                // НАВЕДЕНИЕ ПО ЗАДАНИЯМ — независимо от цели плана: карта в руке
                // может требовать стройки на определённом гексе при любой
                // стратегии, и терять эту наводку только потому, что бот сейчас
                // идёт за келемием, незачем (замер 19.08.2026: до этого выбор
                // гекса вообще не смотрел на задания, и «В ЭТОТ ХОД построй
                // так, чтобы…» закрывалось лишь случайно).
                double bonus = kelium.engine.ObjectiveTargeting.gainFromBuild(
                    s, seat, hid, "command_center".equals(bt))
                    * k * wget("objective.pursuit") * 12.0;
                // Под добычу — только впритык к жиле, это по-прежнему жёстко.
                if (plan.goal == Plan.Goal.KELIUM && "miner".equals(bt)) {
                    bonus += Plan.touchesLiveTile(s, hid, Plan.liveTileHexes(s))
                        ? k * 6.0 : -k * 4.0;
                }
                return bonus;
            }
            case "assemble" -> {
                // ЧТО НАНИМАТЬ — тоже цель внутри действия: часть заданий
                // требует род войск или их разнообразие за один ход.
                kelium.core.UnitType made = assembleUnit(s, o);
                if (made == null) {
                    return 0.0;
                }
                return kelium.engine.ObjectiveTargeting.gainFromProduce(s, seat, made.code)
                    * k * wget("objective.pursuit") * 12.0;
            }
            case "energy_place" -> {
                // ГЛАВНАЯ точка, где цепочка рвалась: кубик кладём в то здание,
                // которое ждёт план, а не в «вообще полезное».
                if (step == null || step.needPowerUid == null
                        || !(o.payload() instanceof Number n)) {
                    return 0.0;
                }
                return n.intValue() == step.needPowerUid ? k * 8.0 : 0.0;
            }
            case "energy_hex" -> {
                // гекс-исход берём тот, чьи кубики реально сдвинут план
                if (step == null || step.needPowerUid == null) {
                    return 0.0;
                }
                return o.payload() != null || "energy_storage".equals(o.kind()) ? k * 3.0 : 0.0;
            }
            case "mine" -> {
                // при цели «келемий» ветка контейнера — не то, зачем шли
                if (plan.goal == Plan.Goal.KELIUM && "kelium".equals(o.payload())) {
                    return k * 4.0;
                }
                return 0.0;
            }
            default -> {
                return 0.0;
            }
        }
    }

    /** 1.0, если приказ с этим кодом содержит нужное действие, иначе 0. */
    private static double orderMatch(Object orderCode, String action) {
        if (orderCode == null || action == null) {
            return 0.0;
        }
        kelium.engine.Order ord;
        try {
            ord = kelium.engine.Order.fromCode(String.valueOf(orderCode));
        } catch (RuntimeException e) {
            return 0.0;
        }
        for (String a : kelium.engine.Order.ORDER_ACTIONS.get(ord)) {
            if (a.equals(action)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    /** Действие-развязка цели: то, ради чего вся цепочка и строилась. */
    /**
     * ВЫБОР КАРТЫ ПРИКАЗА — ОТ ПЛАНА (15.08.2026). До этого приказ выбирала
     * формула весов действий, которая ПЛАН НЕ СПРАШИВАЛА ВООБЩЕ: бот мог вести
     * набег и вскрыть Приобретения, потому что «у рынка вес повыше». Именно это
     * дизайнер и видел как «каждый ход играют случайные карты приказов».
     *
     * <p>Теперь приказ, содержащий действие следующего шага плана, получает
     * прибавку (ген plan.reveal_pull), а финальное действие готового плана —
     * полуторную: дойти до конца цепочки важнее, чем начать её.
     */
    private double scoreRevealPlanned(GameState state, Choice o) {
        double base = super.scorerFor("reveal_order", null).apply(state, o);
        if (plan == null || o.payload() == null) {
            return base;
        }
        String needNow = plan.nextStep != null ? plan.nextStep.action : finalAction(plan);
        String needThen = finalAction(plan);
        Map<String, Object> card = Ctx.cards(state, "orders").byId((String) o.payload());
        if (card == null || Boolean.TRUE.equals(card.get("joker"))) {
            return base;                      // джокер и так гибкий, тянуть не надо
        }
        Order top = Order.fromCode((String) card.get("top"));
        double pull = genome.get("plan.reveal_pull", 6.0);
        for (String a : Order.ORDER_ACTIONS.get(top)) {
            if (a.equals(needNow)) {
                // готовый план (nextStep == null) закрывается финальным действием
                // — это самый ценный приказ на руке.
                base += plan.nextStep == null ? pull * 1.5 : pull;
            } else if (a.equals(needThen)) {
                base += pull * 0.4;           // приказ пригодится на шаг позже
            }
        }
        return base;
    }

    /**
     * Пересмотр плана с ОБЯЗАТЕЛЬСТВОМ: держим текущий курс, пока новый не
     * лучше его свежей оценки в {@code plan.commit} раз.
     *
     * <p>«Свежая версия текущего» — это план ТОЙ ЖЕ цели (и той же жертвы для
     * набега), пересобранный по нынешней обстановке: шаги в нём уже отмечены
     * сделанными, дистанции пересчитаны. Если цель умерла (жертву снесли,
     * жилы кончились) — свежей версии не будет, и мы честно берём лучший из
     * оставшихся. Это ровно та «инерция намерения», которой не хватало: у
     * живого игрока начатый манёвр стоит дороже равноценной альтернативы.
     */
    private Plan reconsiderPlan(GameState state) {
        java.util.List<Plan> all = Plan.candidates(state, seat, genome);
        Plan refreshed = null;
        Plan best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Plan p : all) {
            if (p == null) {
                continue;
            }
            double sc = p.score(genome);
            if (sc > bestScore) {
                bestScore = sc;
                best = p;
            }
            if (plan != null && p.goal == plan.goal
                    && java.util.Objects.equals(p.targetHex, plan.targetHex)) {
                refreshed = p;
            }
        }
        Plan kept;
        if (refreshed == null) {
            kept = best;                      // прежняя цель мертва — новый курс
        } else {
            double commit = Math.max(1.0, genome.get("plan.commit", 1.25));
            kept = bestScore > refreshed.score(genome) * commit ? best : refreshed;
        }
        // СЧЁТ ЦЕЛЕЙ ИДЁТ ИМЕННО ЗДЕСЬ. Plan.best вызывается один раз за партию
        // на игрока (первый план), а дальше курс держит этот пересмотр — счётчики
        // на Plan.best показывали 1% и отвечали не на тот вопрос.
        Plan.ВЫБОРОВ.incrementAndGet();
        if (kept != null && kept.goal == Plan.Goal.OBJECTIVE) {
            Plan.ВЫБРАНО_ЗАДАНИЕ.incrementAndGet();
        }
        return kept;
    }

    private static String finalAction(Plan p) {
        return switch (p.goal) {
            case KELIUM -> "mining";
            case SELL, ECONOMY -> "market";
            case TECH -> "science";
            case ARMY -> "assembly";
            case STRIKE -> "combat";       // финал набега — удар
            case OBJECTIVE -> null;
        };
    }

    /** Озвучить план, если он сменился (в лог мыслей и в рассказ). */
    private void sayPlan() {
        if (plan == null) {
            return;
        }
        String text = plan.describe();
        if (text.equals(planSpoken)) {
            return;
        }
        planSpoken = text;
        if (narrative != null && narrative.isOpen()) {
            narrative.say(seat, text);
        }
        if (thoughts != null) {
            thoughts.accept(seat, text);
        }
    }

    /** Текущий план бота — для отчётов и тестов. */
    public Plan currentPlan() {
        return plan;
    }

    /**
     * Движение: главный приоритет — встать в дистанцию удара (d==1) к врагу,
     * которого этот юнит МОЖЕТ убить. Если убиваемых целей нет — двигаемся к
     * ближайшему врагу вообще (слабее). Стоять на самом гексе врага (d==0) плохо:
     * бить оттуда нельзя (цель должна быть на СОСЕДНЕМ гексе).
     */
    @SuppressWarnings("unchecked")
    private double scoreMoveStrategic(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return g("aggression") >= 1.0 ? 0.2 : 0.6;
        }
        Map<String, Object> pl = (Map<String, Object>) o.payload();
        String dest = (String) pl.get("to");
        int uid = pl.get("uid") instanceof Number n ? n.intValue() : -1;
        UnitToken mover = findUnit(state, uid);
        if (mover == null) {
            return 1.0;
        }
        WorldView wv = new WorldView(state, seat);
        var killable = wv.killableEnemyHexes(mover.type);
        double agg = g("aggression");

        // НАБЕГ: сводим ВСЮ ударную силу к ОДНОМУ плацдарму рядом с одной ценной
        // целью — ЦУ приоритетно, а если оно недостижимо, план переключается на
        // любое другое чужое здание (см. siegeTargetHex). Бой даёт залп ВСЕМ
        // юнитам с ОДНОГО гекса-источника — поэтому важно копить технику на ОДНОЙ
        // клетке (staging), а не размазывать по соседям. Урон копится между
        // раундами, кучный залп добивает цель.
        // Цель берём из ПЛАНА, если он ведёт набег: движение, бой и приказы
        // обязаны смотреть на одну и ту же жертву, иначе цепочка расползается.
        String siege = plan != null && plan.goal == Plan.Goal.STRIKE
            && plan.targetHex != null && тамЕщёВраг(state, plan.targetHex)
            ? plan.targetHex : siegeTargetHex(state, wv, mover);
        boolean siegeIsCu = siege != null && isCuHex(state, siege);
        if (siege != null) {
            String staging = stagingHexFor(state, siege);
            if (staging != null && staging.equals(dest)) {
                // встал на плацдарм: за ЦУ бонус выше (мгновенная победа), за
                // прочую цель — обычная ценность добивания здания.
                return g("move.strike_range") * (1.2 + agg)
                    + (siegeIsCu ? g("combat.cu_bonus") : g("combat.building_bonus"));
            }
            java.util.Set<String> stg = staging != null
                ? java.util.Set.of(staging)
                : new java.util.HashSet<>(state.field.neighbors(siege));
            Integer dc = wv.bfsDistanceTo(dest, stg);
            if (dc != null) {
                double bonus = siegeIsCu ? g("combat.cu_bonus") : g("combat.building_bonus");
                return (g("move.toward_killable") + bonus * 0.5) * (0.7 + agg / (dc + 1.0));
            }
        }

        // КУЛАК (заказ дизайнера 15.08.2026, по замеру воронки боя). В бою
        // участвует ОДИН гекс-источник против ОДНОГО гекса-цели, поэтому сила
        // залпа — это сколько своих войск стоит НА ОДНОМ ГЕКСЕ, а вовсе не
        // сколько их на поле. Замер: 88% стопок — одиночные, отсюда 1.45
        // попадания на результативный бой и ноль военных побед. Двенадцать
        // жетонов по одному на гекс бьют ровно так же слабо, как один.
        //
        // Поэтому за переход на гекс, где УЖЕ стоит своё войско, полагается
        // прибавка — и тем больше, чем ближе оттуда до врага: собираться в кулак
        // в тылу бессмысленно, а на передовой это и есть подготовка залпа.
        double fist = 0.0;
        int alliesAtDest = 0;
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (u != mover && dest.equals(u.hexId)) {
                alliesAtDest++;
            }
        }
        if (alliesAtDest > 0) {
            Integer dEnemy = wv.distanceToNearestEnemy(dest);
            // Насыщение по числу: третий и четвёртый жетон в стопке добавляют
            // меньше второго — таблица атак всё равно ограничивает число рядов.
            double mass = Math.min(2.0, alliesAtDest);
            double front = dEnemy == null ? 0.3 : 1.0 / (1.0 + dEnemy);
            fist = g("move.mass_up") * mass * (0.3 + front);
        }

        if (!killable.isEmpty()) {
            // Приоритет — убиваемые токены ЛИДЕРА (давим сильнейшего). Строим
            // множество их гексов; если есть — целим их, иначе любые убиваемые.
            int leader = rivalLeaderSeat(state);
            java.util.Set<String> leaderKillable = new java.util.HashSet<>();
            if (leader >= 0) {
                for (Token t : wv.enemyTokens) {
                    if (t.owner() == leader && killable.contains(t.hexId())) {
                        leaderKillable.add(t.hexId());
                    }
                }
            }
            double leaderMul = leaderKillable.isEmpty() ? 1.0
                : 1.0 + g("combat.hit_leader") / 5.0;
            java.util.Set<String> aim = leaderKillable.isEmpty() ? killable : leaderKillable;
            // расстояние от НОВОГО положения до ближайшей (приоритетной) цели
            Integer d = wv.bfsDistanceTo(dest, aim);
            if (d != null) {
                if (d == 1) {
                    // Встал на ударную позицию — и тем ценнее, чем больше своих
                    // уже стоит здесь же: залп пойдёт всей стопкой разом.
                    return g("move.strike_range") * (0.6 + agg) * leaderMul + fist;
                }
                if (d == 0) {
                    return g("move.toward_killable") * 0.4 + fist;      // на гексе цели — бить нельзя
                }
                return g("move.toward_killable") * (0.5 + agg / (d + 1.0)) * leaderMul + fist;
            }
        }
        // убиваемых целей не видно — просто сближаемся с ближайшим врагом
        Integer de = wv.distanceToNearestEnemy(dest);
        if (de == null) {
            return 0.6 + fist;
        }
        if (de == 1) {
            return g("move.toward_enemy") * (0.8 + agg) + fist;
        }
        return g("move.toward_enemy") * (0.5 + agg / (de + 1.0)) + fist;
    }

    /**
     * Выбор гекса-ИСТОЧНИКА в бою. Для осады ЦУ: с гекса стреляют ВСЕ юниты
     * разом, поэтому предпочитаем плацдарм рядом с осадным ЦУ — там массируется
     * ударная техника, и кучный залп добивает ЦУ (обгоняя реген −1/раунд).
     */
    private double scoreCombatSourceStrategic(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            // Пасовать бой невыгодно, если рядом осадное ЦУ и есть чем бить.
            return 0.5;
        }
        String src = (String) o.payload();
        WorldView wv = new WorldView(state, seat);
        // Гекс без смежных целей = гарантированно ПУСТОЙ бой — хуже паса.
        // (Это был корень «боёв без ударов»: источник выбирался вслепую.)
        double tv = adjacentTargetValue(state, src);
        if (tv <= 0) {
            return 0.05;
        }
        int attackersHere = 0;
        for (UnitToken u : state.player(seat).unitsOnField()) {
            if (src.equals(u.hexId)) {
                attackersHere++;
            }
        }
        double best = (1.0 + tv) * (0.8 + 0.6 * g("aggression")) + 0.3 * attackersHere;
        for (Token t : wv.enemyTokens) {
            if (!(t instanceof BuildingToken b) || b.type != BuildingType.COMMAND_CENTER) {
                continue;
            }
            if (!state.field.neighbors(src).contains(b.hexId)) {
                continue;
            }
            // сколько моих бьющих-здания юнитов стоит на этом гексе-источнике
            int strikers = 0;
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!src.equals(u.hexId)) {
                    continue;
                }
                if (hitsBuildings(state, seat, u.type)) {
                    strikers++;
                }
            }
            int remain = Math.max(1, Passives.effectiveHp(state, b) - b.damage);
            // залп добьёт ЦУ (strikers >= остаток HP) — максимальный приоритет
            double val = strikers >= remain
                ? g("combat.cu_bonus") * 3.0
                : g("combat.cu_bonus") * (0.5 + strikers / (double) remain);
            best = Math.max(best, val);
        }
        return best;
    }

    /**
     * Выбор гекса-цели в бою: ценим гекс тем выше, чем больше на нём жетонов,
     * которые мы действительно способны убить; крупный бонус за здания/ЦУ
     * (уничтожение ЦУ — путь к военной победе, здания дают крупные трофеи).
     */
    private double scoreCombatTargetStrategic(GameState state, Choice o, Map<String, Object> ctx) {
        String hx = (String) o.payload();
        WorldView wv = new WorldView(state, seat);
        int leader = rivalLeaderSeat(state);
        double score = 0.5;
        // ПРОБИВАЕМОСТЬ С ИСТОЧНИКА: цель, по которой резолвер не даст ни одной
        // оплачиваемой атаки (таблица, закрытый гекс, скрытые юниты), — пустая
        // трата боя.
        String source = ctx != null ? (String) ctx.get("source") : null;
        if (source != null && state.combat instanceof kelium.engine.CombatResolver cr
                && !cr.canAttack(seat, source, hx)) {
            return 0.05;
        }
        // СНОС НЕЙТРАЛА: трофеи + контейнер сейчас, место под застройку потом.
        // Ценность растёт с теснотой — нейтралы задуманы как ограничитель
        // экспансии, который сносят в мид-гейме, когда место кончается.
        kelium.core.Hex targetHex = state.field.get(hx);
        if (targetHex.hasNeutral()) {
            double scarcity = buildSpaceScarcity(state);
            kelium.core.Hex.NeutralBuilding nb = targetHex.neutrals.get(0);
            score += g("combat.raze_neutral") * (0.3 + 1.2 * scarcity)
                + 0.3 * nb.trophyReward();
        }
        // ВЗГЛЯД НА ПАРТИЮ КАК НА ИГРУ С СОПЕРНИКАМИ (15.08.2026). Раньше здесь
        // была одна поправка «бей лидера» по числу очков. Теперь цель оценивается
        // с трёх сторон сразу: кому принадлежит (насколько этот игрок опасен),
        // сколько противник потеряет ДО КОНЦА ПАРТИИ, и не подставляюсь ли я сам.
        Rivalry riv = new Rivalry(state, seat);
        for (Token t : wv.enemyTokens) {
            if (!hx.equals(t.hexId())) {
                continue;
            }
            boolean killable = wv.anyUnitCanKill(t);
            // КОМУ ВРЕДИМ. Урон отстающему — подарок лидеру: действие потрачено,
            // а разрыв с тем, кто обгоняет, не изменился. Множитель около 1.4 за
            // удар по опасному и около 0.3 за удар по безобидному.
            double whose = 1.0 + g("rivalry.pick_target") * (riv.damageValue(t.owner()) - 0.7);
            // ЧЕГО ЛИШАЕМ. Уничтожение — это не разовый трофей, а изъятие всего,
            // что жетон принёс бы владельцу до конца партии. Именно этой поправки
            // не хватало: без неё размен всегда выглядел убытком, и бот копил
            // боеприпасы вместо стрельбы (замер 15.08.2026: 5.4 боеприпаса на
            // руках при 0.8 боя за раунд).
            double future = 1.0 + g("rivalry.future_loss") * (riv.lostFutureValue(t) - 1.0);
            if (killable) {
                score += g("combat.kill_value") * whose * future;
            } else {
                score += 0.5 * whose;   // цель есть, но убить нечем
            }
            // БЕЙ ЛИДЕРА: подавление сильнейшего соперника (мои ПО − лидер).
            if (t.owner() == leader && killable) {
                score += g("combat.hit_leader");
            }
            if (t instanceof BuildingToken b) {
                score += g("combat.building_bonus");
                // ЗАХВАТ: повреждённое чужое здание близко к добиванию —
                // добьём = отнимем у владельца + жетон себе (кто добил — берёт).
                if (killable && b.damage > 0) {
                    score += g("combat.building_bonus") * (1.0 + b.damage);
                }
                if (b.type == BuildingType.COMMAND_CENTER && killable) {
                    double progress = 1.0 + b.damage;   // damage копится между раундами
                    score += g("combat.cu_bonus") * progress;
                }
            }
        }
        return score;
    }

    /**
     * Дефицит места под застройку: 1.0 = строить почти негде, ~0.1 = просторно.
     * Кандидаты = свои гексы + их соседи (норм., без нейтрала, со свободными
     * сторонами) — грубая оценка зоны стройки.
     */
    private double buildSpaceScarcity(GameState state) {
        PlayerState me = state.player(seat);
        java.util.Set<String> options = new java.util.HashSet<>();
        for (BuildingToken b : me.buildingsOnField()) {
            kelium.core.Hex own = state.field.get(b.hexId);
            if (own.freeSectors() > 0) {
                options.add(b.hexId);
            }
            for (String nb : state.field.neighbors(b.hexId)) {
                kelium.core.Hex nh = state.field.get(nb);
                if (nh.kind == kelium.core.HexKind.NORMAL && !nh.hasNeutral()
                        && nh.freeSectors() > 0) {
                    options.add(nb);
                }
            }
        }
        int n = options.size();
        if (n <= 1) {
            return 1.0;
        }
        if (n <= 3) {
            return 0.6;
        }
        return n <= 5 ? 0.3 : 0.1;
    }

    /** Место соперника с наибольшими ПО (кого давить). -1, если соперников нет. */
    private int rivalLeaderSeat(GameState state) {
        int best = -1;
        int bestVp = Integer.MIN_VALUE;
        for (int st = 0; st < state.numPlayers(); st++) {
            if (st == seat) {
                continue;
            }
            int vp = kelium.engine.Scoring.scorePlayer(state, st).getOrDefault("total", 0);
            if (vp > bestVp) {
                bestVp = vp;
                best = st;
            }
        }
        return best;
    }

    /**
     * Выбрать ОДНО чужое ЦУ для осады — общую цель всей ударной силы. Иначе
     * техника/авиация распыляются и ни одно ЦУ не добивается. Приоритет: уже
     * повреждённое (ближе к падению), при равенстве — ближайшее к любому моему
     * бьющему-здания юниту и достижимое. null, если бить ЦУ нечем/недостижимо.
     */
    /**
     * Единый плацдарм для осады — ОДНА клетка рядом с осадным ЦУ, куда сводится
     * вся ударная техника (залп идёт со всего одного гекса-источника). Выбираем
     * детерминированно: соседний с ЦУ обычный проходимый гекс с наименьшим id.
     */
    private String stagingHexFor(GameState state, String cuHex) {
        String best = null;
        for (String nb : state.field.neighbors(cuHex)) {
            kelium.core.Hex h = state.field.get(nb);
            if (h == null || h.kind == kelium.core.HexKind.FORBIDDEN
                    || h.hasSpawnTile()) {
                continue;
            }
            // не занят чужим зданием
            boolean enemyBld = false;
            for (PlayerState p : state.players) {
                if (p.seat == seat) {
                    continue;
                }
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (nb.equals(b.hexId)) {
                        enemyBld = true;
                        break;
                    }
                }
            }
            if (enemyBld) {
                continue;
            }
            if (best == null || nb.compareTo(best) < 0) {
                best = nb;
            }
        }
        return best;
    }

    /** Жив ли на гексе хоть один чужой жетон — жертва плана могла быть снесена. */
    private boolean тамЕщёВраг(GameState state, String hexId) {
        for (PlayerState p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    return true;
                }
            }
            for (UnitToken u : p.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Стоит ли на этом гексе чужое ЦУ — различить, каким бонусом награждать плацдарм. */
    private boolean isCuHex(GameState state, String hexId) {
        for (PlayerState p : state.players) {
            if (p.seat == seat) {
                continue;
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER && hexId.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Бьёт ли этот род войск здания/вышки по печатной таблице планшета этого
     * места — эвристика «стоит ли вести юнита к чужому зданию», не настоящий
     * бой (модули и цену не учитывает, только печатную таблицу).
     *
     * <p>БОЙ 2.0 ({@link kelium.core.TroopSide#dualCell()}): универсальная
     * ячейка есть у каждого рода и достаёт здания всегда — печатной таблицы
     * тут не хватает, чтобы честно ответить «нет».
     */
    private static boolean hitsBuildings(GameState state, int seat, UnitType type) {
        var side = state.player(seat).board.troop;
        if (side.dualCell()) {
            return true;
        }
        Target[] atk = side.attacks(type);
        return atk != null && (atk[0] == Target.BUILDINGS_TOWERS || atk[1] == Target.BUILDINGS_TOWERS);
    }

    private String siegeCuHex(GameState state, WorldView wv, UnitToken mover) {
        // мувер должен уметь бить здания (техника/авиация/вышка)
        if (!hitsBuildings(state, seat, mover.type)) {
            return null;
        }
        String best = null;
        int bestDmg = -1;
        Integer bestDist = null;
        for (Token t : wv.enemyTokens) {
            if (!(t instanceof BuildingToken b) || b.type != BuildingType.COMMAND_CENTER) {
                continue;
            }
            java.util.Set<String> adj = new java.util.HashSet<>(state.field.neighbors(b.hexId));
            // достижимо ли хоть от одного моего юнита, способного бить здания
            Integer dist = null;
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!hitsBuildings(state, seat, u.type)) {
                    continue;
                }
                Integer d = wv.bfsDistanceTo(u.hexId, adj);
                if (d != null && (dist == null || d < dist)) {
                    dist = d;
                }
            }
            if (dist == null) {
                continue;   // недостижимо
            }
            // приоритет: больше урона -> ближе
            if (b.damage > bestDmg || (b.damage == bestDmg && (bestDist == null || dist < bestDist))) {
                bestDmg = b.damage;
                bestDist = dist;
                best = b.hexId;
            }
        }
        return best;
    }

    /**
     * ПЛАН НАБЕГА НА ЛЮБУЮ ЦЕННУЮ ЦЕЛЬ, не только на ЦУ (заказ дизайнера
     * 15.08.2026, по замеру: жнец с глубоким просчётом сносил 2.61 жетона за
     * партию вместо заявленных 8, а в 25% партий не сносил вообще ничего).
     *
     * <p>Единственный в коде механизм многоходового плана — сведение техники на
     * один плацдарм — существовал ТОЛЬКО для осады чужого ЦУ ({@link
     * #siegeCuHex}). А военная победа за 1350 замеренных партий не случилась ни
     * разу: значит этот план почти никогда не активировался, и всё «планирование
     * наперёд» у бота фактически было пустым местом всю партию, кроме редких
     * случаев осады ЦУ.
     *
     * <p>Здесь та же схема (плацдарм → накопление силы → залп), но целью
     * становится ЛЮБОЕ ценное чужое здание, до которого реально дотянуться. ЦУ
     * остаётся приоритетом первого порядка (мгновенная победа), но если оно
     * недостижимо — план не пустеет, а переключается на следующую по ценности
     * цель, вместо того чтобы молчать всю партию.
     *
     * @return гекс-плацдарм рядом с целью, либо {@code null} — целей нет вовсе
     */
    private String siegeTargetHex(GameState state, WorldView wv, UnitToken mover) {
        String cu = siegeCuHex(state, wv, mover);
        if (cu != null) {
            return cu;
        }
        if (!hitsBuildings(state, seat, mover.type)) {
            return null;
        }
        Rivalry riv = new Rivalry(state, seat);
        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Token t : wv.enemyTokens) {
            if (!(t instanceof BuildingToken b) || b.hexId == null
                    || b.type == BuildingType.COMMAND_CENTER) {
                continue;                      // ЦУ уже разобран веткой выше
            }
            java.util.Set<String> adj = new java.util.HashSet<>(state.field.neighbors(b.hexId));
            Integer dist = null;
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!hitsBuildings(state, seat, u.type)) {
                    continue;
                }
                Integer d = wv.bfsDistanceTo(u.hexId, adj);
                if (d != null && (dist == null || d < dist)) {
                    dist = d;
                }
            }
            if (dist == null) {
                continue;                      // недостижимо ни одним бьющим здания юнитом
            }
            // Ценность цели: чем опаснее владелец (по Rivalry) и чем дороже
            // здание (по урону, уже накопленному, и по типу — экономика владельца
            // ценнее казармы), тем выше приоритет; дальняя цель дешевле — план
            // должен успеть сложиться за оставшуюся часть партии.
            double value = 1.0 + b.damage
                + (b.type == BuildingType.MINER || b.type == BuildingType.POWER_PLANT ? 1.5 : 0.5);
            double score = value * (0.4 + riv.damageValue(b.owner())) / (1.0 + dist);
            if (score > bestScore) {
                bestScore = score;
                best = b.hexId;
            }
        }
        return best;
    }

    /**
     * Стройка: поверх базовой логики HeuristicAgent повышаем ценность ЗАВОДА и
     * АВИАБАЗЫ — только техника и авиация способны бить ЦУ и здания. Без них
     * военная победа недостижима, а боты по умолчанию клепают лишь казармы.
     */
    @SuppressWarnings("unchecked")
    private double scoreBuildPickStrategic(GameState state, Choice o, Map<String, Object> ctx) {
        double base = super.scorerFor("build_pick", ctx).apply(state, o);
        if ("pass".equals(o.kind())) {
            return base;
        }
        Map<String, Object> spec = o.payload() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String label = String.valueOf(spec.getOrDefault("label", ""));
        PlayerState me = state.player(seat);
        int spare = spareEnergy(state, me);
        if ((label.equals("factory") || label.equals("airbase"))) {
            int need = "factory".equals(label) ? 2 : 3;
            // считаем ударные здания, что уже есть
            int strikeBld = 0;
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.type == BuildingType.FACTORY || b.type == BuildingType.AIRBASE) {
                    strikeBld++;
                }
            }
            // строим, если хватает энергии и таких зданий ещё мало
            if (spare >= need && strikeBld < 2) {
                base += g("build.strike_building") * (1.0 + 0.5 * g("aggression"));
            }
            // ПЕРВОЕ ударное здание — обязательная часть базы: без техники/
            // авиации нечем бить здания, и война выключается целиком. Свободной
            // энергии не бывает почти никогда — но здание можно запитать
            // МОНЕТАМИ (замена энергии), поэтому энергетический замок не ждём.
            if (strikeBld == 0 && "factory".equals(label)) {
                // ПЕРВОЕ ударное здание — из ГЕНОМА, без ручной константы.
                // Экономика и военка спорят за один и тот же ранний ход, и
                // подкручивание константы просто перекидывало качели: то нет
                // техники, то нет добычи. Пусть баланс находит обучение.
                base += g("build.strike_building");
            }
        }
        // ЭКОНОМИКА — равный голос с войной. Раньше стратегический слой давал
        // прибавку ТОЛЬКО военным зданиям, поэтому добытчики строил один лишь
        // «голубь» (у него занижена агрессия). Келемий конечен: кто первым
        // поставил добытчик к жиле — тот её и выберет.
        if (label.startsWith("miner") || label.startsWith("plant")) {
            double econ = g("eval.economy");
            if (label.startsWith("miner")) {
                // сколько келемия реально можно достать: считаем ближайшие жилы
                int reachable = 0;
                for (var h : state.field.hexes.values()) {
                    if (h.spawnTile == null || h.spawnTile.kelium <= 0) {
                        continue;
                    }
                    boolean mine = false;
                    for (String nb : state.field.neighbors(h.id)) {
                        for (BuildingToken b : me.buildingsOnField()) {
                            if (nb.equals(b.hexId)) {
                                mine = true;
                                break;
                            }
                        }
                    }
                    if (mine) {
                        reachable += h.spawnTile.kelium;
                    }
                }
                // ВТОРОЙ И ТРЕТИЙ ДОБЫТЧИК НУЖНЫ. Действие Добыча снимает
                // келемий СО ВСЕХ запитанных добытчиков сразу, поэтому каждый
                // новый добытчик у СВОЕЙ жилы умножает отдачу одного действия.
                // Раньше здесь стоял делитель (1 + число добытчиков), и бот
                // ограничивался одним — это была ошибка (замечание дизайнера).
                // Считаем не «сколько их у меня», а сколько келемия ещё НЕ
                // прикрыто моими добытчиками: пустой второй добытчик у той же
                // выработанной жилы бесполезен, а у новой — удваивает Добычу.
                int uncovered = 0;
                for (var h : state.field.hexes.values()) {
                    if (h.spawnTile == null || h.spawnTile.kelium <= 0) {
                        continue;
                    }
                    boolean mineAdj = false;
                    boolean minerAdj = false;
                    for (String nb : state.field.neighbors(h.id)) {
                        for (BuildingToken b : me.buildingsOnField()) {
                            if (!nb.equals(b.hexId)) {
                                continue;
                            }
                            mineAdj = true;
                            if (b.type == BuildingType.MINER) {
                                minerAdj = true;
                            }
                        }
                    }
                    if (mineAdj && !minerAdj) {
                        uncovered += h.spawnTile.kelium;
                    }
                }
                base += econ * (2.0 + 0.6 * Math.min(8, reachable)
                    + 0.9 * Math.min(8, uncovered));
                if (spare <= 0) {
                    // питать нечем — но в Разработке ячейку можно закрыть
                    // монетой, так что это лишь повод поторопиться со станцией
                    base -= me.resources.coin() >= 2 ? 0.6 : 2.0;
                }
            } else {
                // станция нужна, когда питать нечем: без неё стоит вся экономика
                base += econ * (spare <= 0 ? 4.0 : 1.0);
            }
        }
        // ПОД СОСТАВ ВРАГА: военное здание ценнее, если его род войск способен
        // убивать то, что реально стоит на поле у противников.
        UnitType ut = switch (label) {
            case "barracks" -> UnitType.INFANTRY;
            case "factory" -> UnitType.VEHICLE;
            case "airbase" -> UnitType.AIRCRAFT;
            default -> null;
        };
        if (ut != null) {
            WorldView wv = new WorldView(state, seat);
            int killables = 0;
            for (Token t : wv.enemyTokens) {
                if (wv.canKill(ut, t)) {
                    killables++;
                }
            }
            base += Math.min(6, killables) * 0.8 * (0.5 + 0.5 * g("aggression"));
        }
        return base;
    }

    /**
     * Сборка: предпочитаем производить ТЕХНИКУ/АВИАЦИЮ (единственные, кто бьёт
     * ЦУ), когда здание это позволяет и есть смысл давить на военную победу.
     */
    @SuppressWarnings("unchecked")
    private double scoreAssembleStrategic(GameState state, Choice o, Map<String, Object> ctx) {
        double base = super.scorerFor("assemble", ctx).apply(state, o);
        if ("pass".equals(o.kind())) {
            return base;
        }
        Map<String, Object> pl = o.payload() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        if ("unit".equals(pl.get("kind"))) {
            String bt = ctx != null ? String.valueOf(ctx.get("building_type")) : "";
            if ("factory".equals(bt) || "airbase".equals(bt)) {
                base += g("assemble.strike_unit") * (0.8 + 0.4 * g("aggression"));
            }
            // ВЫШКА из ЦУ: скорость 0, в атаку не пойдёт. Не штамповать вышки
            // ради очков — ЦУ полезнее патронами (война ест боеприпасы).
            if ("command_center".equals(bt)) {
                base = Math.max(0.2, base - 2.5);
            }
            // ПОД СОСТАВ ВРАГА: юнит ценен настолько, насколько много чужих
            // жетонов он способен убить (камень-ножницы планшета). Без этого
            // армия из одной пехоты стоит вплотную к зданиям и не может бить.
            UnitType ut = switch (bt) {
                case "barracks" -> UnitType.INFANTRY;
                case "factory" -> UnitType.VEHICLE;
                case "airbase" -> UnitType.AIRCRAFT;
                default -> null;
            };
            if (ut != null) {
                WorldView wv = new WorldView(state, seat);
                int killables = 0;
                for (Token t : wv.enemyTokens) {
                    if (wv.canKill(ut, t)) {
                        killables++;
                    }
                }
                int fielded = 0;
                for (UnitToken u : state.player(seat).unitsOnField()) {
                    if (u.type == ut) {
                        fielded++;
                    }
                }
                // Пока ударных юнитов этого типа мало — производить их важнее
                // всего: без них война физически невозможна.
                double hunger = fielded < 4 ? 0.9 : 0.4;
                base += Math.min(8, killables) * hunger * (0.7 + 0.3 * g("aggression"));
            }
        }
        return base;
    }

    /**
     * Выбор жетона для бесплатного манёвра: предпочитаем тот, у кого есть
     * убиваемая цель — манёвр приблизит к удару без затрат Операции/боеприпасов.
     */
    private double scoreManeuverUnit(GameState state, Choice o) {
        if ("pass".equals(o.kind())) {
            return 0.3;
        }
        int uid = o.payload() instanceof Number n ? n.intValue() : -1;
        UnitToken u = findUnit(state, uid);
        if (u == null) {
            return 0.5;
        }
        WorldView wv = new WorldView(state, seat);
        var killable = wv.killableEnemyHexes(u.type);
        if (killable.isEmpty()) {
            return 0.8;
        }
        Integer d = wv.bfsDistanceTo(u.hexId, killable);
        if (d == null) {
            return 0.8;
        }
        // ближе к убиваемой цели — ценнее манёвр (d==2 идеально: за манёвр встанем в удар)
        return 2.0 + 3.0 / (d + 0.5);
    }

    private UnitToken findUnit(GameState state, int uid) {
        for (UnitToken u : state.player(seat).units) {
            if (u.uid == uid) {
                return u;
            }
        }
        return null;
    }

    // ================= оценочная функция позиции =========================
    /**
     * Оценка позиции игрока {@code seat} глазами генома: взвешенная сумма
     * признаков (ПО сейчас, убиваемые цели в досягаемости, армия у линии удара,
     * экономический задел, боеприпасы, прогресс треков, трофейный пул). Служит
     * fitness-сигналом обучения и для сравнения исходов (мелкий lookahead).
     */
    public double evaluate(GameState state) {
        return evaluate(state, seat, genome);
    }

    /**
     * Статическая оценка позиции {@code seat} — ВЗВЕШЕННАЯ СУММА ПРИЗНАКОВ
     * ({@link StateFeatures}) с весами из генома.
     *
     * <p>Признаки и веса разведены нарочно: набор признаков — это «что вообще
     * бывает важно в этой игре» (человеческое знание, один список на всю
     * систему), а веса — «насколько важно именно это» (машинное знание, его
     * находит отбор). Раньше и то и другое было замешано в семь строк кода, и
     * настраивать было почти нечего.
     *
     * <p>Если подключена обученная {@link ValueNet}, она ЗАМЕНЯЕТ линейную сумму:
     * сеть учится нелинейным связям («келемий без места в хранилище бесполезен»),
     * которые линейной суммой не выражаются вовсе.
     *
     * <p>{@link ValueNetOnnx} (PyTorch-пайплайн, 18.08.2026) — ЗАПАСНОЙ ВАРИАНТ
     * ПОСЛЕ {@link ValueNet}, а не вместо неё: у ValueNet вход — веса генома
     * ({@link StateFeatures}, обучается вместе с самим геномом отбором),
     * у ValueNetOnnx — общий на процесс граф от {@link PublicView}, который
     * учится отдельно, на Python. Обе выключены по умолчанию (оба {@code
     * active()} — {@code null}), поведение без явного включения не меняется.
     */
    public static double evaluate(GameState state, int seat, Genome genome) {
        // Судья позиции берётся СНАЧАЛА из генома самого бота: так за одним столом
        // сидят и бот с обученной оценкой, и бот с линейной, и сравнение честное.
        // Общая на процесс сеть остаётся запасным вариантом для старых стендов.
        ValueNet net = genome != null && genome.judge != null ? genome.judge
            : ValueNet.active();
        if (net != null) {
            return net.value(state, seat);
        }
        ValueNetOnnx onnx = ValueNetOnnx.active();
        if (onnx != null) {
            return onnx.value(state, seat);
        }
        return linearEvaluate(state, seat, genome);
    }

    /** Линейная (читаемая) оценка позиции — базовая версия и запасной вариант. */
    public static double linearEvaluate(GameState state, int seat, Genome genome) {
        double[] f = StateFeatures.of(state, seat);
        double val = 0.0;
        for (int i = 0; i < StateFeatures.DIM; i++) {
            val += genome.get(StateFeatures.weightKey(i), 0.0) * f[i];
        }
        return val;
    }

    // ================= память о партиях ==================================
    @Override
    public void observeEvent(Map<String, Object> event) {
        if (archive != null) {
            archive.record(seat, event);
        }
    }

    /** Тип планшета (сторона) для справки в архиве/логах. */
    @SuppressWarnings("unused")
    private String troopSide(GameState state) {
        GameConfig cfg = Ctx.cfg(state);
        return cfg != null ? state.player(seat).board.troop.side : "?";
    }
}
