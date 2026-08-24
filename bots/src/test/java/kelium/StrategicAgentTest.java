package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.agents.RandomAgent;
import kelium.agents.StrategicAgent;
import kelium.agents.WorldView;
import kelium.core.GameState;
import kelium.core.Target;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.core.Agent;

/**
 * Проверки стратегического бота: корректность прицеливания по таблице атак,
 * round-trip генома, и что стратег в партии не падает и не слабее случайного.
 */
class StrategicAgentTest {

    /**
     * Мнение бота о том, кого он может убить, сверяется с БОЕВЫМ РЕЗОЛВЕРОМ —
     * единственным источником правды о том, что чем достаётся.
     *
     * <p>ПОЧЕМУ НЕ С ПЕЧАТНОЙ ТАБЛИЦЕЙ. С двумя атаками (диктовка 24.08.2026)
     * печатная цель — только СПЕЦИАЛЬНАЯ атака за 1 боеприпас; универсальная за
     * 2 достаёт любой тип, а красный модуль печатную цель и вовсе закрывает.
     * Сверять бота с печатной таблицей значит проверять его против той картины,
     * по которой бой уже не идёт.
     *
     * <p>Прежняя версия этого теста была тавтологией: обе стороны равенства
     * считались по одной и той же паре целей, и упасть она не могла никогда.
     */
    @Test
    void canKillAgreesWithTheCombatResolver() {
        GameState s = kelium.support.Fix.game(4, 4000L);
        WorldView wv = new WorldView(s, 0);
        String spot = kelium.support.Fix.freeNeighbour(s, s.player(0).startHex);
        kelium.core.UnitToken мой =
            kelium.support.Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);

        for (UnitType type : UnitType.values()) {
            kelium.core.UnitToken enemy = kelium.support.Fix.unit(s, 1, type, spot);
            boolean достаёт = kelium.engine.CombatResolver.canHit(s, 0, мой, enemy);
            assertEquals(достаёт, wv.canKill(UnitType.INFANTRY, enemy),
                "пехота против " + type + " (" + enemy.category()
                    + "): резолвер говорит " + достаёт);
            if (s.player(0).board.troop.dualCell()) {
                assertTrue(достаёт,
                    "с двумя атаками универсальная достаёт любой тип, а " + type
                        + " оказался недостижим");
            }
            s.player(1).units.remove(enemy);
        }
    }

    /** Геном сериализуется и читается без потерь по обучаемым ключам. */
    @Test
    void genomeRoundTrip() throws Exception {
        Genome g = Genome.defaults().mutate(new Random(7), 0.2);
        Path tmp = Files.createTempFile("genome", ".json");
        g.saveJson(tmp);
        Genome back = Genome.loadJson(tmp);
        for (String key : Genome.TUNABLE_KEYS) {
            assertEquals(g.get(key, -1), back.get(key, -2), 1e-3, "ключ " + key);
        }
        Files.deleteIfExists(tmp);
    }

    /** Стратег доигрывает партию и в среднем не хуже случайного бота по ПО. */
    @Tag("balance")
    @Test
    void strategicNotWorseThanRandom() {
        int stratWins = 0;
        double stratVpSum = 0, randVpSum = 0;
        int games = 12;
        for (long seed = 5000; seed < 5000 + games; seed++) {
            // партия A: стратег на месте 0, остальные случайные
            double sv = playSeat0Vp(seed, true);
            // партия B: случайный на месте 0, остальные случайные (базлайн)
            double rv = playSeat0Vp(seed, false);
            stratVpSum += sv;
            randVpSum += rv;
            if (sv >= rv) {
                stratWins++;
            }
        }
        // Стратег должен в среднем набирать не меньше случайного и выигрывать
        // сравнение в большинстве партий.
        assertTrue(stratVpSum >= randVpSum,
            "стратег суммарно ПО=" + stratVpSum + " должен быть >= random=" + randVpSum);
        assertTrue(stratWins >= games / 2,
            "стратег выиграл сравнение в " + stratWins + "/" + games + " партий");
    }

    private double playSeat0Vp(long seed, boolean strategicSeat0) {
        GameConfig cfg = GameConfig.build(4, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        if (strategicSeat0) {
            agents.add(new StrategicAgent(0, new Random(seed * 31 + 1), Genome.defaults()));
        } else {
            agents.add(new RandomAgent(0, new Random(seed * 31 + 1)));
        }
        for (int seat = 1; seat < 4; seat++) {
            agents.add(new RandomAgent(seat, new Random(seed * 31 + seat + 1)));
        }
        GameEngine.playGame(s, agents, null);
        return kelium.engine.Scoring.scorePlayer(s, 0).getOrDefault("total", 0);
    }

    /** Каждый игрок получает СВОЮ цветную колоду приказов (4 приказа + БЕЗОПАСНОСТЬ). */
    @Test
    void coloredOrderDecksDealt() {
        GameConfig cfg = GameConfig.build(4, 3L);
        GameState s = Setup.buildGame(cfg);
        // dealStart вызывается в начале run(); прогоняем партию и проверяем цвета
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new kelium.agents.RandomAgent(seat, new Random(seat + 1)));
        }
        GameEngine.playGame(s, agents, null);
        java.util.Set<String> colors = new java.util.HashSet<>();
        for (int seat = 0; seat < 4; seat++) {
            String c = s.player(seat).orderColor;
            assertTrue(c != null && !c.isEmpty(), "у игрока " + seat + " нет цвета колоды");
            colors.add(c);
        }
        // все 4 цвета уникальны
        assertEquals(4, colors.size(), "цвета колод должны быть уникальны: " + colors);
    }

    /** Стратег с дефолтным геномом играет полную партию против эвристик без ошибок. */
    @Test
    void strategicPlaysAgainstHeuristics() {
        GameConfig cfg = GameConfig.build(4, 6000L);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        agents.add(new StrategicAgent(0, new Random(1), Genome.defaults()));
        String[] chars = {"aggressor", "defender", "economist"};
        for (int seat = 1; seat < 4; seat++) {
            agents.add(new HeuristicAgent(seat, new Random(seat + 1), chars[seat - 1]));
        }
        Map<String, Object> result = GameEngine.playGame(s, agents, null);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("winner"));
    }
}
