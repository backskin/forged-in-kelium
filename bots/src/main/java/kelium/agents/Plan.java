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
        ECONOMY("поднять экономику"),
        /**
         * НАБЕГ: выбрать чужую цель, довести до неё войско и ударить.
         *
         * <p>Заведён 15.08.2026 по прямому разносу дизайнера, и разнос был
         * заслужен: до этой цели ни одна цепочка не соединяла «собрать войско»,
         * «подвинуть его к противнику» и «провести атаку» — план армии
         * ЗАКАНЧИВАЛСЯ на Сборке, а движение и бой оценивались каждый сам по
         * себе, без общей цели. Ровно так и выглядит «бот без тактики»: он умеет
         * всё по отдельности и ничего подряд. Это та же мысль, что в литературе
         * по ботам стратегий (GOAP, портфельный поиск, Churchill/Buro): при
         * огромном ветвлении искать надо не по атомарным ходам, а по ЦЕПОЧКАМ
         * с предусловиями.
         */
        STRIKE("провести набег"),
        /**
         * ВОЕННАЯ ПОБЕДА: чужой жетон уничтожения ЦУ уже на руках, значит
         * следующий снесённый ЦУ заканчивает партию победой немедленно.
         *
         * <p>Заведена 24.08.2026. Прежде эта ситуация ничем не отличалась от
         * обычного набега: ЦУ получало плоскую надбавку в выборе цели, и бот
         * мог спокойно пойти сносить добытчик, имея на руках победу в один удар.
         * Самое ценное действие в игре не было представлено целью вовсе.
         */
        WAR_WIN("добить ЦУ и выиграть");

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
    /**
     * Гекс цели набега (для {@link Goal#STRIKE}), иначе null. Хранится В ПЛАНЕ,
     * а не пересчитывается на каждом выборе: план, который каждый ход выбирает
     * новую жертву, — это не план, а генератор случайных блужданий.
     */
    public final String targetHex;
    public final List<Step> steps;
    /** Первый невыполненный шаг — «что мешает». null, если цель достижима сейчас. */
    public final Step nextStep;
    /** Насколько цель ценна (для выбора между планами). */
    public final double value;

    private Plan(Goal goal, List<Step> steps, double value) {
        this(goal, null, steps, value);
    }

    private Plan(Goal goal, String targetHex, List<Step> steps, double value) {
        this.goal = goal;
        this.targetHex = targetHex;
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
        List<Plan> all = candidates(s, seat, g);
        // Штраф за длину ЦЕПОЧКИ мягкий, иначе бот вечно берёт самую короткую
        // цель и никогда не начинает длинную — а длинные (добыча) как раз и есть
        // развитие. Величина штрафа тоже ген: темп игры подбирает отбор.
        Plan best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Plan p : all) {
            if (p == null) {
                continue;
            }
            double sc = p.score(g);
            if (sc > bestScore) {
                bestScore = sc;
                best = p;
            }
        }
        return best;
    }

    /**
     * ВСЕ кандидаты-планы этого хода. Отдельным методом, потому что агенту для
     * УДЕРЖАНИЯ плана между ходами нужен не только лучший, но и свежая версия
     * ТЕКУЩЕГО плана — сравнить их и решить, стоит ли смена курса своей цены.
     */
    public static List<Plan> candidates(GameState s, int seat, Genome g) {
        return candidates(s, seat, g, null);
    }

    /**
     * То же, но с УДЕРЖИВАЕМОЙ целью набега {@code heldHex}: в список попадает
     * ещё и план на прежнюю жертву, чтобы агенту было с чем сравнивать новый
     * курс. Без этого кандидата «прежняя цель» всегда выглядела мёртвой, и
     * набег разворачивался на полпути.
     */
    public static List<Plan> candidates(GameState s, int seat, Genome g, String heldHex) {
        List<Plan> all = new ArrayList<>();
        all.add(kelium(s, seat, g));
        all.add(sell(s, seat, g));
        all.add(tech(s, seat, g));
        all.add(army(s, seat, g));
        all.add(economy(s, seat, g));
        all.add(objective(s, seat, g));
        all.add(strike(s, seat, g));
        if (heldHex != null) {
            all.add(strike(s, seat, g, heldHex));
        }
        all.add(warWin(s, seat, g));
        return all;
    }

    /** Ценность плана с поправкой на длину цепочки — единая формула выбора. */
    public double score(Genome g) {
        double penalty = Math.max(0.05, g.get("plan.chain_penalty", 0.5));
        return value / (1.0 + penalty * missing());
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
        int pool = me.resources.trophy() + me.trophySpacePoints();
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

    /**
     * ЦЕПОЧКА НАБЕГА — то, чего не хватало всей системе планов: военное здание →
     * запитано → войско собрано → боеприпас есть → войско ДОВЕДЕНО до цели →
     * Бой. Шесть шагов, три-четыре хода, одна цель на всю дорогу.
     *
     * <p>Цель выбирается один раз при построении плана и живёт в {@link
     * #targetHex}: здание ценнее войска (его не уведут из-под удара), экономика
     * владельца ценнее казарм, накопленный на цели урон повышает приоритет
     * (добить дешевле, чем начать), опасный по {@link Rivalry} владелец — жирнее
     * цель. Дальняя цель дешевле: план должен успеть сложиться за партию.
     */
    private static Plan strike(GameState s, int seat, Genome g) {
        return strike(s, seat, g, null);
    }

    /**
     * То же, но с УДЕРЖАНИЕМ ЦЕЛИ: если {@code heldHex} задан и там всё ещё
     * стоит чужое здание, план строится ИМЕННО на эту цель.
     *
     * <p>Зачем понадобилось. Набег занимает три-четыре хода, а план
     * пересчитывается каждый ход заново. На втором ходу рядом оказывалась цель
     * подешевле и поближе — формула {@code цена / (1 + расстояние)} честно
     * переключала бота на неё, войско разворачивалось, дорога проделывалась
     * впустую. Удержание цели даёт агенту то, что он умеет: сравнить прежний
     * курс с новым и сменить его только если новый ЗАМЕТНО лучше
     * ({@code plan.commit}). Без кандидата на прежнюю цель этому сравнению
     * просто нечего было сравнивать.
     */
    private static Plan strike(GameState s, int seat, Genome g, String heldHex) {
        PlayerState me = s.player(seat);
        // Курсы очков берутся ИЗ СВОДА: иначе цели набега снова отстанут от
        // правил, как отстала прежняя формула.
        double trophyVp = ((Number) kelium.dataio.Ctx.rules(s)
            .get("economy.debris_storage_vp_per_unit", 0.5)).doubleValue();
        double cuTokenVp = kelium.dataio.Ctx.rules(s)
            .getInt("command_center.destruction_token_vp", 3);

        // --- цель: лучшая по цене/дистанции среди чужих зданий ---
        List<String> myUnitHexes = new ArrayList<>();
        for (kelium.core.UnitToken u : me.unitsOnField()) {
            if (u.hexId != null) {
                myUnitHexes.add(u.hexId);
            }
        }
        Rivalry riv = new Rivalry(s, seat);
        String target = null;
        int targetDist = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (PlayerState other : s.players) {
            if (other.seat == seat) {
                continue;
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (b.hexId == null) {
                    continue;
                }
                int dist = myUnitHexes.isEmpty() ? 4 : bfs(s, myUnitHexes, b.hexId);
                if (dist < 0) {
                    continue;                    // недостижимо вовсе
                }
                // ЦЕНА ЦЕЛИ — В ПОБЕДНЫХ ОЧКАХ ПО КУРСУ СВОДА (24.08.2026).
                //
                // Прежняя формула («добытчик и станция 1.5, прочее 0.5, ЦУ +3»)
                // ставила добытчик №1 за один трофей ВЫШЕ авиабазы за четыре, а
                // завод и авиабазу — в «прочее». То есть бот целился не туда,
                // где очки, а туда, куда решил автор формулы. Теперь считается
                // ровно то, что игрок получит: трофей жетона по курсу трофея,
                // а у ЦУ — напечатанные на жетоне уничтожения очки.
                double value = b.type == BuildingType.COMMAND_CENTER
                    ? cuTokenVp
                    : b.trophyValue() * trophyVp;
                // Жетон уходит с поля: владелец теряет ещё и свою четверть очка
                // за «здания на поле», а с добытчика или станции — производство.
                value += 0.25;
                // Добить начатое дешевле, чем начать: накопленный урон — это уже
                // вложенные боеприпасы.
                value += 0.5 * b.damage;
                double sc = value * (0.4 + riv.damageValue(other.seat)) / (1.0 + dist);
                // УДЕРЖАНИЕ: заданная цель побеждает любую другую, но только
                // пока она жива. Выбор «менять ли курс» остаётся за агентом.
                if (heldHex != null) {
                    if (heldHex.equals(b.hexId)) {
                        bestScore = sc;
                        target = b.hexId;
                        targetDist = dist;
                        break;
                    }
                    continue;
                }
                if (sc > bestScore) {
                    bestScore = sc;
                    target = b.hexId;
                    targetDist = dist;
                }
            }
        }
        if (target == null) {
            return null;                          // бить некого — цели нет
        }

        // --- цепочка с предусловиями ---
        BuildingToken mil = null;
        for (BuildingToken b : me.buildingsOnField()) {
            if (isMilitary(b.type) && (mil == null || (!mil.powered() && b.powered()))) {
                mil = b;
            }
        }
        boolean haveUnit = !myUnitHexes.isEmpty();
        // КУЛАК: сколько моих войск уже стоит вплотную к цели. Залп бьёт всеми
        // с ОДНОГО гекса, значит два подведённых жетона — это двойной залп, а
        // не два одиночных: единственный способ поднять скорость жатвы, не
        // трогая правила (замер: одиночная стопка даёт 1.3 попадания за бой).
        int nearTarget = 0;
        for (String h : myUnitHexes) {
            int d = bfs(s, java.util.List.of(h), target);
            if (d >= 0 && d <= 1) {
                nearTarget++;
            }
        }
        // Требуем двоих, только если войска на двоих есть: с единственным
        // жетоном план не должен зависать в ожидании несуществующего второго.
        int fistWant = Math.min(2, myUnitHexes.size());
        // НА ЗАЛП, А НЕ НА ВЫСТРЕЛ. Первый замер цепочки провалился ровно здесь:
        // с порогом в один боеприпас бот шёл в набег, стрелял одиночным, царапал
        // цель с двумя прочностями и возвращался за патроном — уничтожения УПАЛИ
        // с 7.25 до 2.91 за партию. Залп по настоящей цели стоит 2-3 боеприпаса.
        // БОЕПРИПАСОВ — ПО ПРОЧНОСТИ ЦЕЛИ, А НЕ КОНСТАНТОЙ ТРИ (24.08.2026).
        //
        // Порог 3 ставился, когда каждая атака стоила один боеприпас. По своду
        // 1.24.0 у жетона две атаки: универсальная за 2 и специальная за 1, то
        // есть один жетон наносит 2 урона за 3 боеприпаса — примерно полтора
        // боеприпаса за единицу прочности. Для казармы (прочность 1) хватает
        // трёх, а на ЦУ (прочность 3) с тремя боеприпасами бот шёл
        // недозаряженным: царапал цель и уходил. Ровно на этом провалился
        // первый замер цепочки, только тогда число было другим.
        int нужноУрона = Math.max(1, целевоеЗдоровье(s, target) );
        int нужноБпр = Math.max(3, (int) Math.ceil(1.5 * нужноУрона));
        boolean haveAmmo = me.resources.ammo() >= нужноБпр;
        boolean inPlace = targetDist <= 1;

        List<Step> steps = new ArrayList<>();
        steps.add(new Step("военное здание есть", haveUnit || mil != null, "build",
            null, BuildingType.FACTORY));
        steps.add(new Step("оно запитано", haveUnit || (mil != null && mil.powered()),
            "energy_swap", mil != null ? mil.uid : null, null));
        steps.add(Step.of("войско собрано", haveUnit, "assembly"));
        steps.add(Step.of("боеприпасы на залп (" + me.resources.ammo() + " из "
            + нужноБпр + ")", haveAmmo, "assembly"));
        steps.add(Step.of("войско у цели (осталось " + Math.max(0, targetDist - 1)
            + " шагов)", inPlace, "movement"));
        steps.add(Step.of("кулак собран (" + nearTarget + " из " + Math.max(1, fistWant)
            + " у цели)", nearTarget >= Math.max(1, fistWant), "movement"));
        steps.add(Step.of("сыграть Бой", false, "combat"));

        // Ценность растёт с готовностью: план, у которого осталось ударить,
        // должен перевешивать что угодно — иначе бот дойдёт и передумает.
        double v = g.get("plan.value.strike", 8.0) + Math.max(0.0, bestScore) * 2.0
            + (nearTarget >= Math.max(1, fistWant) && haveAmmo ? 4.0 : 0.0);
        return new Plan(Goal.STRIKE, target, steps, v);
    }

    /**
     * Кратчайшее расстояние от любого из гексов {@code from} до гекса
     * {@code to} по связям поля; −1 — недостижимо. Поле маленькое (20–30
     * гексов), волновой обход дешевле любых ухищрений.
     */
    private static int bfs(GameState s, List<String> from, String to) {
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        java.util.Map<String, Integer> dist = new java.util.HashMap<>();
        for (String f : from) {
            dist.put(f, 0);
            queue.add(f);
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = dist.get(cur);
            if (cur.equals(to)) {
                return d;
            }
            for (String nb : s.field.neighbors(cur)) {
                if (!dist.containsKey(nb)) {
                    dist.put(nb, d + 1);
                    queue.add(nb);
                }
            }
        }
        return -1;
    }

    /**
     * Сколько прочности осталось у самого крепкого чужого здания на гексе цели:
     * именно его придётся продавить, чтобы залп не ушёл впустую.
     */
    private static int целевоеЗдоровье(GameState s, String hex) {
        int max = 1;
        for (PlayerState p : s.players) {
            for (BuildingToken b : p.buildingsOnField()) {
                if (hex.equals(b.hexId)) {
                    max = Math.max(max, Math.max(1, b.hp - b.damage));
                }
            }
        }
        return max;
    }

    /**
     * ЦЕПОЧКА ВОЕННОЙ ПОБЕДЫ: чужой жетон уничтожения ЦУ на руках — значит
     * следующий снесённый ЦУ заканчивает партию.
     *
     * <p>Это не «набег покрупнее», а другая цель: цена её не в очках, а в самой
     * партии, поэтому она обязана перевешивать всё остальное, пока выполнима.
     * Цель фиксирована — ближайшее достижимое чужое ЦУ.
     */
    private static Plan warWin(GameState s, int seat, Genome g) {
        PlayerState me = s.player(seat);
        if (me.cuDestructionTokens < 1) {
            return null;                      // побеждать пока нечем
        }
        List<String> myUnitHexes = new ArrayList<>();
        for (kelium.core.UnitToken u : me.unitsOnField()) {
            if (u.hexId != null) {
                myUnitHexes.add(u.hexId);
            }
        }
        String target = null;
        int best = Integer.MAX_VALUE;
        for (PlayerState other : s.players) {
            if (other.seat == seat) {
                continue;
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (b.type != BuildingType.COMMAND_CENTER || b.hexId == null) {
                    continue;
                }
                int dist = myUnitHexes.isEmpty() ? 6 : bfs(s, myUnitHexes, b.hexId);
                if (dist >= 0 && dist < best) {
                    best = dist;
                    target = b.hexId;
                }
            }
        }
        if (target == null) {
            return null;
        }
        int нужноБпр = Math.max(4, (int) Math.ceil(1.5 * целевоеЗдоровье(s, target)));
        boolean haveUnit = !myUnitHexes.isEmpty();
        int nearTarget = 0;
        for (String h : myUnitHexes) {
            int d = bfs(s, java.util.List.of(h), target);
            if (d >= 0 && d <= 1) {
                nearTarget++;
            }
        }
        List<Step> steps = new ArrayList<>();
        steps.add(Step.of("войско есть", haveUnit, "assembly"));
        steps.add(Step.of("боеприпасы на снос ЦУ (" + me.resources.ammo()
            + " из " + нужноБпр + ")", me.resources.ammo() >= нужноБпр, "assembly"));
        steps.add(Step.of("войско у чужого ЦУ (осталось " + Math.max(0, best - 1)
            + " шагов)", best <= 1, "movement"));
        steps.add(Step.of("кулак у ЦУ (" + nearTarget + " из 2)",
            nearTarget >= Math.min(2, Math.max(1, myUnitHexes.size())), "movement"));
        steps.add(Step.of("сыграть Бой и выиграть партию", false, "combat"));
        // Цена — сама партия. Ген оставлен, чтобы отбор мог решить, насколько
        // рано бот бросает всё ради добивания.
        return new Plan(Goal.WAR_WIN, target, steps, g.get("plan.value.war_win", 30.0));
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
        double bestValue = -1;
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
            // КАРТА, ЖИВУЩАЯ В КОДЕ, СПРАШИВАЕТСЯ НАПРЯМУЮ. Раньше действие
            // угадывалось по подстроке в имени предиката (actionFor) — угадывание
            // читало requirement.predicate из данных. Карта в коде этого поля
            // больше не пишет (условие — код, а не строка), и угадывание молча
            // переставало работать для каждой переехавшей карты. Теперь спрашиваем
            // саму карту, а угадывание по подстроке остаётся запасным путём для
            // карт, ещё не переехавших в код.
            kelium.engine.cards.ObjectiveCard oc = kelium.engine.cards.CardRegistry.objective(cid);
            String action = oc != null
                ? oc.suggestedAction(new kelium.engine.cards.EngineCardContext(s, seat))
                : null;
            if (action == null) {
                Object pid = ((Map<String, Object>) req).get("predicate");
                action = pid == null ? null : actionFor(pid.toString());
            }
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
            // ВЫБИРАЕТСЯ ЛУЧШАЯ КАРТА, А НЕ ПЕРВАЯ ПОДВЕРНУВШАЯСЯ (21.08.2026).
            //
            // Прежде перебор останавливался на первой карте, у которой нашлось
            // подсказанное действие, — то есть цель хода определял ПОРЯДОК КАРТ В
            // РУКЕ. Ни цена карты, ни то, насколько она близка к выполнению, в
            // расчёт не входили, и бот с равным усердием шёл к карте на 4 очка,
            // до которой три хода, и к карте на 12 очков, которой не хватало
            // одного шага.
            //
            // Считается ожидаемая польза: цена карты, умноженная на близость.
            // Близость берётся у самой карты (progress: 0 — «ничего не начато»,
            // 1 — «требование выполнено»); дно 0.35 стоит потому, что карта, к
            // которой ещё не подступались, тоже чего-то стоит — иначе бот
            // навсегда цеплялся бы за начатое и не менял бы цель, даже когда рядом
            // лежит вдвое дороже.
            double value = objectiveValue(s, seat, cid, oc, card);
            if (value > bestValue) {
                bestValue = value;
                bestCid = cid;
                bestAction = action;
                bestWhat = "требование задания «" + cardName(content, cid)
                    + "» ещё не выполнено";
            }
        }
        if (bestCid == null) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(bestWhat, false, bestAction, null, null));
        steps.add(Step.of("сдать задание (СПЕЦ)", false, null));
        // ЦЕНА ПЛАНА ЗАВИСИТ ОТ КАРТЫ, А НЕ ТОЛЬКО ОТ ГЕНОМА (21.08.2026).
        //
        // Прежде здесь стояло ровно plan.value.objective — одно число на все
        // задания. Из-за этого выбор цели хода не различал карту на 12 очков,
        // которой не хватает одного шага, и карту на 4 очка, до которой три хода:
        // обе весили одинаково, и «пойти за заданием» либо всегда проигрывало
        // науке с добычей, либо всегда выигрывало — в зависимости от одного гена.
        // Замер это и показал: правка выбора КАРТЫ внутри плана не дала ничего,
        // потому что дальше плану всё равно выставлялась плоская цена.
        //
        // Делитель 6 — ориентир «обычная карта» (награда с усилением у
        // большинства карт 4–9 по прейскуранту ObjectiveHints). Коридор 0.4–2.0
        // держит цель в игре: слабая карта не отменяет погоню совсем, сильная не
        // затмевает всё остальное.
        double scale = Math.max(0.4, Math.min(2.0, bestValue / 6.0));
        ЦЕЛЬ_ЗАДАНИЕ.incrementAndGet();
        return new Plan(Goal.OBJECTIVE, steps,
            g.get("plan.value.objective", 8.0) * scale);
    }

    // ======================================================================
    //  СЧЁТЧИКИ ДЛЯ ЗАМЕРА (не влияют на игру)
    // ======================================================================
    //  Правку поведения ботов нельзя оценить по одному числу «выполнено заданий»:
    //  она может не сработать вовсе — например, цель просто не выбирается. Эти
    //  счётчики отвечают на вопрос «дошло ли дело до этой ветки», и без них
    //  предыдущая правка выглядела бы просто бесполезной, хотя она и не
    //  запускалась.

    /** Сколько раз строился план «выполнить задание». */
    public static final java.util.concurrent.atomic.AtomicLong ЦЕЛЬ_ЗАДАНИЕ =
        new java.util.concurrent.atomic.AtomicLong();

    /** Сколько раз план «выполнить задание» ВЫИГРАЛ выбор цели хода. */
    public static final java.util.concurrent.atomic.AtomicLong ВЫБРАНО_ЗАДАНИЕ =
        new java.util.concurrent.atomic.AtomicLong();

    /** Сколько раз выбор цели хода вообще состоялся. */
    public static final java.util.concurrent.atomic.AtomicLong ВЫБОРОВ =
        new java.util.concurrent.atomic.AtomicLong();

    /**
     * ОЖИДАЕМАЯ ПОЛЬЗА ОТ ПОГОНИ ЗА ЭТИМ ЗАДАНИЕМ: цена карты, взвешенная
     * близостью к выполнению.
     *
     * <p>Цена берётся ту же, какой её считают подсказки движка
     * ({@link kelium.engine.ObjectiveHints#rewardValue}) — второй прейскурант
     * рано или поздно разошёлся бы с первым. Учитывается награда С УСИЛЕНИЕМ:
     * бот, идущий к карте, вправе рассчитывать на её потолок.
     *
     * <p>Близость спрашивается у самой карты. Дно 0.35 не даёт «прилипнуть» к
     * начатой карте, когда рядом лежит вдвое дороже; потолок 1.0 — карта, чьё
     * требование уже выполнено, сюда не попадает (её разбирает ветка выше).
     */
    private static double objectiveValue(GameState s, int seat, String cid,
                                         kelium.engine.cards.ObjectiveCard oc,
                                         Map<String, Object> card) {
        double price = kelium.engine.ObjectiveHints.rewardValue(card.get("base_reward"))
            + kelium.engine.ObjectiveHints.rewardValue(card.get("special_reward"));
        if (price <= 0) {
            price = 4.0;    // карта без разобранной награды всё равно чего-то стоит
        }
        double closeness = 0.0;
        if (oc != null) {
            try {
                closeness = oc.progress(new kelium.engine.cards.EngineCardContext(s, seat));
            } catch (RuntimeException notNow) {
                closeness = 0.0;
            }
        }
        if (Double.isNaN(closeness) || closeness < 0) {
            closeness = 0.0;
        }
        return price * (0.35 + 0.65 * Math.min(1.0, closeness));
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
            case "debris" -> p.resources.trophy();
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
