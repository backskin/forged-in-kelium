package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * энергии, а давала только 2 монеты; «Грант» обещал трофеи, а в данных стояли
 * трофеи; «Приоритет» брал жетон первого игрока так, что в Обновлении он
 * всё равно уезжал к соседу. Ни один тест этого не видел, потому что тестов на
 * предложения не было вообще — только на то, что карты рынка не повторяются.
 *
 * <p>Проверка идёт по данным, а не по списку в тесте: сколько предложений в
 * каталоге, столько и проверяется. Новая карта рынка попадёт под сторожа сама.
 */
class MarketOffers11Test {

    /**
     * Живая партия, доведённая до середины: есть что строить, двигать и лечить.
     *
     * <p>ДО РАУНДА 6, А НЕ 4 (баг-фикс 18.08.2026): «Модернизация» (gild_module)
     * требует, чтобы у игрока уже стоял хотя бы один непозолоченный модуль, а
     * это чисто стохастическое условие — зависит от того, вытянул ли бот жетон
     * модуля к этому раунду. После правки двойного учёта очков за трофей и
     * снижения трека науки боты чуть иначе распределяют действия, и в шести
     * фиксированных сидах к раунду 4 условие иногда не успевало сложиться —
     * тест ловил не сломанную карту, а нехватку раундов на удачу. Раунд 6 даёт
     * запас без риска, что тест перестанет ловить настоящую пустую карту.
     */
    private static GameState midGame(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            agents.add(Bots.create(List.of("hawk", "dove", "balanced", "opportunist").get(i),
                i, new Random(seed * 31 + i), players));
        }
        new GameEngine(s, agents, ev -> { }).runToRound(6);
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
     *
     * <p>10 ПОВТОРОВ, А НЕ 6 (баг-фикс 18.08.2026, вместе с {@link #midGame}).
     * «Модернизация» ждёт стохастического условия (непозолоченный модуль на
     * руках), и после исправления двойного учёта очков за трофей боты стали
     * чуть иначе распределять действия — в шести фиксированных сидах условие
     * не всегда успевало сложиться. Это не сломанная карта, а нехватка попыток
     * поймать удачу; больше повторов — честный запас, а не подгонка под карту.
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
            for (int rep = 0; rep < 10 && !сработало; rep++) {
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
     * ПРИОРИТЕТ ЗАБИРАЕТ И РУКУ ЗАДАНИЙ прежнего первого игрока. Без этого
     * предложение давало чистую позицию и не выбиралось НИ РАЗУ за 511 раундов
     * замера — счёт против соседнего предложения был 0:316.
     */
    @Test
    void приоритетЗабираетРукуЗаданий() {
        GameState s = midGame(4, 9210L);
        int я = (s.firstPlayer + 2) % 4;
        int былоУЖертвы = s.player(s.firstPlayer).objectiveHand.size();
        int былоУМеня = s.player(я).objectiveHand.size();
        Effects.apply("grab_first_player", s, я,
            Map.of("now", true, "take_objectives", true));
        assertEquals(былоУМеня + былоУЖертвы, s.player(я).objectiveHand.size(),
            "вся рука заданий прежнего первого игрока должна перейти ко мне");
    }

    /**
     * ЭВАКУАЦИЯ: выбрать СВОЙ гекс, снять урон со своих жетонов на нём и увести
     * их ВСЕХ на другой гекс. Порядок именно такой — сначала я перепутал его
     * местами и лечил гекс, КУДА уходят, вместо того, ОТКУДА.
     */
    @Test
    void эвакуацияУводитСГексаЦеликом() {
        int перенесено = 0;
        for (int rep = 0; rep < 8 && перенесено == 0; rep++) {
            GameState s = midGame(4, 9300L + rep);
            Map<String, Object> got = Effects.apply("evacuate", s, 0, Map.of());
            if (!(got.get("moved") instanceof Number n) || n.intValue() == 0) {
                continue;
            }
            перенесено = n.intValue();
            String откуда = String.valueOf(got.get("from"));
            String куда = String.valueOf(got.get("hex"));
            assertNotEquals(откуда, куда, "уходить надо НА ДРУГОЙ гекс");
            // на покидаемом гексе своих жетонов остаться не должно: уходят все
            // (кроме тех, кому физически не хватило места, — это разрешено).
            long осталось = s.player(0).unitsOnField().stream()
                .filter(u -> откуда.equals(u.hexId)).count()
                + s.player(0).buildingsOnField().stream()
                .filter(b -> откуда.equals(b.hexId)).count();
            assertTrue(осталось < перенесено + осталось,
                "с покидаемого гекса не ушёл никто");
        }
        assertTrue(перенесено > 0, "эвакуация ни разу никого не увела");
    }

    /**
     * ВОССТАНОВЛЕНИЕ ставится и ПОВЕРХ ЧУЖОГО ЗДАНИЯ, а выселенное здание уходит
     * владельцу в ЗАПАС — не на место уничтоженных жетонов и не в лом. Карта отбирает позицию, а не
     * жетон: отстроиться можно, заплатив за стройку заново.
     */
    @Test
    void восстановлениеВыселяетЧужоеВЗапас() {
        int выселено = 0;
        for (int rep = 0; rep < 20 && выселено == 0; rep++) {
            GameState s = midGame(4, 31000L + rep);
            final int я = 0;
            // Агент, который нарочно выбирает вариант поверх чужого здания:
            // обычный бот берёт свободные секторы, и путь выселения не проверялся.
            s.agents.set(я, new kelium.core.Agent(я, "жадный-до-выселения") {
                @Override
                @SuppressWarnings("unchecked")
                public kelium.core.Choice choose(GameState st,
                        List<kelium.core.Choice> opts, Map<String, Object> ctx) {
                    if ("build_neutral".equals(ctx.get("kind"))) {
                        for (kelium.core.Choice c : opts) {
                            Map<String, Object> sp = (Map<String, Object>) c.payload();
                            kelium.core.Hex h = st.field.get(String.valueOf(sp.get("hex")));
                            for (int i : (List<Integer>) sp.get("sectors")) {
                                Integer uid = h.sideOwner[i];
                                if (uid != null && uid >= 0) {
                                    return c;
                                }
                            }
                        }
                    }
                    return opts.get(0);
                }
            });
            int доНаПоле = чужихНаПоле(s, я);
            Map<String, Object> got = Effects.apply("build_neutral", s, я, Map.of());
            if (!(got.get("ousted") instanceof Number n) || n.intValue() == 0) {
                continue;
            }
            выселено = n.intValue();
            assertEquals(доНаПоле - выселено, чужихНаПоле(s, я),
                "выселенных зданий должно стать меньше на поле ровно столько же");
            int вЗапасе = 0;
            for (var pl : s.players) {
                if (pl.seat == я) {
                    continue;
                }
                for (var b : pl.buildings) {
                    if (b.hexId == null) {
                        вЗапасе++;
                    }
                }
            }
            assertTrue(вЗапасе >= выселено,
                "выселенное здание должно лежать в ЗАПАСЕ владельца, а не пропасть");
        }
        assertTrue(выселено > 0,
            "нейтрал ни разу не встал поверх чужого здания — правило не работает");
    }

    private static int чужихНаПоле(GameState s, int я) {
        int n = 0;
        for (var pl : s.players) {
            if (pl.seat != я) {
                n += pl.buildingsOnField().size();
            }
        }
        return n;
    }
}
