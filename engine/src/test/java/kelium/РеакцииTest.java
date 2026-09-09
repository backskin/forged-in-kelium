package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.TurnJournal;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.ContentSet;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.Setup;
import kelium.engine.Реакции;

/**
 * РЕАКЦИИ КАРТ ЗАДАНИЙ — верхи «В МОМЕНТЕ» (решение дизайнера 04.09.2026).
 *
 * <p>Проверяется то, ради чего они и заведены: карта срабатывает В ЧУЖОЙ ХОД и
 * МЕНЯЕТ ИСХОД чужого удара. Прежние верхи такого не умели вовсе, и мерить в
 * них было нечего — «дали бесплатное действие» видно и без теста.
 *
 * <p>Карты для проверки заводятся ЗДЕСЬ, в наборе теста, а не берутся из колоды:
 * реакции появляются в колоде отдельной работой, а механика обязана держаться
 * сама по себе — иначе тест мерил бы состав колоды, а не правило.
 */
class РеакцииTest {

    /** Агент, который всегда отвечает реакцией и бьёт по подготовленному гексу. */
    private static final class Отвечающий extends Agent {
        String preferHex;

        Отвечающий(int seat) {
            super(seat, "reaction#" + seat);
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            for (Choice o : options) {
                if ("reaction_burn".equals(o.kind())) {
                    return o;              // реакция — всегда да
                }
            }
            if (preferHex != null) {
                for (Choice o : options) {
                    if (preferHex.equals(o.payload())) {
                        return o;
                    }
                    if (o.payload() instanceof Map<?, ?> m && preferHex.equals(m.get("target"))) {
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

    /** Стол на двоих с боем и агентами, всегда отвечающими реакцией. */
    private GameState стол() {
        GameConfig cfg = GameConfig.build(2, 42L);
        GameState s = Setup.buildGame(cfg);
        s.journal = new TurnJournal(s.numPlayers());
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < s.numPlayers(); i++) {
            agents.add(new Отвечающий(i));
        }
        s.agents = agents;
        s.combat = new CombatResolver(s, e -> { }).bindAgents(agents);
        return s;
    }

    /** Завести карту задания с верхом-реакцией и положить её игроку в руку. */
    private void датьРеакцию(GameState s, int seat, String cid, Реакции.Вид вид) {
        ContentSet было = Ctx.cards(s, "objectives");
        List<Map<String, Object>> записи = new ArrayList<>(было.entries);
        Map<String, Object> карта = new LinkedHashMap<>();
        карта.put("id", cid);
        карта.put("name", "проверка " + вид.код);
        карта.put("kind", "regular");
        карта.put("type", "state");
        карта.put("top", Map.of("label", вид.name(), "effect", Реакции.ЭФФЕКТ,
            "params", Map.of("kind", вид.код)));
        записи.add(карта);
        Ctx.cfg(s).content.sets.put("objectives",
            new ContentSet(было.type, было.version, записи, было.raw, было.sourcePath));
        s.player(seat).objectiveHand.add(cid);
    }

    private String соседний(GameState s, String hex) {
        for (String nb : s.field.neighbors(hex)) {
            kelium.core.Hex h = s.field.get(nb);
            if (h.kind == kelium.core.HexKind.NORMAL && !h.hasNeutral()) {
                return nb;
            }
        }
        return s.field.neighbors(hex).get(0);
    }

    /** Поставить свою пехоту на гекс и дать боеприпасов. */
    private UnitToken атакующий(GameState s, PlayerState p, String hex) {
        UnitToken a = p.unitsOnField().get(0);
        a.hexId = hex;
        p.resources.add(Resource.AMMO, 5);
        return a;
    }

    @Test
    void ответныйОгоньРанитУбийцу() {
        GameState s = стол();
        PlayerState p0 = s.player(0);
        PlayerState p1 = s.player(1);
        s.journal.startTurn(0);
        String откуда = p0.startHex;
        String куда = соседний(s, откуда);
        UnitToken убийца = атакующий(s, p0, откуда);
        UnitToken жертва = s.tokenStats.makeUnit(UnitType.INFANTRY, 1, 999);
        жертва.hexId = куда;
        p1.units.add(жертва);
        датьРеакцию(s, 1, "tst_rf", Реакции.Вид.ОТВЕТНЫЙ_ОГОНЬ);

        ((Отвечающий) s.agents.get(0)).preferHex = куда;
        ((CombatResolver) s.combat).runBattle(0, (Agent) s.agents.get(0));

        // У пехоты прочность 1, поэтому единственный удар хрипа её и добивает:
        // жетон уходит с поля и достаётся ТОМУ, КТО ОТВЕТИЛ. Это и есть смысл
        // карты — гибнущее войско забирает убийцу с собой.
        assertNull(убийца.hexId, "убийца снят с поля предсмертным хрипом");
        assertTrue(p1.destroyedTokens.contains(убийца),
            "убийца достался тому, кто ответил");
        assertFalse(p1.objectiveHand.contains("tst_rf"), "карта сгорела");
    }

    @Test
    void безКартыХрипаНет() {
        GameState s = стол();
        PlayerState p0 = s.player(0);
        PlayerState p1 = s.player(1);
        s.journal.startTurn(0);
        String откуда = p0.startHex;
        String куда = соседний(s, откуда);
        UnitToken убийца = атакующий(s, p0, откуда);
        UnitToken жертва = s.tokenStats.makeUnit(UnitType.INFANTRY, 1, 999);
        жертва.hexId = куда;
        p1.units.add(жертва);

        ((Отвечающий) s.agents.get(0)).preferHex = куда;
        ((CombatResolver) s.combat).runBattle(0, (Agent) s.agents.get(0));

        assertEquals(0, убийца.damage, "без карты реакции убийца невредим");
    }

    @Test
    void эвакуацияУводитЖетонВЗапасВладельца() {
        GameState s = стол();
        PlayerState p0 = s.player(0);
        PlayerState p1 = s.player(1);
        s.journal.startTurn(0);
        String откуда = p0.startHex;
        String куда = соседний(s, откуда);
        атакующий(s, p0, откуда);
        UnitToken жертва = s.tokenStats.makeUnit(UnitType.INFANTRY, 1, 999);
        жертва.hexId = куда;
        p1.units.add(жертва);
        датьРеакцию(s, 1, "tst_ev", Реакции.Вид.ЭВАКУАЦИЯ_ТРОФЕЕВ);

        ((Отвечающий) s.agents.get(0)).preferHex = куда;
        ((CombatResolver) s.combat).runBattle(0, (Agent) s.agents.get(0));

        assertFalse(p0.destroyedTokens.contains(жертва),
            "жетон НЕ достался атакующему");
        assertTrue(p1.units.contains(жертва), "жетон остался у своего владельца");
        assertNull(жертва.hexId, "и ушёл с поля в запас");
    }

    @Test
    void реакцияОпознаётсяПоЗаписиКарты() {
        GameState s = стол();
        датьРеакцию(s, 0, "tst_rc", Реакции.Вид.РИКОШЕТ);
        assertEquals(Реакции.Вид.РИКОШЕТ, Реакции.видКарты(s, "tst_rc"));
        assertTrue(Реакции.естьЧем(s, 0, Реакции.Вид.РИКОШЕТ));
        assertFalse(Реакции.естьЧем(s, 0, Реакции.Вид.ОТХОД));
    }
}
