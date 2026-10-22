package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.Deck;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Effects;
import kelium.engine.Setup;

/**
 * КОЛОДА КОНТЕЙНЕРОВ 6.0.0 — ровно то, что нарисовано на экспорте дизайнера
 * (состав от 10.09.2026): четырнадцать лиц, тридцать две карты, ни одного
 * выбора «или», и два лица с жетоном войска из запаса на любой гекс без
 * чужих войск.
 *
 * <p>Тест сторожит состав и правило высадки, а не баланс: сдвинуть число
 * копий или вернуть вторую сторону — правка одной строки данных, и без
 * сторожа она пройдёт незамеченной.
 */
class ContainersDeckTest {

    private static ContentSet containers() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 6L, null, null);
        return cfg.content.get("containers");
    }

    @Test
    void действующийСводБерётКолодуШестойВерсии() {
        assertEquals("6.0.0", containers().version);
    }

    @Test
    void четырнадцатьЛицТридцатьДвеКарты() {
        ContentSet set = containers();
        assertEquals(14, set.entries.size(), "лиц в колоде");
        for (int players = 2; players <= 4; players++) {
            assertEquals(32, Deck.cullForPlayers(set.entries, players).size(),
                "карт в колоде на " + players + " игроков");
        }
    }

    @Test
    void ниОдногоВыбораИНиОдногоРазряда() {
        for (Map<String, Object> card : containers().entries) {
            String id = String.valueOf(card.get("id"));
            assertNull(card.get("b"), "у контейнера " + id + " есть вторая сторона");
            assertNull(card.get("tier"), "у контейнера " + id + " есть разряд редкости");
            assertTrue(card.get("a") instanceof Map<?, ?>, "у контейнера " + id
                + " нет напечатанной награды");
            assertTrue(card.get("face") instanceof String f && !f.isBlank(),
                "у контейнера " + id + " нет буквы лица");
        }
    }

    @Test
    void жетоныВойскСтавятсяНаЛюбойГексБезЧужихВойск() {
        List<Map<String, Object>> units = new ArrayList<>();
        for (Map<String, Object> card : containers().entries) {
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) card.get("a");
            if ("landing".equals(a.get("effect"))) {
                units.add(card);
            }
        }
        assertEquals(2, units.size(), "лиц с жетоном войска (M техника, N пехота)");
        for (Map<String, Object> card : units) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) ((Map<?, ?>) card.get("a"))
                .get("params");
            assertEquals("any_free_hex", params.get("where"),
                "контейнер " + card.get("id") + " должен ставить жетон на любой гекс");
            assertEquals(1, ((Number) params.get("count")).intValue());
        }
    }

    /** Агент, который запоминает предложенные гексы и всегда отказывается. */
    private static final class Spy extends Agent {
        final List<String> offered = new ArrayList<>();

        Spy(int seat) {
            super(seat, "шпион");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice o : options) {
                if ("landing".equals(o.kind()) && o.payload() instanceof Map<?, ?> m) {
                    offered.add(String.valueOf(m.get("hex")));
                }
            }
            for (Choice o : options) {
                if (o.payload() == null) {
                    return o;
                }
            }
            return options.get(0);
        }
    }

    /** Агент, который берёт ПЕРВЫЙ предложенный жетон, а не отказывается. */
    private static final class Taker extends Agent {
        String hex;

        Taker(int seat) {
            super(seat, "берущий");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice o : options) {
                if ("landing".equals(o.kind()) && o.payload() instanceof Map<?, ?> m) {
                    hex = String.valueOf(m.get("hex"));
                    return o;
                }
            }
            return options.get(0);
        }
    }

    /**
     * Жетон из контейнера — НАЙМ ИЗ ЗАПАСА (ответ дизайнера 10.09.2026: «тут
     * картон и дерево, тут всё конечно»): пока запас рода не пуст, жетон
     * встаёт на выбранный гекс; исчерпан запас — карта не даёт ничего.
     */
    @Test
    void жетонИзКонтейнераБерётсяИзЗапасаИКончаетсяВместеСНим() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
        int seat = 0;
        Taker taker = new Taker(seat);
        s.agents = List.of(taker, new Spy(1), new Spy(2), new Spy(3));
        var me = s.player(seat);
        var kind = kelium.core.UnitType.fromCode("infantry");
        int stock = s.tokenStats.unitStock(kind);
        int had = me.unitsOfKind(kind);
        assertTrue(had < stock, "на старте запас пехоты не пуст — иначе тест бессмыслен");

        Map<String, Object> card = Map.of("count", 1, "types", List.of("infantry"),
            "where", "any_free_hex");
        for (int i = had; i < stock; i++) {
            Map<String, Object> got = Effects.apply("landing", s, seat, card);
            assertEquals(1, got.get("landed"), "жетон №" + (i + 1) + " из запаса не встал");
            String hex = taker.hex;
            assertTrue(me.unitsOnField().stream()
                    .anyMatch(u -> u.type == kind && hex.equals(u.hexId)),
                "жетон должен стоять на выбранном гексе " + hex);
        }
        assertEquals(stock, me.unitsOfKind(kind), "запас рода исчерпан до дна");
        Map<String, Object> none = Effects.apply("landing", s, seat, card);
        assertEquals(0, none.get("landed"), "при пустом запасе контейнер ничего не даёт");
    }

    /**
     * Высадка «на любой гекс» шире своей зоны стройки — в этом её смысл, — но
     * гексы с чужими войсками не предлагает никогда.
     */
    @Test
    void высадкаИзКонтейнераШиреЗоныСтройкиИОбходитЧужиеВойска() {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
        int seat = 0;
        Spy spy = new Spy(seat);
        s.agents = List.of(spy, new Spy(1), new Spy(2), new Spy(3));

        Effects.apply("landing", s, seat,
            Map.of("count", 1, "types", List.of("infantry"), "where", "any_free_hex"));

        assertFalse(spy.offered.isEmpty(), "высадка не предложила ни одного гекса");
        List<String> zone = Actions.buildableHexes(s, seat);
        assertTrue(spy.offered.stream().anyMatch(h -> !zone.contains(h)),
            "«любой гекс» не вышел за пределы своей зоны стройки: " + spy.offered);
        List<String> чужие = new ArrayList<>();
        for (var p : s.players) {
            if (p.seat == seat) {
                continue;
            }
            for (UnitToken u : p.unitsOnField()) {
                чужие.add(u.hexId);
            }
        }
        assertFalse(чужие.isEmpty(), "на старте у соперников нет войск — тест ничего не проверил");
        for (String hex : spy.offered) {
            assertFalse(чужие.contains(hex), "предложен гекс с чужими войсками: " + hex);
        }
    }
}
