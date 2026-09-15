package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.ability.Abilities;

/**
 * КОЛОДА АРСЕНАЛА 6.0.0 — сорок карт по диктовке дизайнера 14–15.09.2026.
 *
 * <p>Сторожит ровно те правила, которые дизайнер назвал вслух и которые
 * ломаются правкой одной строки:
 *
 * <ul>
 *   <li>сорок обычных карт и восемь стартовых;</li>
 *   <li>ОДНО ИМЯ НА ОДИН НИЗ — повторил установку, повторил и название;</li>
 *   <li>пара «верх + низ» не повторяется ни разу;</li>
 *   <li>три редких утиля подняты до двух копий;</li>
 *   <li>у каждого низа есть РЕАЛИЗОВАННАЯ способность, у каждого верха —
 *       зарегистрированный эффект: карта без кода молча выпадает из колоды
 *       на подготовке, и в отчётах она выглядит просто редкой.</li>
 * </ul>
 */
class ArsenalSixDeckTest {

    private static ContentSet arsenal() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 6L, null, null);
        return cfg.content.get("arsenal");
    }

    private static List<Map<String, Object>> обычные() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : arsenal().entries) {
            if (!"starting".equals(String.valueOf(c.get("kind")))) {
                out.add(c);
            }
        }
        return out;
    }

    private static String низ(Map<String, Object> card) {
        return card.get("bottom") instanceof Map<?, ?> m
            ? String.valueOf(m.get("passive")) : "";
    }

    private static String верх(Map<String, Object> card) {
        return card.get("top") instanceof Map<?, ?> m
            ? String.valueOf(m.get("label")) : "";
    }

    @Test
    void действующийСводБерётШестойНабор() {
        assertEquals("6.0.0", arsenal().version);
    }

    @Test
    void сорокОбычныхИВосемьСтартовых() {
        assertEquals(40, обычные().size(), "обычных карт в колоде");
        assertEquals(48, arsenal().entries.size(), "всего записей, считая стартовые");
    }

    @Test
    void одинНизОдноИмя() {
        Map<String, String> имяПоНизу = new HashMap<>();
        for (Map<String, Object> c : обычные()) {
            String п = низ(c);
            String имя = String.valueOf(c.get("name"));
            String было = имяПоНизу.putIfAbsent(п, имя);
            if (было != null) {
                assertEquals(было, имя,
                    "низ " + п + " лежит под двумя именами — правило дизайнера "
                        + "15.09.2026: повторил установку, повторил и название");
            }
        }
    }

    @Test
    void параВерхНизНеПовторяется() {
        Set<String> пары = new HashSet<>();
        for (Map<String, Object> c : обычные()) {
            String пара = верх(c) + " | " + низ(c);
            assertTrue(пары.add(пара),
                "пара «верх + низ» повторяется у " + c.get("id") + ": " + пара);
        }
    }

    @Test
    void редкийУтильПоднятДоДвухКопий() {
        Map<String, Integer> счёт = new TreeMap<>();
        for (Map<String, Object> c : обычные()) {
            счёт.merge(верх(c), 1, Integer::sum);
        }
        // Три верха стояли в наборе 5.0.0 в единственном экземпляре; новые
        // карты подняли каждый до двух — прямое указание дизайнера.
        for (String редкий : List.of(
                "минус келемий - улучши 1 модуль",
                "воспользуйся одной сделкой с любой сброшенной карты рынка",
                "3 боеприпаса")) {
            assertEquals(2, счёт.getOrDefault(редкий, 0),
                "копий утиля «" + редкий + "»");
        }
        // Новый утиль ровно один: он сильный, и второй экземпляр менял бы
        // колоду сильнее, чем этого просили.
        assertEquals(1, счёт.getOrDefault("три СПЕЦ-действия в этот ход", 0),
            "копий нового утиля");
    }

    @Test
    void шестьНовыхНизовНаМесте() {
        Map<String, String> ожидаемые = new LinkedHashMap<>();
        ожидаемые.put("repair_all_in_refresh", "Ремонтная база");
        ожидаемые.put("counter_battle_for_ammo", "Ответный огонь");
        ожидаемые.put("spec_neutral_near_own_building", "Подрядчик");
        ожидаемые.put("spec_release_garrison", "Комендатура");
        ожидаемые.put("kelium_instead_of_ammo_in_operation", "Кристальный боезапас");
        ожидаемые.put("spec_energy_on_card_free_attack", "Разрядник");

        Map<String, String> есть = new HashMap<>();
        for (Map<String, Object> c : обычные()) {
            есть.putIfAbsent(низ(c), String.valueOf(c.get("name")));
        }
        for (var e : ожидаемые.entrySet()) {
            assertEquals(e.getValue(), есть.get(e.getKey()),
                "низ " + e.getKey() + " должен лежать на карте «" + e.getValue() + "»");
        }
    }

    @Test
    void укаждогоНизаЕстьРеализованнаяСпособность() {
        List<String> мёртвые = new ArrayList<>();
        for (Map<String, Object> c : обычные()) {
            String п = низ(c);
            if (п.isBlank() || "null".equals(п)) {
                мёртвые.add(c.get("id") + ": низ без способности");
                continue;
            }
            if (!Abilities.implemented(п)) {
                мёртвые.add(c.get("id") + ": " + п);
            }
        }
        assertTrue(мёртвые.isEmpty(),
            "низы без реализации (карта молча выпадет из колоды): " + мёртвые);
    }

    @Test
    void укаждогоВерхаЕстьЗарегистрированныйЭффект() {
        List<String> мёртвые = new ArrayList<>();
        for (Map<String, Object> c : обычные()) {
            String эффект = c.get("top") instanceof Map<?, ?> m
                ? String.valueOf(m.get("effect")) : "";
            assertFalse(эффект.isBlank(), "у карты " + c.get("id") + " нет верха");
            if (!kelium.engine.Effects.isImplemented(эффект)) {
                мёртвые.add(c.get("id") + ": " + эффект);
            }
        }
        assertTrue(мёртвые.isEmpty(), "верхние эффекты без реализации: " + мёртвые);
    }

    @Test
    void новаяСпособностьРемонтаЗнаетПроСебя() {
        var ability = Abilities.byId("repair_all_in_refresh");
        assertNotNull(ability, "способность ремонта зарегистрирована");
        assertNotNull(ability.hint(), "у способности есть самоописание для бота");
    }
}
