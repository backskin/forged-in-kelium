package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.cards.objectives.Утиль;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.TurnJournal;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Modules;
import kelium.engine.Power;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.Storage;
import kelium.engine.ЖетоныХранилища;
import kelium.engine.cards.EngineCardContext;

/**
 * ЖЕТОНЫ ХРАНИЛИЩА 2.0 И УТИЛЬ «∞ +2 СПЕЦ-ДЕЙСТВИЯ» (решения Влада 02.10.2026,
 * свод 1.49.0).
 */
class ЖетоныХранилищаTest {

    /**
     * Свод 1.49.0 привязывает свои карты к общему реестру; другие тесты модуля
     * ждут в нём колоду свода по умолчанию — возвращаем её.
     */
    @org.junit.jupiter.api.AfterAll
    static void вернутьКолодуПоУмолчанию() {
        kelium.engine.cards.CardRegistry.reset();
        kelium.engine.Setup.buildGame(kelium.dataio.GameConfig.buildCached(
            kelium.dataio.GameConfig.DEFAULT_RULESET, 4, 1L, null, null));
    }

    private static GameState партия(String свод) {
        return Setup.buildGame(GameConfig.buildCached(свод, 2, 7L, null, null));
    }

    /** Агент, который золотит жетон хранилища, если его предлагают. */
    private static final class ЗолотитХранилище extends Agent {
        ЗолотитХранилище() {
            super(0, "тест");
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            for (Choice c : options) {
                if ("gild_storage".equals(c.kind())) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    private static BuildingToken цу(PlayerState p) {
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b;
            }
        }
        return null;
    }

    @Test
    void жетонЭнергииДаётКубикЦуСразуАЗолотойДва() {
        GameState s = партия("1.49.0");
        PlayerState p = s.player(0);
        BuildingToken цу = цу(p);
        assertNotNull(цу, "ЦУ стоит на поле с подготовки");
        int выработка = Power.sourceCubes(s, цу);
        int свободно = цу.energyIdle;

        ЖетоныХранилища.положить(s, p, ЖетоныХранилища.ЭНЕРГИЯ);
        assertEquals(выработка + 1, Power.sourceCubes(s, цу));
        assertEquals(свободно + 1, цу.energyIdle, "кубик лёг на ЦУ сразу");

        assertTrue(Modules.gildOne(s, p, new ЗолотитХранилище()));
        assertTrue(ЖетоныХранилища.золотой(p.storageTokens.get(0)));
        assertEquals(выработка + 2, Power.sourceCubes(s, цу), "золотая сторона — +2 энергии ЦУ");
        assertEquals(свободно + 2, цу.energyIdle);
        assertEquals(1, p.goldModules, "золотой жетон хранилища — звезда");
    }

    @Test
    void золотаяЯчейкаДаётЯчейкуЗвездуИСпецДействие() {
        GameState s = партия("1.49.0");
        PlayerState p = s.player(0);
        int ячеек = Storage.totalMax(s, p);

        ЖетоныХранилища.положить(s, p, ЖетоныХранилища.ЯЧЕЙКА);
        assertEquals(ячеек + 1, Storage.totalMax(s, p));
        assertEquals(0, ЖетоныХранилища.спецДействий(p), "обычная сторона спец-действия не даёт");
        assertFalse(Scoring.scorePlayer(s, 0).containsKey("storage_cell_stars"),
            "у обычной стороны ячейки звезды нет");

        assertTrue(Modules.gildOne(s, p, new ЗолотитХранилище()));
        assertEquals(ячеек + 1, Storage.totalMax(s, p), "ячейка осталась одна");
        assertEquals(1, ЖетоныХранилища.спецДействий(p));
        assertEquals(1, p.goldModules);
        assertEquals(1, Scoring.scorePlayer(s, 0).get("gold_modules"));
    }

    @Test
    void вПрежнемСводеЖетоныХранилищаНеЗолотятся() {
        GameState s = партия("1.48.0");
        PlayerState p = s.player(0);
        ЖетоныХранилища.положить(s, p, ЖетоныХранилища.ЯЧЕЙКА);
        assertFalse(Modules.canGild(s, p));
        assertFalse(Modules.gildOne(s, p, new ЗолотитХранилище()));
    }

    @Test
    void свободныйУтильДаётДваСпецДействия() {
        GameState s = партия("1.49.0");
        for (String id : List.of("z2_04", "z2_13")) {
            Map<String, Object> карта = kelium.dataio.Ctx.cards(s, "objectives").find(id);
            assertNotNull(карта, id);
            assertTrue(карта.get("top") instanceof Map<?, ?> верх
                && "spec_actions".equals(верх.get("effect")), id + ": верх «+2 спец-действия»");
            assertTrue(GameEngine.верхСвободный(s, id), id + ": верх свободный (∞)");
        }
        s.journal = new TurnJournal(2);
        s.journal.startTurn(0);
        assertTrue(Утиль.СПЕЦ_ДЕЙСТВИЯ.сыграть(new EngineCardContext(s, 0)));
        assertEquals(2, s.journal.of(0).specBonus);
    }
}
