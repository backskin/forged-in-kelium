package kelium.проверки;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/** ХРОНИКА ЭКОНОМИКИ одного места: в начале каждого его хода — ресурсы и здания, затем его действия. */
public final class ХроникаЭкономики {

    public static void main(String[] args) {
        long сид = args.length > 0 ? Long.parseLong(args[0]) : 9001L;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        int место = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String бот = args.length > 3 ? args[3] : "builder:3";
        GameState s = Setup.buildGame(GameConfig.buildCached("1.50.0", n, сид, null, null));
        List<Agent> ags = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ags.add(kelium.agents.BotCatalog.create(бот, i, new Random(сид + i), n));
        }
        GameEngine.playGame(s, ags, ev -> {
            String t = String.valueOf(ev.get("type"));
            if (!(ev.get("seat") instanceof Number num) || num.intValue() != место) {
                return;
            }
            if ("turn_orders".equals(t)) {
                PlayerState p = s.player(место);
                StringBuilder b = new StringBuilder();
                for (BuildingToken x : p.buildingsOnField()) {
                    b.append(x.type.code).append(x.level == null ? "" : "L" + x.level)
                        .append('[').append(x.energyPlaced).append('/').append(x.energySlots)
                        .append(x.energyIdle > 0 ? " своб " + x.energyIdle : "").append("] ");
                }
                System.out.printf("р%d к%d %s  мон %d кел %d бп %d | %s%n", s.round, s.circle, ev.get("card"),
                    p.resources.coin(), p.resources.kelium(), p.resources.ammo(), b);
            } else if ("action".equals(t) && Boolean.TRUE.equals(ev.get("ok"))) {
                System.out.println("    " + (Boolean.TRUE.equals(ev.get("free")) ? "(даром) " : "") + ev.get("fork") + ":" + ev.get("action") + " — " + ev.get("detail"));
            }
        });
    }
}
