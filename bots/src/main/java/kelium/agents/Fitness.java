package kelium.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * FITNESS — что именно считается «играть хорошо». Один расчёт на все обучатели.
 *
 * <p>ЧТО БЫЛО НЕ ТАК. Прежняя целевая функция складывала в одну сумму победу,
 * очки, отрыв, свои жетоны, чужие жетоны, удары, убийства, произведённых юнитов и
 * шаги науки. Победа стоила в ней столько же, сколько три убийства или десяток
 * ударов, — и отбор закономерно выводил ботов, которые ИМИТИРУЮТ ДЕЯТЕЛЬНОСТЬ
 * вместо того, чтобы выигрывать. Поддержка (шейпинг) была нужна на старте, когда
 * бот не выигрывал вовсе и сигнала не было, но её никто не отключал: она
 * оставалась в цели навсегда.
 *
 * <p>КАК СТАЛО. Победа — главный член. Отрыв от лидера — второй. Абсолютные очки
 * дают маленькую добавку (чтобы отличать второе место от последнего). Поддержка
 * ЗАТУХАЕТ: в начале обучения она помогает нащупать игру, к середине умножается
 * на ноль, и дальше отбор идёт только по результату.
 *
 * <p>Здесь же считается ПОВЕДЕНЧЕСКИЙ ПОРТРЕТ партии — «как именно бот играл»:
 * сколько воевал, сколько копал, сколько шёл в науку. Он не влияет на
 * приспособленность, но по нему {@link MapElites} раскладывает ботов по стилям и
 * получается атлас работающих стратегий, а не один чемпион.
 */
public final class Fitness {

    private Fitness() {
    }

    /** Итог одной оценочной партии. */
    public record Result(double fitness, int vp, int rivalMax, boolean win,
                         double[] behavior, Map<String, Integer> counters) {
    }

    /** Оси поведенческого портрета (для атласа стратегий). */
    public static final List<String> BEHAVIOR_AXES =
        List.of("война", "экономика", "наука");

    /**
     * НА КАКИХ ПОЛЯХ УЧИМ. Набор задаёт {@link LayoutLibrary#pool}: решение
     * дизайнера 13.08.2026 — учить и мерить только на его нарисованных
     * раскладках. Раньше выбор полей был выписан здесь же, отдельно от стендов, и
     * стенды играли на встроенном поле, а обучение — на всей библиотеке.
     */
    private static GameConfig layoutConfig(int players, long seed) {
        return LayoutLibrary.configFor(players, seed);
    }

    /**
     * МОЗГ, КОТОРЫМ ИГРАЕТ ОБУЧЕНИЕ. Ставить сюда «просчёт» дороже, но иначе
     * обучение проверяет не того бота, которого мы потом сажаем за стол.
     *
     * <p>Зачем понадобилось. Из шестидесяти обучаемых весов ДВАДЦАТЬ ДЕВЯТЬ — это
     * оценочная функция позиции ({@code eval.*}), а её спрашивает ТОЛЬКО просчёт
     * вперёд. Обучение же играло ботами без просчёта, то есть эти двадцать девять
     * весов не влияли на исход ни одной обучающей партии: отбор их мутировал, но
     * ничего в них не отбирал — чистое случайное блуждание. А сильнейший наш бот
     * судит позицию как раз ими. Получалось, что у лучшего бота судья настроен
     * наугад.
     */
    /**
     * ЦЕЛЬ ОБУЧЕНИЯ ЛИНИИ — за что именно линию отбирают.
     *
     * <p>До 13.08.2026 цель была ОДНА на всех: победа плюс отрыв. Характеры при
     * этом отличались только перекосом весов на старте, и отбор всё равно тянул их
     * к одной и той же игре — экономика и наука. Поэтому «агрессивной линии» в
     * системе не существовало: ястреб был ястребом только по имени.
     */
    public enum Goal {
        /** Победа и отрыв по очкам — обычная цель. */
        ПОБЕДА,
        /**
         * ВОЙНА (заказ дизайнера 13.08.2026): максимальный отрыв по очкам,
         * добытый агрессией. Отрыв остаётся главным членом — иначе получится бот,
         * который шумит и проигрывает, — но к нему добавляется постоянная (не
         * затухающая) плата за уничтожения, выигранные бои, число нанятых войск и
         * за РАЗНООБРАЗИЕ родов на поле: «насытить поле разными войсками».
         */
        ВОЙНА,
        /**
         * АКСИОМА (заказ дизайнера 2026-08-15): очки и победа НЕ УЧАСТВУЮТ В
         * ФОРМУЛЕ ВООБЩЕ — бота учат трём заповедям НАПРЯМУЮ, как данности, а не
         * выводят их из счёта: (1) забраться как можно выше на ВСЕХ ТРЁХ треках
         * науки разом (не одном длинном, а именно на всех), (2) уничтожать чужие
         * жетоны, и ценность уничтожения меряется их ТРОФЕЙНОЙ ЦЕНОЙ (в обломках),
         * а не штукой за штуку, (3) насытить поле МАКСИМАЛЬНЫМ РАЗНООБРАЗИЕМ
         * жетонов — и войск, и зданий (не только войск, как в ВОЙНА). Цель этой
         * линии — сравнить: приводит ли слепое следование этим трём заповедям к
         * такому же счёту, что и прямое обучение на победу, или это разные игры.
         */
        АКСИОМА,
        /**
         * ЖНЕЦ, СТУПЕНЬ 1 (заказ дизайнера 2026-08-15): уничтожить за партию КАК
         * МОЖНО БОЛЬШЕ ЧУЖИХ ЖЕТОНОВ — и уцелеть, чтобы продолжать уничтожать.
         *
         * <p>Отличие от {@link #ВОЙНА}: там уничтожения были прибавкой к отрыву по
         * очкам, и отрыв всё равно весил больше. Здесь очки и победа НЕ УЧАСТВУЮТ
         * ВОВСЕ — только снесённые жетоны, попадания и собственная живучесть. Это
         * не «агрессивный победитель», а измерительный прибор: сколько жетонов в
         * этой игре ВООБЩЕ можно снести за партию, если ничего другого не хотеть.
         *
         * <p>Живучесть входит отдельным членом, потому что она средство, а не
         * украшение: мёртвое войско не убивает, а линия, разменивающая свои жетоны
         * один к одному, упирается в потолок личного запаса (по 4 на род) быстрее
         * той, что бьёт и остаётся жива.
         */
        ЖНЕЦ,
        /**
         * ЖНЕЦ, СТУПЕНЬ 2: всё то же самое ПЛЮС максимум шагов по всем трём трекам
         * науки. Ступень накопительная — жатва не отменяется, к ней добавляется
         * наука, причём «на всех треках разом», как в {@link #АКСИОМА}: сырая сумма
         * шагов поощряет закопаться в один длинный трек.
         */
        ЖНЕЦ_НАУКА,
        /**
         * ЖНЕЦ, СТУПЕНЬ 3: жатва плюс наука плюс КАК МОЖНО БОЛЬШЕ ВЫПОЛНЕННЫХ
         * УСИЛЕННЫХ заданий. Усиленное требование ценится отдельно и дороже
         * обычного выполнения: замеры показывали, что задания в 4.8 раза чаще жгут,
         * чем выполняют, а усиленную ветку почти не берут вовсе.
         */
        ЖНЕЦ_ЗАДАНИЯ,

        /**
         * СПЕЦИАЛИСТ (заказ дизайнера 18.08.2026): победа как обычно, плюс
         * бонус за САМУЮ БОЛЬШУЮ ОДНОРОДНУЮ ГРУППУ войск на поле к концу партии
         * — какой род ни выбери отбор, лишь бы он был один и в количестве.
         * Ровно противоположность {@link #ВОЙНА}, где ценится разнообразие.
         */
        СПЕЦИАЛИСТ,
        /**
         * АРСЕНАЛ (заказ дизайнера 18.08.2026): победа плюс бонус за установленные
         * карты арсенала к концу партии — линия должна научиться жить арсеналом,
         * а не просто иногда его подбирать.
         */
        АРСЕНАЛ,
        /**
         * ЗАДАЧНИК (заказ дизайнера 18.08.2026): победа плюс бонус за КАЖДОЕ
         * выполненное задание за партию (и обычное, и усиленное — в отличие от
         * {@link #ЖНЕЦ_ЗАДАНИЯ}, где считается только усиленное и очки не в счёте).
         */
        ЗАДАЧНИК,
        /**
         * ГРОМИЛА (заказ дизайнера 18.08.2026): победа плюс большой и тупой бонус
         * за объём боевых действий — без тяги {@link #ВОЙНА} к разнообразию
         * родов войск, просто «бей всех и как можно чаще».
         */
        ГРОМИЛА,
        /**
         * УЧЁНЫЙ (заказ дизайнера 18.08.2026): победа плюс бонус за шаги науки —
         * в отличие от {@link #АКСИОМА}, можно закапываться в один трек, это
         * разрешено и даже поощряется (наука ради победы, не ради заповеди).
         */
        УЧЁНЫЙ,
        /**
         * СУПЕРОРУЖИЕ (заказ дизайнера 18.08.2026): победа плюс бонус за прогресс
         * супер-задания и отдельная крупная награда, если оно запущено.
         */
        СУПЕРОРУЖИЕ,
        /**
         * ОХОТНИК (заказ дизайнера 18.08.2026): победа плюс бонус за урон по
         * чужим ЦУ и отдельно — за каждый добытый жетон разрушения ЦУ (2 таких
         * жетона — мгновенная военная победа, поэтому это не украшение, а прямой
         * путь к выигрышу).
         */
        ОХОТНИК;

        /** Любая из трёх ступеней линии «жнец». */
        public boolean жнец() {
            return this == ЖНЕЦ || this == ЖНЕЦ_НАУКА || this == ЖНЕЦ_ЗАДАНИЯ;
        }
    }

    public enum Brain {
        /** Жадная формула — быстро; {@code eval.*} веса не участвуют. */
        ФОРМУЛА,
        /**
         * Просчёт с ДОИГРЫВАНИЕМ ПРИКАЗОВ — только он и заставляет работать веса
         * оценки позиции.
         *
         * <p>Тонкость, найденная тестом: отсева холостых ходов НЕДОСТАТОЧНО. В
         * дешёвом просчёте оценка позиции не спрашивается вовсе, и веса {@code
         * eval.*} остаются вне отбора ровно как раньше. Спрашивает их доигрывание
         * (см. {@link Lookahead#horizonScore}), поэтому обучающий бот доигрывает
         * приказы — скромно (два приказа по два прогона, бюджет на партию), иначе
         * обучение станет дороже в десятки раз.
         *
         * <p>ЗАМЕР 13.08.2026, ПОЧЕМУ ЭТО НЕ ВКЛЮЧЕНО ПО УМОЛЧАНИЮ. Обучение с
         * просчётом идёт примерно в ДВАДЦАТЬ раз дольше (жадное: 348 тысяч партий
         * за 12 минут; с просчётом за час не прошло и половины сопоставимого
         * бюджета), а выигрыша в силе не дало: геном линии «balanced», обученный с
         * просчётом, играет с обученным жадно вровень (49% против 51% на 192 очных
         * сравнениях). Отдельная проверка объясняет, почему: если у обученного
         * генома ЗАМЕНИТЬ все двадцать девять весов оценки на исходные, сила не
         * меняется (48–52% во всех парах). Значит блуждание этих весов силе не
         * вредило, и отбирать их незачем — решает не оценка позиции, а само
         * доигрывание приказов.
         *
         * <p>Ручку оставляем: она понадобится, если оценка позиции начнёт весить
         * больше (скажем, при более длинном горизонте). Но включать её «на всякий
         * случай» — платить двадцатикратную цену ни за что.
         */
        ПРОСЧЁТ
    }

    private static Agent brainAgent(Brain brain, int seat, Random rng, Genome g) {
        return brain == Brain.ПРОСЧЁТ
            ? new SearchAgent(seat, rng, g, "обучение", 4, 0, 2, 2, 2, 60)
            : new StrategicAgent(seat, rng, g);
    }

    /** Сыграть оценочную партию жадной формулой (прежнее поведение). */
    public static Result play(int players, long seed, int seat, Genome cand,
                              List<Genome> rivals, double shaping) {
        return play(players, seed, seat, cand, rivals, shaping, Brain.ФОРМУЛА);
    }

    /**
     * Сыграть одну оценочную партию.
     *
     * @param cand     проверяемый геном; садится на место {@code seat}
     * @param rivals   геномы соперников (берутся по кругу)
     * @param shaping  вес затухающей поддержки: 1.0 в начале обучения, 0.0 потом
     * @param brain    чем играют боты в этой партии (см. {@link Brain})
     */
    public static Result play(int players, long seed, int seat, Genome cand,
                              List<Genome> rivals, double shaping, Brain brain) {
        return play(players, seed, seat, cand, rivals, shaping, brain, Goal.ПОБЕДА);
    }

    /** То же, но с ЦЕЛЬЮ обучения линии (см. {@link Goal}). */
    public static Result play(int players, long seed, int seat, Genome cand,
                              List<Genome> rivals, double shaping, Brain brain, Goal goal) {
        return play(players, seed, seat, cand, rivals, shaping, brain, goal, 0.0);
    }

    /**
     * То же, но с ВЕРОЯТНОСТЬЮ ПОЛУЧИТЬ КАРТУ БЕСПЛАТНО в Обновление
     * ({@code cardFloodRate}, 0..1) — приём curriculum-обучения
     * {@link SelfPlayTrainer}: боты должны часто видеть карты арсенала и
     * заданий, прежде чем веса их оценки начнут значить что-то. 0 = как везде
     * (правила не меняются). Копия правил не протекает в другие партии
     * (тот же приём, что в {@code kelium.RuleExperiment}).
     */
    public static Result play(int players, long seed, int seat, Genome cand,
                              List<Genome> rivals, double shaping, Brain brain, Goal goal,
                              double cardFloodRate) {
        GameConfig cfg = layoutConfig(players, seed);
        if (cardFloodRate > 0) {
            kelium.rules.Ruleset rules = cfg.ruleset.copy();
            rules.override("training.card_flood_rate", cardFloodRate);
            cfg = new GameConfig(rules, cfg.content, players, seed, cfg.dataRoot,
                cfg.boardSides, cfg.scenarioId, cfg.cuFacing, cfg.scenarioFile);
        }
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        int r = 0;
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i + 1);
            if (i == seat) {
                agents.add(brainAgent(brain, i, rng, cand));
            } else {
                Genome g = rivals.isEmpty() ? Genome.defaults()
                    : rivals.get(r++ % rivals.size());
                agents.add(brainAgent(brain, i, rng, g));
            }
        }

        Map<String, Integer> c = new HashMap<>();
        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            // СВОИ ПОТЕРИ считаются ДО отбора событий по месту: в combat_hit место
            // — это АТАКУЮЩИЙ, а свои потери приходят как раз чужими событиями
            // (жертва указана отдельно, victim_owner). Нужно цели ЖНЕЦ, где
            // живучесть входит в отбор наравне с уничтожениями.
            if ("combat_hit".equals(String.valueOf(ev.get("type")))
                    && ev.get("victim_owner") instanceof Number vo
                    && vo.intValue() == seat) {
                c.merge("damage_taken", 1, Integer::sum);
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    c.merge("losses", 1, Integer::sum);
                }
            }
            if (!(ev.get("seat") instanceof Number n) || n.intValue() != seat) {
                return;
            }
            String type = String.valueOf(ev.get("type"));
            switch (type) {
                case "combat_hit" -> {
                    c.merge("hits", 1, Integer::sum);
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        c.merge("kills", 1, Integer::sum);
                        // ТРОФЕЙНАЯ ЦЕНА уничтоженного (в обломках) — нужна цели
                        // АКСИОМА: там уничтожение весит не «штука за штуку», а по
                        // напечатанной ценности жетона (см. CombatResolver: поле
                        // "trophy" в combat_hit).
                        if (ev.get("trophy") instanceof Number tv) {
                            c.merge("kills_trophy_value", tv.intValue(), Integer::sum);
                        }
                    }
                    // ПОПАДАНИЯ ПО ЧУЖОМУ ЦУ — нужны цели ОХОТНИК (18.08.2026):
                    // victimLabel(building) отдаёт code уровня "command_centerL2",
                    // поэтому проверяем по префиксу, а не равенству.
                    if (String.valueOf(ev.get("victim")).startsWith("command_center")) {
                        c.merge("cu_hits", 1, Integer::sum);
                    }
                }
                case "objective" -> {
                    c.merge("objectives", 1, Integer::sum);
                    // УСИЛЕННОЕ выполнение считается отдельно: цель ЖНЕЦ_ЗАДАНИЯ
                    // платит именно за него, а не за выполнение вообще.
                    if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                        c.merge("objectives_enhanced", 1, Integer::sum);
                    }
                }
                case "container" -> c.merge("containers", 1, Integer::sum);
                case "action" -> {
                    String a = String.valueOf(ev.get("action"));
                    c.merge("act_all", 1, Integer::sum);
                    if (Boolean.TRUE.equals(ev.get("ok"))) {
                        c.merge("act_ok", 1, Integer::sum);
                        c.merge("act_" + a, 1, Integer::sum);
                    } else {
                        c.merge("act_idle", 1, Integer::sum);
                    }
                    if (ev.get("telemetry") instanceof Map<?, ?> tel) {
                        if (tel.get("kelium") instanceof Number k) {
                            c.merge("kelium_mined", k.intValue(), Integer::sum);
                        }
                        if (tel.get("units") instanceof Number u) {
                            c.merge("units_made", u.intValue(), Integer::sum);
                        }
                    }
                }
                default -> { }
            }
        });

        int vp = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int rivalMax = Integer.MIN_VALUE;
        // ОТРЫВ ОТ КАЖДОГО, а не только от лучшего (заказ дизайнера 15.08.2026:
        // «победить каждого и заставить всех проиграть как можно сильнее»).
        // Разница не косметическая: по одному лишь отрыву от лидера боту всё
        // равно, идут остальные вровень с ним или далеко позади, — а за столом
        // на четверых это две совершенно разные партии.
        double marginSum = 0.0;
        int rivalCount = 0;
        for (int i = 0; i < players; i++) {
            if (i == seat) {
                continue;
            }
            int theirs = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            rivalMax = Math.max(rivalMax, theirs);
            marginSum += vp - theirs;
            rivalCount++;
        }
        if (rivalMax == Integer.MIN_VALUE) {
            rivalMax = 0;
        }
        double avgMargin = rivalCount == 0 ? vp : marginSum / rivalCount;
        boolean win = res.get("winner") instanceof Number w && w.intValue() == seat;

        int techSteps = 0;
        for (int v : s.player(seat).techSteps.values()) {
            techSteps += v;
        }
        // ПОДДЕРЖКА: только то, что показывает «бот вообще пользуется машинерией
        // игры». Ровно эти слагаемые и надо выключать по мере обучения, иначе
        // они начинают конкурировать с победой.
        double support =
              0.30 * c.getOrDefault("kills", 0)
            + 0.10 * c.getOrDefault("hits", 0)
            + 0.30 * c.getOrDefault("objectives", 0)
            + 0.20 * techSteps
            + 0.10 * c.getOrDefault("units_made", 0)
            + 0.20 * c.getOrDefault("kelium_mined", 0);

        double fitness;
        if (goal == Goal.АКСИОМА) {
            // ПО, ПОБЕДА И ОТРЫВ ЗДЕСЬ НЕ УЧАСТВУЮТ ВООБЩЕ (заказ дизайнера
            // 2026-08-15) — три заповеди задаются как данность, а не выводятся
            // из счёта партии.
            //
            // (1) ЗАБРАТЬСЯ НА ВСЕ ТРИ ТРЕКА НАУКИ РАЗОМ, не только на один
            // длинный: сырая сумма шагов поощряет «закопаться в один трек», а
            // произведение (или минимум) шагов по трекам — именно РАЗОМ на все
            // три. Берём минимум шага среди трёх треков с большим весом (это и
            // есть «на всех разом»), плюс сумму — чтобы не обнулять частичный
            // прогресс, пока минимум ещё ноль.
            int minTrackStep = Integer.MAX_VALUE;
            for (String track : s.tech.tracks) {
                int step = s.player(seat).techSteps.getOrDefault(track, 0);
                minTrackStep = Math.min(minTrackStep, step);
            }
            if (minTrackStep == Integer.MAX_VALUE) {
                minTrackStep = 0;
            }
            double techAxiom = 1.5 * techSteps + 4.0 * minTrackStep;

            // (2) УНИЧТОЖЕНИЕ ПО ТРОФЕЙНОЙ ЦЕНЕ (в обломках), не «штука за
            // штуку» — техника и авиация стоят дороже пехоты, и должны весить
            // больше. Плюс отдельно поощряем СЫРОЕ число попаданий и убийств за
            // партию (заказ дизайнера 2026-08-15) — бот, который дерётся много
            // и часто, ценится САМ ПО СЕБЕ, а не только за итоговую трофейную
            // сумму.
            double killAxiom = 3.0 * c.getOrDefault("kills_trophy_value", 0)
                + 1.0 * c.getOrDefault("kills", 0)
                + 0.4 * c.getOrDefault("hits", 0);

            // (3) МАКСИМАЛЬНОЕ РАЗНООБРАЗИЕ жетонов на поле — И войск, И
            // зданий (в отличие от ВОЙНА, где считались только рода войск).
            java.util.Set<kelium.core.UnitType> unitKinds =
                java.util.EnumSet.noneOf(kelium.core.UnitType.class);
            int unitsOnField = 0;
            for (kelium.core.UnitToken u : s.player(seat).unitsOnField()) {
                unitKinds.add(u.type);
                unitsOnField++;
            }
            java.util.Set<kelium.core.BuildingType> buildingKinds =
                java.util.EnumSet.noneOf(kelium.core.BuildingType.class);
            int buildingsOnField = 0;
            for (var b : s.player(seat).buildingsOnField()) {
                buildingKinds.add(b.type);
                buildingsOnField++;
            }
            double diversityAxiom =
                  3.0 * (unitKinds.size() + buildingKinds.size())
                + 0.3 * (unitsOnField + buildingsOnField);

            fitness = techAxiom + killAxiom + diversityAxiom;
        } else if (goal.жнец()) {
            // ==========================================================
            //  ЖНЕЦ. Очки и победа НЕ УЧАСТВУЮТ. Считается жатва.
            // ==========================================================
            // ЖАТВА. Уничтожение — главный и почти единственный член. Попадания
            // оплачиваются отдельно и дёшево: они путь к уничтожению (у техники и
            // ЦУ по 2–3 прочности, за один удар не снести), но платить за них
            // много нельзя — получится бот, который бесконечно царапает толстую
            // цель вместо того, чтобы добить тонкую.
            double harvest = 10.0 * c.getOrDefault("kills", 0)
                + 1.0 * c.getOrDefault("hits", 0)
                + 1.0 * c.getOrDefault("act_combat", 0);

            // ЖИВУЧЕСТЬ — средство, а не украшение. Свой потерянный жетон стоит
            // дешевле чужого снесённого (иначе линия станет трусливой и перестанет
            // лезть в размен, а размен ей выгоден), но и не бесплатен: личный запас
            // по 4 жетона на род, и разменявший всё войско больше не жнёт.
            double survival = -4.0 * c.getOrDefault("losses", 0)
                - 0.3 * c.getOrDefault("damage_taken", 0);

            // ЧЕМ ЖНУТ. Уничтожать нечем без войск, боеприпасов и досягаемости,
            // поэтому армия на поле оплачивается — но скромно, чтобы бот не начал
            // копить войско вместо того, чтобы им бить.
            int onField = 0;
            for (kelium.core.UnitToken u : s.player(seat).unitsOnField()) {
                onField++;
            }
            double army = 0.6 * onField + 0.4 * c.getOrDefault("units_made", 0);

            fitness = harvest + survival + army;

            if (goal == Goal.ЖНЕЦ_НАУКА || goal == Goal.ЖНЕЦ_ЗАДАНИЯ) {
                // СТУПЕНЬ 2: наука НА ВСЕХ ТРЁХ ТРЕКАХ РАЗОМ. Минимум по трекам с
                // большим весом — это и означает «разом»; сумма шагов идёт мелким
                // членом, чтобы частичный прогресс не обнулялся, пока минимум ноль.
                int minTrackStep = Integer.MAX_VALUE;
                for (String track : s.tech.tracks) {
                    minTrackStep = Math.min(minTrackStep,
                        s.player(seat).techSteps.getOrDefault(track, 0));
                }
                if (minTrackStep == Integer.MAX_VALUE) {
                    minTrackStep = 0;
                }
                fitness += 2.0 * techSteps + 6.0 * minTrackStep;
            }
            if (goal == Goal.ЖНЕЦ_ЗАДАНИЯ) {
                // СТУПЕНЬ 3: усиленные задания. Платим ТОЛЬКО за усиленное
                // выполнение и совсем немного за обычное — обычное бот и так берёт,
                // а усиленную ветку по замерам почти не берёт никто.
                fitness += 12.0 * c.getOrDefault("objectives_enhanced", 0)
                    + 2.0 * c.getOrDefault("objectives", 0);
            }
        } else {
            // ЦЕЛЬ — ПОБЕДА НАД СОПЕРНИКАМИ, А НЕ ЛИЧНЫЙ СЧЁТ (15.08.2026).
            // Раньше здесь третьим членом стояли СВОИ очки (0.5 * vp), и это была
            // ошибка по существу: партия с 20 очками при лидере с 25 — проигрыш,
            // а с 12 при лидере с 10 — победа, поэтому абсолютному счёту в цели
            // делать нечего. Он стоял там ради одного: отличить второе место от
            // последнего. Ту же работу честнее делает СРЕДНИЙ ОТРЫВ ОТ ВСЕХ —
            // он и различает места, и вознаграждает разгром всего стола, а не
            // одного лидера.
            fitness =
                  (win ? 60.0 : 0.0)          // ГЛАВНОЕ: игра играется на победу
                + 2.0 * (vp - rivalMax)       // обогнать сильнейшего
                + 1.0 * avgMargin             // и оторваться от КАЖДОГО
                + shaping * support;
        }

        if (goal == Goal.ВОЙНА) {
            // РАЗНООБРАЗИЕ РОДОВ НА ПОЛЕ: считаем, сколько РАЗНЫХ родов войск
            // игрок держит на поле к концу партии. Дизайнер просил «предельное
            // насыщение поля разными войсками», а не гору пехоты.
            java.util.Set<kelium.core.UnitType> kinds =
                java.util.EnumSet.noneOf(kelium.core.UnitType.class);
            int onField = 0;
            for (kelium.core.UnitToken u : s.player(seat).unitsOnField()) {
                kinds.add(u.type);
                onField++;
            }
            // Плата за агрессию НЕ ЗАТУХАЕТ: это не поддержка на разгон, а сама
            // цель линии. Веса подобраны так, чтобы отрыв по очкам всё равно
            // весил больше: 10 убийств ≈ 5 очков отрыва.
            fitness +=
                  1.0 * c.getOrDefault("kills", 0)
                + 0.3 * c.getOrDefault("hits", 0)
                + 0.4 * c.getOrDefault("units_made", 0)
                + 0.5 * onField
                + 2.0 * kinds.size()
                + 0.5 * c.getOrDefault("act_combat", 0);
        }

        if (goal == Goal.СПЕЦИАЛИСТ) {
            // САМАЯ БОЛЬШАЯ ОДНОРОДНАЯ ГРУППА на поле — противоположность
            // разнообразию ВОЙНА/АКСИОМА. Отбор сам решает, в какой род упереться.
            Map<kelium.core.UnitType, Integer> byType = new HashMap<>();
            for (kelium.core.UnitToken u : s.player(seat).unitsOnField()) {
                byType.merge(u.type, 1, Integer::sum);
            }
            int biggest = byType.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            fitness += 3.0 * biggest + 0.3 * c.getOrDefault("units_made", 0);
        }

        if (goal == Goal.АРСЕНАЛ) {
            // КАРТЫ АРСЕНАЛА, ДЕЙСТВИТЕЛЬНО УСТАНОВЛЕННЫЕ к концу партии —
            // «жить арсеналом», а не просто иногда его подбирать.
            fitness += 4.0 * s.player(seat).arsenalInstalled.size();
        }

        if (goal == Goal.ЗАДАЧНИК) {
            // КАЖДОЕ выполненное задание — и обычное, и усиленное (в отличие от
            // ЖНЕЦ_ЗАДАНИЯ, где считается только усиленное).
            fitness += 3.0 * c.getOrDefault("objectives", 0)
                + 2.0 * c.getOrDefault("objectives_enhanced", 0);
        }

        if (goal == Goal.ГРОМИЛА) {
            // ТУПОЙ ОБЪЁМ боевых действий — без бонуса ВОЙНА за разнообразие
            // родов, просто «бей всех и как можно чаще».
            fitness += 0.8 * c.getOrDefault("kills", 0)
                + 0.3 * c.getOrDefault("hits", 0)
                + 0.4 * c.getOrDefault("act_combat", 0)
                + 0.3 * c.getOrDefault("act_movement", 0)
                + 0.2 * c.getOrDefault("units_made", 0);
        }

        if (goal == Goal.УЧЁНЫЙ) {
            // Наука ради победы: закапываться в один трек РАЗРЕШЕНО (в отличие
            // от АКСИОМЫ, где считается только высота, взятая РАЗОМ на всех трёх).
            fitness += 2.5 * techSteps;
        }

        if (goal == Goal.СУПЕРОРУЖИЕ) {
            fitness += 3.0 * s.player(seat).superObjectiveProgress
                + (s.player(seat).superObjectiveComplete ? 30.0 : 0.0);
        }

        if (goal == Goal.ОХОТНИК) {
            // Урон по чужому ЦУ — путь; жетон разрушения — уже почти победа
            // (2 таких жетона = мгновенная военная победа).
            fitness += 1.0 * c.getOrDefault("cu_hits", 0)
                + 8.0 * s.player(seat).cuDestructionTokens;
        }

        int actOk = c.getOrDefault("act_ok", 0);
        int war = c.getOrDefault("act_combat", 0) + c.getOrDefault("act_movement", 0);
        int econ = c.getOrDefault("act_mining", 0) + c.getOrDefault("act_market", 0)
            + c.getOrDefault("act_build", 0);
        double[] behavior = {
            actOk == 0 ? 0 : (double) war / actOk,
            actOk == 0 ? 0 : (double) econ / actOk,
            Math.min(1.0, techSteps / 9.0)
        };
        c.put("tech_steps", techSteps);
        return new Result(fitness, vp, rivalMax, win, behavior, c);
    }

    /**
     * Насколько сильна поддержка на данном шаге обучения: 1.0 в самом начале,
     * плавно до 0 к {@code fadeBy} доле пути. Дальше отбор идёт ЧИСТО по
     * результату — это и есть отжиг поддержки, которого не было.
     */
    public static double shapingAt(double progress, double fadeBy) {
        if (progress >= fadeBy) {
            return 0.0;
        }
        return 1.0 - progress / Math.max(1e-9, fadeBy);
    }
}
