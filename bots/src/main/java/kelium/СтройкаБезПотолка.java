package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ЧТО БУДЕТ СО СТРОЙКОЙ, ЕСЛИ СНЯТЬ ПОТОЛОК НАРЯДОВ.
 *
 * <p>Вопрос дизайнера 06.09.2026: наряды стоят абзаца правил, счёта за столом и
 * тривиального первого хода — а что они, собственно, предотвращают? Заявленный
 * страх один: богатый игрок разворачивает всю базу за одно действие и больше
 * не открывает Инфраструктуру. Стенд меряет ровно это.
 *
 * <p>Потолок снимается значением 99 у ops_per_military_building, а не удалением
 * ключа: без ключа движок возвращается к СТАРОМУ правилу надбавки, и замер
 * мерил бы не то.
 */
public final class СтройкаБезПотолка {

    private СтройкаБезПотолка() {
    }

    private static void прогон(String имя, int бюджет, int игроков, int партий) {
        int[] строек = new int[1];
        long[] сумма = new long[1];
        int[] макс = new int[1];
        int[] большихТрёх = new int[1];
        long[] денегВКонце = new long[1];

        for (int i = 0; i < партий; i++) {
            long seed = 7000L + i;
            GameConfig base = GameConfig.buildCached(
                GameConfig.DEFAULT_RULESET, игроков, seed, null, null);
            Ruleset rules = base.ruleset.copy();
            rules.override("actions.build.ops_per_military_building", бюджет);
            GameConfig cfg = new GameConfig(rules, base.content, игроков, seed,
                base.dataRoot, base.boardSides, base.scenarioId, base.cuFacing,
                base.scenarioFile);
            GameState s = Setup.buildGame(cfg);
            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(kelium.agents.Bots.create("balanced", seat,
                    new Random(seed * 31 + seat), игроков));
            }
            new GameEngine(s, боты, ev -> {
                if (!"action".equals(ev.get("type")) || !"build".equals(ev.get("action"))) {
                    return;
                }
                if (!(ev.get("telemetry") instanceof Map<?, ?> tel) || tel.get("ops") == null) {
                    return;
                }
                int ops = ((Number) tel.get("ops")).intValue();
                строек[0]++;
                сумма[0] += ops;
                макс[0] = Math.max(макс[0], ops);
                if (ops >= 3) {
                    большихТрёх[0]++;
                }
            }).run();
            for (int seat = 0; seat < игроков; seat++) {
                денегВКонце[0] += s.player(seat).resources.coin();
            }
        }
        System.out.printf("%-22s %d игроков | Строек %4d | средне %.2f операции | "
            + "макс за действие %d | 3+ операций в %.0f%% Строек | монет к концу %.1f%n",
            имя, игроков, строек[0], сумма[0] / (double) строек[0], макс[0],
            100.0 * большихТрёх[0] / строек[0],
            денегВКонце[0] / (double) (партий * игроков));
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        for (int n : new int[] {2, 3, 4}) {
            прогон("наряды (как сейчас)", 1, n, партий);
            прогон("БЕЗ ПОТОЛКА", 99, n, партий);
            System.out.println();
        }
    }
}
