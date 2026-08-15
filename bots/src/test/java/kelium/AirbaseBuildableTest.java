package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;

/**
 * Авиация не появилась НИ В ОДНОЙ из 60 просмотренных партий. Проверяем, дело
 * в экономике (дорого) или в том, что авиабазу физически невозможно построить
 * (тогда это баг: у неё след из ТРЁХ смежных ячеек).
 */
class AirbaseBuildableTest {

    /** Агент, который соглашается на первый предложенный вариант и всё логирует. */
    private static final class Spy extends Agent {
        final List<String> seen = new ArrayList<>();

        Spy() {
            super(0, "spy");
        }

        @Override public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice o : options) {
                seen.add(String.valueOf(o.label()));
            }
            return options.get(0);
        }
    }

    @Test
    void airbaseIsOfferedWhenPlayerCanAffordIt() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 5L, null, null);
        GameState s = Setup.buildGame(cfg);
        PlayerState p = s.player(0);
        // денег и энергии заведомо достаточно
        p.resources.add(Resource.COIN, 60);

        Spy spy = new Spy();
        List<Agent> agents = new ArrayList<>();
        agents.add(spy);
        for (int i = 1; i < 4; i++) {
            agents.add(new kelium.agents.RandomAgent(i, new Random(i)));
        }
        s.agents = agents;
        s.journal = new kelium.core.TurnJournal(4);   // вне движка журнал не создан

        Actions.create("build", s).perform(p, new TurnContext(0, 0), spy);
        boolean offered = spy.seen.stream().anyMatch(l -> l.contains("airbase"));
        // Диагностика в отчёт теста: что вообще предлагали строить
        System.out.println("предложено при стройке: " + spy.seen);
        assertTrue(offered, "авиабаза должна предлагаться игроку с деньгами; "
            + "если нет — её негде поставить (нужны 3 смежные свободные ячейки)");
    }
}
