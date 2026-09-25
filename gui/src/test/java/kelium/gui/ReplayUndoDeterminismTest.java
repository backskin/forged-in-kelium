package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ОТКАТ ПЕРЕИГРОВКОЙ СТОИТ НА ДЕТЕРМИНИЗМЕ.
 *
 * <p>Окно партии отменяет любое решение так: берёт копию стола, снятую до
 * первого хода, и заново проигрывает ленту решений до нужной точки. Это верно,
 * только если та же лента на той же копии даёт ту же партию. Здесь это и
 * сторожится: партия ботов пишет ленту, потом лента проигрывается на копии
 * начального стола агентами, которые сами ничего не решают, — и итог обязан
 * совпасть до последнего очка.
 */
class ReplayUndoDeterminismTest {

    @Test
    void лентаНаКопииНачальногоСтолаДаётТуЖеПартию() {
        for (long seed : new long[]{11L, 20260925L}) {
            GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, seed,
                null, null, null, null, null);
            HotSeatWindow.StartTable table = HotSeatWindow.StartTable.of(Setup.buildGame(cfg));
            GameState live = table.fresh();

            List<Integer> moves = new ArrayList<>();
            List<Agent> bots = new ArrayList<>();
            for (int s = 0; s < 2; s++) {
                bots.add(kelium.agents.BotCatalog.create("builder:1", s,
                    new Random(seed * 131 + s + 1), 2));
            }
            Map<String, Object> first = GameEngine.playGame(live,
                MoveLog.recording(bots, moves), e -> { });

            GameState again = table.fresh();
            List<Agent> blind = new ArrayList<>();
            for (int s = 0; s < 2; s++) {
                blind.add(new Agent(s, "лента") {
                    @Override
                    public Choice choose(GameState st, List<Choice> o, Map<String, Object> c) {
                        throw new IllegalStateException("лента кончилась раньше партии");
                    }
                });
            }
            Map<String, Object> second = GameEngine.playGame(again,
                MoveLog.playback(blind, moves, null), e -> { });

            assertEquals(String.valueOf(first.get("winner")), String.valueOf(second.get("winner")),
                "победитель разошёлся, сид " + seed);
            assertEquals(String.valueOf(first.get("rounds")), String.valueOf(second.get("rounds")),
                "число раундов разошлось, сид " + seed);
            for (int s = 0; s < 2; s++) {
                assertEquals(live.players.get(s).resources.toString(),
                    again.players.get(s).resources.toString(),
                    "ресурсы места " + s + " разошлись, сид " + seed);
            }
        }
    }
}
