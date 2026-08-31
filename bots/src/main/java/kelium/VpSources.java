package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ОТКУДА В ИГРЕ БЕРУТСЯ ПОБЕДНЫЕ ОЧКИ — по итогам настоящих партий.
 *
 * <p>Заказ дизайнера 15.08.2026. Разбор счёта в конце партии уже существует
 * ({@link Scoring#scorePlayer} возвращает разбивку по источникам), но никто
 * никогда не смотрел на него СРЕДНИМ по многим партиям. А это главный вопрос
 * баланса: если девять источников из четырнадцати дают ноль, значит игра на
 * самом деле про два-три из них, а остальные — украшение.
 *
 * <p>Считается ещё и ДОЛЯ ПАРТИЙ, где источник дал хоть что-то: источник,
 * который в среднем даёт 0.5 очка, устроен совсем по-разному, если он приносит
 * пол-очка всем или пять очков одному из десяти.
 *
 * <p>Запуск: {@code kelium.VpSources [партий] [игроков]}.
 */
public final class VpSources {

    private VpSources() {
    }

    /** Человеческие имена источников — те же, что в разборе счёта. */
    private static String ru(String key) {
        return switch (key) {
            case "coins" -> "монеты в казне";
            case "kelium" -> "келемий на складе";
            case "debris" -> "трофеи и несданные трофеи";
            case "buildings_on_field" -> "здания на поле";
            case "units_on_field" -> "войска на поле";
            case "tech" -> "шаги по трекам науки";
            case "gold_modules" -> "позолоченные модули";
            case "spawn_tiles" -> "перевёрнутые тайлы зарождения";
            case "cu_tokens" -> "жетоны уничтожения ЦУ";
            case "kills" -> "уничтожения (если включено правилом)";
            case "war_track" -> "военный трек";
            case "objective_card_vp" -> "очки, напечатанные на заданиях";
            case "arsenal_vp" -> "очки от карт арсенала";
            case "super_arsenal" -> "супер-арсенал";
            case "super_first_part" -> "первая часть супер-задания";
            case "level4_stars" -> "звёзды зданий 4-го уровня";
            default -> key;
        };
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "balanced", "axiom", "dove");

        Map<String, Double> sum = new TreeMap<>();
        Map<String, Integer> nonZero = new TreeMap<>();
        double totalSum = 0;
        // Отдельно — счёт ПОБЕДИТЕЛЯ: игра ведь про то, чем выигрывают, а не про
        // то, чем набирают все подряд.
        Map<String, Double> winnerSum = new TreeMap<>();
        double winnerTotal = 0;
        int measured = 0;

        for (int g = 0; g < games; g++) {
            long seed = 10_100_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> { });
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
            for (int i = 0; i < players; i++) {
                Map<String, Integer> br = Scoring.scorePlayer(s, i);
                for (var e : br.entrySet()) {
                    if ("total".equals(e.getKey())) {
                        continue;
                    }
                    sum.merge(e.getKey(), (double) e.getValue(), Double::sum);
                    if (e.getValue() > 0) {
                        nonZero.merge(e.getKey(), 1, Integer::sum);
                    }
                    if (i == winner) {
                        winnerSum.merge(e.getKey(), (double) e.getValue(), Double::sum);
                    }
                }
                totalSum += br.getOrDefault("total", 0);
                measured++;
                if (i == winner) {
                    winnerTotal += br.getOrDefault("total", 0);
                    // счётчик побед считаем один раз
                }
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Откуда берутся победные очки — по партиям\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты ").append(Bots.describe()).append(".\n\n");
        md.append("| источник | очков на игрока | доля счёта | в скольких партиях сработал | у победителя |\n");
        md.append("|---|---:|---:|---:|---:|\n");
        out.printf("%-40s %9s %8s %12s %10s%n",
            "источник", "на игрока", "доля", "срабатывал", "победитель");

        final int seats = measured;
        final double avgTotal = totalSum / seats;
        sum.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .forEach(e -> {
                double per = e.getValue() / seats;
                double share = avgTotal <= 0 ? 0 : 100 * per / avgTotal;
                double hit = 100.0 * nonZero.getOrDefault(e.getKey(), 0) / seats;
                double win = winnerSum.getOrDefault(e.getKey(), 0.0) / Math.max(1, games);
                md.append(String.format(Locale.ROOT,
                    "| %s | %.2f | %.0f%% | %.0f%% | %.2f |%n",
                    ru(e.getKey()), per, share, hit, win));
                out.printf(Locale.ROOT, "%-40s %9.2f %7.0f%% %11.0f%% %10.2f%n",
                    ru(e.getKey()), per, share, hit, win);
            });
        md.append(String.format(Locale.ROOT, "%n**Всего очков на игрока: %.1f. "
            + "У победителя: %.1f.**%n", avgTotal, winnerTotal / games));
        out.printf(Locale.ROOT, "%nвсего на игрока %.1f, у победителя %.1f%n",
            avgTotal, winnerTotal / games);

        Path p = Path.of("reports", "balance", "источники-очков.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("отчёт: " + p.toAbsolutePath());
    }
}
