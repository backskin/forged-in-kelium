package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.ability.Abilities;

/**
 * КОЛОДА АРСЕНАЛА 7.0.0 И СУПЕР-АРСЕНАЛ 3.0.0 — по печатным картам дизайнера
 * (экспорт 13.09 и 17.09.2026).
 *
 * <p>Сторожит то, что считано с печати и ломается правкой одной строки: состав
 * (32 обычные, 4 начальные, 4 супер-войска), «одно имя — один низ», звёзды на
 * лицах и то, что у каждой карты есть живой код — карта без кода молча
 * выпадает из колоды на подготовке.
 */
class ArsenalSevenDeckTest {

    private static GameConfig cfg() {
        return GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 7L, null, null);
    }

    private static ContentSet arsenal() {
        return cfg().content.get("arsenal");
    }

    private static List<Map<String, Object>> вид(String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : arsenal().entries) {
            boolean старт = "starting".equals(String.valueOf(c.get("kind")));
            if (старт == "starting".equals(kind)) {
                out.add(c);
            }
        }
        return out;
    }

    private static String низ(Map<String, Object> card) {
        return card.get("bottom") instanceof Map<?, ?> m
            ? String.valueOf(m.get("passive")) : "";
    }

    @Test
    void действующийСводБерётСедьмойНаборИТретийСупер() {
        assertEquals("7.0.0", arsenal().version);
        assertEquals("3.0.0", cfg().content.get("super_arsenal").version);
    }

    @Test
    void тридцатьДвеОбычныхИЧетыреНачальных() {
        assertEquals(32, вид("regular").size(), "обычных карт — печатные №1…32");
        assertEquals(4, вид("starting").size(), "начальных — по одной на игрока");
    }

    @Test
    void одинНизОдноИмя() {
        Map<String, String> имяПоНизу = new HashMap<>();
        for (Map<String, Object> c : вид("regular")) {
            String было = имяПоНизу.putIfAbsent(низ(c), String.valueOf(c.get("name")));
            if (было != null) {
                assertEquals(было, c.get("name"), "низ " + низ(c) + " под двумя именами");
            }
        }
    }

    @Test
    void параВерхНизНеПовторяется() {
        Set<String> пары = new HashSet<>();
        for (Map<String, Object> c : вид("regular")) {
            String верх = c.get("top") instanceof Map<?, ?> m ? String.valueOf(m.get("label")) : "";
            assertTrue(пары.add(верх + " | " + низ(c)), "пара повторяется у " + c.get("id"));
        }
    }

    @Test
    void звёздыПоПечати() {
        // Звезда в левом нижнем углу лица: №1, 5, 7, 8, 9, 11, 12, 16, 17, 19,
        // 20, 22, 26, 27, 28, 29 — по одной.
        Set<String> соЗвездой = new HashSet<>();
        for (Map<String, Object> c : вид("regular")) {
            if (c.get("vp_on_card") instanceof Number n && n.intValue() > 0) {
                assertEquals(1, n.intValue(), "на карте одна звезда: " + c.get("id"));
                соЗвездой.add(String.valueOf(c.get("id")));
            }
        }
        Set<String> ждём = new HashSet<>();
        for (int n : new int[]{1, 5, 7, 8, 9, 11, 12, 16, 17, 19, 20, 22, 26, 27, 28, 29}) {
            ждём.add(String.format("a7_%02d", n));
        }
        assertEquals(ждём, соЗвездой);
        for (Map<String, Object> c : вид("starting")) {
            assertFalse(c.containsKey("vp_on_card"), "на начальных звёзд нет");
        }
    }

    @Test
    void укаждойКартыЖивойКод() {
        List<String> мёртвые = new ArrayList<>();
        for (Map<String, Object> c : arsenal().entries) {
            if (!Abilities.implemented(низ(c))) {
                мёртвые.add(c.get("id") + " низ " + низ(c));
            }
            String эффект = c.get("top") instanceof Map<?, ?> m
                ? String.valueOf(m.get("effect")) : "";
            if (!kelium.engine.Effects.isImplemented(эффект)) {
                мёртвые.add(c.get("id") + " верх " + эффект);
            }
        }
        for (Map<String, Object> c : cfg().content.get("super_arsenal").entries) {
            if (!Abilities.implemented(String.valueOf(c.get("passive")))) {
                мёртвые.add(c.get("id") + " " + c.get("passive"));
            }
        }
        assertTrue(мёртвые.isEmpty(), "без реализации: " + мёртвые);
    }

    @Test
    void суперВойскаПоПечати() {
        List<Map<String, Object>> супер = cfg().content.get("super_arsenal").entries;
        assertEquals(4, супер.size(), "четыре заполненных лица");
        Set<String> роды = new HashSet<>();
        for (Map<String, Object> c : супер) {
            assertEquals("troop", c.get("kind"));
            assertEquals(1, ((Number) c.get("attacks")).intValue(), "одна рамка атаки");
            assertEquals(1, ((Number) c.get("vp_on_card")).intValue(), "одна звезда");
            роды.add(String.valueOf(c.get("unit")));
        }
        assertEquals(Set.of("infantry", "vehicle", "aircraft", "tower"), роды);
    }
}
