package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Modules;
import kelium.support.Fix;

/**
 * ГЛУХОЙ ЖЕТОН УНИЧТОЖЕНИЯ ЦУ — стерегём то, что уже один раз молча сломалось.
 *
 * <p>Жетон лежит на планшете владельца как обычный жетон модуля атаки, но
 * ничего не открывает: род с ним теряет ту атаку, на которой жетон лежит.
 *
 * <p>НАЙДЕНО ЗАМЕРОМ 25.08.2026: у 470 игроков из 600 жетона к концу партии не
 * было вовсе, хотя ЦУ снесли лишь в 0.27 партии. Этап смены модулей в
 * Обновление стирает раскладку целиком и собирает её заново из ВЫТЯНУТЫХ
 * жетонов — а глухой ни в мешке, ни в руке не числится, и потому пропадал
 * каждый раунд. Замер «жетоны равны между собой» тогда мерил жетон, которого
 * уже не было.
 */
class ГлухойЖетонTest {

    /** Агент, берущий первый предложенный вариант — раскладку это устраивает. */
    private static final class Первый extends Agent {
        Первый(int seat) {
            super(seat, "первый");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            return options.get(0);
        }
    }

    private static Map<String, Object> глухойЖетон() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", PlayerState.CU_MODULE);
        m.put("blocks", true);
        return m;
    }

    /** Смена модулей в Обновление НЕ теряет глухой жетон. */
    @Test
    void сменаМодулейНеТеряетГлухойЖетон() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.redPlacements.put(UnitType.INFANTRY, глухойЖетон());

        Modules.moduleSwap(s, 0, new Первый(0), ev -> { });

        Map<String, Object> после = Modules.redModuleOn(p, UnitType.INFANTRY);
        assertTrue(после != null && Boolean.TRUE.equals(после.get("blocks")),
            "после смены модулей глухой жетон обязан остаться на своём роде, "
                + "а раскладка вышла такой: " + p.redPlacements);
    }

    /** Род с глухим жетоном теряет специальную атаку, но не универсальную. */
    @Test
    void глухойЖетонЗакрываетТолькоСвоюЯчейку() {
        GameState s = Fix.game();
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            agents.add(new Первый(seat));
        }
        GameEngine.bind(s, agents, ev -> { });
        PlayerState p = s.player(0);

        UnitToken пехота = Fix.unit(s, 0, UnitType.INFANTRY, p.startHex);
        var досегоо = ((CombatResolver) s.combat).reachableTargets(0, пехота);
        assertEquals(4, досегоо.size(), "без жетона достаются все четыре цели");

        p.redPlacements.put(UnitType.INFANTRY, глухойЖетон());
        var после = ((CombatResolver) s.combat).reachableTargets(0, пехота);
        assertEquals(4, после.size(),
            "универсальная атака остаётся: она бьёт любой тип за 2 боеприпаса, "
                + "а жетон закрыл только дешёвую ячейку");
    }

    /** Снос ЦУ уносит жетон и открывает ячейку. */
    @Test
    void сносЦУОткрываетЯчейку() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.redPlacements.put(UnitType.VEHICLE, глухойЖетон());
        assertTrue(p.ownCuTokenAvailable, "на старте жетон у владельца");

        // Ровно то, что делает бой при уничтожении ЦУ.
        p.ownCuTokenAvailable = false;
        p.redPlacements.entrySet().removeIf(e ->
            Boolean.TRUE.equals(e.getValue().get("blocks")));

        assertFalse(p.redPlacements.containsKey(UnitType.VEHICLE),
            "жетон уехал к захватчику — ячейка обязана открыться");
    }
}
