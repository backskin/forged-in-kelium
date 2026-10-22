package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.support.Fix;

/**
 * БОЙ ПО ЦЕЛИ (решение дизайнера 26.09.2026): «выбираешь гекс, и все соседние
 * гексы могут бить по этому гексу цели. Вот весь бой. Каждый жетон сам
 * выбирает, по чему он стреляет в атакуемом гексе».
 *
 * <p>Сторож: в своде действует эта грамматика; по выбранному гексу бьют свои
 * жетоны с РАЗНЫХ соседних гексов; у каждого — одна атака; доплаты нет.
 */
class БойПоЦелиTest {

    @Test
    void сводИграетБойПоЦели() {
        GameState s = Fix.game();
        assertEquals("target_hex", Ctx.rules(s).getStr("actions.combat.surcharge_model", ""));
    }

    @Test
    void поЦелиБьютВсеСоседиПоОднойАтаке() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, 42L, null, List.of("A", "A")));
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

        // цель — обычный гекс с двумя обычными соседями, свободными от всего
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
        s.player(0).resources.setAmmo(5);
        s.player(1).resources.setAmmo(0);

        boolean fought = ((CombatResolver) s.combat)
            .runBattle(0, new Fix.AimingAgent(0, цель));
        assertTrue(fought, "бой состоялся");
        Map<String, Integer> поГексам = new HashMap<>();
        for (Map<String, Object> h : hits) {
            assertEquals(цель, h.get("target"), "бьют только по выбранному гексу: " + h);
            поГексам.merge(String.valueOf(h.get("from")), 1, Integer::sum);
        }
        assertEquals(2, поГексам.size(), "ударили оба соседа: " + hits);
        for (int n : поГексам.values()) {
            assertEquals(1, n, "у каждого жетона одна атака: " + hits);
        }
        int потрачено = 0;
        for (Map<String, Object> h : hits) {
            assertEquals(((Number) h.get("base_ammo")).intValue(),
                ((Number) h.get("ammo")).intValue(), "доплаты за жетон нет: " + h);
            потрачено += ((Number) h.get("ammo")).intValue();
        }
        assertEquals(5 - потрачено, s.player(0).resources.ammo(),
            "списано ровно по печатной цене атак");
    }

    private static boolean обычный(Hex h) {
        return h != null && h.kind == HexKind.NORMAL && h.spawnTile == null && !h.hasNeutral();
    }

    private static boolean занят(GameState s, String hex) {
        for (var p : s.players) {
            for (var u : p.units) {
                if (hex.equals(u.hexId)) {
                    return true;
                }
            }
            for (var b : p.buildings) {
                if (hex.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
