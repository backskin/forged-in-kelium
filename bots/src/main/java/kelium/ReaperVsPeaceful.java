package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.SearchAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЖНЕЦ ПРОТИВ ИССЛЕДОВАТЕЛЯ И ГОЛУБЯ — прямая проверка по требованию дизайнера
 * 15.08.2026: «сто партий, если жнец не сносит хотя бы 8 жетонов за партию,
 * значит боты не умеют строить план на 3–4 хода вперёд».
 *
 * <p>Играют С ГЛУБОКИМ ПРОСЧЁТОМ ({@link SearchAgent#deep}), не обычным
 * {@code StrategicAgent} — только просчёт вперёд вообще спрашивает оценку
 * позиции и доигрывает варианты на несколько шагов. Без него сравнивать
 * «умеет ли бот планировать наперёд» просто нечем: обычный бот не планирует
 * вообще, он считает формулу один раз за ход.
 *
 * <p>Место жнеца за столом РОТИРУЕТСЯ, чтобы преимущество первого хода не
 * исказило результат.
 *
 * <p>Запуск: {@code kelium.ReaperVsPeaceful [партий] [игроков]}.
 */
public final class ReaperVsPeaceful {

    private ReaperVsPeaceful() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        // Третий аргумент — свод правил: контрольные опыты гоняют ТОТ ЖЕ бенчмарк
        // на вариантах правил (напр. 1.8.0-war12), меняя ровно одну переменную.
        String ruleset = args.length > 2 ? args[2]
            : kelium.dataio.GameConfig.DEFAULT_RULESET;

        double totalKills = 0;
        double totalHits = 0;
        double totalLosses = 0;
        double totalVp = 0;
        double rounds = 0;
        int wins = 0;
        int[] histogram = new int[20];   // сколько партий с уничтожениями = i

        for (int g = 0; g < games; g++) {
            long seed = 20_000_000L + g;
            kelium.dataio.GameConfig base = kelium.dataio.GameConfig.build(
                ruleset, players, seed, null, null);
            GameState s = Setup.buildGame(LayoutLibrary.configFor(base, players, seed));
            int reaperSeat = g % players;
            List<String> seats = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                if (i == reaperSeat) {
                    seats.add("reaper");
                } else {
                    // Половина оставшихся мест — исследователь, половина — голубь.
                    seats.add(((i + g) % 2 == 0) ? "explorer" : "dove");
                }
            }
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(SearchAgent.deep(i, new Random(seed * 31 + i), Bots.genome(
                    seats.get(i), players), seats.get(i)));
            }
            int[] kills = new int[players];
            int[] hits = new int[players];
            int[] losses = new int[players];
            var res = GameEngine.playGame(s, agents, ev -> {
                if (!"combat_hit".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                if (ev.get("seat") instanceof Number a && a.intValue() >= 0
                        && a.intValue() < players) {
                    hits[a.intValue()]++;
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        kills[a.intValue()]++;
                    }
                }
                if (Boolean.TRUE.equals(ev.get("destroyed"))
                        && ev.get("victim_owner") instanceof Number v
                        && v.intValue() >= 0 && v.intValue() < players) {
                    losses[v.intValue()]++;
                }
            });
            int roundCount = res.get("rounds") instanceof Number r ? r.intValue() : 0;
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;

            totalKills += kills[reaperSeat];
            totalHits += hits[reaperSeat];
            totalLosses += losses[reaperSeat];
            totalVp += Scoring.scorePlayer(s, reaperSeat).getOrDefault("total", 0);
            rounds += roundCount;
            if (winner == reaperSeat) {
                wins++;
            }
            histogram[Math.min(19, kills[reaperSeat])]++;

            out.printf(Locale.ROOT, "партия %3d (сид %d): жнец на месте %d — снёс %d, "
                + "попал %d, потерял %d, раундов %d%s%n",
                g + 1, seed, reaperSeat, kills[reaperSeat], hits[reaperSeat],
                losses[reaperSeat], roundCount, winner == reaperSeat ? " — ПОБЕДА" : "");
        }

        out.println("\n=== ИТОГ: " + games + " партий, жнец против исследователя и голубя, "
            + "просчёт вперёд (SearchAgent.deep) ===");
        out.printf(Locale.ROOT, "среднее уничтожений за партию: %.2f%n", totalKills / games);
        out.printf(Locale.ROOT, "среднее попаданий за партию:   %.2f%n", totalHits / games);
        out.printf(Locale.ROOT, "среднее потерь за партию:      %.2f%n", totalLosses / games);
        out.printf(Locale.ROOT, "среднее ПО за партию:          %.2f%n", totalVp / games);
        out.printf(Locale.ROOT, "среднее раундов:               %.2f%n", rounds / games);
        out.printf(Locale.ROOT, "побед:                         %d%%%n", 100 * wins / games);
        out.println("\nраспределение по числу уничтожений за партию:");
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0) {
                out.printf("  %2d уничтожений: %3d партий (%d%%)%n", i, histogram[i],
                    100 * histogram[i] / games);
            }
        }
    }
}
