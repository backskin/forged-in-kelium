package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.agents.ExploitAgent;
import kelium.agents.Genome;
import kelium.agents.HumanLikeAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.support.Fix;

/**
 * ДВЕ СЕМЬИ БОТОВ: «живые» (помнят обиды, заводятся, ошибаются как люди) и
 * «ищейки» (ищут дыры в правилах, а не победу).
 *
 * <p>Тесты сторожат СВОЙСТВА, а не числа: сила ботов меняется с каждой правкой
 * правил, и тест, прибитый к «24% побед», сломается на первой же балансовой
 * правке. Проверяем то, что должно держаться всегда.
 */
class BotFamiliesTest {

    /** Обида появляется от удара по МОИМ жетонам и растёт с тяжестью потери. */
    @Test
    void grudgeGrowsOnlyForHitsAgainstMe() {
        HumanLikeAgent me = HumanLikeAgent.normal(0, new Random(1), Genome.defaults(), 4);
        double[] before = me.grudges();

        // Удар по чужому жетону — мне безразлично.
        me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 1,
            "victim_owner", 2, "victim", "infantry", "destroyed", true));
        assertTrue(me.grudges()[1] == before[1], "чужие ссоры обиды не создают");

        // Удар по МОЕМУ жетону — обида на обидчика.
        me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 1,
            "victim_owner", 0, "victim", "infantry", "destroyed", false));
        double scratched = me.grudges()[1];
        assertTrue(scratched > 0, "царапина по моему жетону — уже обида");

        // Уничтожение тяжелее царапины, снос ЦУ — тяжелее всего.
        me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 1,
            "victim_owner", 0, "victim", "infantry", "destroyed", true));
        double killed = me.grudges()[1];
        assertTrue(killed - scratched > scratched,
            "уничтожение обиднее царапины: " + scratched + " → " + killed);
        me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 2,
            "victim_owner", 0, "victim", "command_center", "destroyed", true));
        assertTrue(me.grudges()[2] > killed - scratched,
            "снос ЦУ — самая тяжёлая обида");
    }

    /** Обида ЗАБЫВАЕТСЯ со временем, но не мгновенно. */
    @Test
    void grudgeFadesButNotAtOnce() {
        HumanLikeAgent me = HumanLikeAgent.normal(0, new Random(2), Genome.defaults(), 4);
        me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 1,
            "victim_owner", 0, "victim", "infantry", "destroyed", true));
        double fresh = me.grudges()[1];
        me.observePublicEvent(java.util.Map.of("type", "refresh", "round", 2));
        double afterRound = me.grudges()[1];
        assertTrue(afterRound < fresh, "обида должна затухать");
        assertTrue(afterRound > fresh * 0.3, "но не забываться сразу: " + afterRound);
    }

    /** Задетость копится от потерь и поднимается только у живых. */
    @Test
    void tiltRisesOnLosses() {
        HumanLikeAgent me = HumanLikeAgent.normal(0, new Random(3), Genome.defaults(), 4);
        assertTrue(me.tilt() == 0.0, "на старте бот спокоен");
        for (int i = 0; i < 3; i++) {
            me.observePublicEvent(java.util.Map.of("type", "combat_hit", "seat", 1,
                "victim_owner", 0, "victim", "vehicle", "destroyed", true));
        }
        assertTrue(me.tilt() > 0.5, "потери должны заводить: " + me.tilt());
        assertTrue(me.moodLine().contains("обида"), "настроение должно читаться словами");
    }

    /**
     * КОНТРОЛЬНЫЙ ОБРАЗЕЦ: «человек» с выключенной человечностью обязан играть
     * как обычный стратег. Если это перестанет быть так — сломана механика
     * выбора, а не человеческие черты (именно так и нашлась ошибка, из-за
     * которой живые проваливались вдвое по очкам).
     */
    @Test
    void humanityOffPlaysLikeTheMachine() {
        int wins = 0;
        int machineWins = 0;
        for (long seed : new long[]{11L, 22L, 33L, 44L, 55L, 66L}) {
            wins += playSeat0(seed, true) ? 1 : 0;
            machineWins += playSeat0(seed, false) ? 1 : 0;
        }
        assertTrue(Math.abs(wins - machineWins) <= 3,
            "с выключенной человечностью бот должен играть как машина: побед "
                + wins + " против " + machineWins);
    }

    private boolean playSeat0(long seed, boolean humanControl) {
        GameState s = Fix.game(4, seed);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Random rng = new Random(seed * 31 + i);
            if (i == 0 && humanControl) {
                agents.add(new HumanLikeAgent(0, rng, Genome.defaults(), "контроль",
                    0.0, 0.0, 999, 0.0, 1, 4));
            } else {
                agents.add(new kelium.agents.StrategicAgent(i, rng, Genome.defaults()));
            }
        }
        var res = GameEngine.playGame(s, agents, null);
        return res.get("winner") instanceof Number w && w.intValue() == 0;
    }

    /** Ищейка доигрывает партию и накапливает находки по отдаче действий. */
    @Test
    void exploitHunterFindsAndRepeats() {
        GameState s = Fix.game(4, 77L);
        List<Agent> agents = new ArrayList<>();
        ExploitAgent hunter = ExploitAgent.hunter(0, new Random(5),
            Bots.genome("balanced", 4));
        agents.add(hunter);
        for (int i = 1; i < 4; i++) {
            agents.add(new kelium.agents.StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        var res = GameEngine.playGame(s, agents, null);
        assertNotNull(res.get("winner"));
        assertFalse(hunter.findings().isEmpty(),
            "ищейка обязана хоть что-то замерить за партию");
        for (var e : hunter.findings().entrySet()) {
            assertTrue(e.getValue() >= 0.0, "отдача не может быть отрицательной: " + e);
        }
    }

    /** Живые и ищейки собираются фабрикой лиги — значит их можно сажать за стол. */
    @Test
    void bothFamiliesAreAvailableAtTheTable() {
        for (String spec : List.of("human", "vengeful", "cool", "exploit")) {
            Agent a = kelium.agents.Arena.make(spec, 0, new Random(1), 4);
            assertNotNull(a, "фабрика должна знать " + spec);
        }
    }
}
