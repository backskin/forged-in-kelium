package kelium;

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

import kelium.agents.Arena;
import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.LayoutLibrary;

/**
 * CuVictoryProbe — БЫВАЕТ ЛИ ВООБЩЕ военная победа (второе уничтожение ЦУ)?
 *
 * <p>Вопрос дизайнера, и отвечать на него по общей табличке нельзя: там условия
 * окончания были свалены в грубые ведра «по очкам / военная / супер». Здесь всё
 * пересчитано прицельно и по отдельности:
 * <ul>
 *   <li>сколько партий кончилось КАЖДЫМ условием (полная разбивка, без ведер);</li>
 *   <li>сколько ЦУ вообще сносят за партию и в каком раунде это происходит;</li>
 *   <li>сколько игроков доходит до ОДНОГО чужого жетона ЦУ и сколько — до ВТОРОГО
 *       сноса, который и даёт мгновенную победу;</li>
 *   <li>меняется ли это, если за столом сидят сильные (просчитывающие) боты, — то
 *       есть «военной победы не бывает» или «боты её не находят».</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.CuVictoryProbe [игроков] [партий]}.
 */
public final class CuVictoryProbe {

    private CuVictoryProbe() {
    }

    /** Итог по одному составу стола. */
    private static final class Tally {
        int games;
        final Map<String, Integer> byCondition = new LinkedHashMap<>();
        int cuDestroyed;            // всего сносов ЦУ
        int gamesWithCuKill;        // партий, где ЦУ снесли хоть раз
        int gamesWithTwoByOne;      // партий, где ОДИН игрок снёс два ЦУ
        int playersWithToken;       // игроков, добывших хоть один жетон ЦУ
        int militaryWins;
        int firstCuRoundSum;        // сумма раундов первого сноса (для среднего)
        int firstCuGames;
        int militaryRoundSum;
        int cuRebuilt;              // сколько раз снесённое ЦУ вернулось на поле
    }

    private static synchronized void merge(Tally into, Tally one) {
        into.games += one.games;
        one.byCondition.forEach((k, v) -> into.byCondition.merge(k, v, Integer::sum));
        into.cuDestroyed += one.cuDestroyed;
        into.gamesWithCuKill += one.gamesWithCuKill;
        into.gamesWithTwoByOne += one.gamesWithTwoByOne;
        into.playersWithToken += one.playersWithToken;
        into.militaryWins += one.militaryWins;
        into.firstCuRoundSum += one.firstCuRoundSum;
        into.firstCuGames += one.firstCuGames;
        into.militaryRoundSum += one.militaryRoundSum;
        into.cuRebuilt += one.cuRebuilt;
    }

    private static Tally playOne(int players, long seed, String mode) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        String[] mixed = {"hawk", "dove", "balanced", "opportunist"};
        int shift = (int) (seed % players);
        for (int i = 0; i < players; i++) {
            Random rng = new Random(seed * 31 + i);
            switch (mode) {
                case "все ястребы" -> agents.add(Bots.create("hawk", i, rng, players));
                case "просчёт вперёд" ->
                    agents.add(Arena.make("search:hawk", i, rng, players));
                default -> agents.add(
                    Bots.create(mixed[(i + shift) % mixed.length], i, rng, players));
            }
        }

        Tally t = new Tally();
        int[] cuKillRound = {0};
        Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
            // Снос ЦУ виден по событию боя: цель — командный центр.
            if ("combat_hit".equals(String.valueOf(ev.get("type")))
                    && Boolean.TRUE.equals(ev.get("destroyed"))
                    && String.valueOf(ev.get("victim")).contains("command_center")) {
                t.cuDestroyed++;
                if (cuKillRound[0] == 0) {
                    cuKillRound[0] = s.round;
                }
            }
        });

        t.games = 1;
        String cond = String.valueOf(res.get("condition"));
        t.byCondition.merge(cond, 1, Integer::sum);
        if ("military".equals(cond)) {
            t.militaryWins = 1;
            t.militaryRoundSum = s.round;
        }
        // Сколько ЦУ снесено — считаем по жетонам разрушения у игроков: это
        // надёжнее события, потому что жетон и есть след сноса.
        int tokensHeld = 0;
        int maxByOne = 0;
        for (int i = 0; i < players; i++) {
            int held = s.player(i).cuDestructionTokens;
            if (held > 0) {
                t.playersWithToken++;
            }
            tokensHeld += held;
            maxByOne = Math.max(maxByOne, s.player(i).cuKills);
        }
        if (tokensHeld > 0 || t.cuDestroyed > 0) {
            t.gamesWithCuKill = 1;
        }
        if (maxByOne >= 2) {
            t.gamesWithTwoByOne = 1;
        }
        if (cuKillRound[0] > 0) {
            t.firstCuRoundSum = cuKillRound[0];
            t.firstCuGames = 1;
        }
        // Отстроилось ли снесённое ЦУ заново (владелец забирает его в запас).
        for (int i = 0; i < players; i++) {
            if (!s.player(i).ownCuTokenAvailable && s.player(i).hasCommandCenter()) {
                t.cuRebuilt++;
            }
        }
        return t;
    }

    private static Tally run(int players, int games, String mode) {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Tally total = new Tally();
        List<Future<Tally>> futures = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            final long seed = 7_700_000L + g;
            futures.add(pool.submit((Callable<Tally>) () -> playOne(players, seed, mode)));
        }
        for (Future<Tally> f : futures) {
            try {
                merge(total, f.get());
            } catch (Exception e) {
                System.err.println("партия сорвалась: " + e.getMessage());
            }
        }
        pool.shutdown();
        return total;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;

        List<String> modes = List.of("вперемешку", "все ястребы", "просчёт вперёд");
        Map<String, Tally> results = new LinkedHashMap<>();
        for (String mode : modes) {
            long t0 = System.nanoTime();
            Tally t = run(players, games, mode);
            results.put(mode, t);
            System.out.printf(Locale.ROOT,
                "  %-16s военных побед %d/%d (%.1f%%), сносов ЦУ %.2f за партию  (%.0f с)%n",
                mode, t.militaryWins, t.games, 100.0 * t.militaryWins / Math.max(1, t.games),
                t.cuDestroyed / (double) Math.max(1, t.games),
                (System.nanoTime() - t0) / 1e9);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Бывает ли военная победа (два уничтожения ЦУ)?\n\n");
        sb.append("По ").append(games).append(" партий на состав, ").append(players)
          .append(" игрока. Правило: за снос чужого ЦУ атакующий забирает ЖЕТОН ")
          .append("РАЗРУШЕНИЯ владельца; если такой жетон у него УЖЕ был — партия ")
          .append("немедленно заканчивается его победой. То есть нужно снести ")
          .append("**два ЦУ разных игроков**.\n\n");
        sb.append("| состав стола | военных побед | партий со сносом ЦУ | сносов ЦУ за партию "
            + "| партий, где один снёс два | первый снос, раунд | военная победа, раунд |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (String mode : modes) {
            Tally t = results.get(mode);
            int g = Math.max(1, t.games);
            sb.append(String.format(Locale.ROOT,
                "| %s | **%.1f%%** | %.0f%% | %.2f | %.1f%% | %.1f | %s |%n",
                mode, 100.0 * t.militaryWins / g, 100.0 * t.gamesWithCuKill / g,
                t.cuDestroyed / (double) g, 100.0 * t.gamesWithTwoByOne / g,
                t.firstCuGames == 0 ? 0 : t.firstCuRoundSum / (double) t.firstCuGames,
                t.militaryWins == 0 ? "—"
                    : String.format(Locale.ROOT, "%.1f",
                        t.militaryRoundSum / (double) t.militaryWins)));
        }

        sb.append("\n## Чем кончаются партии — полная разбивка\n\n");
        sb.append("| состав стола |");
        List<String> conds = new ArrayList<>();
        for (Tally t : results.values()) {
            for (String c : t.byCondition.keySet()) {
                if (!conds.contains(c)) {
                    conds.add(c);
                }
            }
        }
        for (String c : conds) {
            sb.append(' ').append(condRu(c)).append(" |");
        }
        sb.append('\n').append("|---|");
        for (int i = 0; i < conds.size(); i++) {
            sb.append("---:|");
        }
        sb.append('\n');
        for (String mode : modes) {
            Tally t = results.get(mode);
            int g = Math.max(1, t.games);
            sb.append("| ").append(mode).append(" |");
            for (String c : conds) {
                sb.append(String.format(Locale.ROOT, " %.0f%% |",
                    100.0 * t.byCondition.getOrDefault(c, 0) / g));
            }
            sb.append('\n');
        }

        Tally mix = results.get("вперемешку");
        Tally deep = results.get("просчёт вперёд");
        sb.append("\n## Ответ\n\n");
        sb.append(String.format(Locale.ROOT,
            "- Военной победой кончается **%.1f%%** партий при обычном составе и "
            + "**%.1f%%** при столе из просчитывающих ботов. ",
            100.0 * mix.militaryWins / Math.max(1, mix.games),
            100.0 * deep.militaryWins / Math.max(1, deep.games)));
        sb.append(mix.militaryWins == 0
            ? "То есть в текущих правилах она НЕ СРАБАТЫВАЕТ ни разу.\n"
            : "То есть она реально бывает, а не только описана в правилах.\n");
        sb.append(String.format(Locale.ROOT,
            "- ЦУ сносят в %.0f%% партий, в среднем %.2f сноса за партию, первый — "
            + "около %.1f раунда.%n",
            100.0 * mix.gamesWithCuKill / Math.max(1, mix.games),
            mix.cuDestroyed / (double) Math.max(1, mix.games),
            mix.firstCuGames == 0 ? 0 : mix.firstCuRoundSum / (double) mix.firstCuGames));
        sb.append(String.format(Locale.ROOT,
            "- Довести до ДВУХ сносов одному игроку удаётся в %.1f%% партий.%n",
            100.0 * mix.gamesWithTwoByOne / Math.max(1, mix.games)));

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "военная-победа-" + players + "p.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String condRu(String cond) {
        return switch (cond) {
            case "military" -> "военная (2 ЦУ)";
            case "victory_points" -> "по очкам";
            case "super_objective" -> "супер-задание";
            case "all_peaks_occupied" -> "все вершины треков";
            case "last_spawn_tile" -> "последний тайл";
            default -> cond;
        };
    }
}
