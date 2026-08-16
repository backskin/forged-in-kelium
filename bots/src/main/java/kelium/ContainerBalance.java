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
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * БАЛАНС КОНТЕЙНЕРОВ — какую сторону берут и какая лежит мёртвой.
 *
 * <p>У карты контейнера две стороны, и игрок выбирает одну. Значит у каждой
 * карты есть простая и жёсткая проверка: если одну сторону берут почти всегда,
 * вторая на карте просто НЕ СУЩЕСТВУЕТ — место занимает, выбора не даёт.
 *
 * <p>Заказ дизайнера 15.08.2026 после того, как метрики утиля показали, что
 * четыре эффекта не сработали ни разу за 200 партий: карты в колоде, эффекты
 * реализованы, а сторону с ними не выбирает никто.
 *
 * <p>Читать так: доля стороны А близко к 50% — карта даёт настоящий выбор;
 * меньше 15% или больше 85% — сторона мертва, и карту надо чинить.
 *
 * <p>Запуск: {@code kelium.ContainerBalance [партий] [игроков]}.
 */
public final class ContainerBalance {

    private ContainerBalance() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "balanced", "axiom", "dove");

        Map<String, int[]> picks = new TreeMap<>();     // id -> [A, Б]

        for (int g = 0; g < games; g++) {
            long seed = 9_900_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            GameEngine.playGame(s, agents, ev -> {
                if (!"container".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                String cid = String.valueOf(ev.get("card"));
                String variant = String.valueOf(ev.get("variant"));
                int[] p = picks.computeIfAbsent(cid, k -> new int[2]);
                p["a".equals(variant) ? 0 : 1]++;
            });
        }

        GameConfig cfg = GameConfig.build(players, 1L);
        Map<String, Map<String, Object>> byId = new TreeMap<>();
        for (Map<String, Object> c : cfg.content.get("containers").entries) {
            byId.put(String.valueOf(c.get("id")), c);
        }

        StringBuilder md = new StringBuilder();
        md.append("# Контейнеры: какую сторону берут\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты ").append(Bots.describe()).append(".\n\n");
        md.append("У карты две стороны, игрок выбирает одну. Доля около половины — "
            + "карта даёт настоящий выбор. Меньше 15% или больше 85% — вторая "
            + "сторона на карте не существует.\n\n");
        md.append("| карта | тир | открытий | доля А | вердикт | сторона А | сторона Б |\n");
        md.append("|---|---|---:|---:|---|---|---|\n");
        out.printf("%-6s %-7s %8s %7s  %-14s %s%n",
            "карта", "тир", "открытий", "доля А", "вердикт", "стороны");

        int dead = 0;
        for (var e : byId.entrySet()) {
            int[] p = picks.getOrDefault(e.getKey(), new int[2]);
            int total = p[0] + p[1];
            double shareA = total == 0 ? Double.NaN : 100.0 * p[0] / total;
            String verdict;
            if (total == 0) {
                verdict = "не открывалась";
            } else if (shareA < 15) {
                verdict = "**А мертва**";
                dead++;
            } else if (shareA > 85) {
                verdict = "**Б мертва**";
                dead++;
            } else {
                verdict = "выбор живой";
            }
            md.append(String.format(Locale.ROOT, "| %s | %s | %d | %s | %s | %s | %s |%n",
                e.getKey(), e.getValue().getOrDefault("tier", "?"), total,
                total == 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", shareA),
                verdict, label(e.getValue(), "a"), label(e.getValue(), "b")));
            out.printf(Locale.ROOT, "%-6s %-7s %8d %7s  %-14s %s | %s%n",
                e.getKey(), e.getValue().getOrDefault("tier", "?"), total,
                total == 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", shareA),
                verdict.replace("**", ""), label(e.getValue(), "a"),
                label(e.getValue(), "b"));
        }
        md.append("\n**Карт с мёртвой стороной: ").append(dead).append(" из ")
          .append(byId.size()).append("**\n");
        out.println("\nкарт с мёртвой стороной: " + dead + " из " + byId.size());

        Path path = Path.of("reports", "balance", "контейнеры-баланс.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
        out.println("отчёт: " + path.toAbsolutePath());
    }

    private static String label(Map<String, Object> card, String side) {
        if (card.get(side) instanceof Map<?, ?> v) {
            Object l = v.get("label");
            return l == null ? String.valueOf(v.get("effect")) : String.valueOf(l);
        }
        return "—";
    }
}
