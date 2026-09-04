package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Effects;
import kelium.engine.Modules;
import kelium.engine.Setup;
import kelium.engine.Storage;

/** Тесты вспомогательных систем: модули, склад, эффекты. */
class SystemsTest {

    private GameState build() {
        GameConfig cfg = GameConfig.build(4, 5L);
        return Setup.buildGame(cfg);
    }

    @Test
    void blueModuleRaisesAssemblyOutput() {
        GameState s = build();
        PlayerState p = s.player(0);
        // без модуля оба выхода = печатная 1
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.BARRACKS, "unit"));
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.BARRACKS, "ammo"));
        // C3 лицом: 1 БП / 2 войска (стрелка на войсках)
        Map<String, Object> face = new HashMap<>(Modules.BLUE_MODULES.get("C3"));
        face.put("gold", false);
        p.bluePlacements.put(BuildingType.BARRACKS, face);
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.BARRACKS, "unit"));
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.BARRACKS, "ammo"));
        // C3 золотом: войска 2->3 (стрелка), БП без изменений
        Map<String, Object> gold = new HashMap<>(Modules.BLUE_MODULES.get("C3"));
        gold.put("gold", true);
        p.bluePlacements.put(BuildingType.FACTORY, gold);
        assertEquals(3, Modules.assemblyOutput(p, BuildingType.FACTORY, "unit"));
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.FACTORY, "ammo"));
        // C2 золотом: БП 2->3 (стрелка), войска остаются 1
        Map<String, Object> c2g = new HashMap<>(Modules.BLUE_MODULES.get("C2"));
        c2g.put("gold", true);
        p.bluePlacements.put(BuildingType.AIRBASE, c2g);
        assertEquals(3, Modules.assemblyOutput(p, BuildingType.AIRBASE, "ammo"));
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.AIRBASE, "unit"));
    }

    @Test
    void redModulePairsCoverEachTargetTwice() {
        // Каждая из 4 целей встречается ровно в двух модулях.
        Map<String, Integer> counts = new HashMap<>();
        for (var pair : Modules.RED_MODULES.values()) {
            counts.merge(pair[0].code, 1, Integer::sum);
            counts.merge(pair[1].code, 1, Integer::sum);
        }
        assertEquals(4, counts.size(), "все 4 цели присутствуют");
        for (int c : counts.values()) {
            assertEquals(2, c, "каждая цель встречается дважды");
        }
    }

    @Test
    void storageCapsKeliumToOpenCells() {
        GameState s = build();
        PlayerState p = s.player(0);
        // У игрока на старте только ЦУ + добытчик №1: ограниченный склад.
        int before = p.resources.kelium();
        int added = Storage.addKeliumCapped(p, 100);
        int after = p.resources.kelium();
        assertEquals(added, after - before, "добавлено ровно столько, сколько влезло");
        assertTrue(after < 100, "склад ограничивает келемий (18 невозможно)");
    }

    /**
     * Трофей (правило 2026-08-15) занимает ЛЮБУЮ ячейку склада, но делит
     * ОБЩИЙ бюджет с келемием/боеприпасом — kelium+ammo+trophy ≤ totalMax.
     */
    @Test
    void trophySharesTotalBudgetWithKeliumAndAmmo() {
        GameState s = build();
        PlayerState p = s.player(0);
        p.resources.setKelium(0);
        p.resources.setAmmo(0);
        p.resources.add(Resource.TROPHY, -p.resources.trophy());
        int total = Storage.totalMax(s, p);
        assertTrue(total > 0, "у свежего игрока есть хоть какая-то вместимость склада");

        int addedD = Storage.addTrophyCapped(s, p, total);
        assertEquals(total, addedD, "с пустым складом трофей занимает весь бюджет");
        int addedKAfter = Storage.addKeliumCapped(s, p, 5);
        assertEquals(0, addedKAfter,
            "склад полностью занят трофеями — келемию места не осталось");

        p.resources.add(Resource.TROPHY, -p.resources.trophy());
        Storage.addKeliumCapped(s, p, total);
        int room = Storage.trophyMax(s, p);
        assertEquals(0, room, "склад занят под завязку келемием — под трофей места нет");
    }

    @Test
    void gainEffectAddsCoinAndContainers() {
        GameState s = build();
        s.agents = new java.util.ArrayList<>();
        PlayerState p = s.player(0);
        int coin0 = p.resources.coin();
        int cont0 = p.containers;
        Map<String, Object> params = new HashMap<>();
        params.put("coin", 3);
        params.put("containers", 2);
        Map<String, Object> got = Effects.apply("gain", s, 0, params);
        assertEquals(coin0 + 3, p.resources.coin());
        assertEquals(cont0 + 2, p.containers);
        assertEquals(3, got.get("coin"));
    }

    /**
     * ПОЛОВИНОК МОДУЛЕЙ БОЛЬШЕ НЕТ (решение дизайнера 13.08.2026): всё, что раньше
     * давало половинку, тянет ЦЕЛЫЙ жетон модуля из мешка. Ключ данных
     * {@code module_half} оставлен читаемым, чтобы старые карты работали без правки.
     */
    @Test
    void moduleHalfNowGivesAWholeModule() {
        GameState s = build();
        PlayerState p = s.player(0);
        assertEquals(0, p.redModules);
        Map<String, Object> half = new HashMap<>();
        half.put("module_half", "attack");
        Effects.apply("gain", s, 0, half);
        assertEquals(1, p.redModules, "одна «половинка» теперь сразу даёт целый модуль");
        Effects.apply("gain", s, 0, half);
        assertEquals(2, p.redModules, "и вторая тоже");
    }

    @Test
    void payResourceGuards() {
        GameState s = build();
        PlayerState p = s.player(0);
        assertTrue(p.resources.canPay(Resource.COIN, 1));
        assertFalse(p.resources.canPay(Resource.COIN, 999));
    }
}
