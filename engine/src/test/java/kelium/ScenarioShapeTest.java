package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.engine.Scenario;

/**
 * Формат shape с ОТСТУПАМИ рядов: {offset: N, count: M} = гексы в колонках
 * N+1..N+M общей сетки; колонки в special — абсолютные. Особый гекс вне
 * границ формы — громкая ошибка (раньше молча выпадал, терялись старты).
 */
class ScenarioShapeTest {

    private static Map<String, Object> scenario(List<Object> shape, List<Object> special) {
        return Map.of("id", "test", "shape", shape, "special", special);
    }

    @Test
    void offsetRowsProduceAbsoluteColumns() {
        // ряд 1: колонки 1..2; ряд 2 со сдвигом 2: колонки 3..4
        List<Object> shape = List.of(2, Map.of("offset", 2, "count", 2));
        List<Object> special = List.of(
            Map.of("row", 2, "col", 3, "content", "player_start", "seat", 0));
        List<Object> hexes = Scenario.expandedHexes(scenario(shape, special));
        assertEquals(4, hexes.size(), "2 + 2 гекса");

        // ряд 2 (row0=1, even-r): q = col0 - (1+1)/2 = col0 - 1
        boolean foundStart = false;
        for (Object o : hexes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hx = (Map<String, Object>) o;
            if ("player_start".equals(hx.get("content"))) {
                foundStart = true;
                assertEquals(1, hx.get("q"), "col 3 (col0=2) в ряду 2 => q=1");
                assertEquals(1, hx.get("r"));
            }
        }
        assertTrue(foundStart, "старт должен попасть в развёрнутый список");
    }

    @Test
    void specialOutsideShapeIsLoudError() {
        List<Object> shape = List.of(2, 2);
        List<Object> special = List.of(
            Map.of("row", 2, "col", 5, "content", "player_start", "seat", 1));
        Scenario.ScenarioError e = assertThrows(Scenario.ScenarioError.class,
            () -> Scenario.expandedHexes(scenario(shape, special)));
        assertTrue(e.getMessage().contains("вне границ формы"), e.getMessage());
    }

    @Test
    void plainIntRowsStillWork() {
        List<Object> shape = List.of(3, 4, 3);
        List<Object> hexes = Scenario.expandedHexes(scenario(shape, List.of()));
        assertEquals(10, hexes.size());
    }
}
