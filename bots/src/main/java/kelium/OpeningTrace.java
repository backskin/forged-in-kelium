package kelium;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;
import kelium.engine.LayoutLibrary;

/**
 * OpeningTrace — построчная расшифровка дебюта ОДНОГО игрока.
 *
 * <p>Дизайнер описал очевидный дебют новичка: добытчик у жилы → добыл 2 келемия
 * → продал за 6 монет → на них станция, второй добытчик и казарма → снова
 * Добыча и Сборка → пехота, движение, контейнеры → третий добытчик и завод.
 * Зонд печатает, что бот на самом деле делает круг за кругом: какой приказ
 * вскрыл, какие действия сыграл, что получил и сколько у него ресурсов —
 * чтобы увидеть, на каком именно круге он сворачивает с этой дороги.
 *
 * <p>Запуск: {@code java -cp ... kelium.OpeningTrace [seed] [seat] [players]}
 */
public final class OpeningTrace {

    private OpeningTrace() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, "UTF-8"));
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 5000L;
        int watch = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int players = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        Genome genome;
        try {
            genome = Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            genome = Genome.defaults();
        }
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            agents.add(new StrategicAgent(seat, new Random(seed * 131 + seat), genome));
        }

        System.out.println("=== Дебют места " + (watch + 1) + ", сид " + seed + " ===");
        System.out.println(resources(s.player(watch)) + "  " + assets(s, s.player(watch)));
        System.out.println();

        final int[] round = {0};
        final int[] circle = {0};
        GameEngine.playGame(s, agents, ev -> {
            Object type = ev.get("type");
            if ("round_start".equals(type) || "round".equals(type)) {
                round[0] = ev.get("round") instanceof Number n ? n.intValue() : round[0] + 1;
                System.out.println("---------- РАУНД " + round[0] + " ----------");
                return;
            }
            if ("reveal".equals(type)) {
                circle[0] = ev.get("circle") instanceof Number n ? n.intValue() : circle[0] + 1;
                return;
            }
            int seat = ev.get("seat") instanceof Number n ? n.intValue() : -1;
            if (seat != watch) {
                return;
            }
            if ("action".equals(type)) {
                System.out.printf("  круг %d | %-12s %-4s %s%n", circle[0],
                    String.valueOf(ev.get("action")),
                    Boolean.TRUE.equals(ev.get("ok")) ? "ок" : "НЕТ",
                    shorten(String.valueOf(ev.get("detail"))));
            } else if ("turn_end".equals(type)) {
                PlayerState p = s.player(watch);
                System.out.println("            итог хода: " + resources(p) + "  " + assets(s, p));
            } else if ("objective".equals(type)) {
                System.out.println("            ЗАДАНИЕ выполнено: " + ev.get("card")
                    + " награда " + ev.get("granted"));
            }
        });

        System.out.println();
        System.out.println("=== Конец ===");
        for (int seat = 0; seat < players; seat++) {
            PlayerState p = s.player(seat);
            System.out.println("место " + (seat + 1) + ": " + resources(p) + "  " + assets(s, p)
                + "  ПО=" + kelium.engine.Scoring.scorePlayer(s, seat).getOrDefault("total", 0));
        }
    }

    private static String resources(PlayerState p) {
        return String.format("МОН=%d КЕЛ=%d БПР=%d ТРФ=%d КОНТ=%d",
            p.resources.coin(), p.resources.kelium(), p.resources.ammo(),
            p.resources.debris(), p.containers);
    }

    private static String assets(GameState s, PlayerState p) {
        StringBuilder sb = new StringBuilder("[");
        for (BuildingToken b : p.buildingsOnField()) {
            sb.append(code(b.type)).append(b.level == null ? "" : b.level)
              .append(b.powered() ? "+" : "-").append(' ');
        }
        sb.append("| войск ").append(p.unitsOnField().size()).append(']');
        return sb.toString();
    }

    private static String code(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> "ЦУ";
            case BARRACKS -> "Кз";
            case FACTORY -> "Зв";
            case AIRBASE -> "Ав";
            case MINER -> "Доб";
            case POWER_PLANT -> "ЭС";
        };
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }
}
