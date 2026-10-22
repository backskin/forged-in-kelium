package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.Resource;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Срабатывания;
import kelium.support.Fix;

/**
 * СРАБАТЫВАНИЯ КАРТ ДАННЫМИ (Карты 2.0, 30.09.2026): низ установленной карты
 * «когда … — эффект, не больше N раз за ход» работает без строчки кода под
 * карту.
 */
class СрабатыванияTest {

    /** Партия, в арсенал которой подложены карты-срабатывания. */
    private static GameState стол(List<Map<String, Object>> карты) {
        GameConfig cfg = GameConfig.build("1.46.0", 2, 5L, null, null);
        ContentSet был = cfg.content.get("arsenal");
        List<Map<String, Object>> все = new ArrayList<>(был.entries);
        все.addAll(карты);
        cfg.content.sets.put("arsenal",
            new ContentSet("arsenal", был.version, все, был.raw, был.sourcePath));
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            agents.add(new Fix.FirstChoiceAgent(i));
        }
        GameEngine.bind(s, agents);
        s.публикатор = e -> Срабатывания.раздать(s, e);
        s.journal.startTurn(0);
        return s;
    }

    private static Map<String, Object> карта(String id, Map<String, Object> когда,
                                             Map<String, Object> эффект, int предел) {
        Map<String, Object> низ = new LinkedHashMap<>();
        низ.put("когда", когда);
        низ.put("эффект", эффект);
        низ.put("предел", предел);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("name", id);
        c.put("kind", "regular");
        c.put("bottom", низ);
        return c;
    }

    private static Map<String, Object> событие(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void ветка_даёт_монету_не_больше_предела_за_ход() {
        GameState s = стол(List.of(карта("t_mine",
            Map.of("событие", "ветка", "ветка", "mining"),
            Map.of("effect", "gain", "params", Map.of("coin", 1)), 1)));
        s.player(0).arsenalInstalled.add("t_mine");
        int было = s.player(0).resources.get(Resource.COIN);
        Map<String, Object> добыча = событие("type", "action", "seat", 0,
            "action", "mining", "fork", "extract", "ok", true);
        Срабатывания.раздать(s, добыча);
        Срабатывания.раздать(s, добыча);
        assertEquals(было + 1, s.player(0).resources.get(Resource.COIN), "предел 1 за ход");
        Срабатывания.раздать(s, событие("type", "action", "seat", 1,
            "action", "mining", "fork", "extract", "ok", true));
        s.journal.startTurn(0);
        Срабатывания.раздать(s, добыча);
        assertEquals(было + 2, s.player(0).resources.get(Resource.COIN),
            "чужая добыча не считается, новый ход — новый предел");
    }

    @Test
    void постройка_веткой_развилки_и_спец_действие() {
        GameState s = стол(List.of(карта("t_build",
            Map.of("событие", "ветка", "ветка", "build_plant"),
            Map.of("effect", "спец", "params", Map.of("n", 1)), 2)));
        s.player(0).arsenalInstalled.add("t_build");
        Срабатывания.раздать(s, событие("type", "action", "seat", 0,
            "action", "build", "fork", "power", "ok", true));
        Срабатывания.раздать(s, событие("type", "action", "seat", 0,
            "action", "build", "fork", "extract", "ok", true));
        assertEquals(1, s.journal.of(0).specBonus, "только постройка энергостанции");
    }

    @Test
    void уничтожение_техникой_и_потеря_своего() {
        GameState s = стол(List.of(
            карта("t_kill", Map.of("событие", "уничтожил", "кем", "vehicle", "жертва", "building"),
                Map.of("effect", "gain", "params", Map.of("coin", 2)), 3),
            карта("t_loss", Map.of("событие", "потерял"),
                Map.of("effect", "gain", "params", Map.of("coin", 1)), 1)));
        s.player(0).arsenalInstalled.add("t_kill");
        s.player(1).arsenalInstalled.add("t_loss");
        int было0 = s.player(0).resources.get(Resource.COIN);
        int было1 = s.player(1).resources.get(Resource.COIN);
        Срабатывания.раздать(s, событие("type", "combat_hit", "seat", 0,
            "attacker", "infantry.universal", "victim", "miner", "victim_owner", 1,
            "destroyed", true));
        Срабатывания.раздать(s, событие("type", "combat_hit", "seat", 0,
            "attacker", "vehicle.specialized", "victim", "minerL2", "victim_owner", 1,
            "destroyed", true));
        assertEquals(было0 + 2, s.player(0).resources.get(Resource.COIN), "только техникой");
        assertEquals(было1 + 1, s.player(1).resources.get(Resource.COIN),
            "потеря своего — в чужой ход, предел 1");
    }
}
