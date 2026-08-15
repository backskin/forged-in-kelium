package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.rules.Ruleset;
import kelium.engine.LayoutLibrary;

/**
 * ContainerModels — СРАВНЕНИЕ ТРЁХ МОДЕЛЕЙ КОНТЕЙНЕРОВ на одних и тех же раздачах.
 *
 * <p>Зачем. Замер 13.08.2026 показал, что печатные контейнеры сыплются почти
 * без конца: 79.6 контейнера за партию против 16.4 у прежних жетонов
 * и +2.7 ПО на игрока. Дизайнер продиктовал доработку правил получения, и вопрос
 * «стало ли лучше» решается только замером — рассуждением тут ничего не докажешь.
 *
 * <p>Три модели:
 * <ol>
 *   <li><b>печатные</b> — «Контейнеры 2.0» как есть: накрыл ячейку жетоном — взял;</li>
 *   <li><b>печатные+пустой гекс</b> — доработка 13.08.2026: контейнер достаётся
 *       только с гекса, где НЕТ вообще никаких жетонов игроков;</li>
 *   <li><b>жетоны</b> — прежняя модель: контейнеры выкладываются жетонами на поле
 *       и подбираются войском, вошедшим на гекс.</li>
 * </ol>
 *
 * <p>Все три гоняются на ОДНИХ И ТЕХ ЖЕ сидах, чтобы разница была разницей правил,
 * а не раздач. Запуск: {@code kelium.ContainerModels [игроков] [партий на модель]}.
 */
public final class ContainerModels {

    private ContainerModels() {
    }

    private record Model(String name, Map<String, Object> overrides) { }

    private static final List<Model> MODELS = List.of(
        new Model("печатные (как есть)", Map.of()),
        new Model("+ пустой гекс", Map.of("containers.printed_requires_empty_hex", true)),
        new Model("+ гекс, без Добычи", Map.of(
            "containers.printed_requires_empty_hex", true,
            "containers.printed_mining_branch", false)),
        new Model("+ гекс + набор 3/4", Map.of(
            "containers.printed_requires_empty_hex", true,
            "containers.printed_per_small_block", 3,
            "containers.printed_per_big_block", 4)),
        new Model("только стройкой (без Добычи)", Map.of(
            "containers.printed_requires_empty_hex", true,
            "containers.printed_only_lasting_tokens", true,
            "containers.printed_mining_branch", false)),
        new Model("только стройкой + набор 3/4", Map.of(
            "containers.printed_requires_empty_hex", true,
            "containers.printed_only_lasting_tokens", true,
            "containers.printed_mining_branch", false,
            "containers.printed_per_small_block", 3,
            "containers.printed_per_big_block", 4)),
        new Model("жетоны (старая модель)", Map.of("containers.mode", "tokens")));

    /** Состав стола тот же, что в балансовом стенде: видно, кому модель выгодна. */
    private static final List<String> LINEUP =
        List.of("hawk", "dove", "balanced", "opportunist");

    private static final class Stats {
        int games;
        long containersGot;      // получено карт контейнеров (все игроки)
        long containersOpened;   // вскрыто (то есть реально сыграно)
        long battles;
        long destroyed;
        double vpSum;            // сумма ПО всех игроков
        long rounds;
        int militaryWins;
        int superWins;
        final Map<String, Double> vpByChar = new LinkedHashMap<>();
        final Map<String, Integer> winsByChar = new LinkedHashMap<>();
        final Map<String, Integer> gamesByChar = new LinkedHashMap<>();

        double per(long v) {
            return games == 0 ? 0 : (double) v / games;
        }

        double vpPerPlayer(int players) {
            return games == 0 ? 0 : vpSum / games / players;
        }

        double winRate(String ch) {
            int g = gamesByChar.getOrDefault(ch, 0);
            return g == 0 ? 0 : 100.0 * winsByChar.getOrDefault(ch, 0) / g;
        }
    }

    private static synchronized void merge(Stats into, Stats one) {
        into.games += one.games;
        into.containersGot += one.containersGot;
        into.containersOpened += one.containersOpened;
        into.battles += one.battles;
        into.destroyed += one.destroyed;
        into.vpSum += one.vpSum;
        into.rounds += one.rounds;
        into.militaryWins += one.militaryWins;
        into.superWins += one.superWins;
        one.vpByChar.forEach((k, v) -> into.vpByChar.merge(k, v, Double::sum));
        one.winsByChar.forEach((k, v) -> into.winsByChar.merge(k, v, Integer::sum));
        one.gamesByChar.forEach((k, v) -> into.gamesByChar.merge(k, v, Integer::sum));
    }

    private static Stats playOne(Model m, int players, long seed) {
        GameConfig base = LayoutLibrary.configFor(players, seed);
        Ruleset rules = base.ruleset.copy();
        for (Map.Entry<String, Object> e : m.overrides().entrySet()) {
            rules.override(e.getKey(), e.getValue());
        }
        GameConfig cfg = new GameConfig(rules, base.content, players, seed, base.dataRoot,
            base.boardSides, base.scenarioId, base.cuFacing, base.scenarioFile);
        GameState s = Setup.buildGame(cfg);

        List<Agent> agents = new ArrayList<>();
        List<String> chars = new ArrayList<>();
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            String ch = LINEUP.get((i + shift) % LINEUP.size());
            chars.add(ch);
            agents.add(Bots.create(ch, i, new Random(seed * 31 + i), players));
        }

        Stats st = new Stats();
        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            String type = String.valueOf(ev.get("type"));
            if ("container".equals(type)) {
                st.containersOpened++;   // карта вскрыта, то есть реально сыграна
            } else if ("combat_hit".equals(type)) {
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    st.destroyed++;
                }
            } else if ("action".equals(type) && "combat".equals(ev.get("action"))
                    && Boolean.TRUE.equals(ev.get("ok"))) {
                st.battles++;
            }
        });

        st.games = 1;
        st.rounds = res.get("rounds") instanceof Number r ? r.intValue() : 0;
        String cond = String.valueOf(res.get("condition"));
        if ("military".equals(cond)) {
            st.militaryWins = 1;
        } else if ("super_objective".equals(cond)) {
            st.superWins = 1;
        }
        int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
        for (int i = 0; i < players; i++) {
            String ch = chars.get(i);
            int vp = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            st.vpSum += vp;
            st.vpByChar.merge(ch, (double) vp, Double::sum);
            st.gamesByChar.merge(ch, 1, Integer::sum);
            if (i == winner) {
                st.winsByChar.merge(ch, 1, Integer::sum);
            }
        }
        return st;
    }

    private static Stats run(Model m, int players, int games) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Stats total = new Stats();
        List<Future<Stats>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 7_300_000L + g;   // одни и те же раздачи у всех моделей
            Callable<Stats> task = () -> playOne(m, players, seed);
            futures.add(pool.submit(task));
        }
        for (Future<Stats> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 1000;

        Map<String, Stats> all = new LinkedHashMap<>();
        Map<String, Long> granted = new LinkedHashMap<>();
        for (Model m : MODELS) {
            out.println("считаю модель: " + m.name() + "…");
            // ВСЕ выдачи контейнеров идут через одну точку (Storage), поэтому
            // считаем их там: события вскрытия показывают только сыгранные карты.
            kelium.engine.Storage.resetContainerStats();
            Stats st = run(m, players, games);
            long total = 0;
            for (long v : kelium.engine.Storage.containerStats().values()) {
                total += v;
            }
            st.containersGot = total;
            // РАЗБИВКА ПО ИСТОЧНИКАМ — без неё непонятно, откуда поток: печатные
            // ячейки, Добыча, снос нейтрала, компенсации, карты, задания.
            out.println("  источники: " + kelium.engine.Storage.containerStats());
            granted.put(m.name(), total);
            all.put(m.name(), st);
        }

        StringBuilder md = new StringBuilder();
        md.append("# Контейнеры — три модели на одних раздачах\n\n");
        md.append("Игроков: ").append(players).append(", партий на модель: ")
          .append(games).append(". Сиды у моделей совпадают, состав стола тоже, ")
          .append("поэтому разница — это разница ПРАВИЛ.\n\n");
        md.append("| модель | контейнеров за партию | вскрыто | ПО на игрока "
            + "| боёв | уничтожено | раундов | военных побед | супер-побед |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        all.forEach((name, st) -> md.append(String.format(Locale.ROOT,
            "| %s | %.1f | %.1f | %.2f | %.2f | %.2f | %.2f | %.1f%% | %.1f%% |%n",
            name, st.per(st.containersGot), st.per(st.containersOpened),
            st.vpPerPlayer(players), st.per(st.battles), st.per(st.destroyed),
            st.per(st.rounds), 100.0 * st.militaryWins / Math.max(1, st.games),
            100.0 * st.superWins / Math.max(1, st.games))));

        md.append("\n## Кому какая модель выгодна (доля побед характера)\n\n| модель |");
        for (String ch : LINEUP) {
            md.append(' ').append(ch).append(" |");
        }
        md.append("\n|---|");
        LINEUP.forEach(c -> md.append("---|"));
        md.append('\n');
        all.forEach((name, st) -> {
            md.append("| ").append(name).append(" |");
            for (String ch : LINEUP) {
                md.append(String.format(Locale.ROOT, " %.1f%% |", st.winRate(ch)));
            }
            md.append('\n');
        });

        Path outFile = Path.of("reports/balance/контейнеры-три-модели.md");
        Files.createDirectories(outFile.getParent());
        Files.writeString(outFile, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + outFile.toAbsolutePath());
    }
}
