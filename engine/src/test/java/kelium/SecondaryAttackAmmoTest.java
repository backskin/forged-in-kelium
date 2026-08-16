package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Target;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.rules.Ruleset;
import kelium.support.Fix;

/**
 * ВТОРАЯ СТРОКА АТАКИ СТОИТ 1 БОЕПРИПАС (правило дизайнера 16.08.2026).
 *
 * <p>До этого на планшетах войск было два ряда с разной ценой: основной за 1 БПР
 * и второй за 2. Теперь атак за два боеприпаса на планшетах войск нет вовсе —
 * обе строки стоят одинаково.
 *
 * <p>Проверяется и число в своде, и то, что бой реально списывает именно его:
 * цена жила в двух местах (свод и разбор строк резолвера), и совпадение их
 * значений — как раз то, что молча расходится.
 */
class SecondaryAttackAmmoTest {

    /** В своде обе строки планшета стоят одинаково — и ровно 1. */
    @Test
    void bothPrintedRowsCostOneAmmoInTheRuleset() {
        GameState s = Fix.game();
        Ruleset rs = Ctx.rules(s);
        assertEquals(1, rs.getInt("actions.combat.primary_row_ammo_cost"),
            "основная строка планшета — 1 БПР");
        assertEquals(1, rs.getInt("actions.combat.secondary_row_ammo_cost"),
            "вторая строка планшета обязана стоить столько же: атак за 2 БПР "
            + "на планшетах войск больше нет");
    }

    /**
     * Удар ПО ВТОРОЙ СТРОКЕ списывает 1 боеприпас. Сцена подобрана так, что
     * другой цели у пехоты нет: рядом стоит только техника, а она у стороны А
     * стоит во второй строке пехоты.
     */
    @Test
    void aHitFromTheSecondRowSpendsOneAmmo() {
        GameState s = battleGame();
        int seat = 0;
        String spot = Fix.freeNeighbour(s, s.player(seat).startHex);
        assertTrue(spot != null, "у стартового гекса нет обычного соседа");

        Target[] pair = s.player(seat).board.troop.attacks(UnitType.INFANTRY);
        assertEquals(Target.VEHICLE, pair[1],
            "сцена рассчитана на планшет, где вторая строка пехоты бьёт технику");

        Fix.unit(s, seat, UnitType.INFANTRY, s.player(seat).startHex);
        Fix.unit(s, 1, UnitType.VEHICLE, spot);
        s.player(seat).resources.setAmmo(6);
        s.player(1).resources.setAmmo(0);          // ответка не должна путать счёт

        List<Map<String, Object>> hits = new ArrayList<>();
        bindWatching(s, hits);

        int before = s.player(seat).resources.ammo();
        boolean fought = ((CombatResolver) s.combat)
            .runBattle(seat, new Fix.AimingAgent(seat, spot));
        assertTrue(fought, "бой должен был состояться");

        List<Map<String, Object>> second = new ArrayList<>();
        for (Map<String, Object> h : hits) {
            if (String.valueOf(h.get("attacker")).endsWith(".secondary")) {
                second.add(h);
            }
        }
        assertTrue(!second.isEmpty(),
            "по технике пехота обязана была бить ВТОРОЙ строкой, а ударов "
            + "второй строкой не случилось: " + hits);
        for (Map<String, Object> h : second) {
            assertEquals(1, ((Number) h.get("base_ammo")).intValue(),
                "печатная цена второй строки — 1 БПР, а в бою: " + h);
        }
        assertEquals(before - hits.size(), s.player(seat).resources.ammo(),
            "каждый удар обязан стоить ровно 1 боеприпас, ударов было "
            + hits.size());
    }

    /** Партия с наблюдателем боевых событий (Fix.game привязывает бой без него). */
    private static GameState battleGame() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 2, 42L, null, null));
    }

    private static void bindWatching(GameState s, List<Map<String, Object>> hits) {
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents, ev -> {
            if ("combat_hit".equals(String.valueOf(ev.get("type")))) {
                hits.add(new java.util.HashMap<>(ev));
            }
        });
        s.round = 1;
        s.circle = 1;
    }
}
