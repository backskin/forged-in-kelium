package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.Ability;
import kelium.engine.ability.Hook;
import kelium.engine.ability.OptionSource;
import kelium.engine.ability.RuleQuery;

/**
 * КАРТЫ АРСЕНАЛА 2.0.0 — доказательство, что новые пассивки РАБОТАЮТ, а не просто
 * числятся в реестре.
 *
 * <p>Мало зарегистрировать способность: движок должен спрашивать её точку правил
 * в нужном месте. Именно на этом 13.08.2026 погорели шесть пассивок из 29 —
 * «реализованы» и молчат. Здесь каждая новая механика проверяется по эффекту:
 * поставили карту — поведение движка изменилось, сняли — вернулось.
 */
class Arsenal2CardsTest {

    /**
     * Партия НА КОЛОДЕ 2.0.0. Обязательно через {@code pickContentVersion} +
     * {@code buildCached}: свод 1.7.0 назначает арсенал 1.3.0, где карт b04/b08/
     * bs1 нет вовсе, — {@code Abilities.activeFor} не находит карту по id и
     * способность молчит. На этом тест и упал в первый раз.
     */
    private static GameState game() {
        GameConfig.pickContentVersion("arsenal", "2.0.0");
        try {
            return Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 42L, null, null));
        } finally {
            GameConfig.pickContentVersion("arsenal", null);
        }
    }

    /** Поставить карту игроку в установленные (как будто он её открыл). */
    private static void install(GameState s, int seat, String cardId) {
        s.player(seat).arsenalInstalled.add(cardId);
    }

    /** Все карты 2.0.0, кроме одной отложенной, реально зарегистрированы. */
    @Test
    void everyArsenal200PassiveIsRegisteredExceptTheDeferredOne() {
        List<String> expected = List.of(
            "speed_plus1_ground", "speed_plus1_air_tower", "vehicle_fast_fragile",
            "ammo_cell_and_spec_assembly", "kelium_cell_and_spec_mining",
            "spec_heal_one_damage", "spec_move_energy_cube", "kelium_ignores_block",
            "bottom_order_two_actions", "coin_per_energy_cube_on_refresh",
            "kelium_instead_of_trophy", "air_gives_range_two", "air_shields_own_hex",
            "airbase_needs_one_less_energy", "card_is_energy_source_upkeep",
            "ammo_on_being_retaliated", "spec_loot_enemy_building_hex",
            "spec_draw_two_objectives_keep_one", "coin_on_kill",
            "buildings_plus1_hp_until_round_end", "extra_trophy_on_spawn_flip",
            "build_on_adjacent_without_wall");
        for (String id : expected) {
            assertTrue(Abilities.byId(id) != null,
                "способность не зарегистрирована: " + id);
        }
    }

    /**
     * «Вольная застройка» РАСШИРЯЕТ зону стройки: без карты строить можно только
     * туда, куда здание смотрит стенкой, с картой — на любой соседний гекс.
     */
    @Test
    void buildOnAdjacentWithoutWallWidensTheBuildZone() {
        GameState s = game();
        // МЕСТО 1, А НЕ 0, И ЭТО ВАЖНО. У места 0 на сиде 42 стартовый гекс h3_1
        // окружён так, что карте нечего добавить: две стороны за краем поля, одна
        // — тайл зарождения, ещё две заняты нейтралами, и остаётся ровно тот гекс,
        // куда ЦУ и так смотрит стенкой. Первая версия теста падала именно на
        // этом и обвиняла код вместо раскладки.
        int seat = 1;
        int before = Actions.buildableHexes(s, seat).size();
        install(s, seat, "bs8");   // «Вольная застройка»
        assertEquals(1, RuleQuery.of(s, seat, Hook.BUILD_ZONE).base(0).ask(),
            "способность не активна — карта не найдена в наборе");
        int after = Actions.buildableHexes(s, seat).size();
        assertTrue(after > before,
            "зона стройки не выросла: было " + before + ", стало " + after);
    }

    /** «Полевой генератор» добавляет источник энергии — точка ENERGY_SOURCES жива. */
    @Test
    void fieldGeneratorAddsAnEnergySource() {
        GameState s = game();
        assertEquals(0, RuleQuery.of(s, 0, Hook.ENERGY_SOURCES).base(0).ask(),
            "без карты источников от арсенала быть не должно");
        install(s, 0, "bs1");   // «Полевой генератор»
        assertEquals(1, RuleQuery.of(s, 0, Hook.ENERGY_SOURCES).base(0).ask(),
            "карта обязана давать 1 кубик энергии");
    }

    /** «Двойной протокол» удваивает действия открытого нижнего приказа. */
    @Test
    void doubleProtocolGivesTwoBottomActions() {
        GameState s = game();
        assertEquals(1, RuleQuery.of(s, 0, Hook.ORDER_BOTTOM_ACTIONS).base(1).ask());
        install(s, 0, "b09");   // «Двойной протокол»
        assertEquals(2, RuleQuery.of(s, 0, Hook.ORDER_BOTTOM_ACTIONS).base(1).ask(),
            "нижний приказ обязан давать два действия");
    }

    /** «Патронный ящик» и «Келемиевый бак» расширяют склад по СВОЕМУ ресурсу. */
    @Test
    void extraCellCardsWidenStorage() {
        GameState s = game();
        install(s, 0, "b04");   // «Патронный ящик» → +1 ячейка боеприпаса
        assertEquals(1, RuleQuery.of(s, 0, Hook.STORAGE_CELLS)
            .about(Resource.AMMO).base(0).ask());
        GameState s2 = game();
        install(s2, 0, "b05");  // «Келемиевый бак» → +1 ячейка келемия
        assertEquals(1, RuleQuery.of(s2, 0, Hook.STORAGE_CELLS)
            .about(Resource.KELIUM).base(0).ask());
    }

    /**
     * «Резервный штаб»: СПЕЦ кладёт келемий НА КАРТУ (из запаса), и сжечь его
     * можно только когда приказ РЕАЛЬНО заблокирован — иначе карта сгорела бы зря.
     */
    @Test
    void reserveHqStoresKeliumAndSpendsItOnlyWhenOrderIsBlocked() {
        GameState s = game();
        install(s, 0, "b08");
        PlayerState p = s.player(0);
        p.resources.add(Resource.KELIUM, 1);
        int keliumBefore = p.resources.kelium();

        Ability a = Abilities.byId("kelium_ignores_block");
        OptionSource src = (OptionSource) a;

        // Блокировки нет: предлагается только «положить», но не «сжечь».
        s.journal.of(0).orderBlocked = false;
        List<kelium.core.Choice> opts = src.options(s, 0, OptionSource.Slot.SPEC);
        assertEquals(1, opts.size(), "без блокировки должен быть ровно один вариант");
        assertTrue(opts.get(0).kind().endsWith(":put"));

        // Кладём келемий на карту: из запаса ушёл, на карте появился.
        assertTrue(src.perform(s, 0, opts.get(0), null));
        assertEquals(keliumBefore - 1, p.resources.kelium(), "келемий не списан из запаса");
        assertEquals(1, p.arsenalCardKelium.getOrDefault("b08", 0), "келемий не лёг на карту");

        // Блокировки по-прежнему нет — жечь нечего.
        assertTrue(src.options(s, 0, OptionSource.Slot.SPEC).isEmpty(),
            "без блокировки сжигать келемий предлагать нельзя");

        // Приказ заблокирован — появляется «сжечь», и он даёт лишнее действие.
        s.journal.of(0).orderBlocked = true;
        List<kelium.core.Choice> blocked = src.options(s, 0, OptionSource.Slot.SPEC);
        assertEquals(1, blocked.size());
        assertTrue(blocked.get(0).kind().endsWith(":burn"));
        assertTrue(src.perform(s, 0, blocked.get(0), null));
        assertEquals(0, p.arsenalCardKelium.getOrDefault("b08", 0), "келемий не сожжён");
        assertEquals(1, s.journal.of(0).takeBlockBypassGrants(),
            "обход блокировки не выдал лишнего действия");
        assertEquals(0, s.journal.of(0).takeBlockBypassGrants(),
            "разрешение обязано забираться ровно один раз");
    }

    /**
     * «Аварийные щиты» поднимают прочность зданий на время раунда: точка TOKEN_HP
     * обязана отвечать больше печатного числа.
     */
    @Test
    void emergencyShieldsRaiseBuildingHp() {
        GameState s = game();
        PlayerState p = s.player(0);
        BuildingToken b = p.buildingsOnField().get(0);
        int base = RuleQuery.of(s, 0, Hook.TOKEN_HP).about(b).base(b.hp).ask();
        install(s, 0, "bs6");   // «Аварийные щиты»
        int withCard = RuleQuery.of(s, 0, Hook.TOKEN_HP).about(b).base(b.hp).ask();
        assertEquals(base + 1, withCard, "здание не стало крепче");
    }

    /**
     * Алиасы ведут себя как оригиналы: «Наводчик» обязан давать ту же дальность,
     * что и уже работавшая {@code attack_range2_with_aircraft}.
     */
    @Test
    void aliasBehavesExactlyLikeItsOriginal() {
        Ability alias = Abilities.byId("air_gives_range_two");
        Ability origin = Abilities.byId("attack_range2_with_aircraft");
        assertFalse(alias == null || origin == null, "алиас или оригинал не найдены");
        assertEquals(origin.hooks(), alias.hooks(), "алиас обязан слушать те же точки");
        assertEquals(origin.trigger(), alias.trigger());
    }
}
