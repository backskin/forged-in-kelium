package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.SpawnTile;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.Ctx;
import kelium.engine.Scoring;
import kelium.engine.СуперЗадания;
import kelium.support.Fix;

/**
 * СУПЕР-ЗАДАНИЯ 9.0.0: каждая категория лёгкого («по ★») и трудного («по ★★»)
 * раздела считает ровно то, что напечатано под плашкой, и карта платит 1 ПО за
 * единицу верхнего раздела и 2 ПО за единицу нижнего.
 *
 * <p>Сцена собирается на пустом поле: со стола снято всё, что положила
 * подготовка, поэтому каждое число в проверке — от жетонов, поставленных
 * тестом.
 */
class СуперЗадания9Test {

    private GameState s;
    private PlayerState я;

    @BeforeEach
    void сцена() {
        s = Fix.game(2, 42L);
        for (PlayerState p : s.players) {
            p.buildings.forEach(b -> b.hexId = null);
            p.units.forEach(u -> u.setHexId(null));
            p.resources.setAmmo(0);
            p.resources.setKelium(0);
            p.resources.pay(Resource.TROPHY, p.resources.trophy());
            p.resources.pay(Resource.COIN, p.resources.coin());
            p.redPlacements.clear();
            p.bluePlacements.clear();
            p.arsenalInstalled.clear();
            p.mandateArsenalCard = null;
            p.superArsenalCards.clear();
            p.destroyedTokens.clear();
            p.goldModules = 0;
            p.superObjectives.clear();
        }
        for (Hex h : s.field.hexes.values()) {
            java.util.Arrays.fill(h.sideOwner, null);
        }
        for (List<List<Integer>> поСтупеням : s.tech.occupancy.values()) {
            поСтупеням.forEach(List::clear);
        }
        я = s.player(0);
    }

    private int единиц(String категория) {
        return СуперЗадания.очкиКатегории(s, я, категория);
    }

    /** Обычный гекс без тайла, запрета и нейтралов, не из списка занятых. */
    private String свободныйГекс(List<String> кроме) {
        for (Hex h : s.field.hexes.values()) {
            if (h.kind == HexKind.NORMAL && h.spawnTile == null && !h.hasNeutral()
                    && !кроме.contains(h.id)) {
                return h.id;
            }
        }
        throw new IllegalStateException("на поле нет свободного гекса");
    }

    // ------------------------------------------------------------ данные

    @Test
    void наборДевятьВСводеИКаждаяКартаЛёгкоеИТрудное() {
        var set = Ctx.cards(s, "super_objectives");
        assertEquals("9.0.0", set.version);
        assertEquals(12, set.entries.size(), "двенадцать карт");
        List<String> лёгкие = new ArrayList<>();
        List<String> трудные = new ArrayList<>();
        for (Map<String, Object> card : set.entries) {
            String id = String.valueOf(card.get("id"));
            assertEquals(List.of(1, 2), СуперЗадания.весаКарты(s, id),
                id + ": верхний раздел по ★, нижний по ★★");
            List<String> кат = СуперЗадания.категорииКарты(s, id);
            assertEquals(2, кат.size(), id);
            лёгкие.add(кат.get(0));
            трудные.add(кат.get(1));
        }
        assertEquals(12, лёгкие.stream().distinct().count(), "лёгкие не повторяются");
        assertEquals(12, трудные.stream().distinct().count(), "трудные не повторяются");
        for (String к : лёгкие) {
            assertTrue(!трудные.contains(к), к + " — и лёгкое, и трудное");
        }
    }

    @Test
    void картаПлатитОдинЗаВерхИДваЗаНиз() {
        // s9_02: за каждые 2 своих войска на поле / за каждое войско на гексе с чужим зданием
        String чужой = свободныйГекс(List.of());
        String свой = свободныйГекс(List.of(чужой));
        Fix.building(s, 1, BuildingType.BARRACKS, чужой, null);
        Fix.unit(s, 0, UnitType.INFANTRY, чужой);
        Fix.unit(s, 0, UnitType.INFANTRY, чужой);
        Fix.unit(s, 0, UnitType.INFANTRY, свой);
        // 3 войска: 1 единица верха; 2 войска у чужого здания: 2 единицы низа
        assertEquals(1 + 2 * 2, СуперЗадания.очкиКарты(s, я, "s9_02"));
        я.superObjectives.add("s9_02");
        assertEquals(5, Scoring.scorePlayer(s, 0).get("super_objectives"));
    }

    // ------------------------------------------------------------ лёгкие

    @Test
    void здания_поТри() {
        String h = свободныйГекс(List.of());
        for (int i = 1; i <= 4; i++) {
            Fix.building(s, 0, BuildingType.MINER, h, i);
        }
        Fix.building(s, 0, BuildingType.POWER_PLANT, свободныйГекс(List.of(h)), 1);
        assertEquals(1, единиц("buildings_3"), "5 зданий — одна тройка");
        Fix.building(s, 0, BuildingType.POWER_PLANT, свободныйГекс(List.of(h)), 2);
        assertEquals(2, единиц("buildings_3"));
    }

    @Test
    void войска_поДва() {
        String h = свободныйГекс(List.of());
        Fix.unit(s, 0, UnitType.INFANTRY, h);
        assertEquals(0, единиц("units_2"));
        Fix.unit(s, 0, UnitType.VEHICLE, h);
        Fix.unit(s, 0, UnitType.AIRCRAFT, h);
        assertEquals(1, единиц("units_2"));
        UnitToken вЗапасе = Fix.unit(s, 0, UnitType.TOWER, h);
        вЗапасе.setHexId(null);
        assertEquals(1, единиц("units_2"), "войско в запасе не считается");
    }

    @Test
    void родыВойск() {
        String h = свободныйГекс(List.of());
        Fix.unit(s, 0, UnitType.INFANTRY, h);
        Fix.unit(s, 0, UnitType.INFANTRY, h);
        Fix.unit(s, 0, UnitType.TOWER, h);
        assertEquals(2, единиц("unit_kinds"));
    }

    @Test
    void модулиБояИСборкиНаПланшете() {
        я.redPlacements.put(UnitType.INFANTRY, new HashMap<>());
        я.redPlacements.put(UnitType.VEHICLE, new HashMap<>(Map.of("blocks", true)));
        я.bluePlacements.put(BuildingType.BARRACKS, new HashMap<>());
        assertEquals(2, единиц("modules_on_board"), "модуль блокировки боя не в счёт");
    }

    @Test
    void кубикиНаТреках() {
        String t0 = s.tech.tracks.get(0);
        String t1 = s.tech.tracks.get(1);
        s.tech.placeCube(t0, 0, 1);
        s.tech.placeCube(t0, 0, 2);
        s.tech.placeCube(t1, 0, 1);
        s.tech.placeCube(t1, 1, 2);
        assertEquals(3, единиц("tech_cubes"));
    }

    @Test
    void кубикиВХранилище_поДва() {
        я.resources.add(Resource.KELIUM, 1);
        я.resources.add(Resource.AMMO, 2);
        я.resources.add(Resource.TROPHY, 2);
        я.resources.add(Resource.COIN, 9);
        assertEquals(2, единиц("storage_cubes_2"), "5 кубиков — две пары; монеты не кубики");
    }

    @Test
    void установленныеКартыАрсенала() {
        я.arsenalInstalled.add("x1");
        я.arsenalInstalled.add("x2");
        я.arsenalHand.add("x3");
        assertEquals(2, единиц("arsenal_installed"), "закрытая карта не в счёт");
    }

    @Test
    void монеты_поДве() {
        я.resources.add(Resource.COIN, 7);
        assertEquals(3, единиц("coins_2"));
    }

    @Test
    void запитанныеЗдания() {
        String h = свободныйГекс(List.of());
        BuildingToken казарма = Fix.building(s, 0, BuildingType.BARRACKS, h, null);
        BuildingToken завод = Fix.building(s, 0, BuildingType.FACTORY, h, null);
        Fix.building(s, 0, BuildingType.POWER_PLANT, свободныйГекс(List.of(h)), 1);
        Fix.power(казарма);
        assertEquals(1, единиц("powered_buildings"),
            "энергостанция без ячеек энергии запитанной не бывает");
        Fix.power(завод);
        assertEquals(2, единиц("powered_buildings"));
    }

    @Test
    void добытчикиУТайлаЗарождения_иДваДобытчикаУОдногоТайла() {
        // гекс с тайлом и два его соседа
        Hex тайл = null;
        List<String> соседи = new ArrayList<>();
        for (Hex h : s.field.hexes.values()) {
            if (h.kind != HexKind.NORMAL || h.hasNeutral()) {
                continue;
            }
            List<String> годные = new ArrayList<>();
            for (int side = 0; side < 6; side++) {
                String nb = h.neighborBySide[side];
                Hex n = nb == null ? null : s.field.get(nb);
                if (n != null && n.kind == HexKind.NORMAL && n.spawnTile == null
                        && !n.hasNeutral()) {
                    годные.add(nb);
                }
            }
            if (годные.size() >= 2) {
                тайл = h;
                соседи = годные.subList(0, 2);
                break;
            }
        }
        assertNotNull(тайл);
        тайл.spawnTile = new SpawnTile(false, 3, 3, 1);
        BuildingToken первый = добытчикСтенкойК(соседи.get(0), тайл.id, 1);
        assertEquals(1, единиц("miners_at_spawn"));
        assertEquals(0, единиц("spawn_double"), "один добытчик — тайл не взят вдвоём");
        добытчикСтенкойК(соседи.get(1), тайл.id, 2);
        assertEquals(2, единиц("miners_at_spawn"));
        assertEquals(1, единиц("spawn_double"));
        // стенка отвёрнута от тайла — добытчик не в счёт
        Hex где = s.field.get(первый.hexId);
        java.util.Arrays.fill(где.sideOwner, null);
        for (int side = 0; side < 6; side++) {
            if (!тайл.id.equals(где.neighborBySide[side])) {
                где.sideOwner[side] = первый.uid;
                break;
            }
        }
        assertEquals(1, единиц("miners_at_spawn"));
        assertEquals(0, единиц("spawn_double"));
    }

    /** Добытчик на гексе {@code где}, занимающий сторону, обращённую к {@code куда}. */
    private BuildingToken добытчикСтенкойК(String где, String куда, int номер) {
        BuildingToken b = Fix.building(s, 0, BuildingType.MINER, где, номер);
        Hex h = s.field.get(где);
        h.freeSidesByToken(b.uid);
        for (int side = 0; side < 6; side++) {
            if (куда.equals(h.neighborBySide[side])) {
                h.sideOwner[side] = b.uid;
            }
        }
        return b;
    }

    @Test
    void гексыСоСвоимиЖетонами_поДва() {
        String a = свободныйГекс(List.of());
        String b = свободныйГекс(List.of(a));
        String c = свободныйГекс(List.of(a, b));
        Fix.unit(s, 0, UnitType.INFANTRY, a);
        Fix.unit(s, 0, UnitType.INFANTRY, a);
        Fix.building(s, 0, BuildingType.MINER, b, 1);
        assertEquals(1, единиц("hexes_2"));
        Fix.unit(s, 0, UnitType.INFANTRY, c);
        assertEquals(1, единиц("hexes_2"), "три гекса — одна пара");
    }

    @Test
    void казармаЗаводАвиабаза_безЦУ() {
        String h = свободныйГекс(List.of());
        Fix.building(s, 0, BuildingType.COMMAND_CENTER, h, null);
        Fix.building(s, 0, BuildingType.BARRACKS, h, null);
        Fix.building(s, 0, BuildingType.AIRBASE, свободныйГекс(List.of(h)), null);
        assertEquals(2, единиц("military_3"));
    }

    // ------------------------------------------------------------ трудные

    @Test
    void своиЗданияНаГексеСЧужимЗданием() {
        String общий = свободныйГекс(List.of());
        Fix.building(s, 1, BuildingType.MINER, общий, 1);
        Fix.building(s, 0, BuildingType.POWER_PLANT, общий, 1);
        Fix.building(s, 0, BuildingType.MINER, свободныйГекс(List.of(общий)), 1);
        assertEquals(1, единиц("buildings_at_enemy"));
    }

    @Test
    void войскаНаГексеСЧужимЗданием() {
        String общий = свободныйГекс(List.of());
        Fix.building(s, 1, BuildingType.MINER, общий, 1);
        Fix.unit(s, 0, UnitType.VEHICLE, общий);
        Fix.unit(s, 1, UnitType.VEHICLE, свободныйГекс(List.of(общий)));
        assertEquals(1, единиц("units_at_enemy"));
    }

    @Test
    void полныеЧетвёркиВойск() {
        String h = свободныйГекс(List.of());
        for (UnitType t : UnitType.values()) {
            Fix.unit(s, 0, t, h);
        }
        Fix.unit(s, 0, UnitType.INFANTRY, h);
        Fix.unit(s, 0, UnitType.VEHICLE, h);
        Fix.unit(s, 0, UnitType.AIRCRAFT, h);
        assertEquals(1, единиц("full_unit_sets"), "второй четвёрке не хватает вышки");
        Fix.unit(s, 0, UnitType.TOWER, h);
        assertEquals(2, единиц("full_unit_sets"));
    }

    @Test
    void золотыеМодули() {
        я.goldModules = 2;
        assertEquals(2, единиц("gold_modules"));
    }

    @Test
    void кубикиНаТретьейСтупениИВершине() {
        String t0 = s.tech.tracks.get(0);
        String t1 = s.tech.tracks.get(1);
        for (int step = 1; step <= 4; step++) {
            s.tech.placeCube(t0, 0, step);
        }
        s.tech.placeCube(t1, 0, 2);
        s.tech.placeCube(t1, 0, 3);
        assertEquals(3, единиц("top_steps"));
    }

    @Test
    void тройкиКелемийБоеприпасТрофей() {
        я.resources.add(Resource.KELIUM, 3);
        я.resources.add(Resource.AMMO, 2);
        я.resources.add(Resource.TROPHY, 5);
        assertEquals(2, единиц("resource_sets"));
    }

    @Test
    void картыСуперАрсенала() {
        я.superArsenalCards.add("sa1");
        assertEquals(1, единиц("super_arsenal_cards"));
    }

    @Test
    void келемийВХранилище() {
        я.resources.add(Resource.KELIUM, 3);
        assertEquals(3, единиц("kelium"));
    }

    @Test
    void запитанныеСДвумяЯчейками() {
        String h = свободныйГекс(List.of());
        BuildingToken завод = Fix.building(s, 0, BuildingType.FACTORY, h, null);
        BuildingToken казарма = Fix.building(s, 0, BuildingType.BARRACKS, h, null);
        Fix.power(завод);
        Fix.power(казарма);
        int двух = завод.energySlots == 2 ? 1 : 0;
        двух += казарма.energySlots == 2 ? 1 : 0;
        assertEquals(двух, единиц("powered_two_cell"));
        assertEquals(2, завод.energySlots, "у завода две ячейки энергии");
        завод.stripEnergyOf(завод.uid);
        assertEquals(двух - 1, единиц("powered_two_cell"), "незапитанный не в счёт");
    }

    @Test
    void трекиГдеКубикВышеВсех() {
        String t0 = s.tech.tracks.get(0);
        String t1 = s.tech.tracks.get(1);
        String t2 = s.tech.tracks.get(2);
        s.tech.placeCube(t0, 0, 1);
        s.tech.placeCube(t0, 0, 2);
        s.tech.placeCube(t0, 1, 1);              // впереди
        s.tech.placeCube(t1, 0, 2);
        s.tech.placeCube(t1, 1, 2);              // поровну — не впереди
        s.tech.placeCube(t2, 1, 1);              // своего кубика нет
        assertEquals(1, единиц("track_leader"));
    }

    @Test
    void жетоныНаСвалке() {
        String h = свободныйГекс(List.of());
        UnitToken u = Fix.unit(s, 1, UnitType.INFANTRY, h);
        u.setHexId(null);
        я.destroyedTokens.add(u);
        я.destroyedTokens.add(Fix.unit(s, 1, UnitType.VEHICLE, h));
        assertEquals(2, единиц("dump_tokens"));
    }

    /** У каждой категории карты есть напечатанный текст — его кладёт на лицо скрипт лиц. */
    @Test
    void уКаждойКатегорииЕстьПечатныйТекст() {
        var set = Ctx.cards(s, "super_objectives");
        assertTrue(set.raw.get("categories") instanceof Map<?, ?>, "в наборе нет подписей категорий");
        Map<?, ?> подписи = (Map<?, ?>) set.raw.get("categories");
        for (Map<String, Object> card : set.entries) {
            for (String к : СуперЗадания.категорииКарты(s, String.valueOf(card.get("id")))) {
                Object текст = подписи.get(к);
                assertTrue(текст != null && String.valueOf(текст).startsWith("за "),
                    к + ": подпись должна продолжать плашку «Награда: по ★» словами «за …»");
            }
        }
    }

    @Test
    void каждуюКатегориюНабораДвижокЗнает() {
        for (Map<String, Object> card : Ctx.cards(s, "super_objectives").entries) {
            for (String к : СуперЗадания.категорииКарты(s, String.valueOf(card.get("id")))) {
                assertTrue(СуперЗадания.знаетКатегорию(к), к);
            }
        }
    }
}
