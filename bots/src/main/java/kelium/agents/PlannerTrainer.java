package kelium.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ОБУЧЕНИЕ ПЛАНИРОВЩИКА — подбор весов оценки позиции ({@code pl.*}) по характерам.
 *
 * <p>Что настраивается. Планировщик не «учится играть» — играть он умеет по
 * устройству: проигрывает ход на копии и берёт лучший сценарий. Обучение
 * подбирает, ЧТО он считает лучшим: насколько ценит задание против трофея,
 * армию против хозяйства, осторожность против натиска. Это четырнадцать чисел
 * на характер, и они читаются словами.
 *
 * <p>Как. Каждое поколение: для каждого характера — действующий чемпион и его
 * мутанты садятся по очереди за стол против чемпионов ТРЁХ других характеров,
 * на ОБЩИХ зёрнах партий (одинаковые раскладки и колоды для всех кандидатов —
 * иначе сравнение утонет в шуме). Приспособленность — отрыв по очкам и победа,
 * плюс небольшая постоянная поддержка того, чего просил дизайнер: выполненные
 * задания, установленный арсенал, нанятые войска, снесённые чужие жетоны.
 * Чемпион меняется, только если кандидат лучше его на тех же партиях.
 *
 * <p>Запуск: {@code kelium.agents.PlannerTrainer [игроков] [поколений] [популяция]
 * [партий на кандидата] [потоков]}. Веса пишутся в
 * {@code data/genomes/planner_<N>p_<характер>.json}; отчёт — в
 * {@code reports/training/}.
 */
public final class PlannerTrainer {

    private PlannerTrainer() {
    }

    /** Итог одной оценочной партии глазами кандидата. */
    record Outcome(double margin, boolean win, int objDone, int kills, int arsInstall,
                   int units, int vp) {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int generations = args.length > 1 ? Integer.parseInt(args[1]) : 6;
        int population = args.length > 2 ? Integer.parseInt(args[2]) : 6;
        int gamesPer = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        int threads = args.length > 4 ? Integer.parseInt(args[4])
            : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        List<String> roster = Bots.ROSTER_4;

        Path reportDir = Path.of("reports", "training");
        Files.createDirectories(reportDir);
        String stamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Path report = reportDir.resolve("планировщик-" + players + "p-" + stamp + ".md");
        StringBuilder log = new StringBuilder();
        log.append("# Обучение планировщика — ").append(players).append(" игроков, ")
            .append(stamp).append("\n\n")
            .append("Поколений ").append(generations).append(", популяция ").append(population)
            .append(", партий на кандидата ").append(gamesPer).append(", потоков ").append(threads)
            .append(".\n\nПриспособленность = отрыв + 5·победа + 0.4·(0.5·задания + 0.4·убито "
                + "+ 0.3·арсенал + 0.15·войска).\n\n");

        Map<String, Genome> champs = new LinkedHashMap<>();
        for (String ch : roster) {
            champs.put(ch, PlannerAgent.plannerGenome(ch, players));
        }
        Random rng = new Random(20260907L);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int gen = 1; gen <= generations; gen++) {
                long base = 100_000L * gen;
                log.append("## Поколение ").append(gen).append("\n\n");
                log.append("| характер | чемпион | лучший | принят | ПО | побед | задания | убито | арсенал | войска |\n");
                log.append("|---|---:|---:|:--:|---:|---:|---:|---:|---:|---:|\n");
                for (String ch : roster) {
                    List<Genome> cands = new ArrayList<>();
                    cands.add(champs.get(ch));
                    for (int i = 1; i < population; i++) {
                        cands.add(mutate(champs.get(ch), rng, gen));
                    }
                    List<Future<double[]>> futures = new ArrayList<>();
                    for (Genome cand : cands) {
                        final Genome g = cand;
                        futures.add(pool.submit(() -> evaluate(players, base, gamesPer, ch, g,
                            champs, roster)));
                    }
                    double[] best = null;
                    int bestIdx = -1;
                    double[] champScore = null;
                    for (int i = 0; i < futures.size(); i++) {
                        double[] r = futures.get(i).get();
                        if (i == 0) {
                            champScore = r;
                        }
                        if (best == null || r[0] > best[0]) {
                            best = r;
                            bestIdx = i;
                        }
                    }
                    boolean accepted = bestIdx > 0 && best[0] > champScore[0] + 0.05;
                    if (accepted) {
                        champs.put(ch, cands.get(bestIdx));
                        cands.get(bestIdx).saveJson(PlannerAgent.savedPath(ch, players));
                        PlannerAgent.forgetSaved();
                    }
                    double[] show = accepted ? best : champScore;
                    log.append(String.format(Locale.ROOT,
                        "| %s | %.2f | %.2f | %s | %.1f | %.0f%% | %.2f | %.2f | %.2f | %.2f |\n",
                        ch, champScore[0], best[0], accepted ? "да" : "—",
                        show[1], 100 * show[2], show[3], show[4], show[5], show[6]));
                    System.out.printf(Locale.ROOT,
                        "поколение %d %s: чемпион %.2f, лучший %.2f%s%n",
                        gen, ch, champScore[0], best[0], accepted ? " — ПРИНЯТ" : "");
                }
                log.append("\n");
                for (String ch : roster) {
                    log.append("- ").append(ch).append(": ").append(describe(champs.get(ch)))
                        .append("\n");
                }
                log.append("\n");
                Files.writeString(report, log, StandardCharsets.UTF_8);
            }
        } finally {
            pool.shutdown();
        }
        for (String ch : roster) {
            champs.get(ch).saveJson(PlannerAgent.savedPath(ch, players));
        }
        Files.writeString(report, log, StandardCharsets.UTF_8);
        System.out.println("отчёт: " + report);
    }

    /** Мутант: каждый второй вес планировщика умножается на e^N(0, σ); σ убывает с поколениями. */
    static Genome mutate(Genome g, Random rng, int gen) {
        double sigma = Math.max(0.12, 0.35 / Math.sqrt(gen));
        Genome out = g;
        for (String key : PlannerAgent.PL_KEYS) {
            if ("pl.vp".equals(key) || rng.nextBoolean()) {
                continue;   // очки — якорь шкалы, их не двигаем
            }
            double v = g.get(key, 1.0) * Math.exp(rng.nextGaussian() * sigma);
            out = out.with(key, Math.max(0.2, Math.min(3.5, v)));
        }
        return out;
    }

    static String describe(Genome g) {
        StringBuilder sb = new StringBuilder();
        for (String key : PlannerAgent.PL_KEYS) {
            sb.append(key.substring(3)).append('=')
                .append(String.format(Locale.ROOT, "%.2f ", g.get(key, 1.0)));
        }
        return sb.toString().trim();
    }

    /**
     * Приспособленность кандидата: среднее по партиям на общих зёрнах.
     *
     * @return {fitness, средние ПО, доля побед, задания, убито, арсенал, войска}
     */
    static double[] evaluate(int players, long base, int games, String character, Genome cand,
                             Map<String, Genome> champs, List<String> roster) {
        double fit = 0;
        double vp = 0;
        double wins = 0;
        double obj = 0;
        double kills = 0;
        double ars = 0;
        double units = 0;
        for (int g = 0; g < games; g++) {
            long seed = base + g;
            int seat = g % players;
            Outcome o = playOne(players, seed, seat, character, cand, champs, roster);
            fit += o.margin() + (o.win() ? 5.0 : 0.0)
                + 0.4 * (0.5 * o.objDone() + 0.4 * o.kills() + 0.3 * o.arsInstall() + 0.15 * o.units());
            vp += o.vp();
            wins += o.win() ? 1 : 0;
            obj += o.objDone();
            kills += o.kills();
            ars += o.arsInstall();
            units += o.units();
        }
        return new double[]{fit / games, vp / games, wins / games, obj / games, kills / games,
            ars / games, units / games};
    }

    static Outcome playOne(int players, long seed, int seat, String character, Genome cand,
                           Map<String, Genome> champs, List<String> roster) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        List<String> others = new ArrayList<>(roster);
        others.remove(character);
        int k = 0;
        for (int i = 0; i < players; i++) {
            Random r = new Random(seed * 31 + i);
            if (i == seat) {
                agents.add(new PlannerAgent(i, r, cand, character, 4, false, players));
            } else {
                String ch = others.get(k++ % others.size());
                agents.add(new PlannerAgent(i, r, champs.get(ch), ch, 4, false, players));
            }
        }
        int[] tally = new int[4];   // задания, убито, арсенал, войска
        new GameEngine(s, agents, ev -> {
            if (!(ev.get("seat") instanceof Number sn) || sn.intValue() != seat) {
                return;
            }
            switch (String.valueOf(ev.get("type"))) {
                case "objective" -> tally[0]++;
                case "combat_hit" -> {
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        tally[1]++;
                    }
                }
                case "arsenal" -> {
                    if ("install".equals(ev.get("mode"))) {
                        tally[2]++;
                    }
                }
                case "action" -> {
                    if ("assembly".equals(ev.get("action"))
                            && ev.get("telemetry") instanceof Map<?, ?> m
                            && m.get("units") instanceof Number n) {
                        tally[3] += n.intValue();
                    }
                }
                default -> { }
            }
        }).run();
        int my = Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < players; i++) {
            if (i != seat) {
                best = Math.max(best, Scoring.scorePlayer(s, i).getOrDefault("total", 0));
            }
        }
        boolean win = s.winner != null && s.winner == seat;
        return new Outcome(my - best, win, tally[0], tally[1], tally[2], tally[3], my);
    }
}
