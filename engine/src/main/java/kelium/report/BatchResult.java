package kelium.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Агрегированные итоги пакета партий — сигналы для оценки баланса.
 *
 * <p>Порт из forge/report/batch.py (BatchResult + render_markdown). Собирает:
 * долю побед по МЕСТУ (справедливость порядка хода) и по СТОРОНЕ планшета
 * (справедливость асимметрии), средние трофейные очки по местам, средний отрыв,
 * вклад ИСТОЧНИКОВ очков, распределение условий конца, использование действий
 * (успешных и заблокированных).
 *
 * <p>{@link #renderMarkdown()} печатает Markdown-отчёт для дизайнера/LLM,
 * повторяя формат Python render_markdown.
 */
public final class BatchResult {

    public final String rulesetId;
    public final int numPlayers;
    public final int numGames;
    public final Map<Integer, Integer> seatWins = new HashMap<>();
    public final Map<String, Integer> sideWins = new HashMap<>();
    public final Map<String, Integer> conditionCounts = new HashMap<>();
    public final List<Integer> margins = new ArrayList<>();
    public final Map<Integer, Double> avgVpBySeat = new HashMap<>();
    public final Map<String, Integer> vpSourceTotals = new HashMap<>();
    public final Map<String, Integer> actionTotals = new HashMap<>();
    public final Map<String, Integer> failedActionTotals = new HashMap<>();

    public BatchResult(String rulesetId, int numPlayers, int numGames) {
        this.rulesetId = rulesetId;
        this.numPlayers = numPlayers;
        this.numGames = numGames;
    }

    /** Доля побед по каждому месту за столом. */
    public Map<Integer, Double> winRateBySeat() {
        Map<Integer, Double> out = new HashMap<>();
        for (var e : seatWins.entrySet()) {
            out.put(e.getKey(), e.getValue() / (double) numGames);
        }
        return out;
    }

    /** Доля побед по каждой стороне планшета. */
    public Map<String, Double> winRateBySide() {
        int total = 0;
        for (int w : sideWins.values()) {
            total += w;
        }
        if (total == 0) {
            total = 1;
        }
        Map<String, Double> out = new HashMap<>();
        for (var e : sideWins.entrySet()) {
            out.put(e.getKey(), e.getValue() / (double) total);
        }
        return out;
    }

    private static double mean(List<Integer> xs) {
        if (xs.isEmpty()) {
            return 0.0;
        }
        long s = 0;
        for (int x : xs) {
            s += x;
        }
        return s / (double) xs.size();
    }

    private static double median(List<Integer> xs) {
        if (xs.isEmpty()) {
            return 0.0;
        }
        List<Integer> sorted = new ArrayList<>(xs);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static String bar(double frac) {
        int n = (int) Math.round(frac * 20);
        return "█".repeat(Math.max(0, n));
    }

    // Точки-разделители дробей, как в Python (не зависят от системной локали).
    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.ROOT, pattern, args);
    }

    /** Отрендерить итоги в Markdown-отчёт для человека/LLM (формат Python). */
    public String renderMarkdown() {
        List<String> lines = new ArrayList<>();
        lines.add("# Batch report — " + rulesetId);
        lines.add("");
        lines.add("- players: **" + numPlayers + "**, games: **" + numGames + "**");
        if (!margins.isEmpty()) {
            int max = margins.stream().mapToInt(Integer::intValue).max().orElse(0);
            lines.add(fmt("- avg margin (winner − runner-up): **%.2f** (median %.1f, max %d)",
                mean(margins), median(margins), max));
        } else {
            lines.add("- no games");
        }
        lines.add("");
        lines.add("## Win rate by seat (turn-order fairness)");
        Map<Integer, Double> wrSeat = new TreeMap<>(winRateBySeat());
        for (var e : wrSeat.entrySet()) {
            lines.add(fmt("- seat %d: %5.1f%%  %s", e.getKey(), e.getValue() * 100, bar(e.getValue())));
        }

        lines.add("");
        lines.add("## Win rate by board side (asymmetry fairness)");
        List<Map.Entry<String, Double>> side = new ArrayList<>(winRateBySide().entrySet());
        side.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (var e : side) {
            lines.add(fmt("- %s: %5.1f%%  %s", e.getKey(), e.getValue() * 100, bar(e.getValue())));
        }

        lines.add("");
        lines.add("## Average VP by seat");
        Map<Integer, Double> avg = new TreeMap<>(avgVpBySeat);
        for (var e : avg.entrySet()) {
            lines.add(fmt("- seat %d: %.2f VP", e.getKey(), e.getValue()));
        }

        lines.add("");
        lines.add("## VP source contribution (all players, all games)");
        int tot = 0;
        for (int v : vpSourceTotals.values()) {
            tot += v;
        }
        if (tot == 0) {
            tot = 1;
        }
        List<Map.Entry<String, Integer>> srcs = new ArrayList<>(vpSourceTotals.entrySet());
        srcs.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (var e : srcs) {
            lines.add(fmt("- %-20s %6d  (%4.1f%%)", e.getKey(), e.getValue(), e.getValue() * 100.0 / tot));
        }

        lines.add("");
        lines.add("## End-condition distribution");
        List<Map.Entry<String, Integer>> conds = new ArrayList<>(conditionCounts.entrySet());
        conds.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (var e : conds) {
            lines.add(fmt("- %s: %d (%.1f%%)", e.getKey(), e.getValue(), e.getValue() * 100.0 / numGames));
        }

        lines.add("");
        lines.add("## Action usage (successful plays)");
        List<Map.Entry<String, Integer>> acts = new ArrayList<>(actionTotals.entrySet());
        acts.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (var e : acts) {
            lines.add(fmt("- %-14s %d", e.getKey(), e.getValue()));
        }
        if (!failedActionTotals.isEmpty()) {
            lines.add("");
            lines.add("### Failed/blocked action attempts");
            List<Map.Entry<String, Integer>> fails = new ArrayList<>(failedActionTotals.entrySet());
            fails.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (var e : fails) {
                lines.add(fmt("- %-14s %d", e.getKey(), e.getValue()));
            }
        }

        String[] allActions = {"assembly", "mining", "build", "energy_swap",
            "movement", "combat", "market", "science"};
        List<String> dead = new ArrayList<>();
        for (String n : allActions) {
            if (!actionTotals.containsKey(n)) {
                dead.add(n);
            }
        }
        if (!dead.isEmpty()) {
            lines.add("");
            lines.add("> ⚠ actions never successfully played: " + String.join(", ", dead));
        }

        return String.join("\n", lines);
    }
}
