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
import kelium.engine.Setup;

/**
 * ЧТО ИГРОКИ РЕАЛЬНО ПОЛУЧАЮТ ЗА ЗАДАНИЯ.
 *
 * <p>Вопрос дизайнера 15.08.2026: как часто задания выполняются, как часто
 * выполняются УСИЛЕННО, и сколько за это приходит трофеев и жетонов модулей.
 *
 * <p>До сих пор считалось только «выполнено / сожжено». Награда — вторая
 * половина карты, и без неё нельзя понять, окупается ли усиленная ветка: она
 * требует заметно большего, а платит ли соразмерно — вопрос открытый.
 *
 * <p>Считается по СОБЫТИЯМ движка, а не по догадкам: {@code objective} несёт и
 * признак усиления, и выданную награду.
 *
 * <p>Запуск: {@code kelium.ObjectiveRewards [партий] [игроков]}.
 */
public final class ObjectiveRewards {

    private ObjectiveRewards() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 250;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "balanced", "axiom", "dove");

        double drawn = 0;
        double done = 0;
        double doneEnhanced = 0;
        double burned = 0;
        double trophy = 0;
        double modулesRed = 0;
        double modulesBlue = 0;
        double objectiveCards = 0;
        double storageTokens = 0;
        double kelium = 0;
        // Сколько игроков за партию хоть раз выполнили усиленное требование
        double gamesWithEnhanced = 0;

        for (int g = 0; g < games; g++) {
            long seed = 9_100_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            double[] a = new double[10];
            GameEngine.playGame(s, agents, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "objective_drawn" -> a[0]++;
                    case "objective_burn" -> a[3]++;
                    case "objective" -> {
                        a[1]++;
                        if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                            a[2]++;
                        }
                        // НАГРАДА ЛЕЖИТ ВЛОЖЕННО: granted = {base: {...},
                        // special: {...}}. Читать надо обе части, иначе получаются
                        // ровные нули — на этом я уже обжигался с ключом найма.
                        Map<?, ?> outer = ev.get("granted") instanceof Map<?, ?> o
                            ? o : Map.of();
                        Map<String, Object> gr = new java.util.HashMap<>();
                        for (String part : new String[]{"base", "special"}) {
                            if (outer.get(part) instanceof Map<?, ?> m) {
                                m.forEach((k, v) -> {
                                    if (v instanceof Number n
                                            && gr.get(String.valueOf(k)) instanceof Number old2) {
                                        gr.put(String.valueOf(k), old2.intValue() + n.intValue());
                                    } else {
                                        gr.put(String.valueOf(k), v);
                                    }
                                });
                            }
                        }
                        {
                            if (gr.get("trophy") instanceof Number n) {
                                a[4] += n.intValue();
                            }
                            if (gr.get("kelium") instanceof Number n) {
                                a[8] += n.intValue();
                            }
                            if (gr.get("module") != null) {
                                if ("attack".equals(String.valueOf(gr.get("module")))) {
                                    a[5]++;
                                } else {
                                    a[6]++;
                                }
                            }
                            if (gr.get("objective_card") instanceof Number n) {
                                a[7] += n.intValue();
                            }
                            if (gr.get("storage_token") instanceof Number n) {
                                a[9] += n.intValue();
                            }
                        }
                    }
                    default -> { }
                }
            });
            drawn += a[0];
            done += a[1];
            doneEnhanced += a[2];
            burned += a[3];
            trophy += a[4];
            modулesRed += a[5];
            modulesBlue += a[6];
            objectiveCards += a[7];
            kelium += a[8];
            storageTokens += a[9];
            if (a[2] > 0) {
                gamesWithEnhanced++;
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Задания: что выполняется и что за это дают\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты ").append(Bots.describe())
          .append(". Числа — НА ИГРОКА за партию.\n\n");

        double perPlayer = games * (double) players;
        row(out, md, "получено карт заданий", drawn / perPlayer);
        row(out, md, "ВЫПОЛНЕНО", done / perPlayer);
        row(out, md, "из них УСИЛЕННО", doneEnhanced / perPlayer);
        row(out, md, "сожжено ради верхнего эффекта", burned / perPlayer);
        md.append(String.format(Locale.ROOT,
            "* **доля усиленных среди выполненных** — %.0f%%%n",
            100 * doneEnhanced / Math.max(1, done)));
        out.printf(Locale.ROOT, "%-40s %8.0f%%%n", "доля усиленных среди выполненных",
            100 * doneEnhanced / Math.max(1, done));
        md.append(String.format(Locale.ROOT,
            "* **партий, где хоть кто-то выполнил усиленное** — %.0f%%%n",
            100 * gamesWithEnhanced / games));
        out.printf(Locale.ROOT, "%-40s %8.0f%%%n", "партий с хотя бы одним усиленным",
            100 * gamesWithEnhanced / games);

        md.append("\n## Что получено за задания (на игрока за партию)\n\n");
        out.println("\nполучено за задания (на игрока за партию):");
        row(out, md, "ТРОФЕЕВ (трофейных кубиков)", trophy / perPlayer);
        row(out, md, "жетонов модулей АТАКИ (красных)", modулesRed / perPlayer);
        row(out, md, "жетонов модулей СНАРЯЖЕНИЯ (синих)", modulesBlue / perPlayer);
        row(out, md, "жетонов хранилища", storageTokens / perPlayer);
        row(out, md, "келемия", kelium / perPlayer);
        row(out, md, "лишних карт заданий", objectiveCards / perPlayer);

        Path p = Path.of("reports", "balance", "задания-и-награды.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("\nотчёт: " + p.toAbsolutePath());
    }

    private static void row(PrintStream out, StringBuilder md, String what, double v) {
        md.append(String.format(Locale.ROOT, "* **%s** — %.2f%n", what, v));
        out.printf(Locale.ROOT, "%-40s %8.2f%n", what, v);
    }
}
