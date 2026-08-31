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
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * РАЗНООБРАЗИЕ СТРАТЕГИЙ — КАК ИМЕННО побеждают, в АБСОЛЮТНЫХ числах.
 *
 * <p>Вопрос дизайнера 15.08.2026: «если четыре одинаковых бота играют друг
 * против друга, они всегда играют одинаково — это понятно. Насколько велика
 * динамика и разнообразие тактик у РАЗНЫХ характеров?»
 *
 * <p>ПЕРВАЯ ВЕРСИЯ ЭТОГО СТЕНДА показывала только ДОЛИ (0..1) — дизайнер справедливо
 * указал, что по доле нельзя понять ни абсолютный счёт, ни сами события. Здесь
 * два разных отчёта, и их нельзя путать:
 *
 * <ul>
 *   <li><b>СОБЫТИЯ ЗА ПАРТИЮ</b> — сколько реально произошло: убийств, вскрытых
 *       тайлов, шагов науки. Это факт игры, независимо от того, дают ли они
 *       очки;</li>
 *   <li><b>ОЧКИ ПО ИСТОЧНИКАМ</b> — сколько именно ПОБЕДНЫХ ОЧКОВ дал каждый
 *       канал, в очках, а не в долях. Разница между двумя таблицами и есть ответ
 *       на вопрос «событие произошло, но не окупилось».</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.StrategyDiversity [партий на характер] [игроков]}.
 */
public final class StrategyDiversity {

    private StrategyDiversity() {
    }

    /**
     * Источники очков движка (см. {@code Scoring.scorePlayer}).
     *
     * <p>{@code trophy_storage_vp} убран 18.08.2026 вместе с баг-фиксом двойного
     * учёта трофея в Scoring: это была вторая, добавочная строка поверх
     * {@code trophy}, а не отдельный источник, — движок её больше не выставляет.
     */
    private static final List<String> VP_SOURCES = List.of(
        "kelium", "coins", "debris", "buildings_on_field", "units_on_field", "tech",
        "gold_modules", "spawn_tiles", "cu_tokens", "kills", "war_track",
        "objective_card_vp", "super_arsenal", "super_first_part", "arsenal_vp",
        "level4_stars");

    /** Человеческие подписи источников очков — для таблиц. */
    private static final Map<String, String> VP_LABEL = Map.ofEntries(
        Map.entry("kelium", "келемий на складе"),
        Map.entry("coins", "монеты на руках"),
        Map.entry("debris", "трофеи и несданные трофеи"),
        Map.entry("buildings_on_field", "здания на поле"),
        Map.entry("units_on_field", "войска на поле"),
        Map.entry("tech", "шаги по науке"),
        Map.entry("gold_modules", "позолоченные модули"),
        Map.entry("spawn_tiles", "выработанные тайлы зарождения"),
        Map.entry("cu_tokens", "жетоны уничтожения ЦУ"),
        Map.entry("kills", "уничтожения (военный трек очков)"),
        Map.entry("war_track", "военный трек (доп. правило)"),
        Map.entry("objective_card_vp", "прямые очки от заданий"),
        Map.entry("super_arsenal", "супер-арсенал"),
        Map.entry("super_first_part", "1-я часть супер-задания"),
        Map.entry("arsenal_vp", "очки от карт арсенала"),
        Map.entry("level4_stars", "звёзды склада 4-го уровня"));

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int gamesPerCharacter = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> characters = Bots.CHARACTERS;

        Map<String, Integer> games = new LinkedHashMap<>();
        Map<String, Integer> wins = new LinkedHashMap<>();
        Map<String, Double> totalVp = new LinkedHashMap<>();
        Map<String, Map<String, Double>> vpBySource = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> winCondition = new TreeMap<>();
        // СОБЫТИЯ (факт игры, не очки): убийства, тайлы, шаги науки, здания, войска
        Map<String, Double> kills = new LinkedHashMap<>();
        Map<String, Double> tilesFlipped = new LinkedHashMap<>();
        Map<String, Double> techSteps = new LinkedHashMap<>();
        Map<String, Double> buildingsFinal = new LinkedHashMap<>();
        Map<String, Double> unitsFinal = new LinkedHashMap<>();
        Map<String, Double> rounds = new LinkedHashMap<>();

        for (String c : characters) {
            games.put(c, 0);
            wins.put(c, 0);
            totalVp.put(c, 0.0);
            vpBySource.put(c, new LinkedHashMap<>());
            winCondition.put(c, new TreeMap<>());
            kills.put(c, 0.0);
            tilesFlipped.put(c, 0.0);
            techSteps.put(c, 0.0);
            buildingsFinal.put(c, 0.0);
            unitsFinal.put(c, 0.0);
            rounds.put(c, 0.0);
        }

        // Стол каждую партию — разные характеры по кругу, места ротируются самим
        // сдвигом состава, чтобы посадка за стол не подгоняла один характер под
        // одних и тех же соседей.
        int roundsTotal = (gamesPerCharacter * characters.size() + players - 1) / players;
        for (int g = 0; g < roundsTotal; g++) {
            long seed = 12_300_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<String> seats = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                seats.add(characters.get((g * players + i) % characters.size()));
            }
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(seats.get(i), i, new Random(seed * 31 + i), players));
            }
            var res = GameEngine.playGame(s, agents, ev -> { });
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
            int roundCount = res.get("rounds") instanceof Number r ? r.intValue() : 0;
            String cond = String.valueOf(res.get("condition"));
            for (int i = 0; i < players; i++) {
                String c = seats.get(i);
                PlayerState p = s.player(i);
                Map<String, Integer> b = Scoring.scorePlayer(s, i);
                int total = b.getOrDefault("total", 0);

                games.merge(c, 1, Integer::sum);
                totalVp.merge(c, (double) total, Double::sum);
                rounds.merge(c, (double) roundCount, Double::sum);
                if (winner == i) {
                    wins.merge(c, 1, Integer::sum);
                    winCondition.get(c).merge(cond, 1, Integer::sum);
                }
                for (String src : VP_SOURCES) {
                    vpBySource.get(c).merge(src, (double) b.getOrDefault(src, 0), Double::sum);
                }

                // СОБЫТИЯ — считаются НАПРЯМУЮ из состояния, а не из очков: убил
                // ли бот вообще кого-то — вопрос отдельный от «дали ли за это очки».
                kills.merge(c, (double) p.killsTotal, Double::sum);
                tilesFlipped.merge(c,
                    (double) (p.flippedStartTiles + p.flippedNormalTiles), Double::sum);
                int steps = 0;
                for (int v : p.techSteps.values()) {
                    steps += v;
                }
                techSteps.merge(c, (double) steps, Double::sum);
                buildingsFinal.merge(c, (double) p.buildingsOnField().size(), Double::sum);
                unitsFinal.merge(c, (double) p.unitsOnField().size(), Double::sum);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Разнообразие стратегий — абсолютные числа\n\n");
        md.append("Игроков: ").append(players).append(", раскладки дизайнера. ")
          .append("Стол каждую партию собран из РАЗНЫХ характеров, места ротируются.\n\n");

        // ===================== ТАБЛИЦА 1: СОБЫТИЯ ЗА ПАРТИЮ =====================
        md.append("## 1. Что реально происходит за партию (события, НЕ очки)\n\n");
        md.append("| характер | партий | побед | раундов | убийств | тайлов | шагов науки"
            + " | зданий (в конце) | войск (в конце) |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        out.println("СОБЫТИЯ ЗА ПАРТИЮ (среднее на игрока):");
        out.printf("%-12s %6s %6s %8s %8s %7s %9s %7s %6s%n",
            "характер", "партий", "побед", "раундов", "убийств", "тайлов", "науки",
            "зданий", "войск");
        for (String c : characters) {
            int n = games.get(c);
            md.append(String.format(Locale.ROOT,
                "| %s | %d | %d%% | %.1f | %.2f | %.2f | %.2f | %.1f | %.1f |%n",
                c, n, 100 * wins.get(c) / Math.max(1, n), rounds.get(c) / Math.max(1, n),
                kills.get(c) / Math.max(1, n), tilesFlipped.get(c) / Math.max(1, n),
                techSteps.get(c) / Math.max(1, n), buildingsFinal.get(c) / Math.max(1, n),
                unitsFinal.get(c) / Math.max(1, n)));
            out.printf(Locale.ROOT, "%-12s %6d %5d%% %8.1f %8.2f %7.2f %9.2f %7.1f %6.1f%n",
                c, n, 100 * wins.get(c) / Math.max(1, n), rounds.get(c) / Math.max(1, n),
                kills.get(c) / Math.max(1, n), tilesFlipped.get(c) / Math.max(1, n),
                techSteps.get(c) / Math.max(1, n), buildingsFinal.get(c) / Math.max(1, n),
                unitsFinal.get(c) / Math.max(1, n));
        }

        // =============== ТАБЛИЦА 2: ОЧКИ ПО ИСТОЧНИКАМ, В ОЧКАХ ===============
        md.append("\n## 2. Откуда берутся ПОБЕДНЫЕ ОЧКИ — в очках, не в долях\n\n");
        md.append("Столбец «итого» — средний счёт за партию; остальные столбцы — "
            + "сколько ОЧКОВ (не процентов) дал каждый источник. Источники с нулём у "
            + "ВСЕХ характеров показаны один раз внизу отдельно — это либо выключенное "
            + "правило, либо канал, который ни разу не сработал за все партии.\n\n");

        // Найти источники, у которых хоть у кого-то не ноль
        List<String> activeSources = new ArrayList<>();
        List<String> deadSources = new ArrayList<>();
        for (String src : VP_SOURCES) {
            double max = 0;
            for (String c : characters) {
                max = Math.max(max, vpBySource.get(c).getOrDefault(src, 0.0) / Math.max(1, games.get(c)));
            }
            if (max >= 0.02) {
                activeSources.add(src);
            } else {
                deadSources.add(src);
            }
        }

        md.append("| характер | итого ПО |");
        for (String src : activeSources) {
            md.append(' ').append(VP_LABEL.getOrDefault(src, src)).append(" |");
        }
        md.append("\n|---|---:|");
        for (String src : activeSources) {
            md.append("---:|");
        }
        md.append('\n');

        out.println("\nОЧКИ ПО ИСТОЧНИКАМ (среднее на игрока, В ОЧКАХ):");
        out.printf("%-12s %8s", "характер", "итого");
        for (String src : activeSources) {
            out.printf(" %10s", src);
        }
        out.println();
        for (String c : characters) {
            int n = Math.max(1, games.get(c));
            md.append(String.format(Locale.ROOT, "| %s | %.1f |", c, totalVp.get(c) / n));
            out.printf(Locale.ROOT, "%-12s %8.1f", c, totalVp.get(c) / n);
            for (String src : activeSources) {
                double v = vpBySource.get(c).getOrDefault(src, 0.0) / n;
                md.append(String.format(Locale.ROOT, " %.2f |", v));
                out.printf(Locale.ROOT, " %10.2f", v);
            }
            md.append('\n');
            out.println();
        }

        md.append("\n**Источники очков, которые НЕ СРАБОТАЛИ ни у одного характера ")
          .append("(в среднем меньше 0.02 очка за партию):** ");
        md.append(String.join(", ", deadSources.stream()
            .map(s -> VP_LABEL.getOrDefault(s, s)).toList())).append(".\n");
        out.println("\nисточники очков, которые не сработали ни у кого: "
            + String.join(", ", deadSources));

        // ===================== ТАБЛИЦА 3: ЧЕМ КОНЧАЮТ ПОБЕДУ =====================
        md.append("\n## 3. Чем именно заканчивают победу (число партий, не доля)\n\n");
        md.append("| характер | по тайлу зарождения | по очкам | по супер-заданию |"
            + " по уничтожению ЦУ |\n|---|---:|---:|---:|---:|\n");
        out.println("\nЧЕМ КОНЧАЮТ ПОБЕДУ (число партий):");
        for (String c : characters) {
            var cond = winCondition.get(c);
            int byTile = cond.getOrDefault("last_spawn_tile", 0);
            int byVp = cond.getOrDefault("victory_points", 0);
            int bySuper = cond.getOrDefault("super_objective", 0);
            int byCu = cond.getOrDefault("cu_destroyed", 0) + cond.getOrDefault("military", 0);
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d |%n",
                c, byTile, byVp, bySuper, byCu));
            out.printf("%-12s тайл=%-4d очки=%-4d супер-задание=%-3d ЦУ=%-3d%n",
                c, byTile, byVp, bySuper, byCu);
        }

        Path p = Path.of("reports", "balance", "разнообразие-стратегий.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("\nотчёт: " + p.toAbsolutePath());
    }
}
