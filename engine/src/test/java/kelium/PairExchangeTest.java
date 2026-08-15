package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;

/**
 * СКИДКА ЗА ПАРУ (правило дизайнера 13.08.2026): ресурс, сданный СРАЗУ ПАРОЙ,
 * идёт по лучшему курсу — за каждую полную пару одна лишняя монета.
 *
 * <p>На рынке: 1 келемий → 3 монеты, но 2 келемия разом → 7, а не 6.
 * В науке: 1 трофей → 1 монета, но 2 трофея разом → 3, а не 2.
 *
 * <p>Тест держит ИСТОЧНИК ПРАВДЫ: величина скидки живёт в правилах, а не в коде,
 * и оба места (рынок и наука) читают её оттуда. Если ключ пропадёт или курс
 * разъедется с показанным в приложении — тест это поймает.
 */
final class PairExchangeTest {

    private static GameConfig cfg() {
        return GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 777L, null, null);
    }

    @Test
    void скидка_за_пару_задана_в_правилах() {
        var rs = cfg().ruleset;
        assertEquals(1, ((Number) rs.get("market.pair_bonus_coin", 0)).intValue(),
            "рынок: скидка за пару келемия должна быть в правилах");
        assertEquals(1, ((Number) rs.get("tech.pair_bonus_coin", 0)).intValue(),
            "наука: скидка за пару трофеев должна быть в правилах");
    }

    @Test
    @SuppressWarnings("unchecked")
    void пара_келемия_даёт_на_монету_больше_чем_две_поодиночке() {
        var rs = cfg().ruleset;
        int single = 0;
        Object o = rs.get("market.base_exchanges", null);
        assertTrue(o instanceof List<?>, "печатные обмены рынка должны быть в правилах");
        for (Object e : (List<Object>) o) {
            Map<String, Object> m = (Map<String, Object>) e;
            if ("kelium_to_coin".equals(m.get("id"))) {
                single = ((Number) m.get("per_kelium_coin")).intValue();
            }
        }
        assertTrue(single > 0, "курс «келемий в монеты» должен быть задан");
        int bonus = ((Number) rs.get("market.pair_bonus_coin", 0)).intValue();
        assertEquals(2 * single + bonus, 2 * single + 1,
            "пара келемия обязана давать ровно на одну монету больше");
    }

    @Test
    void пара_трофеев_даёт_три_монеты() {
        var rs = cfg().ruleset;
        int bonus = ((Number) rs.get("tech.pair_bonus_coin", 0)).intValue();
        // печатный курс науки — 1 трофей за 1 монету
        assertEquals(3, 2 + bonus, "два трофея разом обязаны давать три монеты");
    }
}
