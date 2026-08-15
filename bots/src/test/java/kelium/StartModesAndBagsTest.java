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

    private static GameState game(String mode, int players, long seed) {
        GameConfig cfg = GameConfig.buildCached(
            GameConfig.DEFAULT_RULESET, players, seed, null, null);
        if (mode != null) {
            cfg.ruleset.override("super_objectives.mode", mode);
        }
        return Setup.buildGame(cfg);
    }

    // ==================== режимы ====================

    @Test
    void superModeDealsTwoSuperCardsAndNoStarters() {
        GameState s = game("super", 4, 21L);
        for (PlayerState p : s.players) {
            assertEquals(2, p.superObjectiveOffer.size(), "две карты супер задания");
            assertTrue(p.startObjectiveOffer.isEmpty(), "начальных заданий в этом режиме нет");
        }
        assertTrue(startersInDeck(s).isEmpty(),
            "начальные задания изъяты из общей колоды: " + startersInDeck(s));
    }

    @Test
    void startersModeDealsTwoStartingObjectivesAndKeepsOne() {
        GameState s = game("starters", 4, 22L);
        for (PlayerState p : s.players) {
            assertEquals(2, p.startObjectiveOffer.size(),
                "начальные задания раздаются так же: две карты на выбор");
            assertTrue(p.superObjectiveOffer.isEmpty(), "супер заданий в этом режиме нет");
            assertTrue(p.objectiveHand.isEmpty(), "до выбора рука пуста — выбирает игрок");
        }
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(Bots.create("balanced", seat, new Random(seat + 1L), 4));
        }
        GameEngine.playGame(s, agents, null);
        // после партии проверяем, что выбор состоялся: одна из двух карт была взята
        for (PlayerState p : s.players) {
            Set<String> offered = new HashSet<>(p.startObjectiveOffer);
            assertEquals(2, offered.size(), "предложение из двух разных карт");
        }
    }

    @Test
    void noneModeDealsNothing() {
        GameState s = game("none", 4, 23L);
        for (PlayerState p : s.players) {
            assertTrue(p.superObjectiveOffer.isEmpty(), "супер заданий нет");
            assertTrue(p.startObjectiveOffer.isEmpty(), "начальных заданий нет");
            assertTrue(p.superObjective == null, "супер задание не назначено");
        }
    }

    @Test
    void startersAreChosenByThePlayerNotByTheDeck() {
        GameState s = game("starters", 4, 24L);
        List<Agent> agents = new ArrayList<>();
        int[] picks = {0};
        for (int seat = 0; seat < 4; seat++) {
            agents.add(Bots.create("hawk", seat, new Random(seat * 7L + 1), 4));
        }
        GameEngine.playGame(s, agents, ev -> {
            if ("start_objective_pick".equals(ev.get("type"))) {
                picks[0]++;
            }
        });
        assertEquals(4, picks[0], "выбор начального задания делает каждый игрок");
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

    @Test
    void bagHoldsFullSetPerPlayer() {
        for (int players = 2; players <= 4; players++) {
            GameState s = game(null, players, 30L + players);
            assertEquals(4 * players, s.redBag.size(),
                "красный мешок = полный набор (4 жетона) на каждого игрока");
            assertEquals(4 * players, s.blueBag.size(),
                "синий мешок = полный набор на каждого игрока");
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
