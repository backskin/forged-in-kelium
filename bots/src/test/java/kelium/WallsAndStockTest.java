package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.GameEngine;
import kelium.engine.Passability;
import kelium.support.Fix;

/**
 * ДВЕ НАХОДКИ ДИЗАЙНЕРА (13.08.2026), пойманные в проигрывателе:
 * <ol>
 *   <li>техника выстрелила в соседний гекс СКВОЗЬ нейтральное здание, которое
 *       стояло в её же гексе и закрывало ровно эту сторону;</li>
 *   <li>у игроков оказался разный запас войск (у кого 2 жетона рода, у кого 1),
 *       хотя по правилам у КАЖДОГО ровно 4 жетона каждого рода — уничтоженные
 *       возвращаются владельцу на этапе Возврата.</li>
 * </ol>
 */
class WallsAndStockTest {

    // ==================== стены и стрельба ====================

    /** Правило проходимости ОДНО: нейтральная стенка закрывает ребро всем наземным. */
    @Test
    void neutralWallClosesTheEdgeForGroundUnits() {
        GameState s = Fix.game(4, 61L);
        // Ищем на поле любое ребро, закрытое нейтральной постройкой, и проверяем,
        // что правило считает его закрытым для наземных и открытым для авиации.
        String from = null;
        String to = null;
        for (var e : s.field.hexes.entrySet()) {
            var h = e.getValue();
            for (int i = 0; i < h.sideOwner.length; i++) {
                Integer owner = h.sideOwner[i];
                if (owner == null || owner >= 0) {
                    continue;               // нас интересует именно нейтрал (uid < 0)
                }
                for (String nb : s.field.neighbors(e.getKey())) {
                    for (int j : h.sidesFacing(nb)) {
                        if (j == i) {
                            from = e.getKey();
                            to = nb;
                        }
                    }
                }
            }
        }
        if (from == null) {
            return;   // на этом поле нейтральных стенок нет — проверять нечего
        }
        assertFalse(Passability.groundEdgeOpen(s, from, to, 0),
            "нейтральная стенка обязана закрывать ребро наземному войску");

        UnitToken air = new UnitToken(UnitType.AIRCRAFT, 0, 1, 9001);
        air.setHexId(from);
        assertTrue(Passability.canShootAcross(s, air, to),
            "авиация бьёт сверху — стенка ей не мешает");

        UnitToken vehicle = new UnitToken(UnitType.VEHICLE, 0, 2, 9002);
        vehicle.setHexId(from);
        assertFalse(Passability.canShootAcross(s, vehicle, to),
            "техника не должна стрелять сквозь стену");
    }

    /** Своё здание проход не закрывает, чужое — закрывает. */
    @Test
    void ownBuildingsDoNotBlockButEnemyOnesDo() {
        GameState s = Fix.game(4, 62L);
        PlayerState me = s.player(0);
        PlayerState rival = s.player(1);
        var mine = me.buildingsOnField().get(0);
        var theirs = rival.buildingsOnField().get(0);
        assertFalse(Passability.blocksGround(s, mine.uid, 0), "своё здание не мешает");
        assertTrue(Passability.blocksGround(s, theirs.uid, 0), "чужое здание закрывает");
        assertTrue(Passability.blocksGround(s, -7, 0), "нейтральная постройка закрывает");
        assertFalse(Passability.blocksGround(s, null, 0), "пустая сторона открыта");
    }

    /**
     * НИ ОДНОГО выстрела сквозь стену за целые партии.
     *
     * <p>Это и есть проверка находки: раньше Бой брал просто всех соседей гекса и
     * о стенках не знал, потому что правило проходимости жило внутри Движения.
     */
    @Test
    void noShotsThroughWallsInWholeGames() {
        int shots = 0;
        int throughWalls = 0;
        for (long seed : new long[]{770_698L, 63L, 64L}) {
            GameState s = Fix.game(4, seed);
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                agents.add(new StrategicAgent(i, new Random(seed * 31 + i), Genome.defaults()));
            }
            // ПРОВЕРЯТЬ НАДО В МОМЕНТ ВЫСТРЕЛА, а не в конце партии: стены строят и
            // сносят, жетоны ходят, и ребро, закрытое к финалу, в момент удара могло
            // быть открыто. Первая версия теста сверяла удары с ГЕОМЕТРИЕЙ ФИНАЛА и
            // падала на честных партиях — виноват был тест, а не движок.
            int[] counters = {0, 0};   // [0] ударов, [1] сквозь стену
            GameEngine.playGame(s, agents, ev -> {
                if (!"combat_hit".equals(String.valueOf(ev.get("type")))) {
                    return;
                }
                if (!(ev.get("source") instanceof String from)
                        || !(ev.get("target") instanceof String to)) {
                    return;
                }
                int seat = ev.get("seat") instanceof Number n ? n.intValue() : 0;
                counters[0]++;
                // Бил кто-то из жетонов гекса-источника. Нарушение — если НИ ОДИН из
                // них не мог дотянуться: авиация дотягивается всегда, наземные —
                // только через открытое ребро. Это то же условие, что стоит в движке.
                for (UnitToken u : s.player(seat).units) {
                    if (from.equals(u.hexId) && Passability.canShootAcross(s, u, to)) {
                        return;
                    }
                }
                counters[1]++;
            });
            shots += counters[0];
            throughWalls += counters[1];
        }
        assertTrue(shots > 0, "замер должен был увидеть хоть один удар");
        assertEquals(0, throughWalls,
            "выстрелов сквозь стену быть не должно (всего ударов " + shots + ")");
    }

    // ==================== личный запас войск ====================

    /** У каждого игрока не больше четырёх жетонов каждого рода (плюс супер-войска). */
    @Test
    void everyPlayerHasFourTokensOfEachKind() {
        Map<String, Integer> worst = new TreeMap<>();
        for (long seed : new long[]{900_001L, 900_002L, 900_003L, 900_004L}) {
            GameState s = Fix.game(4, seed);
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                agents.add(new StrategicAgent(i, new Random(seed * 31 + i), Genome.defaults()));
            }
            GameEngine.playGame(s, agents, null);
            for (PlayerState p : s.players) {
                for (UnitType t : UnitType.values()) {
                    int n = p.unitsOfKind(t);
                    worst.merge(t.code, n, Math::max);
                    assertTrue(n <= s.tokenStats.unitStock(t),
                        "род " + t.code + ": жетонов " + n + " при запасе "
                            + s.tokenStats.unitStock(t) + " (сид " + seed + ")");
                }
            }
        }
        assertEquals(4, s0Stock(), "личный запас рода по данным — 4 жетона");
        assertFalse(worst.isEmpty(), "замер должен был что-то посчитать");
    }

    private int s0Stock() {
        GameState s = Fix.game(4, 65L);
        return s.tokenStats.unitStock(UnitType.INFANTRY);
    }

    /** Супер-войско с карты в личный запас рода НЕ входит: это отдельный жетон. */
    @Test
    void superUnitIsNotCountedInTheStock() {
        GameState s = Fix.game(4, 66L);
        PlayerState p = s.player(0);
        int before = p.unitsOfKind(UnitType.INFANTRY);
        UnitToken su = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 9100);
        su.superUnit = true;
        p.units.add(su);
        assertEquals(before, p.unitsOfKind(UnitType.INFANTRY),
            "супер-войско печатается отдельно и запас рода не занимает");
    }
}
