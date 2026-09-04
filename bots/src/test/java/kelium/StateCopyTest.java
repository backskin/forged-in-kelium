package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Genome;
import kelium.agents.Lookahead;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.engine.GameEngine;
import kelium.support.Fix;

/**
 * Копия состояния — фундамент всего просчёта вперёд. Если она протекает, бот
 * «думает» прямо в настоящей партии, и поймать это по результатам почти
 * невозможно: партия просто идёт странно. Поэтому проверок здесь много и они
 * придирчивые.
 */
class StateCopyTest {

    @Test
    void copyIsIndependentOfOriginal() {
        GameState s = Fix.game(4, 11L);
        GameState c = s.deepCopy(1234L);

        // правим КОПИЮ во всех местах, где движок вообще что-то меняет
        c.player(0).resources.add(Resource.COIN, 100);
        c.player(0).objectiveHand.add("выдуманная-карта");
        c.player(0).techSteps.put("green", 4);
        c.player(0).containers += 7;
        c.round = 8;
        c.circle = 3;
        for (var h : c.field.hexes.values()) {
            if (h.spawnTile != null) {
                h.spawnTile.kelium = 0;
            }
        }
        c.decks.get("objectives").drawPile.clear();

        assertNotSame(s.field, c.field);
        assertEquals(0, s.player(0).resources.coin() - s.player(0).resources.coin());
        assertFalse(s.player(0).objectiveHand.contains("выдуманная-карта"),
            "рука оригинала не должна меняться");
        assertEquals(0, s.player(0).techSteps.getOrDefault("green", 0),
            "треки оригинала не должны меняться");
        assertTrue(s.round < 8, "раунд оригинала не должен меняться");
        int keliumLeft = 0;
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                keliumLeft += h.spawnTile.kelium;
            }
        }
        assertTrue(keliumLeft > 0, "келемий на тайлах оригинала должен остаться");
        assertFalse(s.decks.get("objectives").drawPile.isEmpty(),
            "колода оригинала не должна пустеть");
    }

    @Test
    void tokensAreCopiedAndTrophiesStayOneObject() {
        GameState s = Fix.game(4, 12L);
        // Кладём ЧУЖОЙ жетон в трофейное пространство: это единственное место, где
        // объект принадлежит одному игроку, а лежит у другого. Если копия его
        // продублирует, движок при возврате «оживит» не тот жетон.
        UnitToken victim = s.player(1).units.get(0);
        victim.setCapturedBy(0);
        s.player(0).destroyedTokens.add(victim);

        GameState c = s.deepCopy(99L);
        Token inTrophy = c.player(0).destroyedTokens.get(0);
        assertNotSame(victim, inTrophy, "в копии должен лежать НОВЫЙ объект");
        assertEquals(victim.uid(), inTrophy.uid());

        UnitToken sameInOwnerList = null;
        for (UnitToken u : c.player(1).units) {
            if (u.uid == victim.uid) {
                sameInOwnerList = u;
            }
        }
        assertSame(inTrophy, sameInOwnerList,
            "жетон среди уничтоженных жетонов и жетон в списке владельца — ОДИН объект");
    }

    @Test
    void copyKeepsSharedImmutableDataByReference() {
        GameState s = Fix.game(3, 13L);
        GameState c = s.deepCopy(7L);
        // Правила, контент и доски за партию не меняются — копировать их и дорого,
        // и вредно (расхождение версий правил внутри одной партии).
        assertSame(s.config, c.config);
        assertSame(s.tokenStats, c.tokenStats);
        assertSame(s.player(0).board, c.player(0).board);
    }

    @Test
    void energyAndDamageSurviveTheCopy() {
        GameState s = Fix.game(4, 14L);
        BuildingToken b = s.player(0).buildingsOnField().get(0);
        b.addEnergyFrom(4242, 1);
        b.damage = 1;
        b.energyIdle = 2;

        GameState c = s.deepCopy(5L);
        BuildingToken cb = null;
        for (BuildingToken x : c.player(0).buildings) {
            if (x.uid == b.uid) {
                cb = x;
            }
        }
        assertEquals(b.energyPlaced, cb.energyPlaced);
        assertEquals(1, cb.damage);
        assertEquals(2, cb.energyIdle);
        assertEquals(Integer.valueOf(1), cb.energyBySource.get(4242));
        // и обратно: правка копии не задевает оригинал
        cb.stripEnergyOf(4242);
        assertEquals(Integer.valueOf(1), b.energyBySource.get(4242));
    }

    @Test
    void playingOutACopyDoesNotTouchTheRealGame() {
        GameState s = Fix.game(4, 15L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        // Довести партию до середины: просчёт из пустого состояния ничего не проверяет.
        new GameEngine(s, agents, null).runToRound(2);
        assertFalse(s.finished, "runToRound не должен закрывать партию");

        String before = fingerprint(s);
        for (int i = 0; i < 3; i++) {
            Lookahead.playOut(s, 0, Genome.defaults(), Genome.defaults(), null, 0, 100 + i);
        }
        assertEquals(before, fingerprint(s),
            "доигранные КОПИИ не должны менять настоящую партию");
        assertFalse(s.finished, "настоящая партия должна остаться незакрытой");
    }

    @Test
    void resumeFinishesAGameStartedElsewhere() {
        GameState s = Fix.game(4, 16L);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new StrategicAgent(i, new Random(i), Genome.defaults()));
        }
        new GameEngine(s, agents, null).runToRound(2);
        int roundAtPause = s.round;

        var result = new GameEngine(s, agents, null).resume();
        assertTrue(s.finished, "продолженная партия должна закончиться");
        assertTrue(s.round >= roundAtPause, "раунд не должен пойти назад");
        assertTrue(result.get("winner") instanceof Integer, "победитель должен быть определён");
    }

    private static String fingerprint(GameState s) {
        StringBuilder sb = new StringBuilder("r" + s.round + "c" + s.circle);
        for (var p : s.players) {
            sb.append('|').append(p.resources.coin()).append('/')
              .append(p.resources.kelium()).append('/').append(p.resources.ammo())
              .append('b').append(p.buildingsOnField().size())
              .append('u').append(p.unitsOnField().size())
              .append('o').append(p.orderHand.size());
        }
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null) {
                sb.append('t').append(h.spawnTile.kelium);
            }
        }
        return sb.toString();
    }
}
