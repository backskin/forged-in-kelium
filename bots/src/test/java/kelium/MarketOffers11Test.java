package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КАЖДОЕ ПРЕДЛОЖЕНИЕ РЫНКА ДЕЛАЕТ ТО, ЧТО ОБЕЩАЕТ — ревью дизайнера 17.08.2026.
 *
 * <p>Зачем нужен сторож. Ревью нашло, что метка на карте и происходящее в игре
 * расходились у половины предложений: «Перекоммутация» обещала бесплатную Смену
 * энергии, а давала только 2 монеты; «Грант» обещал обломки, а в данных стояли
 * трофейные очки; «Приоритет» брал жетон первого игрока так, что в Обновлении он
 * всё равно уезжал к соседу. Ни один тест этого не видел, потому что тестов на
 * предложения не было вообще — только на то, что карты рынка не повторяются.
 *
 * <p>Проверка идёт по данным, а не по списку в тесте: сколько предложений в
 * каталоге, столько и проверяется. Новая карта рынка попадёт под сторожа сама.
 */
class MarketOffers11Test {

    /** Живая партия, доведённая до середины: есть что строить, двигать и лечить. */
    private static GameState midGame(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(List.of("hawk", "dove", "balanced", "opportunist").get(i),
                i, new Random(seed * 31 + i), players));
        }
        new GameEngine(s, agents, ev -> { }).runToRound(4);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> offers(GameConfig cfg) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object card : cfg.content.get("market").entries) {
            Map<String, Object> cm = (Map<String, Object>) card;
            for (String side : new String[]{"left", "right"}) {
                if (cm.get(side) instanceof Map<?, ?> off) {
                    Map<String, Object> o = new LinkedHashMap<>((Map<String, Object>) off);
                    o.put("_card", cm.get("id"));
                    o.put("_side", side);
                    out.add(o);
                }
            }
        }
        return out;
    }

    /**
     * НИ ОДНО ПРЕДЛОЖЕНИЕ НЕ ПУСТОЕ. Каждое применяется на живом состоянии и
     * обязано вернуть непустую телеметрию: пустая означает «игрок заплатил
     * келемий и не получил ничего», и раньше так себя вели три предложения из
     * шестнадцати.
     */
    @Test
    @SuppressWarnings("unchecked")
    void всеПредложенияЧтоТоДелают() {
        int players = 4;
        GameConfig cfg = LayoutLibrary.configFor(players, 9100L);
        List<Map<String, Object>> offers = offers(cfg);
        assertTrue(offers.size() >= 16, "предложений в каталоге меньше шестнадцати: " + offers.size());
        List<String> пустые = new ArrayList<>();
        for (Map<String, Object> off : offers) {
            boolean сработало = false;
            for (int rep = 0; rep < 6 && !сработало; rep++) {
                GameState s = midGame(players, 9100L + rep);
                Map<String, Object> params = off.get("params") instanceof Map<?, ?> pm
                    ? (Map<String, Object>) pm : Map.of();
                Map<String, Object> got = Effects.apply(
                    String.valueOf(off.get("effect")), s, 0, params);
                assertNotNull(got, off.get("_card") + "/" + off.get("_side"));
                сработало = got.values().stream().anyMatch(v ->
                    !(v instanceof Number n && n.intValue() == 0) && !Boolean.FALSE.equals(v));
            }
            if (!сработало) {
                пустые.add(off.get("_card") + "/" + off.get("_side") + " «" + off.get("name") + "»");
            }
        }
        assertTrue(пустые.isEmpty(), "предложения не делают ничего ни в одном состоянии: " + пустые);
    }

    /**
     * ВТОРАЯ ЯЧЕЙКА ПРЕДЛОЖЕНИЯ — ТОЛЬКО НА ЧЕТВЕРЫХ (ревью 17.08.2026; было
     * «3+»). Правило живёт в одном месте движка и в двух подписях интерфейса, и
     * расходились они уже дважды.
     */
    @Test
    void втораяЯчейкаТолькоНаЧетверых() {
        for (int players = 2; players <= 4; players++) {
            int открыто = kelium.engine.Actions.marketCellsOpen(players);
            assertEquals(players >= 4 ? 2 : 1, открыто,
                "на " + players + " игроках должно быть открыто "
                + (players >= 4 ? "две ячейки" : "одна ячейка"));
        }
    }

    /**
     * ПРИОРИТЕТ: жетон первого игрока переходит СРАЗУ и в ближайшее Обновление
     * НЕ ПЕРЕДАЁТСЯ по кругу. Прежняя реализация кладла жетон на предыдущего по
     * кругу в расчёте на сдвиг — по новому правилу это дало бы чужого первого.
     */
    @Test
    void приоритетДержитЖетонОдинРаунд() {
        GameState s = midGame(4, 9200L);
        int я = 2;
        Effects.apply("grab_first_player", s, я, Map.of("now", true));
        assertEquals(я, s.firstPlayer, "жетон должен перейти сразу");
        assertTrue(s.firstPlayerHeld, "передача в Обновлении должна быть отменена");
    }

    /**
     * ЭВАКУАЦИЯ — ТЕЛЕПОРТ, А НЕ ДВИЖЕНИЕ: она собирает жетоны на один гекс, и
     * здания в том числе. Первая реализация переносила только войска, и «любое
     * число зданий и войск в любом сочетании» из заказа не выполнялось.
     */
    @Test
    void эвакуацияПереноситИЗдания() {
        int перенесеноЗданий = 0;
        for (int rep = 0; rep < 8 && перенесеноЗданий == 0; rep++) {
            GameState s = midGame(4, 9300L + rep);
            Map<String, Object> got = Effects.apply("evacuate", s, 0, Map.of());
            if (got.get("moved_buildings") instanceof Number n) {
                перенесеноЗданий += n.intValue();
            }
        }
        assertTrue(перенесеноЗданий > 0,
            "эвакуация ни разу не перенесла здание — значит переносит только войска");
    }
}
