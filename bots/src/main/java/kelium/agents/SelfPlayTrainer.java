package kelium.agents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import kelium.dataio.Locations;

/**
 * SelfPlayTrainer — обучение ВСЕХ характеров друг против друга. ПЕРЕПИСАН.
 *
 * <p>Что было сломано. Соперником служил ТЕКУЩИЙ чемпион линии, который менялся
 * на ходу. У такой соэволюции есть известная беда: популяция может годами ходить
 * по кругу «камень бьёт ножницы, ножницы бьют бумагу», и никакого роста силы при
 * этом нет — а заметить это было нельзя, потому что приспособленность считалась
 * против меняющегося соперника и между поколениями не сравнивалась.
 *
 * <p>Что стало. У каждой линии есть ЗАЛ СЛАВЫ — замороженные чемпионы прошлых
 * этапов. Претендент играет и против свежих чемпионов других характеров, и против
 * старых версий: обыграть их всех «камнем-ножницами» невозможно, круг размыкается.
 * Плюс единая честная цель ({@link Fitness}: победа главное, поддержка затухает) и
 * ОТЛОЖЕННАЯ проверка — чемпион линии обновляется только если стал сильнее на
 * партиях, которых отбор не видел.
 *
 * <p>Запуск: {@code SelfPlayTrainer [партий на характер] [популяция] [партий на геном]}.
 */
public final class SelfPlayTrainer {

    private final AtomicLong gamesPlayed = new AtomicLong();
    private final int population;
    private final int gamesPerGenome;
    private final ExecutorService pool;
    private final StringBuilder log = new StringBuilder();

    /** Действующие чемпионы: «состав/характер» → геном. */
    private final Map<String, Genome> champions = new LinkedHashMap<>();
    /** Зал славы по линиям: «состав/характер» → замороженные прошлые чемпионы. */
    private final Map<String, List<Genome>> hallOfFame = new LinkedHashMap<>();
    /** Лучшая отложенная проверка линии — планка, ниже которой не обновляем. */
    private final Map<String, Double> bestHoldout = new LinkedHashMap<>();

    /** Чем играют боты в обучающих партиях (см. {@link Fitness.Brain}). */
    private final Fitness.Brain brain;

    /**
     * КАКИЕ ЛИНИИ УЧИМ. Пусто — все. Нужно, чтобы доучить одну линию, не трогая
     * остальные: полный проход по семи характерам идёт часами, а поправить обычно
     * надо одну.
     */
    private java.util.Set<String> only = java.util.Set.of();

    /**
     * ЦЕЛЬ ОТБОРА, ЗАДАННАЯ СНАРУЖИ. {@code null} — как заведено: у воителя
     * ВОЙНА, у остальных ПОБЕДА. Задаётся из окна, когда надо проверить, что
     * будет, если учить агрессии не только воителя (или воителя — не агрессии).
     */
    private Fitness.Goal goalOverride;

    /** Учить только эти характеры; пусто или null — все. */
    public void setOnly(java.util.Collection<String> characters) {
        only = characters == null ? java.util.Set.of()
            : java.util.Set.copyOf(characters);
    }

    /** Задать общую цель отбора для всех линий; null — вернуть обычное правило. */
    public void setGoal(Fitness.Goal g) {
        goalOverride = g;
    }

    /** Список характеров этого обучения — с учётом сужения через {@link #setOnly}. */
    private java.util.List<String> characters() {
        if (only.isEmpty()) {
            return Bots.CHARACTERS;
        }
        java.util.List<String> out = new ArrayList<>();
        for (String c : Bots.CHARACTERS) {
            if (only.contains(c)) {
                out.add(c);
            }
        }
        return out.isEmpty() ? Bots.CHARACTERS : out;
    }

    /**
     * ЦЕЛЬ ЛИНИИ. У всех характеров цель одна — победа и отрыв; у «воителя» своя:
     * отрыв, добытый агрессией, плюс насыщение поля разными родами войск (заказ
     * дизайнера 13.08.2026). Без этого «агрессивная линия» была бы просто перекосом
     * весов, который отбор всё равно вытянул бы обратно к экономике. Из окна это
     * правило можно перебить на всё обучение — см. {@link #setGoal}.
     */
    private Fitness.Goal goalOf(String character) {
        if (goalOverride != null) {
            return goalOverride;
        }
        if ("warlord".equals(character)) {
            return Fitness.Goal.ВОЙНА;
        }
        if ("axiom".equals(character)) {
            return Fitness.Goal.АКСИОМА;
        }
        if ("reaper".equals(character)) {
            // Ступень задаётся снаружи через setGoal: жатва → наука → задания.
            // Без указания линия учится первой ступени.
            return Fitness.Goal.ЖНЕЦ;
        }
        // СЕМЬ НОВЫХ ХАРАКТЕРОВ (заказ дизайнера 18.08.2026) — см. Fitness.Goal.
        switch (character) {
            case "specialist": return Fitness.Goal.СПЕЦИАЛИСТ;
            case "arsenal": return Fitness.Goal.АРСЕНАЛ;
            case "quester": return Fitness.Goal.ЗАДАЧНИК;
            case "berserker": return Fitness.Goal.ГРОМИЛА;
            case "scientist": return Fitness.Goal.УЧЁНЫЙ;
            case "superweapon": return Fitness.Goal.СУПЕРОРУЖИЕ;
            case "cuhunter": return Fitness.Goal.ОХОТНИК;
            // СОСТАВ 4.0 (21.08.2026). Цель каждой линии — ПОБЕДА плюс её
            // фирменная дорога к ней: иначе характер стирается за первые же
            // поколения (это уже случалось — см. коридор весов в Genome).
            case "builder": return Fitness.Goal.ЗАДАЧНИК;
            case "supplier": return Fitness.Goal.АРСЕНАЛ;
            case "stalker": return Fitness.Goal.ОХОТНИК;
            case "punisher": return Fitness.Goal.ВОЙНА;
            default: break;
        }
        return Fitness.Goal.ПОБЕДА;
    }

    public SelfPlayTrainer(int population, int gamesPerGenome) {
        this(population, gamesPerGenome, Fitness.Brain.ФОРМУЛА);
    }

    public SelfPlayTrainer(int population, int gamesPerGenome, Fitness.Brain brain) {
        this.population = Math.max(4, population);
        this.gamesPerGenome = Math.max(4, gamesPerGenome);
        this.brain = brain;
        // СКОЛЬКО ЯДЕР ОТДАТЬ ОБУЧЕНИЮ. По умолчанию почти все, но настройкой
        // запуска (-Dkelium.train.threads) можно оставить машине силы на замеры:
        // долгое обучение идёт часами, и всё это время на ней надо работать.
        int threads = Integer.getInteger("kelium.train.threads",
            Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
        this.pool = Executors.newFixedThreadPool(threads);
        say("ядер под обучение: " + threads);
        say("мозг обучающих партий: " + brain);
    }

    private void say(String line) {
        System.out.println(line);
        log.append(line).append(System.lineSeparator());
    }

    /** Что напечатано за обучение. */
    public String logText() {
        return log.toString();
    }

    private String key(int players, String character) {
        return players + "/" + character;
    }

    private Genome champion(int players, String character) {
        return champions.computeIfAbsent(key(players, character),
            k -> Bots.genome(character, players));
    }

    /**
     * Спарринг-состав для линии: свежие чемпионы ДРУГИХ характеров плюс старые
     * версии из зала славы. Смесь обязательна: только свежие — риск ходить по
     * кругу; только старые — обучение отстаёт от жизни.
     */
    private List<Genome> sparring(int players, String character, boolean mirror) {
        List<Genome> out = new ArrayList<>();
        if (mirror) {
            out.add(champion(players, character));
        } else {
            for (String c : Bots.CHARACTERS) {
                if (!c.equals(character)) {
                    out.add(champion(players, c));
                }
            }
        }
        List<Genome> fame = hallOfFame.getOrDefault(key(players, character), List.of());
        int taken = 0;
        for (int i = fame.size() - 1; i >= 0 && taken < 2; i--, taken++) {
            out.add(fame.get(i));
        }
        if (out.isEmpty()) {
            out.add(Genome.defaults());
        }
        return out;
    }

    /** Средняя приспособленность генома (места ротируются). */
    private double evaluate(Genome g, int players, long baseSeed, List<Genome> rivals,
                            double shaping, Fitness.Goal goal) {
        return evaluate(g, players, baseSeed, rivals, shaping, goal, 0.0);
    }

    /**
     * То же, но с вероятностью бесплатной карты в Обновление — см.
     * {@link #familiarizeWithCards}.
     */
    private double evaluate(Genome g, int players, long baseSeed, List<Genome> rivals,
                            double shaping, Fitness.Goal goal, double cardFloodRate) {
        double sum = 0.0;
        for (int i = 0; i < gamesPerGenome; i++) {
            int seat = i % players;
            Fitness.Result r = Fitness.play(players, baseSeed + i, seat, g, rivals, shaping,
                brain, goal, cardFloodRate);
            sum += r.fitness();
            long n = gamesPlayed.incrementAndGet();
            logGame(n, players, seat, g, rivals, r);
        }
        return sum / gamesPerGenome;
    }

    /**
     * СТРОКА НА КАЖДУЮ ПАРТИЮ (заказ дизайнера 18.08.2026 — «хочу видеть номер
     * партии в итерации, состав игроков и итог по очкам»). Печатает НАПРЯМУЮ
     * в {@code System.out}, МИМО {@link #log} (StringBuilder не потокобезопасен,
     * а {@code evaluate} вызывается из пула на до 14 потоках разом — раньше
     * {@code say()} звали только из главного потока между этапами, и общий
     * буфер этого не заметил бы, случись гонка). Сохранённый лог этапа
     * ({@code reports/balance/самоигра.log}) остаётся компактной сводкой по
     * этапам, а не сотнями тысяч строк на партию.
     */
    private static void logGame(long n, int players, int seat, Genome g,
                                List<Genome> rivals, Fitness.Result r) {
        String rivalNames = rivals.stream().map(x -> x.profile).distinct()
            .collect(java.util.stream.Collectors.joining(","));
        System.out.printf(Locale.ROOT,
            "[партия %d] %dp место=%d характер=%-12s против=[%s] -> ПО=%d %-9s fitness=%.1f%n",
            n, players, seat, g.profile, rivalNames, r.vp(),
            r.win() ? "ПОБЕДА" : "проигрыш", r.fitness());
    }

    /**
     * Отложенная проверка линии — ФИКСИРОВАННЫЕ зёрна и ФИКСИРОВАННЫЕ соперники
     * на всё обучение, поддержка выключена. Только так число сравнимо между
     * этапами и годится, чтобы решать «обновлять ли чемпиона».
     */
    private double holdout(Genome g, int players, Fitness.Goal goal) {
        List<Genome> rivals = List.of(Genome.defaults(),
            Genome.defaults().withProfile("hawk"), Genome.defaults().withProfile("dove"));
        double sum = 0.0;
        int games = Math.max(32, 3 * gamesPerGenome);
        for (int i = 0; i < games; i++) {
            int seat = i % players;
            Fitness.Result r = Fitness.play(players, 555_000_000L + i, seat, g, rivals, 0.0,
                brain, goal);
            sum += r.fitness();
            long n = gamesPlayed.incrementAndGet();
            logGame(n, players, seat, g, rivals, r);
        }
        return sum / games;
    }

    /**
     * Сколько этапов уже проведено — входит в зерно поиска. Без этого счётчика
     * этапы одной линии в одном составе получали ОДНО И ТО ЖЕ зерно и повторяли
     * ровно ту же работу: в логе круги 4 и 8 давали побайтно одинаковые числа, то
     * есть половина времени обучения уходила впустую.
     */
    private int stageCounter = 0;

    /** Один этап обучения одной линии. */
    private void stage(String character, int players, boolean mirror, long budget,
                       double progress) {
        stage(character, players, mirror, budget, progress, 0.0);
    }

    /**
     * То же, но с ПОТОЛКОМ вероятности бесплатной карты в Обновление
     * ({@code maxCardFloodRate}), убывающим ЛИНЕЙНО по ходу ЭТОГО этапа вместе
     * с темпом мутации (та же переменная {@code spent/budget}) — «карты льются
     * рекой» в начале этапа и иссякают к его концу, ботам приходится добывать
     * их самим. 0.0 = обычное поведение (существующие вызовы не меняются).
     */
    private void stage(String character, int players, boolean mirror, long budget,
                       double progress, double maxCardFloodRate) {
        String k = key(players, character);
        Genome incumbent = champion(players, character);
        Genome best = incumbent;
        stageCounter++;
        Random rng = new Random(777L * players + character.hashCode() + 31L * stageCounter);
        List<Genome> rivals = sparring(players, character, mirror);
        double shaping = Fitness.shapingAt(progress, 0.35);

        List<Genome> pop = new ArrayList<>();
        pop.add(best);
        for (int i = 1; i < population; i++) {
            pop.add(best.mutate(rng, 0.25));
        }

        long spent = 0;
        int gen = 0;
        long perGen = (long) population * gamesPerGenome;
        double bestFit = Double.NEGATIVE_INFINITY;
        while (spent + perGen <= budget) {
            gen++;
            final long baseSeed = 100_000L + gen * 1000L + players;
            double rate = Math.max(0.05, 0.25 * (1.0 - spent / (double) Math.max(1, budget)));
            // Убывает синхронно с темпом мутации: 1.0 в начале этапа -> 0.0 в конце.
            final double cardFloodRate = maxCardFloodRate <= 0 ? 0.0
                : maxCardFloodRate * Math.max(0.0, 1.0 - spent / (double) Math.max(1, budget));

            List<Future<Double>> futures = new ArrayList<>();
            for (Genome g : pop) {
                futures.add(pool.submit(
                    (Callable<Double>) () -> evaluate(g, players, baseSeed, rivals, shaping,
                        goalOf(character), cardFloodRate)));
            }
            List<double[]> scored = new ArrayList<>();
            double sumFit = 0.0;
            for (int i = 0; i < pop.size(); i++) {
                double f;
                try {
                    f = futures.get(i).get();
                } catch (Exception e) {
                    f = Double.NEGATIVE_INFINITY;
                }
                scored.add(new double[]{i, f});
                sumFit += f;
            }
            spent += perGen;
            scored.sort(Comparator.comparingDouble((double[] a) -> -a[1]));
            if (scored.get(0)[1] > bestFit) {
                bestFit = scored.get(0)[1];
                best = pop.get((int) scored.get(0)[0]);
            }

            int keep = Math.max(2, population / 2);
            List<Genome> elite = new ArrayList<>();
            for (int i = 0; i < keep; i++) {
                elite.add(pop.get((int) scored.get(i)[0]));
            }
            List<Genome> next = new ArrayList<>(elite);
            while (next.size() < population) {
                Genome pa = elite.get(rng.nextInt(elite.size()));
                Genome pb = elite.get(rng.nextInt(elite.size()));
                next.add(Genome.crossover(pa, pb, rng).mutate(rng, rate));
            }
            pop = next;
        }

        // ЧЕМПИОН ОБНОВЛЯЕТСЯ ТОЛЬКО ПО ОЧНОЙ ОТЛОЖЕННОЙ ПРОВЕРКЕ.
        //
        // Претендент и ДЕЙСТВУЮЩИЙ чемпион проверяются ЗАНОВО и на ОДНИХ И ТЕХ ЖЕ
        // партиях. Раньше планка запоминалась один раз: стоило действующему
        // чемпиону получить удачный замер — и обойти его не мог уже никто, обучение
        // вставало навсегда. Очная проверка снимает это полностью: сравниваются два
        // числа, измеренные в одинаковых условиях в один момент.
        double hold = holdout(best, players, goalOf(character));
        double incumbentNow = best == incumbent ? hold
            : holdout(incumbent, players, goalOf(character));
        boolean better = hold > incumbentNow;
        if (better) {
            bestHoldout.put(k, hold);
            champions.put(k, best);
            hallOfFame.computeIfAbsent(k, x -> new ArrayList<>()).add(best);
            save(character, players, best);
        } else {
            bestHoldout.put(k, incumbentNow);
        }
        double prev = incumbentNow;
        say(String.format(Locale.ROOT,
            "  %-12s %dp %-11s поколений %2d, обучение %.1f, претендент %.1f "
            + "против чемпиона %.1f %s",
            character, players, mirror ? "зеркало" : "вперемешку", gen, bestFit, hold, prev,
            better ? "<= чемпион обновлён" : "— оставили прежнего"));
    }

    private void save(String character, int players, Genome g) {
        Path p = Locations.botMemory()
            .resolve("strategic_" + players + "p_" + character + ".json");
        try {
            g.saveJson(p);
        } catch (Exception e) {
            say("не удалось сохранить " + p + ": " + e.getMessage());
        }
        if ("balanced".equals(character)) {
            try {
                g.saveJson(Locations.botMemory().resolve("strategic_" + players + "p.json"));
            } catch (Exception ignored) {
                // линия характера уже сохранена — не критично
            }
        }
    }

    /**
     * ЭТАП ЗНАКОМСТВА С КАРТАМИ — см. {@link #run(long, long, long)}.
     * Зеркало на 3 и 4 игрока для каждой линии, потолок вероятности карты 0.6,
     * убывает до нуля к концу бюджета линии (внутри {@link #stage}).
     */
    private void familiarizeWithCards(long perCharacter) {
        long t0 = System.nanoTime();
        say("=== ЭТАП 0: ЗНАКОМСТВО С КАРТАМИ (карты льются рекой, потом иссякают) ===");
        for (int players : new int[]{3, 4}) {
            for (String c : characters()) {
                stage(c, players, true, perCharacter, 0.0, 0.6);
            }
        }
        say(String.format(Locale.ROOT,
            "знакомство с картами закончено: %d партий, %.1f мин",
            gamesPlayed.get(), (System.nanoTime() - t0) / 6e10));
    }

    /**
     * Полная программа: зеркало (характер против себя же) на 3 и 4 игрока, затем
     * вперемешку кругами, пока каждая линия не отыграет свой бюджет.
     */
    public void run(long perCharacter, long mirrorGames) {
        run(perCharacter, mirrorGames, 0);
    }

    /**
     * То же, но перед обычной программой — ЭТАП ЗНАКОМСТВА С КАРТАМИ (заказ
     * дизайнера 14.08.2026): {@code cardGames} партий на характер играются с
     * вероятностью бесплатной карты в Обновление, УБЫВАЮЩЕЙ от 0.6 до нуля —
     * боты должны много раз увидеть карты арсенала и заданий и решить, что с
     * ними делать, прежде чем веса их оценки начнут значить что-то (иначе они
     * почти не встречаются в partiях обычного обучения и отбор их не
     * проверяет — тот же класс проблемы, что был у 29 весов {@code eval.*}).
     * 0 (по умолчанию) — пропустить этот этап, поведение как раньше.
     */
    public void run(long perCharacter, long mirrorGames, long cardGames) {
        if (cardGames > 0) {
            familiarizeWithCards(cardGames);
        }
        long t0 = System.nanoTime();
        java.util.List<String> lines = characters();
        int stagesTotal = 2 * lines.size() + 8 * lines.size();
        int stageNo = 0;

        say("=== ЭТАП 1: ЗЕРКАЛО (характер против себя же) ===");
        for (int players : new int[]{3, 4}) {
            for (String c : lines) {
                stage(c, players, true, mirrorGames, stageNo++ / (double) stagesTotal);
            }
        }
        say(String.format(Locale.ROOT, "зеркало закончено: %d партий, %.1f мин",
            gamesPlayed.get(), (System.nanoTime() - t0) / 6e10));

        say("");
        say("=== ЭТАП 2: ВПЕРЕМЕШКУ (характеры друг против друга) ===");
        long left = Math.max(0, perCharacter - 2 * mirrorGames);
        long chunk = Math.max((long) population * gamesPerGenome, left / 8);
        long done = 0;
        int round = 0;
        while (done < left) {
            round++;
            long part = Math.min(chunk, left - done);
            int players = round % 2 == 1 ? 4 : 3;
            say(String.format(Locale.ROOT,
                "-- круг %d: состав %d игроков, по %d партий на характер",
                round, players, part));
            for (String c : lines) {
                stage(c, players, false, part, stageNo++ / (double) stagesTotal);
            }
            done += part;
        }

        double mins = (System.nanoTime() - t0) / 6e10;
        say("");
        say(String.format(Locale.ROOT,
            "=== ОБУЧЕНИЕ ЗАВЕРШЕНО: %d партий, %.1f мин (%.0f партий/с) ===",
            gamesPlayed.get(), mins, gamesPlayed.get() / (mins * 60.0)));
        say("Итоговые планки линий (отложенная проверка):");
        for (var e : bestHoldout.entrySet()) {
            say(String.format(Locale.ROOT, "  %-18s %.1f", e.getKey(), e.getValue()));
        }
        pool.shutdown();
    }

    /**
     * ОБУЧЕНИЕ ИЗ ОКНА: те же настройки, что у {@link #main}, но линии и цель
     * задаются с формы, а лог пишется туда же, куда и при запуске из командной
     * строки. Отдельный вход нужен, чтобы окно не собирало массив строк-аргументов
     * — через него нельзя было передать ни выбор линии, ни цель.
     *
     * @param perCharacter партий на характер
     * @param only         какие линии учить; пусто — все
     * @param goal         общая цель отбора; {@code null} — обычное правило
     */
    public static void trainFromGui(long perCharacter, java.util.Collection<String> only,
                                    Fitness.Goal goal) {
        SelfPlayTrainer t = new SelfPlayTrainer(16, 12, Fitness.Brain.ФОРМУЛА);
        t.setOnly(only);
        t.setGoal(goal);
        t.run(perCharacter, Math.max(500L, perCharacter / 10));
        saveLog(t);
    }

    private static void saveLog(SelfPlayTrainer t) {
        try {
            Path out = Path.of("reports", "balance", "самоигра.log");
            Files.createDirectories(out.getParent());
            Files.writeString(out, t.logText(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("лог обучения: " + out.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("лог не сохранён: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        long perCharacter = args.length > 0 ? Long.parseLong(args[0]) : 20_000L;
        int population = args.length > 1 ? Integer.parseInt(args[1]) : 16;
        int gamesPerGenome = args.length > 2 ? Integer.parseInt(args[2]) : 12;
        long mirror = args.length > 3 ? Long.parseLong(args[3]) : 2_000L;
        // Пятый аргумент «просчёт» — учить ботов, которые ПРОВЕРЯЮТ ходы на копии.
        // Без него двадцать девять весов оценки позиции отбор не проверяет вовсе.
        Fitness.Brain brain = args.length > 4 && args[4].toLowerCase(Locale.ROOT)
            .startsWith("просч") ? Fitness.Brain.ПРОСЧЁТ : Fitness.Brain.ФОРМУЛА;
        // Шестой аргумент — партий на характер в ЭТАПЕ ЗНАКОМСТВА С КАРТАМИ
        // (заказ дизайнера 14.08.2026, см. run(long,long,long)). 0 = пропустить.
        long cardGames = args.length > 5 ? Long.parseLong(args[5]) : 0L;
        // Седьмой аргумент — КАКИЕ ЛИНИИ УЧИТЬ, через запятую («состав4» —
        // действующие четыре характера). Без него учатся все, включая архивные:
        // полный проход по пятнадцати линиям идёт много часов, а нужны обычно
        // четыре (заказ дизайнера 21.08.2026).
        java.util.List<String> only = java.util.List.of();
        if (args.length > 6 && !args[6].isBlank()) {
            only = "состав4".equalsIgnoreCase(args[6]) || "roster4".equalsIgnoreCase(args[6])
                ? Bots.ROSTER_4
                : java.util.List.of(args[6].split(","));
        }

        SelfPlayTrainer t = new SelfPlayTrainer(population, gamesPerGenome, brain);
        t.setOnly(only);
        System.out.println("[ОБУЧЕНИЕ] линии: "
            + (only.isEmpty() ? "все" : String.join(", ", only))
            + "; мозг: " + brain + "; партий на характер: " + perCharacter);
        t.run(perCharacter, mirror, cardGames);
        saveLog(t);
    }
}
