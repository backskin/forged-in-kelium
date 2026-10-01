package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import kelium.core.GameState;
import kelium.engine.Срабатывания;
import kelium.support.Fix;

/**
 * Прокачка спец-действия (решение Влада 01.10.2026): установленная карта
 * «Каждый раз, когда начинаешь свой ход, получи ещё одно спец-действие» даёт
 * второе спец-действие в каждом своём ходу — и не в чужом.
 */
class ПрокачкаСпецTest {

    private static String картаПрокачки(GameState s) {
        for (Map<String, Object> e : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            if (e.get("bottom") instanceof Map<?, ?> низ && низ.get("когда") instanceof Map<?, ?> когда
                    && "ход".equals(когда.get("событие"))) {
                return String.valueOf(e.get("id"));
            }
        }
        return null;
    }

    private static Map<String, Object> начало(int место) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", "turn_orders");
        e.put("seat", место);
        e.put("coincided", false);
        e.put("bottom_open", false);
        return e;
    }

    @Test
    void своёНачалоХодаДаётСпецДействие() {
        GameState s = Fix.game("1.48.0", 2, 13L);
        String id = картаПрокачки(s);
        assertNotNull(id, "в колоде свода 1.48.0 есть карта прокачки");
        s.player(0).arsenalInstalled.add(id);
        s.journal.startTurn(0);
        Срабатывания.раздать(s, начало(0));
        assertEquals(1, s.journal.of(0).specBonus, "ещё одно спец-действие в свой ход");
    }

    @Test
    void чужоеНачалоХодаНеДаёт() {
        GameState s = Fix.game("1.48.0", 2, 13L);
        String id = картаПрокачки(s);
        assertNotNull(id);
        s.player(0).arsenalInstalled.add(id);
        s.journal.startTurn(1);
        Срабатывания.раздать(s, начало(1));
        assertEquals(0, s.journal.of(0).specBonus, "чужой ход прокачку не запускает");
    }
}
