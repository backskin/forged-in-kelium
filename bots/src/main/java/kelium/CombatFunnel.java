package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ВОРОНКА БОЯ — куда девается война между «пошёл драться» и «снёс жетон».
 *
 * <p>Замер 15.08.2026 показал странное: после слоя соперничества боты стали
 * драться на четверть чаще, а попаданий больше не стало. Значит теряется что-то
 * между действием и уроном, и надо смотреть не «сколько боёв», а всю цепочку:
 *
 * <pre>
 *   действие Бой  →  атака состоялась  →  попадание  →  жетон уничтожен
 * </pre>
 *
 * <p>Устройство боя, из-за которого цепочка вообще может рваться: в одном бою
 * участвует ОДИН гекс-источник против ОДНОГО гекса-цели. Значит сила залпа — это
 * сколько СВОИХ войск стоит на одном гексе, а вовсе не сколько их на поле. Армия,
 * размазанная по одному жетону на гекс, физически не может бить больше одного
 * раза за действие.
 *
 * <p>Запуск: {@code kelium.CombatFunnel [партий] [игроков]}.
 */
public final class CombatFunnel {

    private CombatFunnel() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "reaper", "axiom", "balanced");

        double combats = 0;          // действий Бой, прошедших успешно
        double combatsNoHit = 0;     // из них не давших НИ ОДНОГО попадания
        double hits = 0;
        double kills = 0;
        double ammoSpent = 0;
        double retaliations = 0;
        // Сколько своих войск стоит на гексе: распределение по всем занятым гексам
        Map<Integer, Integer> stackSizes = new TreeMap<>();
        // Попадания, не убившие цель, по остатку прочности
        Map<String, Integer> survivedBy = new TreeMap<>();
        // ПОЧЕМУ ЗАЛП НЕ СОСТОЯЛСЯ — движок теперь называет причину сам
        Map<String, Integer> dryReasons = new TreeMap<>();
        double unitsOnField = 0;
        double snapshots = 0;

        for (int g = 0; g < games; g++) {
            long seed = 7_300_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            // Хиты текущего действия Бой: считаем, чтобы отличить холостой бой
            // (цель выбрана, а ударить нечем) от результативного.
            int[] hitsInAction = {0};
            boolean[] inAction = {false};
            double[] acc = new double[6];
            Map<Integer, Integer> stacks = new TreeMap<>();
            Map<String, Integer> survived = new TreeMap<>();
            Map<String, Integer> dry = new TreeMap<>();

            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(type)) {
                    acc[0]++;                       // попаданий
                    hitsInAction[0]++;
                    if (ev.get("ammo") instanceof Number a) {
                        acc[3] += a.intValue();
                    }
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        acc[1]++;                   // уничтожений
                    } else {
                        // Попадание не убило: значит у цели прочность больше
                        // урона. Записываем, кто выжил, — это прямо показывает,
                        // хватает ли одиночного удара вообще.
                        String victim = String.valueOf(ev.getOrDefault("victim", "?"));
                        survived.merge(victim.replaceAll("[0-9#].*", ""), 1, Integer::sum);
                    }
                } else if ("action".equals(type)
                        && "combat".equals(String.valueOf(ev.get("action")))) {
                    if (Boolean.TRUE.equals(ev.get("ok"))) {
                        acc[2]++;                   // успешных действий Бой
                        if (hitsInAction[0] == 0) {
                            acc[4]++;               // холостой бой
                        }
                    }
                    hitsInAction[0] = 0;
                } else if ("combat_dry".equals(type)) {
                    String reason = String.valueOf(ev.get("reason"));
                    // ОТКАЗ ОТКАЗУ РОЗНЬ. «Не бить» — это ещё и обычное завершение
                    // боя: отстрелялся и вышел. Настоящий отказ — тот, что случился
                    // ДО единого попадания в этом действии. Не разделив их, легко
                    // объявить проблемой то, что является нормальным концом хода.
                    if (reason.startsWith("сам отказался")) {
                        reason = hitsInAction[0] > 0
                            ? "вышел из боя, отстрелявшись"
                            : reason;
                    }
                    dry.merge(reason, 1, Integer::sum);
                } else if ("refresh".equals(type)) {
                    // Срез поля: как войска РАСПРЕДЕЛЕНЫ по гексам. Это и есть
                    // сила будущего залпа.
                    for (int i = 0; i < players; i++) {
                        Map<String, Integer> perHex = new HashMap<>();
                        for (UnitToken u : s.player(i).unitsOnField()) {
                            perHex.merge(u.hexId, 1, Integer::sum);
                        }
                        for (int n : perHex.values()) {
                            stacks.merge(n, 1, Integer::sum);
                        }
                        acc[5] += s.player(i).unitsOnField().size();
                    }
                    snapshotsHolder[0]++;
                }
            });
            hits += acc[0];
            kills += acc[1];
            combats += acc[2];
            ammoSpent += acc[3];
            combatsNoHit += acc[4];
            unitsOnField += acc[5];
            stacks.forEach((k, v) -> stackSizes.merge(k, v, Integer::sum));
            survived.forEach((k, v) -> survivedBy.merge(k, v, Integer::sum));
            dry.forEach((k, v) -> dryReasons.merge(k, v, Integer::sum));
        }
        snapshots = snapshotsHolder[0];

        StringBuilder md = new StringBuilder();
        md.append("# Воронка боя: где теряется война\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты ").append(Bots.describe()).append(".\n\n");
        md.append("Устройство боя: ОДИН гекс-источник против ОДНОГО гекса-цели. "
            + "Значит сила залпа — это сколько своих войск стоит на одном гексе, "
            + "а не сколько их на поле.\n\n");

        line(out, md, "действий Бой за партию", combats / games);
        line(out, md, "из них ХОЛОСТЫХ (ни одного попадания)", combatsNoHit / games);
        line(out, md, "доля холостых, %", 100 * combatsNoHit / Math.max(1, combats));
        line(out, md, "попаданий за партию", hits / games);
        line(out, md, "ПОПАДАНИЙ НА ОДИН РЕЗУЛЬТАТИВНЫЙ БОЙ",
            hits / Math.max(1, combats - combatsNoHit));
        line(out, md, "уничтожений за партию", kills / games);
        line(out, md, "доля попаданий, снёсших жетон, %", 100 * kills / Math.max(1, hits));
        line(out, md, "боеприпасов истрачено за партию", ammoSpent / games);

        md.append("\n## Почему залп не состоялся\n\n");
        md.append("| причина | случаев за партию | доля |\n|---|---:|---:|\n");
        out.println("\nпочему залп не состоялся:");
        int dryTotal = dryReasons.values().stream().mapToInt(Integer::intValue).sum();
        for (var e : dryReasons.entrySet()) {
            md.append(String.format(Locale.ROOT, "| %s | %.2f | %.1f%% |%n", e.getKey(),
                e.getValue() / (double) games,
                100.0 * e.getValue() / Math.max(1, dryTotal)));
            out.printf(Locale.ROOT, "  %-34s %6.2f за партию (%.1f%%)%n", e.getKey(),
                e.getValue() / (double) games,
                100.0 * e.getValue() / Math.max(1, dryTotal));
        }

        md.append("\n## Как войска стоят на поле\n\n");
        md.append("| войск на одном гексе | сколько таких стопок | доля |\n|---|---:|---:|\n");
        out.println("\nстопки войск на гексе:");
        int totalStacks = stackSizes.values().stream().mapToInt(Integer::intValue).sum();
        for (var e : stackSizes.entrySet()) {
            md.append(String.format(Locale.ROOT, "| %d | %d | %.1f%% |%n",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / Math.max(1, totalStacks)));
            out.printf(Locale.ROOT, "  по %d жетону: %.1f%% стопок%n",
                e.getKey(), 100.0 * e.getValue() / Math.max(1, totalStacks));
        }

        md.append("\n## Кто пережил попадание\n\n| жетон | раз выжил |\n|---|---:|\n");
        out.println("\nпережили попадание:");
        for (var e : survivedBy.entrySet()) {
            md.append(String.format("| %s | %d |%n", e.getKey(), e.getValue()));
            out.printf("  %-22s %d%n", e.getKey(), e.getValue());
        }

        Path p = Path.of("reports", "balance", "воронка-боя.md");
        Files.createDirectories(p.getParent());
        Files.writeString(p, md.toString(), StandardCharsets.UTF_8);
        out.println("\nотчёт: " + p.toAbsolutePath());
    }

    private static final double[] snapshotsHolder = new double[1];

    private static void line(PrintStream out, StringBuilder md, String what, double v) {
        md.append(String.format(Locale.ROOT, "* **%s** — %.2f%n", what, v));
        out.printf(Locale.ROOT, "%-42s %8.2f%n", what, v);
    }
}
