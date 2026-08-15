package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.SpawnTile;

/**
 * ДВОЙНОЙ ТАЙЛ ЗАРОЖДЕНИЯ (уточнения дизайнера 13.08.2026).
 *
 * <ol>
 *   <li>двойной тайл при проверке конца партии считается за ДВА тайла — раньше
 *       считались гексы, и стопка ×2 обрывала партию на раунд раньше срока;</li>
 *   <li>устройство стопки: сначала ОДИН тайл вырабатывается полностью по обычным
 *       правилам (лицо → оборот → снят), и только потом ПОД НИМ появляется НОВЫЙ
 *       такой же тайл, с которым происходит то же самое.</li>
 * </ol>
 */
class DoubleSpawnTileTest {

    /** Стопка разворачивается по одному тайлу: лицо → оборот → следующий тайл. */
    @Test
    void stackUnfoldsOneTileAtATime() {
        SpawnTile t = new SpawnTile(false, 4, 3, 2);   // большое зарождение, стопка ×2
        assertEquals(4, t.kelium, "сверху лежит лицо первого тайла");
        assertFalse(t.flipped);
        assertEquals(2, t.stack, "в стопке два тайла");

        t.kelium = 0;                                   // лицо выработано
        t.flip();
        assertTrue(t.flipped, "первый тайл перевёрнут на оборот");
        assertEquals(3, t.kelium, "на обороте столько же, сколько напечатано");

        t.kelium = 0;                                   // оборот выработан
        assertTrue(t.popStack(), "под первым тайлом лежит ВТОРОЙ — гекс остаётся закрытым");
        assertEquals(1, t.stack, "в стопке остался один тайл");
        assertFalse(t.flipped, "новый тайл лежит ЛИЦОМ вверх — он такой же новый");
        assertEquals(4, t.kelium, "и келемия на нём столько же, сколько на лице");

        t.kelium = 0;
        t.flip();
        t.kelium = 0;
        assertFalse(t.popStack(), "второй тайл выработан — тайлов больше нет, гекс свободен");
    }

    /** Двойной тайл при подсчёте «сколько осталось» весит два. */
    @Test
    void doubleTileCountsAsTwoForEndCondition() {
        SpawnTile single = new SpawnTile(false, 4, 3, 1);
        SpawnTile doubled = new SpawnTile(false, 4, 3, 2);
        assertEquals(1, weight(single), "одиночный тайл весит один");
        assertEquals(2, weight(doubled), "двойной тайл весит ДВА");

        // выработали лицо первого тайла в стопке — вес не меняется: под ним ещё тайл
        doubled.kelium = 0;
        doubled.flip();
        assertEquals(2, weight(doubled), "пока стопка не снята, тайлов всё ещё два");

        // сняли первый тайл целиком — остался один
        doubled.kelium = 0;
        doubled.popStack();
        assertEquals(1, weight(doubled), "после снятия первого остаётся один тайл");
    }

    /** Тот же счёт, что делает движок в проверке конца партии. */
    private static int weight(SpawnTile t) {
        return t.kelium > 0 ? Math.max(1, t.stack) : 0;
    }
}
