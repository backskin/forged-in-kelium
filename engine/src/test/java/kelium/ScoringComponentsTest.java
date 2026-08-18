package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.support.Fix;

/**
 * Подсчёт победных очков ПОКОМПОНЕНТНО.
 *
 * <p>Раньше очки проверялись только суммарно и только в конце случайной партии:
 * ошибка в одном источнике маскировалась остальными. Здесь каждый источник
 * проверяется отдельно — и что итог равен сумме составляющих.
 */
class ScoringComponentsTest {

    private static int vp(GameState s, int seat, String source) {
        return Scoring.scorePlayer(s, seat).getOrDefault(source, 0);
    }

    /** Итог всегда равен сумме составляющих — иначе разбивка врёт отчётам. */
    @Test
    void theTotalIsExactlyTheSumOfItsParts() {
        GameState s = Fix.game();
        for (int seat = 0; seat < s.numPlayers(); seat++) {
            Map<String, Integer> breakdown = Scoring.scorePlayer(s, seat);
            int sum = 0;
            for (Map.Entry<String, Integer> e : breakdown.entrySet()) {
                if (!"total".equals(e.getKey())) {
                    sum += e.getValue();
                }
            }
            assertEquals(sum, breakdown.get("total"),
                "место " + seat + ": итог не сходится с разбивкой " + breakdown);
        }
    }

    /**
     * Келемий даёт очки по курсу из правил, а не «сколько-нибудь». Курс 0 —
     * это не «даром», а ВЫКЛЮЧЕНО: с правил 1.7.1-debris-vp келемий в
     * хранилище победных очков не приносит вовсе.
     */
    @Test
    void keliumConvertsAtTheRateFromTheRules() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int per = ((Number) ((kelium.dataio.GameConfig) s.config).ruleset
            .economy().get("kelium_per_vp")).intValue();

        p.resources.setKelium(0);
        assertEquals(0, vp(s, 0, "kelium"), "без келемия очков за него нет");

        if (per == 0) {
            p.resources.setKelium(50);
            assertEquals(0, vp(s, 0, "kelium"),
                "курс 0 — источник выключен, сколько ни копи");
            return;
        }

        p.resources.setKelium(per - 1);
        assertEquals(0, vp(s, 0, "kelium"), "неполный курс очков не даёт");

        p.resources.setKelium(per * 3);
        assertEquals(3, vp(s, 0, "kelium"), "три полных курса — три очка");
    }

    /** Здания и войска на поле считаются по своим курсам. */
    @Test
    void buildingsAndUnitsOnTheFieldAreCounted() {
        GameState s = Fix.game();
        int seat = 0;
        String start = s.player(seat).startHex;
        int before = vp(s, seat, "buildings_on_field");
        String spot = Fix.freeNeighbour(s, start);
        Fix.building(s, seat, BuildingType.POWER_PLANT, spot, 1);
        assertTrue(vp(s, seat, "buildings_on_field") >= before,
            "построенное здание не должно уменьшать очки за здания");

        int unitsBefore = vp(s, seat, "units_on_field");
        Fix.unit(s, seat, UnitType.INFANTRY, spot);
        Fix.unit(s, seat, UnitType.INFANTRY, spot);
        assertTrue(vp(s, seat, "units_on_field") >= unitsBefore,
            "выставленные войска не должны уменьшать очки за войска");
    }

    /**
     * §12.1: за СВОЙ жетон разрушения, который так и не забрали, игрок получает
     * очки в конце партии. Потеря жетона эти очки убирает.
     */
    @Test
    void keepingYourOwnCommandCentreTokenScores() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        p.ownCuTokenAvailable = true;
        int withToken = vp(s, 0, "cu_tokens");
        p.ownCuTokenAvailable = false;
        int without = vp(s, 0, "cu_tokens");
        assertTrue(withToken > without,
            "неснесённое ЦУ обязано давать очки: " + withToken + " против " + without);
    }

    /** Захваченные жетоны разрушения ЦУ считаются по курсу правил. */
    @Test
    void capturedCommandCentreTokensScore() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int base = vp(s, 0, "cu_tokens");
        p.cuDestructionTokens = 2;
        int per = ((kelium.dataio.GameConfig) s.config).ruleset
            .getInt("command_center.destruction_token_vp");
        assertEquals(base + 2 * per, vp(s, 0, "cu_tokens"),
            "два захваченных жетона дают ровно два курса");
    }

    /**
     * ТАЙЛЫ ЗАРОЖДЕНИЯ: победное очко даёт ТОЛЬКО выработанный до конца (оборот)
     * БОЛЬШОЙ тайл. Правило дизайнера 12.08.2026; прежняя редакция давала очки
     * уже за исчерпание ЛИЦА, и этот тест сторожил именно её.
     */
    @Test
    void onlyExhaustedBigSpawnTileScoresAPoint() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int base = vp(s, 0, "spawn_tiles");

        // Лицо выработано — очков нет ни у большого, ни у малого.
        p.flippedStartTiles = 2;
        p.flippedNormalTiles = 2;
        assertEquals(base, vp(s, 0, "spawn_tiles"),
            "за исчерпание ЛИЦА победных очков не даётся");

        // Оборот МАЛОГО (стартового) — тоже без победных очков.
        p.claimedStartTiles = 2;
        assertEquals(base, vp(s, 0, "spawn_tiles"),
            "оборот малого зарождения победных очков не даёт");

        // Оборот БОЛЬШОГО — по очку за тайл.
        p.claimedNormalTiles = 2;
        assertEquals(base + 2, vp(s, 0, "spawn_tiles"),
            "оборот большого зарождения = 1 победное очко за тайл");
    }

    /** Золотые модули — по очку за штуку, ровно. */
    @Test
    void goldModulesScoreOnePointEach() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        int base = vp(s, 0, "gold_modules");
        p.goldModules = 3;
        assertEquals(base + 3, vp(s, 0, "gold_modules"));
    }

    /**
     * Обломки конвертируются РОВНО ОДНИМ курсом, остаток не считается.
     *
     * <p>ЛИБО-ЛИБО, НЕ ОБА СРАЗУ (баг-фикс 18.08.2026). Действующий свод задаёт
     * оба ключа: {@code trophy_per_vp} (целочисленный, устаревший запасной путь)
     * и {@code debris_storage_vp_per_unit} (дробный, действующий). Раньше они
     * СКЛАДЫВАЛИСЬ — обломок стоил 1/3 + 1.0 = 1.333 ПО вместо одного курса, что
     * и обнаружил дизайнер по замеру партий («как будто 1.33 ПО за 1 обломок»).
     * Тест читает АКТИВНЫЙ курс тем же способом, что и {@link Scoring}, и
     * проверяет остаток на нём — а не на устаревшем trophy_per_vp, который при
     * наличии дробного ключа движком уже не читается.
     */
    @Test
    void debrisConvertsAtTheRate() {
        GameState s = Fix.game();
        PlayerState p = s.player(0);
        var econ = ((kelium.dataio.GameConfig) s.config).ruleset.economy();
        assertTrue(econ.containsKey("debris_storage_vp_per_unit"),
            "тест проверяет действующий курс — в своде должен быть дробный ключ");
        double rate = ((Number) econ.get("debris_storage_vp_per_unit")).doubleValue();
        // 5 кубиков по ставке 0.5 = 2.5 -> пол 2, остаток не считается.
        p.resources.add(Resource.DEBRIS, 5);
        assertEquals((int) Math.floor(5 * rate), vp(s, 0, "debris"),
            "остаток сверх полного курса очков не даёт");
    }

    /**
     * ЕДИНЫЙ КУРС ЗАМЕНЯЕТ, А НЕ СКЛАДЫВАЕТСЯ С УСТАРЕВШИМ.
     *
     * <p>Свод "1.7.1-debris-vp" держит ОБА ключа сразу (trophy_per_vp: 3,
     * debris_storage_vp_per_unit: 1.0) — именно на такой записи и жил баг
     * двойного учёта. До баг-фикса 18.08.2026 три обломка стоили 1 (от
     * trophy_per_vp) + 3 (от debris_storage_vp_per_unit) = 4 очка в отдельной
     * строке "debris_storage_vp" ПОВЕРХ строки "debris". Теперь строка одна:
     * "debris", посчитанная ТОЛЬКО по дробному курсу, — три обломка стоят
     * ровно 3 очка, а второй строки в разбивке больше нет вовсе.
     */
    @Test
    void debrisVpVariantZeroesKeliumAndUsesOnlyFractionalRate() {
        GameState s = Setup.buildGame(
            kelium.dataio.GameConfig.buildCached("1.7.1-debris-vp", 4, 1L, null, null));
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents);
        PlayerState p = s.player(0);

        p.resources.setKelium(50);
        assertEquals(0, vp(s, 0, "kelium"), "вариант: келемий в хранилище очков не даёт");

        p.resources.add(Resource.DEBRIS, 3);
        Map<String, Integer> breakdown = Scoring.scorePlayer(s, 0);
        assertEquals(3, breakdown.getOrDefault("debris", 0),
            "3 обломка по дробному курсу 1.0 — ровно 3 очка, без двойного учёта");
        assertFalse(breakdown.containsKey("debris_storage_vp"),
            "отдельной добавочной строки поверх \"debris\" быть не должно — "
                + "это и была двойная учётка");

        int totalBefore = breakdown.get("total");
        p.resources.add(Resource.DEBRIS, 1);   // теперь 4 обломка
        int totalAfter4 = Scoring.scorePlayer(s, 0).get("total");
        assertEquals(totalBefore + 1, totalAfter4,
            "четвёртый обломок при ставке 1.0 добавляет ровно одно очко");
    }
}
