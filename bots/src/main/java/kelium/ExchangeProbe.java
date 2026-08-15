package kelium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ExchangeProbe — ПОКУПАЮТ ЛИ БОТЫ АРСЕНАЛ ЗА ТРОФЕИ И ЗАДАНИЯ ЗА КЕЛЕМИЙ?
 *
 * <p>Дизайнер 14.08.2026: «арсенал можно покупать за 2 трофея, он даётся куда
 * чаще [чем через сборку], боты просто не покупают его — почему?». Замер прямых
 * событий, а не выведенных чисел: {@code action=science} несёт поле
 * {@code exchange} (какие печатные обмены взяты за действие, склеены "+"),
 * {@code action=market} несёт {@code deals} и {@code objective_cards}.
 *
 * <p>Запуск: {@code kelium.ExchangeProbe [игроков] [партий]}.
 */
public final class ExchangeProbe {

    private ExchangeProbe() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");

        int scienceActions = 0;
        int drawArsenalTaken = 0;
        int gildTaken = 0;
        int moveModuleTaken = 0;
        int trophyToCoinTaken = 0;
        int marketActions = 0;
        int marketDeals = 0;
        int objectiveCardsBought = 0;

        for (int g = 0; g < games; g++) {
            long seed = 9000L + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            int[] c = new int[8];
            new GameEngine(s, agents, ev -> {
                if (!"action".equals(ev.get("type"))) {
                    return;
                }
                Object action = ev.get("action");
                Object telObj = ev.get("telemetry");
                if (!(telObj instanceof java.util.Map<?, ?> tel)) {
                    return;
                }
                if ("science".equals(action)) {
                    c[0]++;
                    Object ex = tel.get("exchange");
                    if (ex != null) {
                        String s2 = String.valueOf(ex);
                        for (String id : s2.split("\\+")) {
                            switch (id) {
                                case "draw_arsenal" -> c[1]++;
                                case "gild" -> c[2]++;
                                case "move_module" -> c[3]++;
                                case "trophy_to_coin" -> c[4]++;
                                default -> { }
                            }
                        }
                    }
                } else if ("market".equals(action)) {
                    c[5]++;
                    if (tel.get("deals") instanceof Number n) {
                        c[6] += n.intValue();
                    }
                    if (tel.get("objective_cards") instanceof Number n) {
                        c[7] += n.intValue();
                    }
                }
            }).run();
            scienceActions += c[0];
            drawArsenalTaken += c[1];
            gildTaken += c[2];
            moveModuleTaken += c[3];
            trophyToCoinTaken += c[4];
            marketActions += c[5];
            marketDeals += c[6];
            objectiveCardsBought += c[7];
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Покупают ли боты арсенал и задания за вечные курсы\n\n");
        sb.append(String.format(Locale.ROOT,
            "%d партий, %d игрока, раскладки дизайнера, состав %s.%n%n", games, players, lineup));
        sb.append(String.format(Locale.ROOT,
            "Действий Наука сыграно: %d (%.2f за партию)%n", scienceActions,
            scienceActions / (double) games));
        sb.append(String.format(Locale.ROOT,
            "  из них взят обмен «2 трофея -> арсенал»: %d (%.1f%% действий, %.3f за партию)%n",
            drawArsenalTaken, 100.0 * drawArsenalTaken / Math.max(1, scienceActions),
            drawArsenalTaken / (double) games));
        sb.append(String.format(Locale.ROOT,
            "  позолота: %d (%.1f%%), перенос модуля: %d (%.1f%%), трофей->монета: %d (%.1f%%)%n",
            gildTaken, 100.0 * gildTaken / Math.max(1, scienceActions),
            moveModuleTaken, 100.0 * moveModuleTaken / Math.max(1, scienceActions),
            trophyToCoinTaken, 100.0 * trophyToCoinTaken / Math.max(1, scienceActions)));
        sb.append(String.format(Locale.ROOT,
            "%nДействий Маркет сыграно: %d (%.2f за партию), сделок всего %d (%.2f за действие)%n",
            marketActions, marketActions / (double) games,
            marketDeals, marketDeals / (double) Math.max(1, marketActions)));
        sb.append(String.format(Locale.ROOT,
            "  из них купили карты заданий (1 келемий -> 2 карты, оставь 2): %d (%.3f за партию)%n",
            objectiveCardsBought, objectiveCardsBought / (double) games));

        String report = sb.toString();
        System.out.println();
        System.out.println(report);
        Path out = Path.of("reports", "balance", "обмены-наука-маркет-" + players + "p.md");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("отчёт: " + out.toAbsolutePath());
    }
}
