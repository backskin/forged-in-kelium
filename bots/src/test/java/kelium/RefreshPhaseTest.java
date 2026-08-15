package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.RandomAgent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.core.Agent;

/**
 * Этап Обновления: контейнеры на пустые гексы и снятие ОДНОГО кубика урона.
 * Больше в эту фазу поле не трогается — в частности, келемий на тайлах
 * зарождения НЕ восстанавливается (движок раньше это делал по ошибке, из-за
 * чего тайлы всегда выглядели нетронутыми).
 */
class RefreshPhaseTest {

    private static GameState playGame(long seed) {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new RandomAgent(seat, new Random(seed * 31 + seat)));
        }
        GameEngine.playGame(s, agents, ev -> { });
        return s;
    }

    @Test
    void keliumOnTilesNeverGrowsBackDuringTheGame() {
        GameState s = playGame(60L);
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile == null) {
                continue;
            }
            int ceiling = h.spawnTile.flipped ? h.spawnTile.backKelium : h.spawnTile.faceKelium;
            assertTrue(h.spawnTile.kelium <= ceiling,
                "на тайле " + h.id + " келемия больше, чем было напечатано ("
                + h.spawnTile.kelium + " > " + ceiling + ") — значит он восполняется");
        }
    }

    @Test
    void miningPermanentlyReducesTheTile() {
        // Прямая проверка: выкопали — убыло и осталось убывшим до конца партии.
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 3L, null, null);
        GameState s = Setup.buildGame(cfg);
        Hex tile = null;
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile != null && h.spawnTile.kelium >= 2) {
                tile = h;
                break;
            }
        }
        assertTrue(tile != null, "на поле есть тайл зарождения");
        int before = tile.spawnTile.kelium;
        tile.spawnTile.kelium -= 1;                 // имитируем добычу одного келемия
        int afterMining = tile.spawnTile.kelium;
        assertEquals(before - 1, afterMining);

        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new RandomAgent(seat, new Random(seat + 7)));
        }
        GameEngine.playGame(s, agents, ev -> { });
        // Тайл либо выработан целиком (ушёл с поля), либо перевёрнут на оборот,
        // либо на нём НЕ БОЛЬШЕ келемия, чем осталось после добычи. Обратного
        // роста быть не может ни при каких обстоятельствах.
        if (tile.spawnTile == null) {
            return;                       // выработан целиком — это законный исход
        }
        if (tile.spawnTile.flipped) {
            assertTrue(tile.spawnTile.kelium <= tile.spawnTile.backKelium,
                "на обороте не может быть больше, чем напечатано на обороте");
            return;
        }
        assertTrue(tile.spawnTile.kelium <= afterMining,
            "келемий на тайле вырос сам собой: было " + afterMining
                + ", стало " + tile.spawnTile.kelium);
    }
}
