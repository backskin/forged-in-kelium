package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;

/** Сборка GameState на 2/3/4 игроков: базовые инварианты подготовки. */
class GameSetupTest {

    private GameState build(int players) {
        GameConfig cfg = GameConfig.build(players, 7L);
        return Setup.buildGame(cfg);
    }

    /**
     * Сколько монет даёт СВОД, а не сколько их было когда-то. Дизайнер это
     * число крутит (5 в 1.24.0, 3 в 1.26.0), и вбитая в тест пятёрка ломала
     * его на каждой такой правке, ничего при этом не проверяя.
     */
    private static int стартовыеМонеты() {
        Object v = GameConfig.build(2, 7L).ruleset.get("setup.start_coins", null);
        if (v instanceof java.util.List<?> list && !list.isEmpty()
                && list.get(0) instanceof Number num) {
            return num.intValue();
        }
        return Setup.START_COINS[0];
    }

    @Test
    void buildsForTwoThreeFourPlayers() {
        for (int n : new int[]{2, 3, 4}) {
            GameState s = build(n);
            assertEquals(n, s.numPlayers(), "число игроков");
            assertTrue(s.field.size() > 0, "поле не пустое");
            for (PlayerState p : s.players) {
                // СВОД-старт: ЦУ + энергия + 1 пехота, стартовые монеты ИЗ СВОДА,
                // стартового добытчика НЕТ (решение дизайнера 2026-08-11).
                assertTrue(p.hasCommandCenter(), "у игрока " + p.seat + " есть ЦУ");
                boolean hasMiner = p.buildingsOnField().stream()
                    .anyMatch(b -> b.type == BuildingType.MINER);
                assertTrue(!hasMiner, "у игрока " + p.seat + " НЕТ стартового добытчика (СВОД)");
                // ЧИСЛО БЕРЁТСЯ ИЗ СВОДА, А НЕ ВБИТО В ТЕСТ: дизайнер его крутит
                // (5 в 1.24.0, 3 в 1.26.0), и вбитая пятёрка ломала тест на
                // каждой такой правке, ничего при этом не проверяя.
                assertEquals(стартовыеМонеты(), p.resources.get(kelium.core.Resource.COIN),
                    "стартовые монеты — как в своде");
                assertEquals(1, p.unitsOnField().size(), "1 стартовая пехота");
                assertNotNull(p.startHex, "стартовый гекс задан");
                // С правил 1.6.0 подготовка РАЗДАЁТ карты супер задания, а выбор
                // делает игрок в начале партии (super_objectives.deal = 2).
                assertTrue(p.superObjective != null || !p.superObjectiveOffer.isEmpty(),
                    "супер-задание назначено или предложено на выбор");
            }
        }
    }

    @Test
    void decksAreDealt() {
        GameState s = build(4);
        // Колоды загружены и стартовые руки будут розданы движком; здесь проверяем
        // наличие всех колод.
        for (String d : new String[]{"objectives", "arsenal", "containers", "orders", "market"}) {
            assertTrue(s.decks.containsKey(d), "колода " + d);
        }
    }
}
