package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;
import kelium.engine.TurnContext;

/**
 * СНОС ПЕРЕД СТРОЙКОЙ (решение Влада 02.10.2026, свод 1.49.0): ветка
 * «построить» сносит любое число своих зданий её вида, по монете за каждое, и
 * потом ставит одно — в том числе только что снесённое.
 */
class СносПередСтройкойTest {

    /**
     * Свод 1.49.0 привязывает свои карты к общему реестру; другие тесты модуля
     * ждут в нём колоду свода по умолчанию — возвращаем её.
     */
    @org.junit.jupiter.api.AfterAll
    static void вернутьКолодуПоУмолчанию() {
        kelium.engine.cards.CardRegistry.reset();
        kelium.engine.Setup.buildGame(kelium.dataio.GameConfig.buildCached(
            kelium.dataio.GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    /** Сносит всё, что предлагают; потом ставит добытчик; потом пас. */
    private static final class СносИСтройка extends Agent {
        final boolean сносить;
        int сносов;
        int построек;

        СносИСтройка(boolean сносить) {
            super(0, "тест");
            this.сносить = сносить;
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            if ("build_pick".equals(ctx.get("kind"))) {
                for (Choice c : options) {
                    if (сносить && "demolish_pick".equals(c.kind())) {
                        сносов++;
                        return c;
                    }
                }
                for (Choice c : options) {
                    if ("build_pick".equals(c.kind()) && построек == 0) {
                        построек++;
                        return c;
                    }
                }
                for (Choice c : options) {
                    if ("pass".equals(c.kind())) {
                        return c;
                    }
                }
            }
            return options.get(0);
        }
    }

    private static long добытчиков(PlayerState p) {
        return p.buildingsOnField().stream().filter(b -> b.type == BuildingType.MINER).count();
    }

    @Test
    void сноситВсеСвоегоВидаИСтавитСнесённоеЗаново() {
        GameState s = Setup.buildGame(GameConfig.buildCached("1.49.0", 2, 7L, null, null));
        PlayerState p = s.player(0);
        p.resources.add(kelium.core.Resource.COIN, 30);
        for (int i = 0; i < 2; i++) {
            Actions.create("build_miner", s).perform(p, new TurnContext(0, 1),
                new СносИСтройка(false));
        }
        long было = добытчиков(p);
        Assumptions.assumeTrue(было >= 2, "поставить два добытчика не вышло");
        int монетДо = p.resources.coin();
        СносИСтройка агент = new СносИСтройка(true);
        TurnContext ctx = new TurnContext(0, 1);
        Actions.create("build_miner", s).perform(p, ctx, агент);
        assertEquals(было, агент.сносов, "снесены все добытчики, не один");
        assertEquals(1, агент.построек, "поставлено одно здание");
        assertEquals(1, добытчиков(p), "на поле один добытчик — поставленный заново");
        assertTrue(p.resources.coin() > монетДо - 10, "за снос пришли монеты");
    }
}
