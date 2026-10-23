package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * СВОД 1.52.0 — решения дизайнера 18.10.2026 и уточнение 22.10.2026:
 * <ul>
 *   <li>снос: в ветке «построить» действием сносят ОДНО своё здание этого
 *       вида и ставят одно; монеты за снос нет;</li>
 *   <li>спец-плашка монеты — +1 монета (набор приказов 7.0.0);</li>
 *   <li>в Бою келемий платит вместо боеприпаса 1 к 1, в том числе вперемешку.</li>
 * </ul>
 */
class Свод152Test {

    private static final String СВОД = "1.52.0";

    @org.junit.jupiter.api.AfterAll
    static void вернутьКолодуПоУмолчанию() {
        // Свод привязывает к общему реестру свои колоды; соседние тесты ждут
        // там колоду опорного свода — возвращаем её.
        kelium.engine.cards.CardRegistry.reset();
        Setup.buildGame(GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    @Test
    void игроваяВерсияИКлючиСвода() {
        GameState s = Fix.game(СВОД, 4, 1L);
        var rs = Ctx.rules(s);
        assertEquals(0, rs.getInt("actions.build.demolish_refund_coins"), "снос монет не даёт");
        assertEquals(0, rs.getInt("actions.build.demolish_cost_coins"), "снос монет не стоит");
        assertEquals(1, rs.getInt("actions.build.branch_demolish_max"), "сносят одно здание");
        assertEquals(1, rs.getInt("orders.spec_coin"), "плашка — одна монета");
        assertEquals("7.0.0", rs.getStr("content_versions.orders", ""), "набор приказов с +1");
        assertTrue(Boolean.TRUE.equals(rs.get("actions.combat.kelium_for_ammo", null)),
            "келемий вместо боеприпаса в Бою");
    }

    // ==================== снос в ветке «построить» ====================

    /** Сносит всё, что предлагают; потом ставит одно здание; потом пас. */
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
    void сносОдногоЗданияБезМонеты() {
        GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, 2, 7L, null, null));
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 30);
        for (int i = 0; i < 2; i++) {
            Actions.create("build_miner", s).perform(p, new TurnContext(0, 1),
                new СносИСтройка(false));
        }
        long было = добытчиков(p);
        Assumptions.assumeTrue(было >= 2, "поставить два добытчика не вышло");

        int монетДо = p.resources.coin();
        СносИСтройка агент = new СносИСтройка(true);
        Actions.create("build_miner", s).perform(p, new TurnContext(0, 1), агент);
        assertEquals(1, агент.сносов,
            "снести можно ОДНО здание этого вида, а сносов было " + агент.сносов);
        assertEquals(1, агент.построек, "поставлено одно здание");
        assertEquals(было, добытчиков(p), "снёс один, поставил один — добытчиков столько же");
        assertTrue(p.resources.coin() < монетДо,
            "монеты за снос нет: стройка только потратила (было " + монетДо + ", стало "
                + p.resources.coin() + ")");
    }

    // ==================== келемий вместо боеприпаса ====================

    @Test
    void недостачуБоеприпасовДобираетКелемий() {
        GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, 2, 42L, null, null));
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 2; seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents, ev -> {
            if ("combat_hit".equals(String.valueOf(ev.get("type")))) {
                hits.add(new HashMap<>(ev));
            }
        });
        s.round = 1;
        s.circle = 1;

        String цель = null;
        List<String> соседи = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (!обычный(h) || занят(s, h.id)) {
                continue;
            }
            List<String> вокруг = new ArrayList<>();
            for (String nb : s.field.neighbors(h.id)) {
                if (обычный(s.field.get(nb)) && !занят(s, nb)) {
                    вокруг.add(nb);
                }
            }
            if (вокруг.size() >= 2) {
                цель = h.id;
                соседи = вокруг.subList(0, 2);
                break;
            }
        }
        assertNotNull(цель, "на поле нашёлся гекс с двумя свободными соседями");

        Fix.unit(s, 1, UnitType.VEHICLE, цель).hp = 9;   // живучая цель — не падает
        Fix.unit(s, 0, UnitType.INFANTRY, соседи.get(0));
        Fix.unit(s, 0, UnitType.INFANTRY, соседи.get(1));
        PlayerState p = s.player(0);
        p.resources.setAmmo(1);
        int келемияДо = 6;
        p.resources.add(Resource.KELIUM, келемияДо - p.resources.get(Resource.KELIUM));

        boolean бой = ((CombatResolver) s.combat).runBattle(0, new Fix.AimingAgent(0, цель));
        assertTrue(бой, "бой состоялся при одном боеприпасе: остальное добрал келемий");
        int цена = 0;
        for (Map<String, Object> h : hits) {
            цена += ((Number) h.get("ammo")).intValue();
        }
        Assumptions.assumeTrue(цена > 1, "атаки стоили не больше одного боеприпаса — "
            + "недостачи не было, проверять нечего");
        assertEquals(0, p.resources.ammo(), "сперва ушли все боеприпасы");
        assertEquals(келемияДо - (цена - 1), p.resources.get(Resource.KELIUM),
            "недостающее заплачено келемием 1 к 1");
    }

    private static boolean обычный(Hex h) {
        return h != null && h.kind == HexKind.NORMAL && h.spawnTile == null && !h.hasNeutral();
    }

    private static boolean занят(GameState s, String hex) {
        for (var pl : s.players) {
            for (var u : pl.units) {
                if (hex.equals(u.hexId)) {
                    return true;
                }
            }
            for (var b : pl.buildings) {
                if (hex.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
