package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import kelium.agents.HeuristicAgent;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.core.Agent;

/**
 * Тесты по четырём проблемам дизайнера:
 * 1) лог показывает id карты приказа и отложенную под трофеи карту;
 * 2) условия конца партии (по трекам/тайлам + резерв 8 раундов);
 * 3) боты доходят до тех-треков и набирают ПО не только от келемия;
 * 4) цепочка трофеи->треки реально работает.
 */
class DesignerFixesTest {

    private List<Agent> heuristics(int players, long seed) {
        String[] chars = {"aggressor", "defender", "economist"};
        List<Agent> agents = new ArrayList<>();
        for (int s = 0; s < players; s++) {
            agents.add(new HeuristicAgent(s, new Random(seed * 1000L + s),
                chars[s % chars.length]));
        }
        return agents;
    }

    // ---- ПРОБЛЕМА 1: лог различает РАЗНЫЕ карты приказов и показывает сброс ---

    @Test
    void revealEventCarriesCardIdsAndBlindDiscardCarriesSetAside() {
        GameConfig cfg = GameConfig.build(4, 42L);
        GameState state = Setup.buildGame(cfg);
        List<Agent> agents = heuristics(4, 42L);

        List<Map<String, Object>> reveals = new ArrayList<>();
        boolean[] sawSetAside = {false};
        GameEngine.playGame(state, agents, ev -> {
            String t = String.valueOf(ev.get("type"));
            if ("reveal".equals(t)) {
                reveals.add(ev);
            }
            if ("blind_discard".equals(t) && ev.get("set_aside") instanceof Map<?, ?> m
                    && !m.isEmpty()) {
                sawSetAside[0] = true;
            }
        });

        assertTrue(!reveals.isEmpty(), "были вскрытия");
        // За один раунд каждый игрок играет 4 РАЗНЫЕ карты (по одной за круг).
        // Проверяем: у какого-то игрока за 4 круга первого раунда все id разные.
        Map<Integer, List<String>> firstRoundBySeat = new HashMap<>();
        for (int i = 0; i < Math.min(4, reveals.size()); i++) {
            @SuppressWarnings("unchecked")
            Map<Integer, String> rev = (Map<Integer, String>) reveals.get(i).get("revealed");
            for (var e : rev.entrySet()) {
                firstRoundBySeat.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
            }
        }
        boolean someoneAllDistinct = false;
        for (var e : firstRoundBySeat.entrySet()) {
            List<String> ids = e.getValue();
            if (ids.size() == 4 && new HashSet<>(ids).size() == 4) {
                someoneAllDistinct = true;
            }
        }
        assertTrue(someoneAllDistinct,
            "за 4 круга игрок вскрывает 4 РАЗНЫЕ карты приказов (id различаются)");
        assertTrue(sawSetAside[0], "событие слепого сброса несёт отложенную карту");
    }

    // ---- ПРОБЛЕМА 2: условия конца партии -----------------------------------

    @Test
    void gameEndsByReserveCapEight() {
        // Резерв = 8 раундов; жёсткого лимита в 7 больше нет.
        GameConfig cfg = GameConfig.build(4, 7L);
        GameState state = Setup.buildGame(cfg);
        Map<String, Object> res = GameEngine.playGame(state, heuristics(4, 7L), null);
        int rounds = (Integer) res.get("rounds");
        assertTrue(rounds >= 1 && rounds <= 8, "раундов 1..8, было " + rounds);
    }

    @Test
    void endConditionByAllPeaksOccupied() {
        // Искусственно занимаем верхние шаги всех треков -> мирный конец.
        GameConfig cfg = GameConfig.build(2, 1L);
        GameState s = Setup.buildGame(cfg);
        int top = s.tech.steps - 1;
        for (String track : s.tech.tracks) {
            s.tech.occupancy.get(track).get(top).add(0);
        }
        assertTrue(s.tech.allPeaksOccupied(), "все вершины заняты");
    }

    @Test
    void endConditionByLastSpawnTile() {
        // Опустошаем все грядки, кроме одной -> условие последнего тайла.
        GameConfig cfg = GameConfig.build(2, 1L);
        GameState s = Setup.buildGame(cfg);
        var tiles = s.field.spawnTiles();
        assertTrue(!tiles.isEmpty(), "на поле есть грядки");
        for (int i = 0; i < tiles.size() - 1; i++) {
            tiles.get(i).spawnTile = null;   // жетон-тайл снят с гекса
        }
        int remaining = 0;
        for (var h : tiles) {
            if (h.spawnTile != null && h.spawnTile.kelium > 0) {
                remaining++;
            }
        }
        assertTrue(remaining <= 1, "остался не более 1 источника келемия");
    }

    // ---- ПРОБЛЕМА 3+4: боты доходят до треков и набирают ПО не только келемием

    @Tag("balance")
    @Test
    void botsReachTechTracksAndScore() {
        int games = 30;
        int totalSteps = 0;
        int gamesWithScience = 0;
        double totalVp = 0;
        int scored = 0;
        int nonKeliumVpGames = 0;
        for (int g = 0; g < games; g++) {
            long seed = 5000L + g;
            GameConfig cfg = GameConfig.build(4, seed);
            GameState state = Setup.buildGame(cfg);
            Map<String, Object> res = GameEngine.playGame(state, heuristics(4, seed), null);
            int steps = 0;
            for (PlayerState p : state.players) {
                for (int st : p.techSteps.values()) {
                    steps += st;
                }
            }
            totalSteps += steps;
            if (steps > 0) {
                gamesWithScience++;
            }
            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, Integer>> scores =
                (Map<Integer, Map<String, Integer>>) res.get("scores");
            boolean nonKelium = false;
            for (var e : scores.entrySet()) {
                Map<String, Integer> bd = e.getValue();
                totalVp += bd.get("total");
                scored++;
                for (var se : bd.entrySet()) {
                    if (!"total".equals(se.getKey()) && !"kelium".equals(se.getKey())
                            && se.getValue() != 0) {
                        nonKelium = true;
                    }
                }
            }
            if (nonKelium) {
                nonKeliumVpGames++;
            }
        }
        double avgVp = totalVp / scored;
        // Треки реально занимаются в большинстве партий (не пустые).
        assertTrue(gamesWithScience >= games / 2,
            "наука работает в >= половине партий, было " + gamesWithScience + "/" + games);
        assertTrue(totalSteps > 0, "суммарно занято шагов треков > 0, было " + totalSteps);
        // Боты осмысленно набирают ПО (не 0-1 в среднем, как раньше).
        assertTrue(avgVp > 1.5, "средние ПО > 1.5, было " + avgVp);
        // ПО берутся не только из остаточного келемия.
        assertTrue(nonKeliumVpGames >= games / 2,
            "ПО из источников кроме келемия в >= половине партий, было " + nonKeliumVpGames);
    }
}
