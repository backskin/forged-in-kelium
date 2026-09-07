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
 * ЧТО ОСТАНАВЛИВАЕТ СТРОЙКУ — НАРЯДЫ ИЛИ ДЕНЬГИ.
 *
 * <p>Вопрос дизайнера 06.09.2026: ограничение «наряд за каждое военное здание»
 * стоит абзаца правил и лишнего счёта за столом. Оправдано оно только если
 * РЕАЛЬНО СВЯЗЫВАЕТ — то есть если игрок хоть сколько-нибудь часто перестаёт
 * строить потому, что кончились наряды, а не потому, что кончились монеты.
 * Правило, которое никогда не является связывающим, это чистые накладные
 * расходы.
 */
public final class НарядыИлиДеньги {

    private НарядыИлиДеньги() {
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int[] всего = new int[1];
        int[] нарядами = new int[1];
        int[] бюджет1 = new int[1];
        long[] суммаНарядов = new long[1];
        long[] суммаБюджета = new long[1];
        int[] монетПосле = new int[1];

        for (int n = 2; n <= 4; n++) {
            всего[0] = нарядами[0] = бюджет1[0] = монетПосле[0] = 0;
            суммаНарядов[0] = суммаБюджета[0] = 0;
            for (int i = 0; i < партий; i++) {
                long seed = 5000L + i;
                GameState s = Setup.buildGame(
                    GameConfig.buildCached(GameConfig.DEFAULT_RULESET, n, seed, null, null));
                List<Agent> боты = new ArrayList<>();
                for (int seat = 0; seat < n; seat++) {
                    боты.add(kelium.agents.Bots.create("balanced", seat,
                        new Random(seed * 31 + seat), n));
                }
                new GameEngine(s, боты, ev -> {
                    if (!"action".equals(ev.get("type"))
                            || !"build".equals(ev.get("action"))) {
                        return;
                    }
                    Object t = ev.get("telemetry");
                    if (!(t instanceof Map<?, ?> tel) || tel.get("ops") == null) {
                        return;
                    }
                    всего[0]++;
                    суммаНарядов[0] += ((Number) tel.get("ops")).intValue();
                    суммаБюджета[0] += ((Number) tel.get("op_budget")).intValue();
                    if (Boolean.TRUE.equals(tel.get("stopped_by_ops"))) {
                        нарядами[0]++;
                        // Упёрся в наряды, а монеты ещё были? Только тогда
                        // правило реально что-то отняло.
                        if (((Number) tel.get("coins_left")).intValue() >= 1) {
                            монетПосле[0]++;
                        }
                    }
                    if (((Number) tel.get("op_budget")).intValue() == 1) {
                        бюджет1[0]++;
                    }
                }).run();
            }
            System.out.printf(
                "%d игроков: Строек %d | средне нарядов потрачено %.2f из %.2f | "
                + "упёрлись в наряды %.0f%% | из них с деньгами на руках %.0f%% | "
                + "бюджет был 1 наряд в %.0f%% Строек%n",
                n, всего[0],
                суммаНарядов[0] / (double) всего[0],
                суммаБюджета[0] / (double) всего[0],
                100.0 * нарядами[0] / всего[0],
                нарядами[0] == 0 ? 0 : 100.0 * монетПосле[0] / нарядами[0],
                100.0 * бюджет1[0] / всего[0]);
        }
    }
}
