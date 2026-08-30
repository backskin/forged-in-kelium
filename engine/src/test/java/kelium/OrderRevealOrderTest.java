package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * ПОРЯДОК ВСКРЫТИЯ ПРИКАЗОВ (правило дизайнера 30.08.2026).
 *
 * <p>Круг раунда идёт так: сначала все ОДНОВРЕМЕННО выбирают карту и кладут её
 * взакрытую; потом ПО ОЧЕРЕДИ, с первого игрока, карты ВСКРЫВАЮТ. Эффекты
 * вскрытия — совпадение верхних приказов (блок) и открытие нижней половины —
 * считаются только с теми, кто вскрылся РАНЬШЕ в этом круге. У первого игрока
 * круга раньше не вскрылся никто, и встретить он не может никого.
 *
 * <p>Движок считал иначе: он собирал верхние приказы ВСЕХ мест сразу, до первого
 * хода, и игрок получал блок от соседа, который свою карту ещё не открыл.
 */
class OrderRevealOrderTest {

    /** Агент, который всегда вскрывает карту с заданным верхним приказом. */
    private static final class SameOrderAgent extends Agent {
        private final String top;

        SameOrderAgent(int seat, String top) {
            super(seat, "same#" + seat);
            this.top = top;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> ctx) {
            if ("reveal_order".equals(ctx.get("kind"))) {
                var orders = kelium.dataio.Ctx.cards(state, "orders");
                for (Choice c : options) {
                    Map<String, Object> card = orders.byId(String.valueOf(c.payload()));
                    if (card != null && top.equals(card.get("top"))) {
                        return c;
                    }
                }
            }
            for (Choice c : options) {
                if (!"pass".equals(c.kind()) && c.payload() != null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    @Test
    void первыйВскрывшийВКругеНеВстречаетНикого() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 3, 11L, null, null));
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            agents.add(new SameOrderAgent(seat, "operation"));
        }

        // Первый круг первого раунда: чей ход — тот и вскрывается, по порядку.
        Map<Integer, Boolean> блок = new LinkedHashMap<>();
        Map<Integer, Boolean> низОткрыт = new LinkedHashMap<>();
        new GameEngine(s, agents, ev -> {
            if (!"turn_orders".equals(ev.get("type"))) {
                return;
            }
            int seat = (Integer) ev.get("seat");
            if (!"operation".equals(ev.get("top")) || блок.containsKey(seat)) {
                return;                       // считаем только первое вскрытие
            }
            блок.put(seat, Boolean.TRUE.equals(ev.get("coincided")));
            низОткрыт.put(seat, Boolean.TRUE.equals(ev.get("bottom_open")));
        }).run();

        assertEquals(3, блок.size(), "все три места обязаны вскрыть «Операцию»");
        List<Integer> порядок = new ArrayList<>(блок.keySet());
        int первый = порядок.get(0);
        assertFalse(блок.get(первый),
            "первый вскрывший в круге не может получить блок: до него не вскрылся никто");
        assertFalse(низОткрыт.get(первый),
            "и нижнюю половину ему открывать тоже некому");
        for (int i = 1; i < порядок.size(); i++) {
            assertTrue(блок.get(порядок.get(i)),
                "место " + (порядок.get(i) + 1) + " вскрыло тот же приказ после соседа — "
                    + "совпадение обязано сработать");
        }
    }
}
