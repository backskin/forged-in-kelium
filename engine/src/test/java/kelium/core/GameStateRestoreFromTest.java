package kelium.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.support.Fix;

/**
 * {@link GameState#restoreFrom} — плумбинг для отмены безопасных действий
 * ({@code UndoableAgent}). Проверяет ИМЕННО то, из-за чего этот метод не
 * реализован простой подменой ссылки: восстановление НА МЕСТЕ (тот же
 * объект), включая поле, доску науки, колоды, счётчики и — отдельно —
 * воспроизводимость ГСЧ после отката.
 */
class GameStateRestoreFromTest {

    @Test
    void restoresPlayerResourcesAndBuildingsInPlace() {
        GameState s = Fix.game(2, 1L);
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 10);
        int coinBefore = p.resources.coin();
        int buildingsBefore = p.buildings.size();

        GameState snap = s.deepCopy(999L);

        p.resources.pay(Resource.COIN, 4);
        Fix.building(s, 0, BuildingType.MINER, anyFreeHex(s), 1);

        assertEquals(coinBefore - 4, p.resources.coin());
        assertEquals(buildingsBefore + 1, p.buildings.size());

        s.restoreFrom(snap, 999L);

        // ТОТ ЖЕ объект PlayerState — не пересоздан, что и требуется: код
        // действия держит ссылку на него через несколько choose() подряд.
        assertTrue(s.player(0) == p);
        assertEquals(coinBefore, p.resources.coin());
        assertEquals(buildingsBefore, p.buildings.size());
    }

    @Test
    void restoresFieldHexOccupancy() {
        GameState s = Fix.game(2, 2L);
        String hexId = anyFreeHex(s);
        Hex liveHex = s.field.get(hexId);
        Hex hexBefore = liveHex.copy();

        GameState snap = s.deepCopy(999L);
        Fix.building(s, 0, BuildingType.MINER, hexId, 1);
        assertFalse(s.field.get(hexId).groundTokens.isEmpty());

        s.restoreFrom(snap, 999L);

        // ТОТ ЖЕ объект Hex — та же причина, что у PlayerState (см. javadoc
        // GameState.restoreFrom): код действия держит свою ссылку на гекс.
        assertTrue(s.field.get(hexId) == liveHex);
        assertEquals(hexBefore.groundTokens, s.field.get(hexId).groundTokens);
    }

    @Test
    void restoresRoundAndCircleCounters() {
        GameState s = Fix.game(2, 3L);
        s.round = 3;
        s.circle = 2;
        GameState snap = s.deepCopy(999L);
        s.round = 8;
        s.circle = 4;

        s.restoreFrom(snap, 999L);

        assertEquals(3, s.round);
        assertEquals(2, s.circle);
    }

    @Test
    void rngIsDeterministicAfterRestore() {
        GameState s = Fix.game(2, 4L);
        GameState snap = s.deepCopy(999L);

        // Раскрутить ГСЧ так, будто действие что-то нарандомило.
        s.rng.nextInt();
        s.rng.nextInt();

        s.restoreFrom(snap, 999L);

        Random expected = new Random(999L);
        assertEquals(expected.nextInt(), s.rng.nextInt());
        assertEquals(expected.nextInt(), s.rng.nextInt());
    }

    @Test
    void snapshotItselfStaysUntouchedAcrossRepeatedRestores() {
        // Второй откат к ТОЙ ЖЕ записи снимка должен дать тот же результат —
        // снимок не должен портиться первым restoreFrom (иначе повторная
        // попытка "попробовать иначе" откатывала бы не туда).
        GameState s = Fix.game(2, 5L);
        PlayerState p = s.player(0);
        int coinBefore = p.resources.coin();
        GameState snap = s.deepCopy(999L);

        p.resources.add(Resource.COIN, 7);
        s.restoreFrom(snap, 999L);
        assertEquals(coinBefore, p.resources.coin());

        p.resources.add(Resource.COIN, 3);
        s.restoreFrom(snap, 999L);
        assertEquals(coinBefore, p.resources.coin());
    }

    /** Любой обычный гекс без нейтрала и тайла зарождения — где угодно на поле. */
    private static String anyFreeHex(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == HexKind.NORMAL && h.spawnTile == null && !h.hasNeutral()) {
                return h.id;
            }
        }
        throw new IllegalStateException("на этом поле нет свободного гекса");
    }
}
