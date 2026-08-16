package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Hex;
import kelium.core.SpawnTile;
import kelium.engine.Scenario;

/**
 * ПРАВКА КЕЛЕМИЯ ИЗ РАСКЛАДКИ действует только на ЛИЦО ВЕРХНЕГО ТАЙЛА.
 *
 * <p>Правило дизайнера (16.08.2026): {@code kelium_delta} в конструкторе — это
 * правка одного напечатанного числа, а не свойство гекса. Значит она НЕ трогает
 * ни оборот того же тайла, ни второй тайл двойной стопки: то, что напечатано на
 * картоне, правкой раскладки не меняется.
 *
 * <p>До этого правка вплавлялась прямо в {@code faceKelium}, а {@code popStack()}
 * возвращал лицо нижнему тайлу — и правка доставалась ему тоже.
 */
class SpawnTileDeltaTest {

    private static Hex hexWithTile(int stack, int delta) {
        Map<String, Object> hx = new java.util.LinkedHashMap<>();
        hx.put("q", 0);
        hx.put("r", 0);
        hx.put("content", "kelium_tile");
        hx.put("stack", stack);
        if (delta != 0) {
            hx.put("kelium_delta", delta);
        }
        Map<String, Object> scn = Map.of("id", "t", "players", 2, "hexes", List.of(hx));
        Hex h = Scenario.buildFieldFromScenario(scn).field().get("h0_0");
        assertTrue(h != null && h.hasSpawnTile(), "тайл зарождения должен лежать на гексе");
        return h;
    }

    /** Правка ложится на лицо верхнего тайла — там она и видна с самого начала. */
    @Test
    void theDeltaLandsOnTheTopTilesFace() {
        SpawnTile plain = hexWithTile(1, 0).spawnTile;
        SpawnTile edited = hexWithTile(1, 2).spawnTile;
        assertEquals(plain.faceKelium + 2, edited.topFaceKelium(),
            "лицо верхнего тайла — напечатанное плюс правка");
        assertEquals(edited.topFaceKelium(), edited.kelium,
            "на гекс тайл ложится лицом, значит и лежит на нём правленое число");
    }

    /** ОБОРОТ того же тайла правка не трогает: он напечатан. */
    @Test
    void theBackOfTheSameTileIsUntouched() {
        SpawnTile plain = hexWithTile(1, 0).spawnTile;
        SpawnTile edited = hexWithTile(1, 3).spawnTile;
        assertEquals(plain.backKelium, edited.backKelium,
            "оборот — напечатанное число, правка раскладки его не меняет");
        edited.flip();
        assertEquals(plain.backKelium, edited.kelium,
            "перевернули верхний тайл — на нём ровно напечатанный оборот");
    }

    /** ВТОРОЙ ТАЙЛ стопки ×2 приходит напечатанным — обеими сторонами. */
    @Test
    void theSecondTileOfAStackComesPrinted() {
        SpawnTile plain = hexWithTile(2, 0).spawnTile;
        SpawnTile edited = hexWithTile(2, 4).spawnTile;
        assertEquals(2, edited.stack, "на гексе двойная стопка");

        // Верхний тайл выработан целиком: лицо, потом оборот.
        edited.flip();
        assertTrue(edited.popStack(), "под верхним тайлом лежит второй");

        assertEquals(plain.faceKelium, edited.kelium,
            "второй тайл ложится НАПЕЧАТАННЫМ лицом, без правки верхнего");
        edited.flip();
        assertEquals(plain.backKelium, edited.kelium,
            "и оборот второго тайла тоже напечатанный");
    }
}
