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

    @Test
    void buildsForTwoThreeFourPlayers() {
        for (int n : new int[]{2, 3, 4}) {
            GameState s = build(n);
            assertEquals(n, s.numPlayers(), "число игроков");
            assertTrue(s.field.size() > 0, "поле не пустое");
            for (PlayerState p : s.players) {
                // СВОД-старт: ЦУ + энергия + 1 пехота, монеты ПО СВОДУ,
                // стартового добытчика НЕТ (решение дизайнера 2026-08-11).
                assertTrue(p.hasCommandCenter(), "у игрока " + p.seat + " есть ЦУ");
                boolean hasMiner = p.buildingsOnField().stream()
                    .anyMatch(b -> b.type == BuildingType.MINER);
                assertTrue(!hasMiner, "у игрока " + p.seat + " НЕТ стартового добытчика (СВОД)");
                // МОНЕТЫ БЕРУТСЯ ИЗ СВОДА, А НЕ ЗАШИТЫ ЧИСЛОМ. Раньше здесь
                // стояла пятёрка, и правка экономики (заказ 25.08.2026: три
                // монеты на старте) ломала тест, хотя игра работала верно.
                // Сторож должен проверять СОГЛАСИЕ подготовки со сводом, а не
                // помнить чью-то старую цифру.
                int поСводу = kelium.dataio.Ctx.rules(s)
                    .getIntList("setup.start_coins").get(p.seat);
                assertEquals(поСводу, p.resources.get(kelium.core.Resource.COIN),
                    "стартовые монеты обязаны совпадать со сводом");
                assertEquals(1, p.unitsOnField().size(), "1 стартовая пехота");
                assertNotNull(p.startHex, "стартовый гекс задан");
                // С правил 1.6.0 подготовка РАЗДАЁТ карты супер задания, а выбор
                // делает игрок в начале партии (super_objectives.deal = 2).
                // Где лежит карта, зависит от РЕЖИМА супер-заданий: в старых
                // режимах это superObjective/superObjectiveOffer, в режимах
                // «одна карта втайне» (solo5/solo6) — super5Card. Сторож
                // проверяет, что карта РАЗДАНА, а не в каком она поле.
                assertTrue(p.superObjective != null || !p.superObjectiveOffer.isEmpty()
                        || p.super5Card != null,
                    "супер-задание назначено, предложено на выбор или роздано втайне");
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
