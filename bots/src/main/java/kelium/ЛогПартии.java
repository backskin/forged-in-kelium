package kelium;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ПОДРОБНЫЙ ЛОГ ОДНОЙ ПАРТИИ (23.09.2026): каждое решение бота — что ему
 * предлагали, что он выбрал и с чем на руках, — каждое событие движка и срез
 * стола в начале каждого хода. Нужен, чтобы глазами увидеть, КАК играют боты,
 * а не только сколько они набрали.
 *
 * <p>Решения пишутся только за настоящим столом: планировщик проигрывает ходы
 * на копиях, и его воображаемые решения в лог не попадают.
 *
 * <p>Запуск: {@code kelium.ЛогПартии [сид] [уровень] [файл]}.
 */
public final class ЛогПартии {

    private ЛогПартии() {
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 9_100_001L;
        int уровень = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        Path файл = Path.of(args.length > 2 ? args[2] : "reports/logs/partiya_" + seed + ".txt");
        Files.createDirectories(файл.toAbsolutePath().getParent());
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(файл, StandardCharsets.UTF_8))) {
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Agent бот = Bots.create(Bots.ROSTER_4.get(i), Bots.Level.of(уровень), i,
                    new Random(seed * 31 + i), 4);
                out.printf("место %d: %s, планшет %s%n", i, бот.name, s.player(i).board.troop.side);
                agents.add(new Писарь(бот, s, out));
            }
            out.println();
            GameEngine.playGame(s, agents, ev -> событие(s, ev, out));
            out.println("\nИТОГ");
            for (PlayerState p : s.players) {
                out.printf("  место %d: %s%n", p.seat, Scoring.scorePlayer(s, p.seat));
            }
        }
        System.out.println("лог: " + файл.toAbsolutePath());
    }

    /** Обёртка бота: пишет каждое решение за настоящим столом. */
    private static final class Писарь extends Agent {
        private final Agent бот;
        private final GameState стол;
        private final PrintWriter out;

        Писарь(Agent бот, GameState стол, PrintWriter out) {
            super(бот.seat, бот.name);
            this.бот = бот;
            this.стол = стол;
            this.out = out;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            Choice c = бот.choose(state, options, ctx);
            if (state == стол && options.size() > 1) {
                String вид = ctx == null ? "?" : String.valueOf(ctx.get("kind"));
                List<String> варианты = new ArrayList<>();
                for (Choice o : options) {
                    варианты.add(o.label() == null ? String.valueOf(o.payload()) : o.label());
                }
                out.printf("      [%d решает %s] выбрал «%s» из %s%n", seat, вид,
                    c.label() == null ? c.payload() : c.label(),
                    варианты.size() > 8 ? варианты.subList(0, 8) + "…(" + варианты.size() + ")"
                        : варианты);
            }
            return c;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            бот.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            бот.observePublicEvent(event);
        }
    }

    private static void событие(GameState s, Map<String, Object> ev, PrintWriter out) {
        String тип = String.valueOf(ev.get("type"));
        Object место = ev.get("seat");
        switch (тип) {
            case "turn_orders" -> {
                int seat = ((Number) место).intValue();
                out.printf("%n— раунд %d, круг %d · место %d · верх %s%s, низ %s%s%n",
                    s.round, s.circle, seat, ev.get("top"),
                    Boolean.TRUE.equals(ev.get("coincided")) ? " (СОВПАЛ → 1 действие)" : "",
                    ev.get("bottom"), Boolean.TRUE.equals(ev.get("bottom_open")) ? " (открыт)" : " (закрыт)");
                out.println("    " + срез(s, s.player(seat)));
            }
            case "action" -> out.printf("    ДЕЙСТВИЕ %s %s: %s%n", ev.get("action"),
                Boolean.TRUE.equals(ev.get("ok")) ? "ok" : "НЕ ВЫШЛО", ev.get("detail"));
            case "combat_hit" -> out.printf("      удар %s → %s%s%n", ev.get("attacker"),
                ev.get("victim"), Boolean.TRUE.equals(ev.get("destroyed")) ? " СНЕСЁН" : "");
            case "objective" -> out.printf("    ЗАДАНИЕ %s выполнено%s, награда %s%n",
                ev.get("card"), Boolean.TRUE.equals(ev.get("enhanced")) ? " (усиленно)" : "",
                ev.get("granted"));
            case "order_spec", "spec_combat", "ability_spec", "arsenal", "container" ->
                out.printf("    СПЕЦ %s %s%n", тип, без(ev));
            case "return" -> {
                out.printf("%n=== КОНЕЦ РАУНДА %d ===%n", s.round);
                for (PlayerState p : s.players) {
                    out.printf("  место %d: ПО %d · %s%n", p.seat,
                        Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0), срез(s, p));
                }
            }
            case "game_end" -> out.println("\n=== КОНЕЦ ПАРТИИ === " + без(ev));
            default -> { }
        }
    }

    private static String срез(GameState s, PlayerState p) {
        Map<String, Integer> зданий = new TreeMap<>();
        int запитано = 0;
        int всего = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            зданий.merge(b.type.code + (b.level == null ? "" : b.level), 1, Integer::sum);
            всего++;
            if (b.powered()) {
                запитано++;
            }
        }
        Map<String, Integer> войск = new TreeMap<>();
        for (UnitToken u : p.unitsOnField()) {
            войск.merge(u.type.code, 1, Integer::sum);
        }
        return String.format("мон %d кел %d БПР %d тро %d · здания %s (запитано %d/%d) · войска %s"
                + " · заданий в руке %d, выполнено %d · арсенал %d уст.",
            p.resources.coin(), p.resources.kelium(), p.resources.ammo(), p.resources.trophy(),
            зданий, запитано, всего, войск, p.objectiveHand.size(), p.objectivesCompleted,
            p.arsenalInstalled.size());
    }

    private static String без(Map<String, Object> ev) {
        Map<String, Object> m = new TreeMap<>(ev);
        m.remove("type");
        m.remove("seat");
        String t = m.toString();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
