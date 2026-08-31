package kelium.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Choice;

/**
 * Проверяет только маршрутизацию точек решения "поле или кнопки"
 * ({@link HotSeatWindow#hexTargets}) — без Swing, без движка, без мыши. Это
 * единственная новая логика в живом окне, у которой можно ошибиться незаметно
 * (не тот payload у не того вида решения), поэтому она стоит отдельной
 * проверки помимо ручного прогона всей партии.
 */
class HotSeatWindowHexRoutingTest {

    private final HotSeatWindow w = new HotSeatWindow(2, 1L, List.of("human", "balanced"));

    @Test
    void bareHexIdKindsRouteToBoard() {
        List<Choice> options = List.of(
            new Choice("build_hex", "h0_1", "h0_1"),
            new Choice("build_hex", "h0_2", "h0_2"));
        Map<String, Integer> targets = w.hexTargets("build_hex", options);
        assertEquals(Map.of("h0_1", 0, "h0_2", 1), targets);
    }

    @Test
    void moveKindReadsDestinationFromPayloadMap() {
        List<Choice> options = List.of(
            new Choice("move", Map.of("uid", 7, "to", "h1_3"), "infantry->h1_3"),
            new Choice("move", Map.of("uid", 7, "to", "h1_4"), "infantry->h1_4"));
        Map<String, Integer> targets = w.hexTargets("move", options);
        assertEquals(Map.of("h1_3", 0, "h1_4", 1), targets);
    }

    @Test
    void maneuverUnitReadsHexFromLabelSuffix() {
        List<Choice> options = List.of(
            new Choice("maneuver_unit", 5, "infantry@h0_1"),
            new Choice("maneuver_unit", 9, "tank@h2_3"));
        Map<String, Integer> targets = w.hexTargets("maneuver_unit", options);
        assertEquals(Map.of("h0_1", 0, "h2_3", 1), targets);
    }

    @Test
    void oneUnresolvableOptionFallsBackToButtonsForTheWholeDecision() {
        // "Всё или ничего": если хоть один вариант не сводится к гексу (тут —
        // карта без ключа "to", каким она не должна быть у настоящего "move",
        // но код не должен на этом упасть, а честно уйти кнопками для ВСЕХ
        // опций, а не нарисовать поле наполовину).
        List<Choice> options = List.of(
            new Choice("move", Map.of("uid", 7, "to", "h1_3"), "infantry->h1_3"),
            new Choice("move", Map.of("uid", 7), "что-то ещё"));
        Map<String, Integer> targets = w.hexTargets("move", options);
        assertNull(targets);
    }

    @Test
    void nonBoardKindNeverRoutesToBoard() {
        List<Choice> options = List.of(
            new Choice("action", "assembly", "assembly"),
            new Choice("action", "mining", "mining"));
        assertNull(w.hexTargets("action", options));
    }

    @Test
    void emptyOptionsNeverRouteToBoard() {
        assertNull(w.hexTargets("build_hex", List.of()));
    }
}
