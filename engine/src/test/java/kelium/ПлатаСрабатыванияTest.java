package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.engine.Actions;
import kelium.engine.Срабатывания;
import kelium.support.Fix;

/**
 * Карта арсенала с платой (01.10.2026): «Каждый раз, когда играешь ветку …,
 * можешь заплатить 1 келемий: …». Согласился — келемий списан, карта сработала;
 * отказался — карта молчит; платить нечем — вопроса нет.
 */
class ПлатаСрабатыванияTest {

    /** Первая карта колоды свода 1.48.0 с платой на поводе «ветка» или «развилка». */
    private static Map<String, Object> картаСПлатой(GameState s) {
        for (Map<String, Object> e : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            if (e.get("bottom") instanceof Map<?, ?> низ && низ.get("плата") != null
                    && низ.get("когда") instanceof Map<?, ?> когда
                    && ("ветка".equals(когда.get("событие")) || "развилка".equals(когда.get("событие")))) {
                return e;
            }
        }
        return null;
    }

    /** Событие «сыграна ветка», под которое подходит повод карты. */
    private static Map<String, Object> событие(Map<?, ?> когда) {
        String ветка;
        if ("ветка".equals(когда.get("событие"))) {
            ветка = String.valueOf(когда.get("ветка"));
        } else {
            ветка = Actions.FORKS.get(String.valueOf(когда.get("развилка"))).get(0);
        }
        Map<String, Object> e = new HashMap<>();
        e.put("type", "action");
        e.put("seat", 0);
        if (ветка.startsWith("build_")) {
            e.put("action", "build");
            for (var x : Actions.FORK_BUILD.entrySet()) {
                if (("build_" + x.getValue()).equals(ветка)) {
                    e.put("fork", x.getKey());
                }
            }
        } else {
            e.put("action", ветка);
        }
        e.put("ok", true);
        return e;
    }

    /** {келемий после, срабатываний карты}. */
    private static int[] сыграть(boolean платить, int келемия) {
        GameState s = Fix.game("1.48.0", 2, 11L);
        Map<String, Object> карта = картаСПлатой(s);
        assertNotNull(карта, "в колоде свода 1.48.0 есть карта с платой");
        String id = String.valueOf(карта.get("id"));
        PlayerState p = s.player(0);
        p.arsenalInstalled.add(id);
        int было = p.resources.kelium();
        if (было > келемия) {
            p.resources.pay(Resource.KELIUM, было - келемия);
        } else {
            p.resources.add(Resource.KELIUM, келемия - было);
        }
        s.agents.set(0, new Agent(0, "плательщик") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                for (Choice c : options) {
                    if ((c.payload() != null) == платить) {
                        return c;
                    }
                }
                return options.get(0);
            }
        });
        Срабатывания.раздать(s, событие((Map<?, ?>) ((Map<?, ?>) карта.get("bottom")).get("когда")));
        return new int[]{p.resources.kelium(), s.journal.срабатываний(0, id)};
    }

    @Test
    void согласилсяЗаплатилИКартаСработала() {
        int[] итог = сыграть(true, 1);
        assertEquals(0, итог[0], "келемий списан");
        assertEquals(1, итог[1], "карта сработала");
    }

    @Test
    void отказалсяКартаМолчит() {
        int[] итог = сыграть(false, 1);
        assertEquals(1, итог[0], "келемий на месте");
        assertEquals(0, итог[1], "карта не сработала и предела не потратила");
    }

    @Test
    void нечемПлатитьКартаМолчит() {
        int[] итог = сыграть(true, 0);
        assertEquals(0, итог[0]);
        assertEquals(0, итог[1], "без келемия карта не срабатывает");
    }
}
