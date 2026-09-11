package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.ModuleSets;
import kelium.engine.Setup;

/**
 * ТРИ РЕЖИМА СТАРТОВЫХ ЗАДАНИЙ и МЕШКИ ЖЕТОНОВ МОДУЛЕЙ
 * (решения дизайнера 12.08.2026).
 *
 * <p>Режимы: {@code super} — супер задания, {@code starters} — НАЧАЛЬНЫЕ задания,
 * {@code none} — без стартовых заданий. Раздача во всех одинаковая: две карты,
 * одну оставляешь. Начальные задания в любом режиме изъяты из общей колоды.
 *
 * <p>Мешки: награда «модуль» тянет СЛУЧАЙНЫЙ жетон, который из мешка извлекается.
 */
class StartModesAndBagsTest {

    /** Свод, на котором стоят эти сторожа: действующий. */
    private static final String СВОД = "1.41.0";

    /**
     * Партия с заданными тумблерами дополнений.
     *
     * <p>Дополнения — ТРИ НЕЗАВИСИМЫХ ТУМБЛЕРА (17.08.2026): супер задания и
     * начальные задания друг друга не исключают, поэтому сцена задаёт их
     * напрямую, а не выбором «режима». {@code mode} {@code null} — оставить как
     * в своде.
     */
    private static GameState game(String mode, int players, long seed) {
        GameConfig cfg = GameConfig.buildCached(СВОД, players, seed, null, null);
        if (mode != null) {
            cfg.ruleset.override("expansions.super_objectives", "super".equals(mode));
            cfg.ruleset.override("expansions.starting_objectives", "starters".equals(mode));
        }
        return Setup.buildGame(cfg);
    }

    @Test
    void noneModeDealsNothing() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 23L, null, null);
        cfg.ruleset.override("expansions.super_objectives", false);
        cfg.ruleset.override("expansions.starting_objectives", false);
        GameState s = Setup.buildGame(cfg);
        for (PlayerState p : s.players) {
            assertTrue(p.superObjectiveOffer.isEmpty(), "супер заданий нет");
            assertTrue(p.startObjectiveOffer.isEmpty(), "начальных заданий нет");
            assertTrue(p.superObjective == null, "супер задание не назначено");
        }
    }


    private static List<String> startersInDeck(GameState s) {
        List<String> found = new ArrayList<>();
        for (Map<String, Object> card : Ctx.cards(s, "objectives").entries) {
            if ("starting".equals(card.get("kind"))
                    && s.decks.get("objectives").drawPile.contains(String.valueOf(card.get("id")))) {
                found.add(String.valueOf(card.get("id")));
            }
        }
        return found;
    }

    // ==================== мешки модулей ====================

    /**
     * ФИКСИРОВАННЫЙ МЕШОК ОДИНАКОВЫЙ ПРИ ЛЮБОМ ЧИСЛЕ ИГРОКОВ (решение
     * дизайнера 20.08.2026, свод 1.19.0). Раньше мешок масштабировался по
     * числу игроков (личный комплект x N) — наследие модели, где комплект
     * раздавался каждому. Мешок общий, делить его не на кого, поэтому R30/C30
     * (все шесть пар целей по два жетона / все четыре вида выхода Сборки по
     * три) кладутся в мешок РОВНО так, как записаны в данных — 12 и 12, для
     * 2, 3 и 4 игроков одинаково.
     */
    @Test
    void fixedBagIsTheSameRegardlessOfPlayerCount() {
        for (int players = 2; players <= 4; players++) {
            GameState s = game(null, players, 30L + players);
            assertEquals(12, s.redBag.size(),
                "фиксированный красный набор — 12 жетонов при любом числе игроков");
            assertEquals(12, s.blueBag.size(),
                "фиксированный синий набор — 12 жетонов при любом числе игроков");
        }
    }

    @Test
    void drawnTokenLeavesTheBagForGood() {
        GameState s = game(null, 4, 31L);
        int before = s.redBag.size();
        Map<String, Integer> counts = new HashMap<>();
        for (String id : s.redBag) {
            counts.merge(id, 1, Integer::sum);
        }
        String drawn = ModuleSets.draw(s.redBag, s.rng);
        assertTrue(drawn != null, "из непустого мешка жетон тянется");
        assertEquals(before - 1, s.redBag.size(), "вытянутый жетон из мешка извлечён");
        int now = 0;
        for (String id : s.redBag) {
            if (id.equals(drawn)) {
                now++;
            }
        }
        assertEquals(counts.get(drawn) - 1, now, "именно этот жетон убыл, а не любой");
    }

    @Test
    void awardGivesConcreteTokenAndEmptyBagGivesNothing() {
        GameState s = game(null, 4, 32L);
        PlayerState p = s.player(0);
        String id = kelium.engine.Modules.awardModule(s, p, "red");
        assertTrue(id != null, "награда «модуль» выдаёт КОНКРЕТНЫЙ жетон из мешка");
        assertEquals(List.of(id), p.redTokens, "жетон лёг игроку");
        assertEquals(1, p.redModules, "счётчик модулей тоже вырос");

        s.redBag.clear();
        int wasModules = p.redModules;
        assertTrue(kelium.engine.Modules.awardModule(s, p, "red") == null,
            "из пустого мешка ничего не выдаётся");
        assertEquals(wasModules, p.redModules,
            "и счётчик не растёт: наград-модулей больше нет");
    }

    @Test
    void tokensComeFromTheSetNamedInTheRuleset() {
        GameState s = game(null, 4, 33L);
        String bag = String.valueOf(Ctx.rules(s).get("modules.red_bag", "bag_R1"));
        ModuleSets.Library lib = ModuleSets.of(s);
        Set<String> allowed = new HashSet<>();
        for (String setId : lib.redBags().getOrDefault(bag, List.of())) {
            var set = lib.redSets().get(setId);
            if (set != null) {
                for (var t : set.tokens()) {
                    allowed.add(t.id());
                }
            }
        }
        assertFalse(allowed.isEmpty(), "мешок " + bag + " ссылается на реальный набор");
        for (String id : s.redBag) {
            assertTrue(allowed.contains(id),
                "в мешке только жетоны своего набора, а найден " + id);
        }
    }
}
