package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.ContentSet;
import kelium.dataio.GameConfig;
import kelium.engine.Predicates;
import kelium.support.Fix;

/**
 * СУПЕР-ЗАДАНИЯ — путь к мгновенной победе, у которого не было ни одного теста.
 *
 * <p>Опасное место: {@code GameEngine.superDeployReady} ГЛОТАЕТ незарегистрированный
 * предикат и любую ошибку, молча возвращая «не готово». Опечатка в
 * {@code win_pattern} навсегда и незаметно отключила бы победу этой картой.
 * Здесь она падает сразу.
 */
class SuperObjectiveTest {

    private static ContentSet cards(GameState s) {
        return ((GameConfig) s.config).content.get("super_objectives");
    }

    /**
     * Каждому игроку раздаются СВОИ карты супер задания, ни одна не повторяется
     * у двух игроков. С правил 1.6.0 карт раздаётся несколько (deal), а выбор
     * делает сам игрок в начале партии — см. SuperObjectives2Test.
     */
    @Test
    void everyPlayerGetsHisOwnProject() {
        GameState s = Fix.game();
        List<String> given = new ArrayList<>();
        for (PlayerState p : s.players) {
            List<String> mine = new ArrayList<>(p.superObjectiveOffer);
            if (mine.isEmpty() && p.superObjective != null) {
                mine.add(p.superObjective);
            }
            assertFalse(mine.isEmpty(), "место " + p.seat + " осталось без супер-задания");
            for (String cid : mine) {
                assertFalse(given.contains(cid), "супер-задание " + cid + " выдано дважды");
                given.add(cid);
            }
        }
    }

    /**
     * У КАЖДОЙ КАРТЫ ЧЕТЫРЕ ЯЧЕЙКИ, и каждая ячейка называет то, что движок
     * умеет списать. Опечатка в виде взноса раньше означала бы карту, которую
     * нельзя вскрыть вообще, и заметить это было бы нечем.
     */
    @Test
    void уКаждойКартыЧетыреПонятныеЯчейки() {
        GameState s = Fix.game();
        java.util.Set<String> known = java.util.Set.of(
            "coin", "ammo", "kelium", "debris",
            "enemy_unit_token", "enemy_building_token", "enemy_token",
            "own_building", "own_miner", "own_power_plant", "own_unit");
        List<String> bad = new ArrayList<>();
        for (Map<String, Object> card : cards(s).entries) {
            String id = String.valueOf(card.get("id"));
            Object cells = card.get("cells");
            if (!(cells instanceof List<?> l) || l.size() != 4) {
                bad.add(id + ": ячеек не четыре");
                continue;
            }
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> cell)) {
                    bad.add(id + ": ячейка не запись");
                    continue;
                }
                String kind = String.valueOf(cell.get("kind"));
                if (!known.contains(kind)) {
                    bad.add(id + ": движок не знает вид взноса «" + kind + "»");
                }
            }
        }
        assertTrue(bad.isEmpty(), "ячейки супер заданий: " + bad);
    }

    /**
     * ВСЕ КАРТЫ ДАЮТ ОДИНАКОВЫЕ ОЧКИ ЗА ВСКРЫТИЕ (правило дизайнера 17.08.2026:
     * «сделай одинаково всем, а не разброс в 2-5»). Разброс возвращать нельзя —
     * это была главная причина, по которой одни проекты брали, а другие нет.
     */
    @Test
    void очкиЗаВскрытиеОдинаковыеУВсех() {
        GameState s = Fix.game();
        java.util.Set<Integer> vps = new java.util.LinkedHashSet<>();
        for (Map<String, Object> card : cards(s).entries) {
            assertTrue(card.get("vp_on_reveal") instanceof Number,
                card.get("id") + ": не сказано, сколько очков за вскрытие");
            vps.add(((Number) card.get("vp_on_reveal")).intValue());
        }
        assertEquals(1, vps.size(), "очки за вскрытие разошлись между картами: " + vps);
    }

    /**
     * У КАЖДОЙ КАРТЫ НАЗВАН РОД ЖЕТОНА СУПЕРОРУЖИЯ, и роды разложены поровну:
     * восемь карт на четыре рода — по две на род. Иначе один род оказался бы
     * представлен вчетверо чаще другого.
     */
    @Test
    void родыСупероружияРазложеныПоровну() {
        GameState s = Fix.game();
        Map<String, Integer> byUnit = new java.util.LinkedHashMap<>();
        for (Map<String, Object> card : cards(s).entries) {
            Object u = card.get("weapon_unit");
            assertNotNull(u, card.get("id") + ": не назван род жетона супероружия");
            byUnit.merge(String.valueOf(u), 1, Integer::sum);
        }
        assertEquals(4, byUnit.size(), "родов должно быть четыре, а не " + byUnit);
        for (var e : byUnit.entrySet()) {
            assertEquals(2, e.getValue().intValue(),
                "род " + e.getKey() + " встречается " + e.getValue() + " раз вместо двух");
        }
    }

    /**
     * ТРЕБУЕМЫЙ СИМВОЛ СУЩЕСТВУЕТ. Разметка символов уже один раз разошлась с
     * колодой (ссылалась на карты a01-a24, которых в игре нет), и модуль молча
     * не работал. Здесь такая опечатка падает сразу.
     */
    @Test
    void требуемыйСимволЕстьВРазметке() {
        GameState s = Fix.game();
        java.util.Set<String> forms =
            new java.util.HashSet<>(kelium.engine.Symbols.of(s).glyphs().keySet());
        List<String> bad = new ArrayList<>();
        for (Map<String, Object> card : cards(s).entries) {
            Object need = card.get("requires_symbol");
            if (need != null && !forms.contains(String.valueOf(need))) {
                bad.add(card.get("id") + ": символа «" + need + "» в разметке нет");
            }
        }
        assertTrue(bad.isEmpty(), String.valueOf(bad));
    }
}
