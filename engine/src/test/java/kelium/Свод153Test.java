package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.dataio.GameConfig;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.support.Fix;

/**
 * СВОД 1.53.0 — БРОСОК КОНТЕЙНЕРОМ В БОЮ (решение дизайнера 23.10.2026).
 *
 * <p>«Бросок кубика без кубика»: перед атакой переворачивают ВЕРХНЮЮ карту
 * колоды контейнеров и читают уголок. +1 — атака на боеприпас дешевле, пусто —
 * ничего, −1 — дороже, а заплатить нечем — осечка. Одна карта на атаку, карта
 * уходит под низ колоды. Уголки треть на треть (набор контейнеров 7.0.0).
 *
 * <p>Чтобы исход не зависел от тасовки, тест кладёт наверх колоды известную
 * карту: A — «+1», B — «−1», H — «пусто».
 */
class Свод153Test {

    private static final String СВОД = "1.53.0";
    private static final String ПЛЮС = "k7_a";
    private static final String МИНУС = "k7_b";
    private static final String ПУСТО = "k7_h";

    @org.junit.jupiter.api.AfterAll
    static void вернутьКолодуПоУмолчанию() {
        kelium.engine.cards.CardRegistry.reset();
        Setup.buildGame(GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    @Test
    void игроваяВерсияИНаборКонтейнеров() {
        assertEquals(СВОД, GameConfig.GAME_RULESET, "игра открывается сводом 1.53.0");
        GameState s = Fix.game(СВОД, 4, 1L);
        assertTrue(Boolean.TRUE.equals(Ctx.rules(s).get("actions.combat.container_gamble", null)),
            "бросок контейнером включён");
        int карт = 0;
        Map<Integer, Integer> поУголку = new HashMap<>();
        for (Map<String, Object> e : Ctx.cards(s, "containers").entries) {
            int копий = ((Number) e.get("copies")).intValue();
            карт += копий;
            int уголок = ((Number) e.get("уголок_боя")).intValue();
            поУголку.merge(уголок, копий, Integer::sum);
        }
        assertEquals(36, карт, "36 карт, как на рендере 13.10");
        assertEquals(12, поУголку.getOrDefault(1, 0), "«+1» — треть колоды");
        assertEquals(12, поУголку.getOrDefault(-1, 0), "«−1» — треть колоды");
        assertEquals(12, поУголку.getOrDefault(0, 0), "«пусто» — треть колоды");
    }

    // ==================== бросок в бою ====================

    /** Стол: один пехотинец места 0 рядом с живучей техникой места 1. */
    private record Стол(GameState s, String цель, List<Map<String, Object>> события) {
    }

    private static Стол стол() {
        GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, 2, 42L, null, null));
        List<Map<String, Object>> события = new ArrayList<>();
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 2; seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        GameEngine.bind(s, agents, ev -> события.add(new HashMap<>(ev)));
        s.round = 1;
        s.circle = 1;
        String цель = null;
        String сосед = null;
        for (Hex h : s.field.hexes.values()) {
            if (!обычный(h) || занят(s, h.id)) {
                continue;
            }
            for (String nb : s.field.neighbors(h.id)) {
                if (обычный(s.field.get(nb)) && !занят(s, nb)) {
                    цель = h.id;
                    сосед = nb;
                    break;
                }
            }
            if (цель != null) {
                break;
            }
        }
        assertNotNull(цель, "на поле нашёлся гекс со свободным соседом");
        Fix.unit(s, 1, UnitType.VEHICLE, цель).hp = 9;   // живучая цель — не падает
        Fix.unit(s, 0, UnitType.INFANTRY, сосед);
        return new Стол(s, цель, события);
    }

    /** Кладёт карту на верх колоды контейнеров. */
    private static void наверх(GameState s, String карта) {
        s.decks.get("containers").drawPile.add(карта);
    }

    /** Бьёт по цели и всегда бросает; запоминает цену выбранной атаки. */
    private static final class Игрок extends Agent {
        private final Fix.AimingAgent прицел;
        Integer цена;
        boolean толькоСБроском;

        Игрок(String цель) {
            super(0, "игрок");
            this.прицел = new Fix.AimingAgent(0, цель);
        }

        @Override
        public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
            if ("container_gamble".equals(ctx.get("kind"))) {
                for (Choice c : options) {
                    if (Boolean.TRUE.equals(c.payload())) {
                        return c;
                    }
                }
            }
            Choice c = прицел.choose(st, options, ctx);
            if ("attack".equals(c.kind()) && c.payload() instanceof Map<?, ?> m && цена == null) {
                цена = ((Number) m.get("ammo")).intValue();
                толькоСБроском = Boolean.TRUE.equals(m.get("gamble_only"));
            }
            return c;
        }
    }

    private static long попаданий(Стол т) {
        return т.события().stream().filter(e -> "combat_hit".equals(e.get("type"))).count();
    }

    private static long событий(Стол т, String тип) {
        return т.события().stream().filter(e -> тип.equals(e.get("type"))).count();
    }

    @Test
    void пустоНеМеняетЦену_картаУходитПодНиз() {
        Стол т = стол();
        PlayerState p = т.s().player(0);
        p.resources.setAmmo(0);
        келемия(p, 10);
        наверх(т.s(), ПУСТО);
        Игрок игрок = new Игрок(т.цель());
        ((CombatResolver) т.s().combat).runBattle(0, игрок);

        assertNotNull(игрок.цена, "атака выбрана");
        assertEquals(1, событий(т, "container_gamble"), "один бросок на одну атаку");
        assertTrue(попаданий(т) >= 1, "атака состоялась");
        assertEquals(10 - игрок.цена, p.resources.get(Resource.KELIUM),
            "«пусто» — цена прежняя (платил келемием: боеприпасов ноль)");
        assertEquals(ПУСТО, т.s().decks.get("containers").drawPile.get(0),
            "перевёрнутая карта ушла ПОД НИЗ колоды, а не в сброс");
    }

    @Test
    void минусБезЗапасаДаётОсечку() {
        // Цену атаки узнаём на таком же столе: сид и раскладка те же.
        Стол разведка = стол();
        разведка.s().player(0).resources.setAmmo(9);
        наверх(разведка.s(), ПУСТО);
        Игрок пробный = new Игрок(разведка.цель());
        ((CombatResolver) разведка.s().combat).runBattle(0, пробный);
        int цена = пробный.цена;

        Стол т = стол();
        PlayerState p = т.s().player(0);
        p.resources.setAmmo(цена);                          // ровно на цену
        келемия(p, 0);
        наверх(т.s(), МИНУС);
        ((CombatResolver) т.s().combat).runBattle(0, new Игрок(т.цель()));

        assertEquals(1, событий(т, "combat_misfire"), "«−1» поднял цену — осечка");
        assertEquals(0, попаданий(т), "атаки не было");
        assertEquals(цена, p.resources.ammo(), "за осечку ничего не списано");
    }

    @Test
    void минусЗакрываетсяКелемием() {
        Стол разведка = стол();
        разведка.s().player(0).resources.setAmmo(9);
        наверх(разведка.s(), ПУСТО);
        Игрок пробный = new Игрок(разведка.цель());
        ((CombatResolver) разведка.s().combat).runBattle(0, пробный);
        int цена = пробный.цена;

        Стол т = стол();
        PlayerState p = т.s().player(0);
        p.resources.setAmmo(цена);
        келемия(p, 3);
        наверх(т.s(), МИНУС);
        ((CombatResolver) т.s().combat).runBattle(0, new Игрок(т.цель()));

        assertEquals(0, событий(т, "combat_misfire"), "минус закрыт келемием — осечки нет");
        assertTrue(попаданий(т) >= 1, "атака состоялась");
        assertEquals(0, p.resources.ammo(), "сперва ушли все боеприпасы");
        assertEquals(2, p.resources.get(Resource.KELIUM), "лишний боеприпас — келемием");
    }

    @Test
    void безОдногоБоеприпасаАтакаТолькоСБроском() {
        Стол разведка = стол();
        разведка.s().player(0).resources.setAmmo(9);
        наверх(разведка.s(), ПУСТО);
        Игрок пробный = new Игрок(разведка.цель());
        ((CombatResolver) разведка.s().combat).runBattle(0, пробный);
        int цена = пробный.цена;

        Стол т = стол();
        PlayerState p = т.s().player(0);
        p.resources.setAmmo(цена - 1);                      // одного не хватает
        келемия(p, 0);
        наверх(т.s(), ПЛЮС);
        Игрок игрок = new Игрок(т.цель());
        ((CombatResolver) т.s().combat).runBattle(0, игрок);

        assertTrue(игрок.толькоСБроском, "атака предложена только с броском");
        assertEquals(1, событий(т, "container_gamble"), "бросок сделан без вопроса");
        assertTrue(попаданий(т) >= 1, "«+1» оплатил выстрел");
        assertEquals(0, p.resources.ammo(), "ушли все боеприпасы — на цену минус один");
        assertFalse(событий(т, "combat_misfire") > 0, "осечки нет");
    }

    /** Ровно столько келемия у игрока — без отрицательных добавок. */
    private static void келемия(PlayerState p, int сколько) {
        int есть = p.resources.get(Resource.KELIUM);
        if (есть > сколько) {
            p.resources.pay(Resource.KELIUM, есть - сколько);
        } else if (есть < сколько) {
            p.resources.add(Resource.KELIUM, сколько - есть);
        }
    }

    private static boolean обычный(Hex h) {
        return h != null && h.kind == HexKind.NORMAL && h.spawnTile == null && !h.hasNeutral();
    }

    private static boolean занят(GameState s, String hex) {
        for (var pl : s.players) {
            for (var u : pl.units) {
                if (hex.equals(u.hexId)) {
                    return true;
                }
            }
            for (var b : pl.buildings) {
                if (hex.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
