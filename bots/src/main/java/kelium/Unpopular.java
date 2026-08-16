package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ЧТО В ИГРЕ НЕ ИСПОЛЬЗУЮТ — перепись непопулярного.
 *
 * <p>Заказ дизайнера 15.08.2026: карты-цели должны награждать за то, чего никто
 * не делает («никто не строит авиацию — пусть за авиацию»). Чтобы такие карты
 * били в цель, надо сперва ЗНАТЬ, что именно заброшено, а не догадываться.
 *
 * <p>Считается всё, что игрок может выбрать и обычно не выбирает: рода войск,
 * типы зданий, уровни добытчиков и энергостанций, треки науки, действия.
 *
 * <p>Запуск: {@code kelium.Unpopular [партий] [игроков]}.
 */
public final class Unpopular {

    private Unpopular() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int players = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        List<String> lineup = List.of("warlord", "balanced", "axiom", "dove");

        Map<String, Double> unitsMade = new TreeMap<>();
        Map<String, Double> unitsAlive = new TreeMap<>();
        Map<String, Double> buildings = new TreeMap<>();
        Map<String, Double> levels = new TreeMap<>();
        Map<String, Double> tracks = new TreeMap<>();
        Map<String, Double> actions = new TreeMap<>();

        for (int g = 0; g < games; g++) {
            long seed = 10_700_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get((i + g) % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            GameEngine.playGame(s, agents, ev -> {
                if ("action".equals(String.valueOf(ev.get("type")))
                        && Boolean.TRUE.equals(ev.get("ok"))) {
                    actions.merge(String.valueOf(ev.get("action")), 1.0, Double::sum);
                }
                if ("assembly_out".equals(String.valueOf(ev.get("type")))
                        && ev.get("unit") instanceof String u) {
                    unitsMade.merge(u, 1.0, Double::sum);
                }
            });
            for (int i = 0; i < players; i++) {
                for (UnitToken u : s.player(i).unitsOnField()) {
                    unitsAlive.merge(u.type.code, 1.0, Double::sum);
                }
                for (BuildingToken b : s.player(i).buildingsOnField()) {
                    buildings.merge(b.type.name().toLowerCase(Locale.ROOT), 1.0, Double::sum);
                    if (b.level != null && b.level > 0) {
                        levels.merge(b.type.name().toLowerCase(Locale.ROOT)
                            + " ур." + b.level, 1.0, Double::sum);
                    }
                }
                for (String t : s.tech.tracks) {
                    tracks.merge(t, (double) s.player(i).techSteps.getOrDefault(t, 0),
                        Double::sum);
                }
            }
        }

        double seats = games * (double) players;
        show(out, "ВОЙСКА НА ПОЛЕ В КОНЦЕ (на игрока)", unitsAlive, seats);
        show(out, "ЗДАНИЯ НА ПОЛЕ В КОНЦЕ (на игрока)", buildings, seats);
        show(out, "УРОВНИ ДОБЫТЧИКОВ И ЭНЕРГОСТАНЦИЙ (на игрока)", levels, seats);
        show(out, "ШАГИ ПО ТРЕКАМ НАУКИ (на игрока)", tracks, seats);
        show(out, "ДЕЙСТВИЯ ЗА ПАРТИЮ (на игрока)", actions, seats);
    }

    private static void show(PrintStream out, String title, Map<String, Double> m,
                             double seats) {
        out.println("\n=== " + title + " ===");
        double total = m.values().stream().mapToDouble(Double::doubleValue).sum();
        m.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .forEach(e -> out.printf(Locale.ROOT, "  %-26s %6.2f  (%.0f%%)%n",
                e.getKey(), e.getValue() / seats,
                total <= 0 ? 0 : 100 * e.getValue() / total));
    }
}
