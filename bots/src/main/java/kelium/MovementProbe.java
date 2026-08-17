package kelium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * MovementProbe — ПОЧЕМУ ВОЙСКА НЕ ХОДЯТ.
 *
 * <p>Аудит показал: действие «Движение» разыгрывается ~29 раз за партию, а шагов
 * получается ~17, и 39% войск не двигаются НИ РАЗУ. Зонд ловит момент розыгрыша
 * Движения и для каждого своего войска считает, куда он МОГ БЫ пойти, а если
 * никуда — разбирает, ЧТО именно закрыло каждое ребро:
 *
 * <ul>
 *   <li>СВОЯ стенка — сторона исходного гекса занята СВОИМ же зданием;</li>
 *   <li>чужая стенка — здание другого игрока;</li>
 *   <li>стенка нейтрала;</li>
 *   <li>тайл зарождения на целевом гексе (он занимает все ячейки);</li>
 *   <li>нет места — на целевом гексе не помещается даже с переупаковкой;</li>
 *   <li>край поля.</li>
 * </ul>
 *
 * <p>Это важно для ПРАВИЛ: закрывают ли СВОИ стенки проход своим же войскам —
 * вопрос дизайнеру, а не выдумка движка.
 *
 * <p>Запуск: {@code java -cp ... kelium.MovementProbe [players] [games]}
 */
public final class MovementProbe {

    private MovementProbe() {
    }

    private static int unitChecks;          // сколько раз смотрели на войско
    private static int unitsStuck;          // из них: идти вообще некуда
    private static int edgeChecks;
    private static final Map<String, Integer> reasons = new LinkedHashMap<>();
    private static int moveActions;
    private static int moveActionsNoOptions;

    /** Обёртка: на каждом выборе «куда идти» разбирает обстановку. */
    private static final class Watcher extends Agent {
        private final Agent inner;

        Watcher(Agent inner) {
            super(inner.seat, inner.name);
            this.inner = inner;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            if (context != null && "move".equals(context.get("kind"))) {
                inspect(state, seat);
            }
            return inner.choose(state, options, context);
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }
    }

    /** Разобрать, куда могут идти войска игрока и что их держит. */
    private static void inspect(GameState s, int seat) {
        PlayerState p = s.player(seat);
        for (UnitToken u : p.unitsOnField()) {
            if (u.type == UnitType.TOWER) {
                continue;                 // вышка неподвижна по правилам
            }
            unitChecks++;
            Hex from = s.field.get(u.hexId);
            if (from == null) {
                continue;
            }
            int open = 0;
            List<String> why = new ArrayList<>();
            for (int side = 0; side < 6; side++) {
                String nbId = from.neighborBySide[side];
                edgeChecks++;
                if (nbId == null) {
                    why.add("край поля");
                    continue;
                }
                Hex nb = s.field.get(nbId);
                // ПРАВИЛО (СВОД): закрывают только ЧУЖИЕ и НЕЙТРАЛЬНЫЕ здания.
                // Свои здания и любые войска проход не закрывают.
                Integer owner = from.sideOwner[side];
                if (blocks(s, owner, seat)) {
                    why.add(wallOwner(s, owner, seat));
                    continue;
                }
                Integer back = nb == null ? null : nb.sideOwner[(side + 3) % 6];
                if (blocks(s, back, seat)) {
                    why.add("стенка с той стороны: " + wallOwner(s, back, seat));
                    continue;
                }
                if (nb != null && nb.hasSpawnTile()) {
                    why.add("тайл зарождения");
                    continue;
                }
                if (nb != null && !nb.fitsWithRepack(u.type == UnitType.VEHICLE ? 2 : 1,
                        vehicles(s, nbId, u.uid), singles(s, nbId, u.uid))) {
                    why.add("нет места на гексе");
                    continue;
                }
                open++;
            }
            if (open == 0) {
                unitsStuck++;
                for (String r : why) {
                    reasons.merge(r, 1, Integer::sum);
                }
            }
        }
    }

    /** Закрывает ли занятая сторона проход (только чужие/нейтральные здания). */
    private static boolean blocks(GameState s, Integer uid, int seat) {
        if (uid == null) {
            return false;
        }
        if (uid < 0) {
            return true;
        }
        for (PlayerState p : s.players) {
            for (var b : p.buildings) {
                if (b.uid == uid) {
                    return p.seat != seat;
                }
            }
        }
        return false;      // войско стенкой не является
    }

    private static String wallOwner(GameState s, int uid, int seat) {
        if (uid < 0) {
            return "стенка нейтрала";
        }
        for (PlayerState p : s.players) {
            for (var b : p.buildings) {
                if (b.uid == uid) {
                    return p.seat == seat ? "СВОЯ стенка" : "чужая стенка";
                }
            }
            for (UnitToken un : p.units) {
                if (un.uid == uid) {
                    return p.seat == seat ? "своё войско" : "чужое войско";
                }
            }
        }
        return "неизвестный жетон";
    }

    private static int vehicles(GameState s, String hexId, int ignoreUid) {
        int n = 0;
        for (PlayerState p : s.players) {
            for (UnitToken u : p.unitsOnField()) {
                if (hexId.equals(u.hexId) && u.uid != ignoreUid && u.type == UnitType.VEHICLE) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int singles(GameState s, String hexId, int ignoreUid) {
        int n = 0;
        for (PlayerState p : s.players) {
            for (UnitToken u : p.unitsOnField()) {
                if (hexId.equals(u.hexId) && u.uid != ignoreUid
                        && u.type != UnitType.VEHICLE && u.type != UnitType.AIRCRAFT) {
                    n++;
                }
            }
        }
        return n;
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        if (args.length > 2) {
            kelium.dataio.Locations.setBotMemory(java.nio.file.Path.of(args[2]));
            Bots.forgetCache();
        }

        for (int g = 0; g < games; g++) {
            long seed = 9100 + g;
            GameState s = Setup.buildGame(GameConfig.buildCached(
                GameConfig.DEFAULT_RULESET, players, seed, null, null));
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                // Только игроки: приборы (аксиома, жнец) обучены без очков в
                // цели и за замерным столом искажают картину.
                String c = Bots.PLAYERS.get((seat + g) % Bots.PLAYERS.size());
                agents.add(new Watcher(
                    Bots.create(c, seat, new Random(seed * 131 + seat + 1), players)));
            }
            GameEngine.playGame(s, agents, ev -> {
                if ("action".equals(ev.get("type")) && "movement".equals(ev.get("action"))) {
                    moveActions++;
                    if (ev.get("telemetry") instanceof Map<?, ?> tel
                            && tel.get("moves") instanceof Number n && n.intValue() == 0) {
                        moveActionsNoOptions++;
                    }
                }
            });
        }

        System.out.println("=== Зонд движения: " + games + " партий, " + players + " игроков ===");
        System.out.println();
        System.out.printf(Locale.ROOT, "Движение сыграно %d раз, из них БЕЗ ЕДИНОГО ШАГА %d "
            + "(%.1f%%)%n", moveActions, moveActionsNoOptions,
            100.0 * moveActionsNoOptions / Math.max(1, moveActions));
        System.out.printf(Locale.ROOT, "Проверок «куда пойти» %d, из них войску идти НЕКУДА "
            + "%d (%.1f%%)%n", unitChecks, unitsStuck,
            100.0 * unitsStuck / Math.max(1, unitChecks));
        System.out.println();
        System.out.println("Что закрывает рёбра у ЗАПЕРТЫХ войск:");
        reasons.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> System.out.printf(Locale.ROOT, "  %-32s %d (%.1f%%)%n",
                e.getKey(), e.getValue(),
                100.0 * e.getValue() / Math.max(1, reasons.values().stream()
                    .mapToInt(Integer::intValue).sum())));
    }
}
