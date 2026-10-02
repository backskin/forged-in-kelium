package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import kelium.cards.objectives.ЗаданиеИзЯзыка;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/**
 * КОЛОДЫ СВОДА 1.50.0 (решение Влада 02.10.2026): заданий 50, арсенала 40,
 * начальных заданий 12, супер-заданий 12, начального арсенала 8, супер-арсенала
 * 8; у каждой карты печатный текст без жалоб формы.
 */
class НаборКарт150Test {

    @AfterAll
    static void вернутьКолодуПоУмолчанию() {
        kelium.engine.cards.CardRegistry.reset();
        Setup.buildGame(GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    private static long вида(GameConfig cfg, String набор, String kind) {
        return cfg.content.get(набор).entries.stream()
            .filter(e -> kind == null || kind.equals(e.get("kind"))).count();
    }

    @Test
    void размерыКолод() {
        GameConfig cfg = GameConfig.buildCached("1.50.0", 4, 1L, null, null);
        assertEquals(50, вида(cfg, "objectives", null) - вида(cfg, "objectives", "starting"));
        assertEquals(12, вида(cfg, "objectives", "starting"));
        assertEquals(40, вида(cfg, "arsenal", "regular"));
        assertEquals(8, вида(cfg, "arsenal", "starting"));
        assertEquals(12, вида(cfg, "super_objectives", null));
        assertEquals(8, вида(cfg, "super_arsenal", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void картыПечатаютсяБезЖалоб() {
        GameConfig cfg = GameConfig.buildCached("1.50.0", 4, 1L, null, null);
        for (Map<String, Object> e : cfg.content.get("objectives").entries) {
            if (!(e.get("язык") instanceof Map<?, ?> язык)) {
                continue;
            }
            ЗаданиеИзЯзыка з = new ЗаданиеИзЯзыка(String.valueOf(e.get("id")), (Map<String, Object>) язык);
            assertNull(з.лицо().жалоба(), e.get("id") + ": " + з.лицо().жалоба());
            assertFalse(з.лицо().условие().isBlank(), e.get("id") + ": пустое условие");
            // ∞ +1 спец-действие — только у карт без спец-действий в наградах
            if ("СПЕЦ_ДЕЙСТВИЯ".equals(String.valueOf(язык.get("верх")))) {
                assertFalse(String.valueOf(язык.get("награда")).contains("спецДействий")
                    || String.valueOf(язык.get("сверх")).contains("спецДействий"), e.get("id") + ": спец в награде");
            }
        }
        for (Map<String, Object> e : cfg.content.get("arsenal").entries) {
            if ("regular".equals(e.get("kind")) && e.get("bottom") instanceof Map<?, ?> низ
                    && низ.get("когда") != null) {
                String текст = kelium.cards.язык.Срабатывание.текст(низ);
                assertTrue(текст.startsWith("Каждый раз, когда"), e.get("id") + ": " + текст);
                assertFalse(текст.contains("upgrade_building") || текст.contains("gain_per")
                    || текст.contains("permanent_energy"), e.get("id") + ": код вместо слов — " + текст);
            }
        }
    }
}
