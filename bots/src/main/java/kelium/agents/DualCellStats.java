package kelium.agents;

import java.io.BufferedWriter;
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
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ШИРОКАЯ СТАТИСТИКА ПО «БОЮ 2.0» (заказ дизайнера 18.08.2026) — прогоняет
 * партии на ruleset 1.15.0 обученным на нём составом ботов и считает то, что
 * нужно, чтобы ответить на «Открытые вопросы» черновика («бой-планшеты-
 * переработка (2).md»): реально ли используется универсальная ячейка, не
 * стала ли специализированная бесполезной (или наоборот — не сделала ли
 * универсальную мёртвой веткой), не выделяется ли какая-то из пяти сторон
 * планшета войск по силе, сколько партий вообще доходит до боя.
 *
 * <p>Строка атакующего в событии {@code combat_hit} — {@code
 * "<род_войск>.<строка>"} (см. {@code CombatResolver.emit(..., "attacker",
 * unit.type.code + "." + row, ...)}), поэтому разбор без изменений в движке:
 * "universal" / "specialized" / "specialized_gold_a" / "specialized_gold_b".
 */
public final class DualCellStats {

    private DualCellStats() {
    }

    private static final class SideTally {
        int games;
        int wins;
        long vpSum;
    }

    public static void main(String[] args) throws IOException {
        int games = 2000;
        long seed = 1;
        Path out = Path.of("reports", "balance", "бой-2.0-статистика.md");
        String botMemory = "data/genomes-boi2";

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--games" -> games = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--botmemory" -> botMemory = args[++i];
                default -> { }
            }
        }

        LayoutLibrary.setRulesetOverride("1.15.0");
        // System-свойство, а НЕ Locations.setBotMemory(...): тот метод пишет в
        // постоянные Preferences (реестр Windows) и переживает выход из
        // процесса — баг-фикс 18.08.2026, найден по испорченному замеру
        // BaselineCheck (тот незаметно унаследовал папку genomes-boi2 от
        // более раннего запуска этого инструмента). Системное свойство живёт
        // только текущий процесс.
        System.setProperty("kelium.botmemory", botMemory);

        List<String> pool = new ArrayList<>();
        for (BotCatalog.Entry e : BotCatalog.ALL) {
            if (!"random".equals(e.id())) {
                pool.add(e.id());
            }
        }

        Map<String, Long> rowCounts = new TreeMap<>();
        Map<String, Long> unitRowCounts = new TreeMap<>();
        Map<String, SideTally> bySide = new TreeMap<>();
        Map<String, Long> vpSourceSums = new TreeMap<>();
        long totalHits = 0;
        long totalRounds = 0;
        long totalGames = 0;
        long gamesWithAnyHit = 0;
        long towerFreeHits = 0;
        long totalSeats = 0;

        for (int g = 0; g < games; g++) {
            long gameSeed = seed * 1_000_003L + g;
            int players = (g % 2 == 0) ? 3 : 4;
            Random pickRng = new Random(gameSeed);

            GameConfig cfg = LayoutLibrary.configFor(players, gameSeed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                String spec = pool.get(pickRng.nextInt(pool.size()));
                agents.add(BotCatalog.create(spec, seat, new Random(gameSeed * 131 + seat * 17 + 3), players));
            }

            long[] hitsThisGame = {0};
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
                if (!"combat_hit".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                hitsThisGame[0]++;
                String attacker = String.valueOf(ev.get("attacker"));
                int dot = attacker.indexOf('.');
                if (dot < 0) {
                    return;
                }
                String unitType = attacker.substring(0, dot);
                String row = attacker.substring(dot + 1);
                rowCounts.merge(row, 1L, Long::sum);
                unitRowCounts.merge(unitType + "." + row, 1L, Long::sum);
            });
            totalHits += hitsThisGame[0];
            if (hitsThisGame[0] > 0) {
                gamesWithAnyHit++;
            }
            totalRounds += s.round;
            totalGames++;

            Integer winner = res.get("winner") instanceof Number n ? n.intValue() : null;
            for (int seat = 0; seat < players; seat++) {
                String side = s.player(seat).board.troop.side;
                Map<String, Integer> breakdown = Scoring.scorePlayer(s, seat);
                int vp = breakdown.getOrDefault("total", 0);
                SideTally t = bySide.computeIfAbsent(side, k -> new SideTally());
                t.games++;
                t.vpSum += vp;
                if (winner != null && winner == seat) {
                    t.wins++;
                }
                for (var src : breakdown.entrySet()) {
                    if (!"total".equals(src.getKey())) {
                        vpSourceSums.merge(src.getKey(), (long) src.getValue(), Long::sum);
                    }
                }
                totalSeats++;
            }

            if ((g + 1) % 200 == 0) {
                System.out.println("партий: " + (g + 1) + "/" + games);
            }
        }

        towerFreeHits = unitRowCounts.entrySet().stream()
            .filter(e -> e.getKey().startsWith("tower.specialized"))
            .mapToLong(Map.Entry::getValue).sum();

        StringBuilder md = new StringBuilder();
        md.append("# Бой 2.0 — статистика (ruleset 1.15.0)\n\n");
        md.append("Черновой вариант («бой-планшеты-переработка (2).md»), обученный состав в `")
            .append(botMemory).append("`.\n\n");
        md.append(String.format(Locale.ROOT,
            "Партий сыграно: **%d** (доля с хотя бы одним боем: %.1f%%). Средняя длина партии: %.2f раунда.%n%n",
            totalGames, 100.0 * gamesWithAnyHit / Math.max(1, totalGames),
            (double) totalRounds / Math.max(1, totalGames)));

        md.append("## Использование ячеек атаки\n\n");
        md.append("Всего попаданий (combat_hit) за все партии: **").append(totalHits).append("**.\n\n");
        md.append("| Строка атаки | Попаданий | Доля |\n|---|---|---|\n");
        for (var e : rowCounts.entrySet()) {
            md.append(String.format(Locale.ROOT, "| %s | %d | %.1f%% |%n",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / Math.max(1, totalHits)));
        }
        md.append(String.format(Locale.ROOT,
            "%nИз них бесплатных ударов вышки специализированной ячейкой: **%d**.%n%n", towerFreeHits));

        md.append("## Разбивка по роду войск и строке (кто чем бьёт)\n\n");
        md.append("| Род.строка | Попаданий |\n|---|---|\n");
        for (var e : unitRowCounts.entrySet()) {
            md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }

        md.append("\n## Источники победных очков (в среднем на место за партию)\n\n");
        md.append("| Источник | Средние ПО |\n|---|---|\n");
        for (var e : vpSourceSums.entrySet()) {
            md.append(String.format(Locale.ROOT, "| %s | %.2f |%n",
                e.getKey(), (double) e.getValue() / Math.max(1, totalSeats)));
        }

        md.append("\n## По стороне планшета войск\n\n");
        md.append("| Сторона | Партий (мест) | Побед | Доля побед | Средние ПО |\n|---|---|---|---|---|\n");
        for (var e : bySide.entrySet()) {
            SideTally t = e.getValue();
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f%% | %.2f |%n",
                e.getKey(), t.games, t.wins, 100.0 * t.wins / Math.max(1, t.games),
                (double) t.vpSum / Math.max(1, t.games)));
        }

        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(md.toString());
        }
        System.out.println("готово: " + totalGames + " партий -> " + out.toAbsolutePath());
    }
}
