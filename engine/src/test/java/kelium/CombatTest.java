package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.Setup;
import kelium.core.TurnJournal;
import kelium.core.Agent;
import kelium.core.Choice;

/** Прямые тесты боевой процедуры: урон, уничтожение, трофеи, разрушение ЦУ. */
class CombatTest {

    /**
     * Агент, всегда выбирающий первую «реальную» опцию (не pass). Если задан
     * preferHex — среди опций с payload-гексом предпочитает его (чтобы тест
     * бил по подготовленной цели, а не по случайному нейтралу рядом).
     */
    private static final class GreedyAgent extends Agent {
        String preferHex;

        GreedyAgent(int seat) {
            super(seat, "greedy#" + seat);
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            if (preferHex != null) {
                for (Choice o : options) {
                    if (preferHex.equals(o.payload())) {
                        return o;
                    }
                }
            }
            for (Choice o : options) {
                if (!"pass".equals(o.kind()) && o.payload() != null) {
                    return o;
                }
            }
            return options.get(options.size() - 1);
        }
    }

    private GameState freshState() {
        GameConfig cfg = GameConfig.build(2, 42L);
        GameState s = Setup.buildGame(cfg);
        s.journal = new TurnJournal(s.numPlayers());
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            agents.add(new GreedyAgent(i));
        }
        s.agents = agents;
        s.combat = new CombatResolver(s, e -> { }).bindAgents(agents);
        return s;
    }

    /** Обычный соседний гекс (не грядка, без нейтрала) — открытый для любой атаки. */
    private String neighbourHex(GameState s, String hex) {
        for (String nb : s.field.neighbors(hex)) {
            kelium.core.Hex h = s.field.get(nb);
            if (h.kind == kelium.core.HexKind.NORMAL && !h.hasNeutral()) {
                return nb;
            }
        }
        return s.field.neighbors(hex).get(0);
    }

    @Test
    void attackDamagesAndDestroysEnemyUnit() {
        GameState s = freshState();
        PlayerState p0 = s.player(0);
        PlayerState p1 = s.player(1);
        s.journal = new TurnJournal(2);
        s.journal.startTurn(0);

        // Поставить пехоту p0 на её стартовый гекс, врага p1 на соседний гекс.
        String src = p0.startHex;
        String tgt = neighbourHex(s, src);
        UnitToken a = p0.unitsOnField().get(0);
        a.hexId = src;
        p0.resources.add(Resource.AMMO, 5);

        UnitToken victim = s.tokenStats.makeUnit(UnitType.INFANTRY, 1, 999);
        victim.hexId = tgt;
        p1.units.add(victim);
        // Убрать чужое здание с гекса цели, чтобы гекс был открыт.
        assertTrue(victim.alive());

        ((GreedyAgent) s.agents.get(0)).preferHex = tgt;
        CombatResolver cr = (CombatResolver) s.combat;
        boolean did = cr.runBattle(0, (Agent) s.agents.get(0));
        assertTrue(did, "бой состоялся");
        // пехота 1 HP -> уничтожена; жетон на месте уничтоженных жетонов атакующего.
        assertFalse(p1.units.contains(victim) && victim.hexId != null && victim.alive(),
            "жертва уничтожена/захвачена");
        assertTrue(p0.destroyedTokens.contains(victim), "жертва на месте уничтоженных жетонов p0");
    }

    @Test
    void destroyingCommandCenterGivesTokenAndRespawns() {
        GameState s = freshState();
        PlayerState p0 = s.player(0);
        PlayerState p1 = s.player(1);
        s.journal.startTurn(0);

        String src = p0.startHex;
        String tgt = neighbourHex(s, src);
        // Убрать стартовую пехоту (пехота стороны A не бьёт по зданиям); дать технику,
        // чья основная строка бьёт по зданиям/вышкам.
        p0.units.clear();
        UnitToken vehicle = s.tokenStats.makeUnit(UnitType.VEHICLE, 0, 500);
        vehicle.hexId = src;
        p0.units.add(vehicle);
        p0.resources.add(Resource.AMMO, 20);

        // Найти ЦУ p1 и переставить его на гекс цели (4 HP), нанести урон вручную.
        BuildingToken cu = null;
        for (BuildingToken b : p1.buildings) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                cu = b;
            }
        }
        assertTrue(cu != null);
        // ослабить ЦУ до 1 HP от гибели, чтобы 1 атака добила
        cu.hexId = tgt;
        cu.damage = cu.hp - 1;
        boolean cuTokenBefore = p1.ownCuTokenAvailable;
        assertTrue(cuTokenBefore);

        ((GreedyAgent) s.agents.get(0)).preferHex = tgt;
        CombatResolver cr = (CombatResolver) s.combat;
        cr.runBattle(0, (Agent) s.agents.get(0));

        assertEquals(1, p0.cuDestructionTokens, "атакующий получил жетон разрушения ЦУ");
        assertFalse(p1.ownCuTokenAvailable, "у владельца жетон разрушения ЦУ израсходован");
        // §12.1: ЦУ уходит владельцу В ЗАПАС (hexId=null), урон снят; на поле
        // возвращается обычной Стройкой. Авто-отстройки больше нет.
        assertEquals(null, cu.hexId, "ЦУ в запасе владельца, не на поле");
        assertEquals(0, cu.damage, "урон ЦУ снят");
        assertFalse(s.finished, "первый снос без чужого жетона на руках — не победа");
    }
}
