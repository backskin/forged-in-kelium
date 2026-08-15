package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;

/**
 * Характеры «Исследователь» и «Хаос» — ТАКИЕ ЖЕ ОБУЧЕННЫЕ ЛИНИИ ГЕНОМОВ, как
 * остальные (решение дизайнера 12.08.2026: «забудь, что Исследователь и Хаос —
 * жёсткие правила»). Прошитых классов-характеров в проекте больше нет.
 *
 * <p>Проверяем: оба характера собираются фабрикой, реально ходят, партии с ними
 * доигрывают, и их геномы отличаются друг от друга и от базового — иначе это
 * один бот под тремя именами.
 */
class NewBotsTest {

    /** Счётчик не-пасов у конкретного места (проверка «бот ходит»). */
    private static final class ActivityAgent extends Agent {
        final Agent inner;
        int nonPassPicks = 0;

        ActivityAgent(Agent inner) {
            super(inner.seat, inner.name);
            this.inner = inner;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            Choice pick = inner.choose(state, options, context);
            if (pick != null && !"pass".equals(pick.kind()) && pick.payload() != null) {
                nonPassPicks++;
            }
            return pick;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }
    }

    @Test
    void explorerAndChaosPlayStable() {
        int games = 4;
        for (long seed = 7000; seed < 7000 + games; seed++) {
            GameConfig cfg = GameConfig.build(4, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            ActivityAgent explorer = new ActivityAgent(
                Bots.create("explorer", 0, new Random(seed * 31 + 1), 4));
            ActivityAgent chaos = new ActivityAgent(
                Bots.create("chaos", 1, new Random(seed * 31 + 2), 4));
            agents.add(explorer);
            agents.add(chaos);
            agents.add(new HeuristicAgent(2, new Random(seed * 31 + 3), "economist"));
            agents.add(new HeuristicAgent(3, new Random(seed * 31 + 4), "defender"));

            Map<String, Object> result = GameEngine.playGame(s, agents, null);
            assertFalse(result.isEmpty(), "партия должна завершиться результатом (сид " + seed + ")");
            assertTrue(result.containsKey("winner"), "должен быть победитель (сид " + seed + ")");
            assertTrue(explorer.nonPassPicks > 0,
                "Исследователь не сделал ни одного хода (сид " + seed + ")");
            assertTrue(chaos.nonPassPicks > 0,
                "Хаос не сделал ни одного хода (сид " + seed + ")");
        }
    }

    /** Один и тот же экземпляр бота переиспользуется между партиями без сбоев. */
    @Test
    void sameBotInstanceSurvivesSeveralGames() {
        Agent explorer = Bots.create("explorer", 0, new Random(1), 4);
        for (long seed = 8000; seed < 8003; seed++) {
            GameConfig cfg = GameConfig.build(4, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            agents.add(explorer);
            for (int seat = 1; seat < 4; seat++) {
                agents.add(new HeuristicAgent(seat, new Random(seed * 31 + seat), "aggressor"));
            }
            Map<String, Object> result = GameEngine.playGame(s, agents, null);
            assertTrue(result.containsKey("winner"), "партия должна доиграть (сид " + seed + ")");
        }
    }

    /** Линии характеров РАЗНЫЕ: перекос генома есть даже до обучения. */
    @Test
    void characterGenomesDiffer() {
        Genome base = Genome.defaults();
        Genome chaos = base.withProfile("chaos");
        Genome explorer = base.withProfile("explorer");
        assertTrue(chaos.get("aggression", 0) > base.get("aggression", 0),
            "Хаос агрессивнее базового");
        assertTrue(chaos.get("action.movement", 0) > explorer.get("action.movement", 0),
            "Хаос двигается охотнее Исследователя");
        assertTrue(explorer.get("action.market", 0) > chaos.get("action.market", 0),
            "Исследователь охотнее ходит на рынок");
        for (String c : Bots.CHARACTERS) {
            assertNotEquals(0.0, base.withProfile(c).get("aggression", 0),
                "характер " + c + " должен иметь осмысленные веса");
        }
    }
}
