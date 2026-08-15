package kelium;

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

import kelium.agents.Bots;
import kelium.agents.ExploitAgent;
import kelium.agents.HumanLikeAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * BotFamilies — отчёт по ДВУМ СЕМЬЯМ БОТОВ (заказ дизайнера 13.08.2026).
 *
 * <p><b>Ищейки.</b> Ищут не победу, а дыры: выполняют действия на копии партии,
 * считают отдачу «сколько прибыло на единицу потраченного» и охотно повторяют то,
 * что дало ненормально много. В отчёт идут действия с самой высокой отдачей и
 * число повторов за партию — это и есть карта слабых мест дизайна.
 *
 * <p><b>Живые.</b> Играют как люди: помнят обиды и мстят, заводятся от потерь,
 * рассматривают два-три варианта вместо восьми, держат план по упрямству. В отчёт
 * идёт мера мстительности (какая доля ударов пришлась по обидчику) и сила против
 * машинного бота — живой обязан быть слабее, иначе он не живой, а просто шумный.
 *
 * <p>Запуск: {@code kelium.BotFamilies [игроков] [партий]}.
 */
public final class BotFamilies {

    private BotFamilies() {
    }

    /** Итог по одной семье. */
    private static final class Tally {
        int games;
        double vpSum;
        int wins;
        double revengeSum;
        int revengeSamples;
        double tiltSum;
        final Map<String, Double> bestYield = new LinkedHashMap<>();
        final Map<String, Integer> repeats = new LinkedHashMap<>();
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        into.vpSum += one.vpSum;
        into.wins += one.wins;
        into.revengeSum += one.revengeSum;
        into.revengeSamples += one.revengeSamples;
        into.tiltSum += one.tiltSum;
        one.bestYield.forEach((k, v) -> into.bestYield.merge(k, v, Math::max));
        one.repeats.forEach((k, v) -> into.repeats.merge(k, v, Integer::sum));
    }

    /**
     * Одна партия: на месте {@code seat} — бот проверяемой семьи, остальные —
     * обычные обученные стратеги. Так видно и силу, и повадки.
     */
    private static Tally playOne(int players, long seed, int seat, String family) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        HumanLikeAgent human = null;
        ExploitAgent hunter = null;
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i);
            if (i != seat) {
                agents.add(Bots.create("balanced", i, rng, players));
                continue;
            }
            switch (family) {
                case "живой" -> {
                    human = HumanLikeAgent.normal(i, rng, Bots.genome("balanced", players),
                        players);
                    agents.add(human);
                }
                case "злопамятный" -> {
                    human = HumanLikeAgent.vengeful(i, rng, Bots.genome("hawk", players),
                        players);
                    agents.add(human);
                }
                case "хладнокровный" -> {
                    human = HumanLikeAgent.coolHeaded(i, rng,
                        Bots.genome("balanced", players), players);
                    agents.add(human);
                }
                // КОНТРОЛЬНЫЙ ОБРАЗЕЦ: «человек» с полностью выключенной
                // человечностью. Обязан играть как машина. Если не играет —
                // сломана сама механика выбора, а не человеческие черты.
                case "человечность выкл" -> {
                    human = new HumanLikeAgent(i, rng, Bots.genome("balanced", players),
                        "контроль", 0.0, 0.0, 999, 0.0, 1, players);
                    agents.add(human);
                }
                case "ищейка" -> {
                    hunter = ExploitAgent.hunter(i, rng, Bots.genome("balanced", players));
                    agents.add(hunter);
                }
                default -> agents.add(Bots.create("balanced", i, rng, players));
            }
        }
        Map<String, Object> res = GameEngine.playGame(s, agents, null);
        Tally t = new Tally();
        t.games = 1;
        t.vpSum = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        t.wins = res.get("winner") instanceof Number w && w.intValue() == seat ? 1 : 0;
        if (human != null) {
            t.revengeSum = human.revengeShare();
            t.revengeSamples = 1;
            t.tiltSum = human.tilt();
        }
        if (hunter != null) {
            t.bestYield.putAll(hunter.findings());
            t.repeats.putAll(hunter.repeatCounts());
        }
        return t;
    }

    private static Tally run(int players, int games, String family) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 3_300_000L + g;
            final int seat = g % players;
            futures.add(pool.submit((Callable<Tally>) () ->
                playOne(players, seed, seat, family)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась (" + family + "): " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        List<String> families = List.of("живой", "злопамятный", "хладнокровный",
            "человечность выкл", "ищейка", "машина");
        Map<String, Tally> res = new LinkedHashMap<>();
        for (String f : families) {
            long t0 = System.nanoTime();
            Tally t = run(players, games, f);
            res.put(f, t);
            System.out.printf(Locale.ROOT, "  %-14s ПО %.2f, побед %.0f%%  (%.0f с)%n",
                f, t.vpSum / Math.max(1, t.games),
                100.0 * t.wins / Math.max(1, t.games), (System.nanoTime() - t0) / 1e9);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Две семьи ботов: живые и ищейки\n\n");
        sb.append("По ").append(games).append(" партий на семью, ").append(players)
          .append(" игрока. Проверяемый бот сидит на РОТИРУЕМОМ месте, остальные — ")
          .append("обычные обученные стратеги. «машина» — тот же обычный стратег, ")
          .append("нужен как точка отсчёта.\n\n");

        sb.append("## Сила семей\n\n");
        sb.append("| семья | ПО в среднем | % побед |\n|---|---:|---:|\n");
        for (String f : families) {
            Tally t = res.get(f);
            int g = Math.max(1, t.games);
            sb.append(String.format(Locale.ROOT, "| %s | %.2f | %.0f%% |%n",
                f, t.vpSum / g, 100.0 * t.wins / g));
        }
        sb.append("\n**Как читать.** Живые ДОЛЖНЫ быть слабее машины — в этом смысл ")
          .append("семьи: они играют как люди, а люди ошибаются и мстят вместо ")
          .append("расчёта. Если живой играет наравне с машиной, значит человеческие ")
          .append("черты в нём не работают, а если он много слабее — они перекручены.\n");

        sb.append("\n## Повадки живых\n\n");
        sb.append("| семья | доля ударов ПО ОБИДЧИКУ | задетость к концу партии |\n");
        sb.append("|---|---:|---:|\n");
        for (String f : families) {
            Tally t = res.get(f);
            if (t.revengeSamples == 0) {
                continue;
            }
            sb.append(String.format(Locale.ROOT, "| %s | %.0f%% | %.2f |%n",
                f, 100.0 * t.revengeSum / t.revengeSamples,
                t.tiltSum / t.revengeSamples));
        }
        sb.append("\nДоля ударов по обидчику — прямая мера мстительности: сколько ")
          .append("боевых целей выбрано не по выгоде, а по памяти о том, кто первым ")
          .append("тронул. У хладнокровного она должна быть заметно ниже.\n");

        Tally hunt = res.get("ищейка");
        sb.append("\n## Что нашли ищейки\n\n");
        sb.append("Отдача действия = сколько материи прибыло на единицу потраченного ")
          .append("(монеты, боеприпасы, келемий). Считается на КОПИИ партии. ")
          .append("«Повторов» — сколько раз за партию бот сыграл действие, у которого ")
          .append("уже нашёл высокую отдачу: если правило позволяет доить, повторы ")
          .append("накапливаются.\n\n");
        sb.append("| действие | лучшая замеченная отдача | повторов за партию |\n");
        sb.append("|---|---:|---:|\n");
        List<String> acts = new ArrayList<>(hunt.bestYield.keySet());
        acts.sort(Comparator.comparingDouble((String a) -> -hunt.bestYield.get(a)));
        int hg = Math.max(1, hunt.games);
        for (String a : acts) {
            sb.append(String.format(Locale.ROOT, "| %s | %.2f | %.2f |%n",
                a, hunt.bestYield.get(a), hunt.repeats.getOrDefault(a, 0) / (double) hg));
        }
        if (acts.isEmpty()) {
            sb.append("| — | — | ищейка ничего подозрительного не нашла |\n");
        }
        sb.append("\n**Как читать.** Строки сверху — действия, которые дают больше ")
          .append("всего за свою цену. Само по себе это не изъян: у Добычи отдача ")
          .append("высока по замыслу. Тревожно, когда высокая отдача СОЧЕТАЕТСЯ с ")
          .append("большим числом повторов — значит правило позволяет повторять ")
          .append("выгодное без ограничения, и это дыра.\n");

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "семьи-ботов-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
