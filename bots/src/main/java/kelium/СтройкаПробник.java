package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * СТРОЙКА БЕЗ ПОТОЛКА — что получилось.
 *
 * <p>Потолок операций снят 06.09.2026 целиком: ни надбавки за объём, ни нарядов
 * от военных зданий. Ограничивают деньги и геометрия. Взамен появилась четвёртая
 * операция — РЕМОНТ за напечатанную цену здания, и снос стал платным вместо
 * доходного.
 *
 * <p>Пробник смотрит на три вещи: не выродилась ли Стройка в «поставил всё за
 * один ход», пользуются ли ремонтом, и — главное — перестала ли монета быть
 * бесполезным излишком. До правки к концу партии у игроков оставалось 7-13
 * непотраченных монет при том, что очков монета не даёт вовсе.
 */
public final class СтройкаПробник {

    private СтройкаПробник() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        System.out.println("свод: " + GameConfig.DEFAULT_RULESET);
        for (int n = 2; n <= 4; n++) {
            int[] строек = new int[1];
            long[] сумма = new long[1];
            int[] макс = new int[1];
            int[] большихТрёх = new int[1];
            long[] монетВКонце = new long[1];
            for (int i = 0; i < партий; i++) {
                long seed = 7000L + i;
                GameState s = Setup.buildGame(
                    GameConfig.buildCached(GameConfig.DEFAULT_RULESET, n, seed, null, null));
                List<Agent> боты = new ArrayList<>();
                for (int seat = 0; seat < n; seat++) {
                    боты.add(kelium.agents.Bots.create("balanced", seat,
                        new Random(seed * 31 + seat), n));
                }
                new GameEngine(s, боты, ev -> {
                    if (!"action".equals(ev.get("type")) || !"build".equals(ev.get("action"))
                            || !(ev.get("telemetry") instanceof Map<?, ?> tel)
                            || tel.get("ops") == null) {
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
                for (int seat = 0; seat < n; seat++) {
                    монетВКонце[0] += s.player(seat).resources.coin();
                }
            }
            System.out.printf("%d игроков | Строек %4d | средне %.2f операции | макс %d | "
                + "3+ операций в %.0f%% | монет не потрачено к концу %.1f%n",
                n, строек[0], сумма[0] / (double) строек[0], макс[0],
                100.0 * большихТрёх[0] / строек[0],
                монетВКонце[0] / (double) (партий * n));
        }
    }
}
