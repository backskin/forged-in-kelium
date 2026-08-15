package kelium.agents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Objectives;
import kelium.engine.Storage;
import kelium.dataio.Ctx;

/**
 * ПЛАН — промежуточная цель бота и цепочка шагов к ней.
 *
 * <p>Зачем это нужно. Раньше бот оценивал каждое решение в отдельности:
 * «положить кубик на добытчика — 3.5 очка, на казарму — 2.5». Никакой связи
 * между решениями не было, поэтому он мог поставить добытчик у самой жилы и
 * НИКОГДА его не запитать: в момент раскладки энергии он уже не помнил, зачем
 * этот добытчик строился. Живой игрок так не думает — он держит в голове
 * цепочку: <i>нужен келемий → нужна добыча → нужен добытчик у жилы → нужно его
 * запитать → нужна энергия</i> — и все ходы подчиняет ей.
 *
 * <p>План — это именно такая цепочка. Он состоит из ШАГОВ; шаги проверяются по
 * порядку, и первый невыполненный становится {@link #nextStep} — «что мешает
 * прямо сейчас». Дальше каждый скоринг спрашивает у плана: приближает ли этот
 * выбор нужный шаг? — и если да, получает крупную надбавку.
 *
 * <p>План пересчитывается на КАЖДОМ ходу заново (обстановка меняется), но
 * держится один на весь ход — иначе бот метался бы между целями внутри хода.
 */
public final class Plan {

    /** Вид цели — крупная линия развития, к которой бот сейчас идёт. */
    public enum Goal {
        /** Добыть келемий: он же победное очко, он же товар для маркета. */
        KELIUM("добыть келемий"),
        /** Продать келемий на маркете: превратить очко в темп (деньги/патроны). */
        SELL("продать келемий за ресурсы"),
        /** Поднять шаг технологического трека: самый крупный источник ПО. */
        TECH("поднять трек науки"),
        /** Произвести войска: без армии нет ни боя, ни давления. */
        ARMY("собрать войско"),
        /** Довести до конца задание из руки. */
        OBJECTIVE("выполнить задание"),
        /** Развернуть экономику: денег ни на что не хватает. */
        ECONOMY("поднять экономику");

        public final String ru;

        Goal(String ru) {
            this.ru = ru;
        }
    }

    /** Один шаг цепочки: что должно быть верно и что делать, если неверно. */
    public static final class Step {
        public final String what;          // «добытчик стоит у живой жилы»
        public final boolean done;
        /** Действие, которым шаг закрывается (build / energy_swap / mining / ...). */
        public final String action;
        /** uid здания, которое надо запитать (или null). */
        public final Integer needPowerUid;
        /** тип здания, которое надо построить (или null). */
        public final BuildingType needBuild;

        Step(String what, boolean done, String action, Integer needPowerUid, BuildingType needBuild) {
            this.what = what;
            this.done = done;
            this.action = action;
            this.needPowerUid = needPowerUid;
            this.needBuild = needBuild;
        }

        static Step of(String what, boolean done, String action) {
            return new Step(what, done, action, null, null);
        }
    }

    public final Goal goal;
    public final List<Step> steps;
    /** Первый невыполненный шаг — «что мешает». null, если цель достижима сейчас. */
    public final Step nextStep;
    /** Насколько цель ценна (для выбора между планами). */
    public final double value;

    private Plan(Goal goal, List<Step> steps, double value) {
        this.goal = goal;
        this.steps = steps;
        this.value = value;
        Step first = null;
        for (Step s : steps) {
            if (!s.done) {
                first = s;
                break;
            }
        }
        this.nextStep = first;
    }

    /** Сколько шагов ещё не сделано. */
    public int missing() {
        int n = 0;
        for (Step s : steps) {
            if (!s.done) {
                n++;
            }
        }
        return n;
    }

    /** Человеческое описание плана для лога мыслей. */
    public String describe() {
        StringBuilder sb = new StringBuilder("ЦЕЛЬ: ").append(goal.ru).append(". ");
        if (nextStep == null) {
            sb.append("Всё готово — забираю.");
        } else {
            sb.append("Мешает: ").append(nextStep.what).append('.');
            List<String> rest = new ArrayList<>();
            for (Step s : steps) {
                rest.add((s.done ? "+ " : "- ") + s.what);
            }
            sb.append(" Цепочка: ").append(String.join(" → ", rest));
        }
        return sb.toString();
    }

    // ==================================================================
    //  Построение планов из обстановки
    // ==================================================================

    /**
     * Выбрать лучший план на этот ход: считаем все цели, берём ту, у которой
     * лучшее отношение «ценность к числу недостающих шагов». Так короткая
     * дешёвая цель обгоняет далёкую дорогую, но далёкая всё же выигрывает,
     * если приз крупный, — ровно как рассуждает живой игрок.
     */
    public static Plan best(GameState s, int seat) {
        return best(s, seat, Genome.defaults());
    }

    /**
     * Выбрать лучший план с ЦЕННОСТЯМИ ЦЕЛЕЙ ИЗ ГЕНОМА. Раньше эти числа были
     * константами в коде, и обучение до них не дотягивалось — «что важнее,
     * наука или армия» решал автор навсегда. Теперь это решает отбор, и линии
     * характеров расходятся именно на выборе цели, а не только на тактике.
     */
    public static Plan best(GameState s, int seat, Genome g) {
        List<Plan> all = new ArrayList<>();
        all.add(kelium(s, seat, g));
        all.add(sell(s, seat, g));
        all.add(tech(s, seat, g));
        all.add(army(s, seat, g));
        all.add(economy(s, seat, g));
        all.add(objective(s, seat, g));
        // Штраф за длину ЦЕПОЧКИ мягкий, иначе бот вечно берёт самую короткую
        // цель и никогда не начинает длинную — а длинные (добыча) как раз и есть
        // развитие. Величина штрафа тоже ген: темп игры подбирает отбор.
        double penalty = Math.max(0.05, g.get("plan.chain_penalty", 0.5));
        Plan best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Plan p : all) {
            if (p == null) {
                continue;
            }
            double sc = p.value / (1.0 + penalty * p.missing());
            if (sc > bestScore) {
                bestScore = sc;
                best = p;
            }
        }
        return best;
    }

    /** Цепочка добычи: жила → добытчик рядом → запитан → место в хранилище. */
    private static Plan kelium(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        Set<String> liveTiles = liveTileHexes(s);
        if (liveTiles.isEmpty()) {
            return null;   // на поле не осталось келемия — цель мертва
        }
        BuildingToken adjMiner = null;
        BuildingToken anyMiner = null;
        int working = 0;
        for (BuildingToken b : me.buildingsOnField()) {
            if (b.type != BuildingType.MINER) {
                continue;
            }
            anyMiner = b;
            if (touchesLiveTile(s, b.hexId, liveTiles)) {
                working++;
                if (adjMiner == null || (!adjMiner.powered() && b.powered())) {
                    adjMiner = b;
                }
            }
        }
        // ОДНОГО ДОБЫТЧИКА МАЛО. Действие Добыча снимает келемий со ВСЕХ
        // запитанных добытчиков сразу, поэтому второй и третий добытчик у своих
        // жил умножают отдачу одного действия. Сколько хотим — столько жил ещё
        // не разобрано, но не больше трёх (дальше упирается в склад и энергию).
        int want = Math.min(3, Math.max(1, liveTiles.size()));
        List<Step> steps = new ArrayList<>();
        steps.add(Step.of("на поле есть живая жила", true, null));
        steps.add(new Step("добытчик стоит у жилы", adjMiner != null, "build", null,
            BuildingType.MINER));
        steps.add(new Step("добытчик запитан",
            adjMiner != null && adjMiner.powered(), "energy_swap",
            adjMiner != null ? adjMiner.uid : null, null));
        // Первый добытчик работает — расширяемся: следующий встаёт у СВОЕЙ жилы
        // и добавляет свою выработку к той же одной Добыче.
        steps.add(new Step("ещё добытчик у свободной жилы (" + working + " из " + want + ")",
            working >= want, "build", null, BuildingType.MINER));
        boolean room = Storage.keliumMax(me) > me.resources.kelium();
        steps.add(Step.of("в хранилище есть место под келемий", room, "market"));
        steps.add(Step.of("сыграть Добычу", false, "mining"));

        // келемий = 1 ПО за штуку, плюс товар на маркете; чем меньше его у нас,
        // тем ценнее первый (склад не резиновый — ценность убывает)
        double v = g.get("plan.value.kelium", 9.0) - 0.6 * me.resources.kelium();
        if (anyMiner != null && adjMiner == null) {
            v += 1.5;   // добытчик уже есть, но не там — перенести дешевле, чем строить
        }
        return new Plan(Goal.KELIUM, steps, Math.max(2.0, v));
    }

    /** Цепочка продажи: есть келемий → сыграть Маркет. */
    private static Plan sell(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        int kel = me.resources.kelium();
        if (kel <= 0) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        steps.add(Step.of("келемий в хранилище есть", true, null));
        steps.add(Step.of("сыграть Маркет", false, "market"));
        // Продавать стоит, когда денег в обрез: келемий сам по себе даёт ПО,
        // поэтому размен оправдан только под конкретную нужду.
        double full = g.get("plan.value.sell", 6.0);
        double need = me.resources.coin() <= 2 ? full : full / 3.0;
        return new Plan(Goal.SELL, steps, need + Math.min(kel, 3));
    }

    /** Цепочка науки: есть трофеи → сыграть Науку. */
    private static Plan tech(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        int pool = me.resources.debris() + me.trophySpacePoints();
        if (pool <= 0) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        steps.add(Step.of("трофеи есть", true, null));
        steps.add(Step.of("сыграть Науку", false, "science"));
        // Захваченные жетоны вернутся владельцам в конце раунда — сдать СЕЙЧАС.
        double urgency = me.trophySpacePoints() > 0 ? 5.0 : 0.0;
        return new Plan(Goal.TECH, steps,
            g.get("plan.value.tech", 6.0) + Math.min(pool, 6) + urgency);
    }

    /** Цепочка армии: военное здание → запитано → Сборка. */
    private static Plan army(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        BuildingToken mil = null;
        for (BuildingToken b : me.buildingsOnField()) {
            if (!isMilitary(b.type)) {
                continue;
            }
            if (mil == null || (!mil.powered() && b.powered())) {
                mil = b;
            }
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("военное здание есть", mil != null, "build", null,
            BuildingType.BARRACKS));
        steps.add(new Step("оно запитано", mil != null && mil.powered(), "energy_swap",
            mil != null ? mil.uid : null, null));
        steps.add(Step.of("сыграть Сборку", false, "assembly"));
        int units = me.unitsOnField().size();
        double v = g.get("plan.value.army", 7.0) - 0.7 * units;
        return new Plan(Goal.ARMY, steps, Math.max(1.5, v));
    }

    /** Цепочка экономики: нет денег → нужна стройка источников дохода. */
    private static Plan economy(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        if (me.resources.coin() >= 4) {
            return null;   // деньги есть, отдельная цель не нужна
        }
        List<Step> steps = new ArrayList<>();
        steps.add(Step.of("нужны деньги на стройку", false, "market"));
        return new Plan(Goal.ECONOMY, steps, g.get("plan.value.economy", 4.0));
    }

    // ==================================================================
    //  ЦЕЛЬ «ВЫПОЛНИТЬ ЗАДАНИЕ» — раньше её не было вообще
    // ==================================================================

    /**
     * Цепочка задания: взять КОНКРЕТНУЮ карту из руки и понять, чего ей не
     * хватает.
     *
     * <p>Это была самая большая дыра в мышлении ботов. Задания — один из главных
     * источников победных очков, но плана под них не существовало: перечисление
     * {@link Goal} содержало {@code OBJECTIVE}, а строителя цепочки — нет. Бот
     * выполнял задание только СЛУЧАЙНО, если требование само собой совпало с тем,
     * что он и так делал. Мысли «мне для этой карты нужен второй добытчик —
     * значит строю добытчик» у него не возникало никогда.
     *
     * <p>Как узнаём, чего не хватает. Требование карты — это ПРЕДИКАТ из реестра
     * ({@link Predicates}). Разобрать произвольный предикат на шаги нельзя, но
     * можно надёжно сказать, КАКИМ ДЕЙСТВИЕМ он обычно закрывается: «уничтожил
     * врага в этот ход» закрывается Боем, «запитанные добытчики у разных жил» —
     * Стройкой и Сменой энергии. Эта таблица и есть {@link #actionFor}.
     */
    private static Plan objective(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        if (me.objectiveHand.isEmpty()) {
            return null;
        }
        var content = Ctx.cards(s, "objectives");
        List<String> ready = Objectives.playableObjectives(s, seat, s.journal);

        // Карта, которую можно закрыть ПРЯМО СЕЙЧАС — цель на один шаг.
        if (!ready.isEmpty()) {
            String cid = ready.get(0);
            List<Step> steps = new ArrayList<>();
            steps.add(Step.of("требование задания «" + cardName(content, cid) + "» выполнено",
                true, null));
            // Закрывается СПЕЦ-действием, а не действием приказа: отдельного
            // «действия» под это нет, поэтому шаг без action — движок сам
            // предложит СПЕЦ после любого действия.
            steps.add(Step.of("сдать задание (СПЕЦ)", false, null));
            return new Plan(Goal.OBJECTIVE, steps,
                g.get("plan.value.objective", 8.0) + 4.0);
        }

        // Иначе — самая близкая карта: та, чьё требование закрывается действием,
        // которое сегодня и так полезно.
        String bestCid = null;
        String bestAction = null;
        String bestWhat = null;
        for (String cid : me.objectiveHand) {
            Map<String, Object> card;
            try {
                card = content.byId(cid);
            } catch (RuntimeException e) {
                continue;
            }
            if (!(card.get("requirement") instanceof Map<?, ?> req)) {
                continue;
            }
            Object pid = ((Map<String, Object>) req).get("predicate");
            if (pid == null) {
                continue;
            }
            String action = actionFor(pid.toString());
            if (action == null) {
                continue;
            }
            // Не хватает жертвы (расходников) — это отдельная, более дешёвая беда.
            String lack = lackingSacrifice(s, me, card);
            if (lack != null) {
                bestCid = cid;
                bestAction = "market";
                bestWhat = "не хватает " + lack + " на жертву задания «"
                    + cardName(content, cid) + "»";
                break;
            }
            if (bestCid == null) {
                bestCid = cid;
                bestAction = action;
                bestWhat = "требование задания «" + cardName(content, cid)
                    + "» (" + pid + ") ещё не выполнено";
            }
        }
        if (bestCid == null) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(bestWhat, false, bestAction, null, null));
        steps.add(Step.of("сдать задание (СПЕЦ)", false, null));
        return new Plan(Goal.OBJECTIVE, steps, g.get("plan.value.objective", 8.0));
    }

    private static String cardName(kelium.dataio.ContentSet content, String cid) {
        try {
            Object n = content.byId(cid).get("name");
            return n == null ? cid : String.valueOf(n);
        } catch (RuntimeException e) {
            return cid;
        }
    }

    /** Какого расходника не хватает на жертву карты (или null — хватает). */
    private static String lackingSacrifice(GameState s, PlayerState p, Map<String, Object> card) {
        if (!(card.get("sacrifice") instanceof Map<?, ?> sac)) {
            return null;
        }
        Object res = sac.get("resource");
        int amt = sac.get("amount") instanceof Number n ? n.intValue() : 0;
        if (res == null || amt <= 0) {
            return null;
        }
        String code = res.toString();
        int have = switch (code) {
            case "container" -> p.containers;
            case "objective_cards" -> Math.max(0, p.objectiveHand.size() - 1);
            case "coin" -> p.resources.coin();
            case "kelium" -> p.resources.kelium();
            case "ammo" -> p.resources.ammo();
            case "debris" -> p.resources.debris();
            default -> Integer.MAX_VALUE;   // позиционные жертвы здесь не считаем
        };
        return have >= amt ? null : code;
    }

    /**
     * КАКИМ ДЕЙСТВИЕМ обычно закрывается требование с этим предикатом. Таблица
     * человеческого знания об игре: она позволяет боту сказать «мне нужен Бой,
     * потому что задание просит уничтожить жетон», а не ждать совпадения.
     * {@code null} — предикат не про действие (например «есть контейнер»).
     */
    static String actionFor(String pid) {
        if (pid.contains("assembly") || pid.startsWith("produced_units")
                || pid.contains("tower_placed")) {
            return "assembly";
        }
        if (pid.contains("kelium") || pid.contains("miner")) {
            // Добытчики закрываются Стройкой, а сам келемий — Добычей. Если
            // добытчик уже стоит и запитан, просить Стройку бессмысленно.
            return pid.contains("powered_miners") ? "build" : "mining";
        }
        if (pid.contains("destroyed") || pid.contains("damaged")
                || pid.contains("enemy_building_hit") || pid.contains("neutral")) {
            return "combat";
        }
        if (pid.startsWith("moved_units") || pid.startsWith("unit")
                || pid.startsWith("units") || pid.contains("aircraft")
                || pid.contains("picked_container_by_unit")
                || pid.startsWith("hidden_unit")
                || pid.startsWith("sp_three_unit") || pid.startsWith("sp_four_hexes")) {
            return "movement";
        }
        if (pid.contains("powered") || pid.contains("energy")) {
            return "energy_swap";
        }
        if (pid.contains("market")) {
            return "market";
        }
        if (pid.contains("tech_step") || pid.contains("tracks_occupied")) {
            return "science";
        }
        if (pid.contains("build") || pid.contains("demolish") || pid.contains("razed")
                || pid.contains("plant") || pid.startsWith("sp_")
                || pid.contains("military_buildings")) {
            return "build";
        }
        return null;
    }

    // ==================================================================
    //  Помощники
    // ==================================================================

    /** Гексы, где лежит тайл зарождения с непустым келемием. */
    public static Set<String> liveTileHexes(GameState s) {
        Set<String> out = new LinkedHashSet<>();
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile != null && h.spawnTile.kelium > 0) {
                out.add(h.id);
            }
        }
        return out;
    }

    /** Примыкает ли гекс к живой жиле (или сам ею является). */
    public static boolean touchesLiveTile(GameState s, String hexId, Set<String> liveTiles) {
        if (hexId == null) {
            return false;
        }
        if (liveTiles.contains(hexId)) {
            return true;
        }
        for (String nb : s.field.neighbors(hexId)) {
            if (liveTiles.contains(nb)) {
                return true;
            }
        }
        return false;
    }

    static boolean isMilitary(BuildingType t) {
        return t == BuildingType.BARRACKS || t == BuildingType.FACTORY
            || t == BuildingType.AIRBASE || t == BuildingType.COMMAND_CENTER;
    }

    /** Тип войска, который производит здание (для описания цели). */
    static UnitType producedBy(BuildingType t) {
        return switch (t) {
            case BARRACKS -> UnitType.INFANTRY;
            case FACTORY -> UnitType.VEHICLE;
            case AIRBASE -> UnitType.AIRCRAFT;
            case COMMAND_CENTER -> UnitType.TOWER;
            default -> null;
        };
    }

    /** Ruleset-независимая проверка: сколько ещё келемия влезет в хранилище. */
    static int keliumRoom(GameState s, PlayerState p) {
        GameConfig cfg = Ctx.cfg(s);
        if (cfg == null) {
            return 1;
        }
        return Math.max(0, Storage.keliumMax(p) - p.resources.kelium());
    }
}
