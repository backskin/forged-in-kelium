package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Storage;

/**
 * ТРИ ЯЧЕЙКИ ПОД ПЛАНШЕТОМ — И НИ ОДНОЙ БОЛЬШЕ (правило дизайнера 31.08.2026).
 *
 * <p>Карт арсенала и контейнеров в руке не бывает: всё, что у игрока есть,
 * лежит в трёх ячейках под планшетом. Ячейка вмещает ЛИБО одну карту арсенала,
 * ЛИБО две карты контейнеров. Отдельно от ячеек лежат контейнеры под «мандатом»
 * и контейнер НА установленной карте с {@code container_slot}.
 *
 * <p>Проверялось это в одну сторону: контейнеры считались по свободным ячейкам,
 * а карта арсенала приходила молча и сверх того. Замер показывал занятых ячеек
 * ЧЕТЫРЕ при пределе три.
 */
class ArsenalCellsTest {

    @Test
    void занятыхЯчеекНикогдаНеБольшеТрёх() {
        int худшее = 0;
        String где = "";
        for (long сид = 1; сид <= 6; сид++) {
            final long seed = сид;
            GameState s = Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 3, seed, null, null));
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < s.numPlayers(); seat++) {
                agents.add(new kelium.support.Fix.FirstChoiceAgent(seat));
            }
            int[] max = {0};
            String[] note = {""};
            new GameEngine(s, agents, ev -> {
                for (var p : s.players) {
                    int занято = Storage.cellsUsed(s, p);
                    if (занято > max[0]) {
                        max[0] = занято;
                        note[0] = "сид " + seed + ", место " + (p.seat + 1)
                            + ": карт арсенала "
                            + (p.arsenalHand.size() + p.arsenalInstalled.size())
                            + ", контейнеров " + p.containers;
                    }
                }
            }).run();
            if (max[0] > худшее) {
                худшее = max[0];
                где = note[0];
            }
        }
        assertTrue(худшее <= 3,
            "занято ячеек " + худшее + " при пределе три — " + где);
    }
}
