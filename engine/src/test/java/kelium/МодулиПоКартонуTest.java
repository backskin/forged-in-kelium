package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.CombatResolver;
import kelium.engine.Modules;
import kelium.support.Fix;

/**
 * МОДУЛИ — КАК НАПЕЧАТАНО НА ЖЕТОНАХ (правка движка по требованию дизайнера
 * 14.09.2026).
 *
 * <p>Три правила, которые движок нарушал:
 * <ul>
 *   <li>полученный жетон кладётся на СВОБОДНУЮ ячейку, лежащие не трогаются —
 *       прежде движок раскладывал все модули заново при каждом получении;</li>
 *   <li>позолота — свойство КОНКРЕТНОГО жетона: игрок выбирает, какой
 *       перевернуть, и золото переезжает вместе с ним — прежде золото было
 *       общим счётчиком и «прилипало» к первым разложенным;</li>
 *   <li>золотой красный модуль — ОДНА атака за 1 боеприпас, по 1 урону жетону
 *       каждого из двух типов — прежде это были две атаки с отдельной платой.</li>
 * </ul>
 */
class МодулиПоКартонуTest {

    private static Map<String, Object> синий(String id, boolean gold) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("id", id);
        pl.put("ammo", 2);
        pl.put("units", 1);
        pl.put("gild", "units");
        pl.put("gold", gold);
        return pl;
    }

    /** Агент: на названной точке решения берёт вариант с этой начинкой, иначе первый не-пас. */
    private static final class Хочу extends Agent {
        private final String kind;
        private final Object payload;
        private final String kind2;
        private final Object payload2;

        Хочу(String kind, Object payload, String kind2, Object payload2) {
            super(0, "тест");
            this.kind = kind;
            this.payload = payload;
            this.kind2 = kind2;
            this.payload2 = payload2;
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            Object k = ctx.get("kind");
            for (Choice c : options) {
                if (kind.equals(k) && payload != null && payload.equals(c.payload())) {
                    return c;
                }
                if (kind2 != null && kind2.equals(k) && c.payload() instanceof Map<?, ?> m
                        && payload2.equals(m.get("building"))) {
                    return c;
                }
            }
            for (Choice c : options) {
                if (!"pass".equals(c.kind()) && c.payload() != null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    @Test
    void новыйЖетонЛожитсяНаСвободнуюЯчейкуИНеТрогаетЛежащие() {
        GameState s = Fix.game(2, 42L);
        PlayerState p = s.player(0);
        p.blueTokens.add("X");
        p.bluePlacements.put(BuildingType.BARRACKS, синий("X", true));
        p.goldModules = 1;

        String drew = Modules.awardModule(s, p, "blue");
        assertNotNull(drew, "мешок полный — жетон обязан вытянуться");

        Map<String, Object> старый = p.bluePlacements.get(BuildingType.BARRACKS);
        assertNotNull(старый, "лежавший жетон остался на казарме");
        assertEquals("X", старый.get("id"));
        assertTrue(Boolean.TRUE.equals(старый.get("gold")), "золото лежавшего не тронуто");
        assertEquals(2, p.bluePlacements.size(), "новый жетон лёг на вторую ячейку");
        boolean новыйЛежит = p.bluePlacements.values().stream()
            .anyMatch(pl -> drew.equals(pl.get("id")) && !Boolean.TRUE.equals(pl.get("gold")));
        assertTrue(новыйЛежит, "вытянутый жетон лежит обычной стороной");
        assertEquals(1, p.goldModules, "золотых по-прежнему один");
    }

    @Test
    void безСвободнойЯчейкиНовыйЖетонЗаменяетЛежащийИНаследуетЗолото() {
        GameState s = Fix.game(2, 42L);
        PlayerState p = s.player(0);
        for (BuildingType b : Modules.MIL_BUILDINGS) {
            p.blueTokens.add("Z" + b.code);
            p.bluePlacements.put(b, синий("Z" + b.code, b == BuildingType.FACTORY));
        }
        p.goldModules = 1;
        // агент заменяет золотой жетон на заводе
        s.agents.set(0, new Хочу("module_replace_blue", null, "module_replace_blue",
            BuildingType.FACTORY));
        String drew = Modules.awardModule(s, p, "blue");
        assertNotNull(drew);
        assertEquals(Modules.MIL_BUILDINGS.length, p.bluePlacements.size(),
            "ячеек столько же: новый лёг строго на место снятого");
        Map<String, Object> наЗаводе = p.bluePlacements.get(BuildingType.FACTORY);
        assertEquals(drew, наЗаводе.get("id"), "новый жетон лежит на месте снятого");
        assertTrue(Boolean.TRUE.equals(наЗаводе.get("gold")),
            "снят золотой — новый сразу золотой стороной");
        assertFalse(p.blueTokens.contains("Z" + BuildingType.FACTORY.code),
            "снятый жетон ушёл из игры");
        assertEquals(1, p.goldModules);
    }

    @Test
    void обменМестамиДвухМодулейОднаСменаМодуля() {
        GameState s = Fix.game(2, 42L);
        PlayerState p = s.player(0);
        p.blueTokens.add("X");
        p.blueTokens.add("Y");
        p.bluePlacements.put(BuildingType.BARRACKS, синий("X", true));
        p.bluePlacements.put(BuildingType.FACTORY, синий("Y", false));

        Modules.moveOneModule(s, 0, new Хочу("module_move_pick", BuildingType.BARRACKS,
            "module_place_blue", BuildingType.FACTORY));
        assertEquals("Y", p.bluePlacements.get(BuildingType.BARRACKS).get("id"));
        assertEquals("X", p.bluePlacements.get(BuildingType.FACTORY).get("id"));
        assertTrue(Boolean.TRUE.equals(p.bluePlacements.get(BuildingType.FACTORY).get("gold")),
            "золото уехало вместе с X");
        assertEquals(2, p.bluePlacements.size());
    }

    @Test
    void позолотаПеревёртываетВыбранныйЖетонИЕдетСНимПриПереносе() {
        GameState s = Fix.game(2, 42L);
        PlayerState p = s.player(0);
        p.blueTokens.add("X");
        p.blueTokens.add("Y");
        p.bluePlacements.put(BuildingType.BARRACKS, синий("X", false));
        p.bluePlacements.put(BuildingType.FACTORY, синий("Y", false));

        assertTrue(Modules.canGild(p));
        assertTrue(Modules.gildOne(s, p,
            new Хочу("module_gild_pick", BuildingType.FACTORY, null, null)));
        assertTrue(Boolean.TRUE.equals(p.bluePlacements.get(BuildingType.FACTORY).get("gold")),
            "золотым стал именно выбранный жетон");
        assertFalse(Boolean.TRUE.equals(p.bluePlacements.get(BuildingType.BARRACKS).get("gold")));
        assertEquals(1, p.goldModules);

        Modules.moveOneModule(s, 0, new Хочу("module_move_pick", BuildingType.FACTORY,
            "module_place_blue", BuildingType.AIRBASE));
        assertFalse(p.bluePlacements.containsKey(BuildingType.FACTORY), "с завода снят");
        Map<String, Object> наАвиабазе = p.bluePlacements.get(BuildingType.AIRBASE);
        assertNotNull(наАвиабазе, "лёг на авиабазу");
        assertEquals("Y", наАвиабазе.get("id"));
        assertTrue(Boolean.TRUE.equals(наАвиабазе.get("gold")), "золото уехало вместе с жетоном");
        assertEquals(1, p.goldModules);
    }

    @Test
    void золотойКрасныйБьётПоОдномуЖетонуКаждогоТипаЗаОдинБоеприпас() {
        GameState s = Fix.game(2, 42L);
        String spot = Fix.freeNeighbour(s, s.player(0).startHex);
        assertNotNull(spot);
        Fix.unit(s, 0, UnitType.INFANTRY, s.player(0).startHex);
        UnitToken пехота = Fix.unit(s, 1, UnitType.INFANTRY, spot);
        UnitToken техника = Fix.unit(s, 1, UnitType.VEHICLE, spot);
        пехота.hp = 3;
        техника.hp = 3;

        Map<String, Object> mod = new HashMap<>();
        mod.put("id", "R30-1");
        mod.put("targets", new String[]{"infantry", "vehicle"});
        mod.put("ammo", 1);
        mod.put("gold", true);
        s.player(0).redPlacements.put(UnitType.INFANTRY, mod);

        s.player(0).resources.setAmmo(1);   // хватает ровно на одну атаку за 1 боеприпас
        s.player(1).resources.setAmmo(0);   // ответного огня не будет

        ((CombatResolver) s.combat).runBattle(0, new Fix.AimingAgent(0, spot));

        assertEquals(1, пехота.damage, "пехота получила 1 урон");
        assertEquals(1, техника.damage, "техника получила 1 урон той же атакой");
        assertEquals(0, s.player(0).resources.ammo(), "заплачен один боеприпас");
    }
}
