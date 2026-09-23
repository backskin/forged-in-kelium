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
        // без модуля — печать планшета красных: казарма 2 БПР / 1 войско,
        // авиабаза 1 БПР / 2 войска (наём двух за раз).
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.BARRACKS, "unit"));
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.BARRACKS, "ammo"));
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.AIRBASE, "unit"));
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.AIRBASE, "ammo"));
        // АССОРТИМЕНТ 23.09.2026: C1 2БПР/2в (зол.войска), C2 2БПР/2в (зол.БПР),
        // C3 3БПР/1в (зол.войска), C4 1БПР/3в (зол.БПР).
        // C2 лицом: 2 БПР / 2 войска.
        Map<String, Object> face = new HashMap<>(Modules.BLUE_MODULES.get("C2"));
        face.put("gold", false);
        p.bluePlacements.put(BuildingType.BARRACKS, face);
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.BARRACKS, "unit"));
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.BARRACKS, "ammo"));
        // C2 золотом (стрелка на БПР): БПР 2->3, войска остаются 2.
        Map<String, Object> gold = new HashMap<>(Modules.BLUE_MODULES.get("C2"));
        gold.put("gold", true);
        p.bluePlacements.put(BuildingType.FACTORY, gold);
        assertEquals(3, Modules.assemblyOutput(p, BuildingType.FACTORY, "ammo"));
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.FACTORY, "unit"));
        // C3 золотом (стрелка на войсках): войска 1->2, БПР остаются 3.
        Map<String, Object> c3g = new HashMap<>(Modules.BLUE_MODULES.get("C3"));
        c3g.put("gold", true);
        p.bluePlacements.put(BuildingType.AIRBASE, c3g);
        assertEquals(2, Modules.assemblyOutput(p, BuildingType.AIRBASE, "unit"));
        assertEquals(3, Modules.assemblyOutput(p, BuildingType.AIRBASE, "ammo"));
        // C4 лицом: жетон накрывает печатное число — 1 БПР / 3 войска.
        Map<String, Object> c4 = new HashMap<>(Modules.BLUE_MODULES.get("C4"));
        c4.put("gold", false);
        p.bluePlacements.put(BuildingType.BARRACKS, c4);
        assertEquals(1, Modules.assemblyOutput(p, BuildingType.BARRACKS, "ammo"));
        assertEquals(3, Modules.assemblyOutput(p, BuildingType.BARRACKS, "unit"));
    }

    /**
     * ЖЕТОНЫ И ПЛАНШЕТЫ ЦВЕТОВ (22.09.2026): прочность и ячейки берутся с
     * жетонов своего цвета, скорость и цена — с планшета своего цвета.
     * Места по кругу: красный, зелёный, синий, жёлтый.
     */
    @Test
    void жетоныИПланшетыПоЦвету() {
        GameState s = Setup.buildGame(GameConfig.build(4, 5L));
        var t = s.tokenStats;
        // прочность войск
        assertEquals(2, t.unitHp(kelium.core.UnitType.INFANTRY, 0));
        assertEquals(2, t.unitHp(kelium.core.UnitType.AIRCRAFT, 2));
        assertEquals(1, t.unitHp(kelium.core.UnitType.TOWER, 3));
        // авиабаза: ячейки и прочность различаются по цвету
        assertEquals(1, t.buildingEnergySlots(BuildingType.AIRBASE, null, 0));
        assertEquals(2, t.buildingEnergySlots(BuildingType.AIRBASE, null, 1));
        assertEquals(3, t.buildingHp(BuildingType.AIRBASE, null, 2));
        assertEquals(1, t.buildingHp(BuildingType.AIRBASE, null, 3));
        // добытчики общие
        assertEquals(t.buildingHp(BuildingType.MINER, 3, 0), t.buildingHp(BuildingType.MINER, 3, 3));
        // жетон на поле получает прочность своего цвета
        assertEquals(1, t.makeUnit(kelium.core.UnitType.INFANTRY, 3, 9001).hp);
        // планшет: скорость и цена
        assertEquals(1, s.player(1).board.troop.speed(kelium.core.UnitType.INFANTRY));
        assertEquals(3, s.player(0).board.troop.buildingPrice("barracks"));
        assertEquals(1, s.player(2).board.troop.buildingPrice("factory"));
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
