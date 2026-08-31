package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * СУПЕР-ЗАДАНИЯ 5.0 — ЗАМЕР, КОТОРЫЙ ЗАКАЗАН ЧЕРНОВИКОМ.
 *
 * <p>Черновик 25.08.2026 сам называет четыре вопроса «проверить замером до
 * печати», и здесь считаются все четыре:
 * <ol>
 *   <li>доля СОЖЖЁННЫХ против СОХРАНЁННЫХ — цель примерно поровну;</li>
 *   <li>сколько очков реально приносит накопитель — расчёт на 4–6;</li>
 *   <li>разброс между картами — лотерея на старте недопустима;</li>
 *   <li>длина партии — двенадцать разовых эффектов могут её укоротить.</li>
 * </ol>
 *
 * <p>Запуск: {@code kelium.Супер5 [партий] [игроков] [свод]}
 */
public final class Супер5 {

    private Супер5() {
    }

    private static final class Карта {
        String имя = "";
        long роздано;
        long сожжено;
        long очковНакопителя;
        long побед;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String ruleset = args.length > 2 ? args[2] : "1.31.0";
        List<String> пул = List.of("builder:3", "supplier:3", "stalker:3", "punisher:3");

        Map<String, Карта> итог = new TreeMap<>();
        long раундов = 0;

        for (int g = 0; g < games; g++) {
            GameConfig cfg = GameConfig.buildCached(ruleset, players, 21000L + g, null, null);
            GameState s = Setup.buildGame(cfg);
            List<Agent> ags = new ArrayList<>();
            int shift = g % players;
            for (int i = 0; i < players; i++) {
                ags.add(kelium.agents.BotCatalog.create(
                    пул.get((i + shift) % players), i, new Random(i * 211L + g), players));
            }
            GameEngine.playGame(s, ags, ev -> { });
            раундов += s.round;
            for (PlayerState p : s.players) {
                if (p.super5Card == null) {
                    continue;
                }
                Карта k = итог.computeIfAbsent(p.super5Card, x -> new Карта());
                if (k.имя.isEmpty()) {
                    var e = cfg.content.get("super_objectives").find(p.super5Card);
                    k.имя = e == null ? p.super5Card : String.valueOf(e.get("name"));
                }
                k.роздано++;
                if (p.super5Burned) {
                    k.сожжено++;
                } else {
                    k.очковНакопителя += Scoring.scorePlayer(s, p.seat)
                        .getOrDefault("super5_stockpile", 0);
                }
                if (s.winner != null && s.winner == p.seat) {
                    k.побед++;
                }
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("# Супер-задания 5.0: жгут или копят\n\n");
        b.append("Свод **").append(ruleset).append("**, партий **").append(games)
            .append("**, игроков ").append(players)
            .append(". Средняя длина партии: ")
            .append(окр((double) раундов / games)).append(" раундов.\n\n");
        b.append("Черновик просит проверить: жгут и хранят примерно поровну; ")
            .append("накопитель приносит 4–6 очков; карты не расходятся вдвое.\n\n");

        b.append("| карта | название | роздано | сожжено | доля сожжённых | ")
            .append("накопитель, ПО в среднем у сохранивших | побед с картой |\n");
        b.append("|---|---|---:|---:|---:|---:|---:|\n");
        for (var e : итог.entrySet()) {
            Карта k = e.getValue();
            long сохранило = k.роздано - k.сожжено;
            b.append("| ").append(e.getKey()).append(" | ").append(k.имя)
                .append(" | ").append(k.роздано)
                .append(" | ").append(k.сожжено)
                .append(" | ").append(проц(k.сожжено, k.роздано))
                .append(" | ").append(сохранило == 0 ? "—"
                    : окр((double) k.очковНакопителя / сохранило))
                .append(" | ").append(проц(k.побед, k.роздано))
                .append(" |\n");
        }

        Path out = Path.of("reports", "balance", "супер5.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, b.toString(), StandardCharsets.UTF_8);
        long роздано = итог.values().stream().mapToLong(k -> k.роздано).sum();
        long сожжено = итог.values().stream().mapToLong(k -> k.сожжено).sum();
        System.out.println("роздано: " + роздано + ", сожжено: " + сожжено
            + " (" + проц(сожжено, роздано) + ")");
        System.out.println("отчёт: " + out.toAbsolutePath());
    }

    private static String проц(long часть, long всего) {
        return всего == 0 ? "—"
            : String.format(Locale.ROOT, "%.1f%%", 100.0 * часть / всего);
    }

    private static String окр(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
