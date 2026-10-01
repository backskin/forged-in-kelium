package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import kelium.core.GameState;
import kelium.engine.Срабатывания;
import kelium.rules.Ruleset;
import kelium.support.Fix;

/**
 * Ветка сыграна, только если что-то сделала (свод 1.48.0, {@code cards.branch_must_act}):
 * «Бой» без выстрела и «Добыть» без добычи не запускают карт «каждый раз, когда
 * играешь ветку …».
 */
class ВеткаСыгранаTest {

    private static Map<String, Object> ветка(String действие, String ключ, int значение) {
        Map<String, Object> e = new HashMap<>();
        e.put("type", "action");
        e.put("seat", 0);
        e.put("action", действие);
        e.put("ok", true);
        e.put("telemetry", Map.of(ключ, значение));
        return e;
    }

    @Test
    void пустойБойИПустаяДобычаНеСыграны() {
        GameState s = Fix.game(2, 7L);
        Ruleset rs = kelium.dataio.Ctx.rules(s);
        Object было = rs.get("cards.branch_must_act", false);
        rs.override("cards.branch_must_act", true);
        try {
            assertFalse(Срабатывания.сыграна(s, ветка("combat", "battle", 0)), "бой без выстрела");
            assertTrue(Срабатывания.сыграна(s, ветка("combat", "battle", 1)), "бой с выстрелом");
            assertFalse(Срабатывания.сыграна(s, ветка("mining", "kelium", 0)), "добыча без келемия");
            assertTrue(Срабатывания.сыграна(s, ветка("mining", "kelium", 2)), "добыча с келемием");
        } finally {
            rs.override("cards.branch_must_act", было);
        }
    }

    @Test
    void безКлючаВеткаСыгранаКакРаньше() {
        GameState s = Fix.game(2, 7L);
        Ruleset rs = kelium.dataio.Ctx.rules(s);
        Object было = rs.get("cards.branch_must_act", false);
        rs.override("cards.branch_must_act", false);
        try {
            assertTrue(Срабатывания.сыграна(s, ветка("combat", "battle", 0)));
        } finally {
            rs.override("cards.branch_must_act", было);
        }
    }
}
