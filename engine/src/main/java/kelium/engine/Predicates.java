package kelium.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.core.TurnJournal;

/**
 * Реестр предикатов — именованные условия заданий (код) + параметры (YAML).
 *
 * <p>Порт из forge/engine/predicates.py. Каждая карта задания называет id
 * предиката и передаёт числовые параметры. Два семейства: STATE-предикаты (С)
 * читают текущее состояние поля; INCIDENT-предикаты (Х) читают {@link TurnJournal}.
 * Геометрические предикаты опираются на настоящее поле сценария.
 */
public final class Predicates {

    private Predicates() {
    }

    /** Ошибка: запрошен неизвестный предикат задания. */
    public static final class PredicateError extends RuntimeException {
        public PredicateError(String msg) {
            super(msg);
        }
    }

    @FunctionalInterface
    private interface Pred {
        boolean test(GameState s, int seat, TurnJournal j, Map<String, Object> p);
    }

    private static final Map<String, Pred> REGISTRY = new HashMap<>();
    private static final Set<String> GEOMETRY = new HashSet<>();

    private static void reg(String id, boolean geo, Pred fn) {
        REGISTRY.put(id, fn);
        if (geo) {
            GEOMETRY.add(id);
        }
    }

    /** Проверить предикат {@code pid} для места {@code seat}. */
    public static boolean check(String pid, GameState s, int seat, TurnJournal j, Map<String, Object> p) {
        Pred fn = REGISTRY.get(pid);
        if (fn == null) {
            throw new PredicateError("неизвестный предикат задания " + pid);
        }
        return fn.test(s, seat, j, p != null ? p : Map.of());
    }

    /** Требует ли предикат {@code pid} геометрию поля. */
    public static boolean isGeometry(String pid) {
        if (!REGISTRY.containsKey(pid)) {
            throw new PredicateError("неизвестный предикат задания " + pid);
        }
        return GEOMETRY.contains(pid);
    }

    /**
     * Все зарегистрированные предикаты — чтобы проверки могли пройтись по ним
     * скопом и убедиться, что ни один не падает и не врёт на пустом поле.
     */
    public static java.util.Set<String> allIds() {
        return java.util.Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** Зарегистрирован ли предикат. */
    public static boolean isRegistered(String pid) {
        return REGISTRY.containsKey(pid);
    }

    private static int intp(Map<String, Object> p, String k, int def) {
        Object v = p.get(k);
        return v instanceof Number n ? n.intValue() : def;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listp(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    // ---- регистрация всех предикатов ---------------------------------------
    static {
        // === РИСУНОК ИЗ ЖЕТОНОВ (13.08.2026) ===
        // Задание требует не «столько-то жетонов», а ФИГУРУ на поле: линию из трёх,
        // треугольник, крюк. Фигуру можно ПОВОРАЧИВАТЬ и НЕЛЬЗЯ ОТРАЖАТЬ — см.
        // kelium.engine.Figures, там же формат записи. Здесь предикат только
        // передаёт параметры карты: вся геометрия живёт в одном месте.
        reg("figure_of_tokens", true, (s, seat, j, p) -> Figures.satisfied(s, seat, p));
        // === STATE (С) — non-geometry ===
        reg("military_buildings_of_distinct_types", false, (s, seat, j, p) -> {
            Set<BuildingType> mil = Set.of(BuildingType.BARRACKS, BuildingType.FACTORY, BuildingType.AIRBASE);
            Set<BuildingType> types = new HashSet<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (mil.contains(b.type)) {
                    if (!Boolean.TRUE.equals(p.get("all_powered")) || b.powered()) {
                        types.add(b.type);
                    }
                }
            }
            return types.size() >= intp(p, "count", 3);
        });

        reg("building_powered", false, (s, seat, j, p) -> {
            Set<String> want = new HashSet<>();
            for (Object o : listp(p, "types")) {
                want.add(o.toString());
            }
            Set<String> powered = new HashSet<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.powered()) {
                    powered.add(b.type.code);
                }
            }
            return powered.containsAll(want);
        });

        reg("units_on_field", false, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            List<UnitToken> units = pl.unitsOnField();
            if (Boolean.TRUE.equals(p.get("on_building_hexes"))) {
                Set<String> bhex = new HashSet<>();
                for (BuildingToken b : pl.buildingsOnField()) {
                    bhex.add(b.hexId);
                }
                units = units.stream().filter(u -> bhex.contains(u.hexId)).toList();
            }
            if (units.size() < intp(p, "count", 1)) {
                return false;
            }
            if (p.containsKey("distinct_kinds")) {
                Set<UnitType> kinds = new HashSet<>();
                for (UnitToken u : units) {
                    kinds.add(u.type);
                }
                return kinds.size() >= intp(p, "distinct_kinds", 1);
            }
            return true;
        });

        reg("damaged_enemy_tokens_standing", false, (s, seat, j, p) -> {
            int n = 0;
            boolean vot = false;
            for (PlayerState pl : s.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (Token t : allTokens(pl)) {
                    if (t.hexId() != null && damage(t) > 0 && alive(t)) {
                        n++;
                        if (t instanceof UnitToken u
                                && (u.type == UnitType.VEHICLE || u.type == UnitType.TOWER)) {
                            vot = true;
                        }
                    }
                }
            }
            if (n < intp(p, "count", 3)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("include_vehicle_or_tower")) || vot;
        });

        reg("enemy_building_damaged", false, (s, seat, j, p) -> {
            int n = 0;
            for (PlayerState pl : s.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (BuildingToken b : pl.buildings) {
                    if (b.hexId != null && b.damage > 0 && b.alive()) {
                        n++;
                    }
                }
            }
            return n >= intp(p, "count", 1);
        });

        // === INCIDENT (Х) — read the journal ===
        reg("produced_units_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.unitsProduced < intp(p, "count", 2)) {
                return false;
            }
            if (p.containsKey("distinct_buildings")) {
                return f.unitsProducedBuildings.size() >= intp(p, "distinct_buildings", 1);
            }
            return true;
        });

        reg("mixed_assembly_outputs_this_turn", false, (s, seat, j, p) ->
            j.of(seat).assemblyOutputsUsed.containsAll(Set.of("unit", "ammo")));

        reg("build_ops_this_turn", false, (s, seat, j, p) ->
            j.of(seat).buildOps >= intp(p, "count", 2));

        reg("destroyed_enemy_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.enemyTokensDestroyed < intp(p, "count", 1)) {
                return false;
            }
            // o21 «Первая кровь» 10.0: усиление платит за толстую цель — хотя бы
            // один из уничтоженных имел прочность не меньше min_hp.
            if (p.containsKey("min_hp") && f.maxDestroyedHp < intp(p, "min_hp", 2)) {
                return false;
            }
            if (Boolean.TRUE.equals(p.get("lost_none")) && f.lostOwnThisTurn > 0) {
                return false;
            }
            if (p.containsKey("victim_types")) {
                Set<String> want = new HashSet<>();
                for (Object o : listp(p, "victim_types")) {
                    want.add(o.toString());
                }
                boolean any = false;
                for (String t : f.destroyedTypes) {
                    if (want.contains(t)) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    return false;
                }
            }
            return true;
        });

        reg("damaged_distinct_enemy_this_turn", false, (s, seat, j, p) ->
            j.of(seat).enemyTokensDamaged.size() >= intp(p, "count", 3));

        reg("destroyed_in_retaliation_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.destroyedInRetaliation < 1) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("lost_none")) || f.lostOwnThisTurn == 0;
        });

        reg("razed_and_rebuilt_same_hex_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            for (String h : f.razedOwnHexes) {
                if (f.builtOnHexes.contains(h)) {
                    return true;
                }
            }
            return false;
        });

        reg("moved_building_this_turn", false, (s, seat, j, p) -> j.of(seat).movedBuilding);

        reg("moved_units_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.unitsMoved < intp(p, "count", 3)) {
                return false;
            }
            if (p.containsKey("distinct_hexes")) {
                return f.movedFromHexes.size() >= intp(p, "distinct_hexes", 1);
            }
            return true;
        });

        reg("destroyed_two_in_one_battle_this_turn", false, (s, seat, j, p) ->
            j.of(seat).maxKillsOneBattle >= 2);

        reg("energy_swap_sources_this_turn", false, (s, seat, j, p) ->
            j.of(seat).energySwapSources.size() >= intp(p, "count", 2));

        reg("took_last_kelium_this_turn", false, (s, seat, j, p) ->
            j.of(seat).tookLastKeliumFromGrid);

        reg("containers_opened_this_turn", false, (s, seat, j, p) ->
            j.of(seat).containersOpened >= intp(p, "count", 2));

        reg("used_market_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (!f.usedMarket) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("printed_rate")) || f.usedMarketPrintedRate;
        });

        // === sacrifice (Ж) ===
        reg("sacrifice_paid", false, (s, seat, j, p) -> true);
        reg("sacrifice_enhanced", false, (s, seat, j, p) -> {
            try {
                return s.player(seat).resources.canPay(
                    Resource.fromCode((String) p.get("resource")), intp(p, "amount", 0));
            } catch (RuntimeException e) {
                return true;
            }
        });

        // === tech / resources ===
        reg("tech_step_reached", false, (s, seat, j, p) -> {
            int step = intp(p, "step", 2);
            int needTracks = intp(p, "tracks", 1);
            int hit = 0;
            for (int v : s.player(seat).techSteps.values()) {
                if (v >= step) {
                    hit++;
                }
            }
            return hit >= needTracks;
        });

        reg("resource_at_least", false, (s, seat, j, p) -> {
            try {
                return s.player(seat).resources.get(Resource.fromCode((String) p.get("resource")))
                    >= intp(p, "amount", 1);
            } catch (RuntimeException e) {
                return false;
            }
        });

        reg("buildings_on_field_count", false, (s, seat, j, p) -> {
            Object typesObj = p.get("types");
            if (!(typesObj instanceof List<?> types) || types.isEmpty()) {
                return s.player(seat).buildingsOnField().size() >= intp(p, "count", 2);
            }
            // фильтр по типам зданий (например только miner/power_plant)
            Set<String> want = new HashSet<>();
            for (Object t : types) {
                want.add(String.valueOf(t));
            }
            int n = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (want.contains(b.type.code)) {
                    n++;
                }
            }
            return n >= intp(p, "count", 2);
        });

        reg("non_cu_building_powered", false, (s, seat, j, p) -> {
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.COMMAND_CENTER && b.powered()) {
                    return true;
                }
            }
            return false;
        });

        reg("unit_off_cu_hex", false, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> cuHexes = new HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    cuHexes.add(b.hexId);
                }
            }
            for (UnitToken u : pl.unitsOnField()) {
                if (!cuHexes.contains(u.hexId)) {
                    return true;
                }
            }
            return false;
        });

        // ПАРАМЕТР count ЧИТАЛСЯ НЕ ВСЕГДА: предикат отвечал «есть хотя бы один»
        // независимо от того, что просила карта. n8 «Находка» с порогом 2 и o49
        // «Схрон» с порогом 4 выполнялись бы с одним контейнером на руках.
        reg("has_unopened_container", false, (s, seat, j, p) ->
            s.player(seat).containers >= intp(p, "count", 1));

        // === КАТАЛОГ 8.0 (objectives 1.5.0) ===

        // o01: все собиравшие здания выбрали БПР (и никто — войска)
        reg("assembly_all_chose_ammo", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.assemblyChoseUnits > 0
                    || f.assemblyAmmoBuildingTypes.size() < intp(p, "count", 2)) {
                return false;
            }
            if (p.containsKey("include_types")) {
                for (Object t : listp(p, "include_types")) {
                    if (f.assemblyAmmoBuildingTypes.contains(t.toString())) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        });

        // o02/o09/n2: производство войск с фильтром «не вышка» и доп. осями
        reg("produced_units_ex_tower", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            int nonTower = 0;
            for (var e : f.producedByType.entrySet()) {
                if (!"tower".equals(e.getKey())) {
                    nonTower += e.getValue();
                }
            }
            if (nonTower < intp(p, "count", 1)) {
                return false;
            }
            if (Boolean.TRUE.equals(p.get("no_ammo")) && f.ammoProduced > 0) {
                return false;
            }
            if (p.containsKey("distinct_buildings")
                    && f.producedUnitBuildingTypes.size() < intp(p, "distinct_buildings", 2)) {
                return false;
            }
            if (p.containsKey("building_types_any")) {
                for (Object t : listp(p, "building_types_any")) {
                    if (f.producedUnitBuildingTypes.contains(t.toString())) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        });

        // o03: вышка поставлена в гекс без своего ЦУ (усил: гекс у противника)
        reg("tower_placed_off_cu", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.towerPlacedHexes.isEmpty()) {
                return false;
            }
            if (!Boolean.TRUE.equals(p.get("borders_enemy"))) {
                return true;
            }
            for (String hid : f.towerPlacedHexes) {
                if (adjacentToEnemy(s, seat, hid)) {
                    return true;
                }
            }
            return false;
        });

        // o04: запитанные добытчики у РАЗНЫХ тайлов (усил: ≥1 не у стартового)
        reg("powered_miners_distinct_spawns", true, (s, seat, j, p) -> {
            // жадное назначение «добытчик -> свой тайл» (добытчиков максимум 4)
            List<List<String>> options = new ArrayList<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.MINER || !b.powered()) {
                    continue;
                }
                List<String> grids = new ArrayList<>();
                for (String nb : s.field.neighbors(b.hexId)) {
                    if (isGrid(s, nb)) {
                        grids.add(nb);
                    }
                }
                if (!grids.isEmpty()) {
                    options.add(grids);
                }
            }
            options.sort((a, b2) -> a.size() - b2.size());
            Set<String> taken = new HashSet<>();
            for (List<String> grids : options) {
                for (String g : grids) {
                    if (!taken.contains(g)) {
                        taken.add(g);
                        break;
                    }
                }
            }
            if (taken.size() < intp(p, "count", 2)) {
                return false;
            }
            // o04-усил 10.0: ВСЕ занятые тайлы — нестартовые. «Хотя бы один не на
            // стартовом» было слишком мягко: стартовый тайл есть у каждого даром.
            if (Boolean.TRUE.equals(p.get("nonstart_all"))) {
                for (String g : taken) {
                    if (s.field.get(g).spawnTile.isStart) {
                        return false;
                    }
                }
                return true;
            }
            int needNonStart = intp(p, "nonstart", 0);
            if (needNonStart > 0) {
                int ns = 0;
                for (String g : taken) {
                    if (!s.field.get(g).spawnTile.isStart) {
                        ns++;
                    }
                }
                return ns >= needNonStart;
            }
            return true;
        });

        // o05: последний келемий с НЕ стартового тайла (усил: тайл ушёл в запас)
        reg("last_kelium_nonstart", false, (s, seat, j, p) ->
            Boolean.TRUE.equals(p.get("claimed"))
                ? j.of(seat).spawnTileClaimedNonStart
                : j.of(seat).lastKeliumNonStart);

        // o08: добытчик взял контейнер вместо келемия (усил: №3/№4)
        reg("miner_took_container", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (!f.minerTookContainer) {
                return false;
            }
            // o08-усил 10.0: и НИ ОДНОГО келемия за ход. У Добычи ровно два
            // выхода, значит это настоящий отказ, а не бесплатная приписка.
            if (Boolean.TRUE.equals(p.get("no_kelium")) && f.keliumMined > 0) {
                return false;
            }
            if (!p.containsKey("levels")) {
                return true;
            }
            for (Object lv : listp(p, "levels")) {
                if (f.minerContainerLevels.contains(((Number) lv).intValue())) {
                    return true;
                }
            }
            return false;
        });

        // o13: снос своего здания (усил: и построил в тот же ход)
        reg("demolished_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            return f.demolishedNonCu
                && (!Boolean.TRUE.equals(p.get("and_built")) || !f.builtOnHexes.isEmpty());
        });

        // o11: построил/перенёс здание к чужим войскам (усил: к чужому зданию)
        reg("built_bordering_enemy", false, (s, seat, j, p) -> {
            boolean wantBuilding = "building".equals(p.get("enemy"));
            for (String hid : j.of(seat).builtOnHexes) {
                for (String nb : s.field.neighbors(hid)) {
                    if (wantBuilding) {
                        if (!enemyBuildingsOn(s, seat, nb).isEmpty()) {
                            return true;
                        }
                    } else {
                        for (Token t : enemyTokensOn(s, seat, nb)) {
                            if (t instanceof UnitToken) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });

        // o12: своё здание на гексе с чужими ВОЙСКАМИ (усил: их там ≥2)
        reg("building_on_hex_with_enemy_units", true, (s, seat, j, p) -> {
            int need = intp(p, "units", 1);
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                int n = 0;
                for (Token t : enemyTokensOn(s, seat, b.hexId)) {
                    if (t instanceof UnitToken) {
                        n++;
                    }
                }
                if (n >= need) {
                    return true;
                }
            }
            return false;
        });

        // o16: перенос здания (не ЦУ)
        reg("moved_noncu_building", false, (s, seat, j, p) ->
            j.of(seat).movedNonCuUids.size() >= intp(p, "count", 1));

        // o17: ЦУ перенесено в «чистый» гекс (усил: гекс у противника)
        reg("moved_cu_to_virgin_hex", false, (s, seat, j, p) -> {
            if (!j.of(seat).movedCuToVirginHex) {
                return false;
            }
            if (!Boolean.TRUE.equals(p.get("borders_enemy"))) {
                return true;
            }
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER && adjacentToEnemy(s, seat, b.hexId)) {
                    return true;
                }
            }
            return false;
        });

        // o18: нет ни одного незапитанного здания (усил: и зданий ≥N)
        reg("no_unpowered_buildings", false, (s, seat, j, p) -> {
            List<BuildingToken> bs = s.player(seat).buildingsOnField();
            if (bs.size() < intp(p, "min_buildings", 1)) {
                return false;
            }
            for (BuildingToken b : bs) {
                if (!b.powered()) {
                    return false;
                }
            }
            return true;
        });

        // o20: Смена энергии из ≥N гексов, каждый кубик сменил гекс (усил: + стройка)
        reg("energy_swap_cross_hex", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            return f.energySwapSourceHexes.size() >= intp(p, "sources", 2)
                && !f.energySwapSameHexCube
                && (!Boolean.TRUE.equals(p.get("and_built")) || !f.builtOnHexes.isEmpty());
        });

        // o22 «Зачистка»: снёс нейтральное здание (усил: и достал жетон противника)
        reg("destroyed_neutral_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.neutralsRazed < intp(p, "count", 1)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("and_damaged_enemy"))
                || f.razedNeutralAndHitEnemySameBattle;
        });

        // o23: ранил ≥N разных и никого не уничтожил
        reg("damaged_distinct_no_kills", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            return f.enemyTokensDamaged.size() >= intp(p, "count", 2)
                && f.enemyTokensDestroyed == 0;
        });

        // o24: уничтожил, потратив на убийственную атаку не больше N БПР
        reg("destroyed_with_ammo_at_most", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            return f.enemyTokensDestroyed >= 1 && f.minKillAmmoCost <= intp(p, "ammo", 2);
        });

        // o25: нанёс ≥N урона зданиям противника в этот ход
        reg("enemy_building_hit_this_turn", false, (s, seat, j, p) ->
            j.of(seat).enemyBuildingHits >= intp(p, "count", 1));

        // o26 «Блицкриг»: тем же войском переместился и убил (усил: 2 убийства)
        reg("moved_and_destroyed_same_unit", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            int kills = intp(p, "kills", 1);
            if (kills <= 1) {
                return f.movedAndKilledSameUnit;
            }
            for (int v : f.killsByMovedUnit.values()) {
                if (v >= kills) {
                    return true;
                }
            }
            return false;
        });

        // o27: своё войско на гексе с чужими ВОЙСКАМИ (усил: своих там ≥2)
        reg("unit_on_hex_with_enemy_units", true, (s, seat, j, p) -> {
            int need = intp(p, "count", 1);
            Map<String, Integer> mine = new HashMap<>();
            for (UnitToken u : s.player(seat).unitsOnField()) {
                mine.merge(u.hexId, 1, Integer::sum);
            }
            for (var e : mine.entrySet()) {
                if (e.getValue() < need) {
                    continue;
                }
                for (Token t : enemyTokensOn(s, seat, e.getKey())) {
                    if (t instanceof UnitToken) {
                        return true;
                    }
                }
            }
            return false;
        });

        // o28 «Клещи»: свои ВОЙСКА в ≥N гексах вокруг одного гекса с чужими войсками
        reg("units_bordering_enemy_units_hex", true, (s, seat, j, p) -> {
            Set<String> myUnitHexes = new HashSet<>();
            for (UnitToken u : s.player(seat).unitsOnField()) {
                myUnitHexes.add(u.hexId);
            }
            int need = intp(p, "hexes", 2);
            for (String centre : s.field.hexes.keySet()) {
                boolean enemyUnits = false;
                for (Token t : enemyTokensOn(s, seat, centre)) {
                    if (t instanceof UnitToken) {
                        enemyUnits = true;
                        break;
                    }
                }
                if (!enemyUnits) {
                    continue;
                }
                int n = 0;
                for (String nb : s.field.neighbors(centre)) {
                    if (myUnitHexes.contains(nb)) {
                        n++;
                    }
                }
                if (n >= need) {
                    return true;
                }
            }
            return false;
        });

        // o29 «Пустой двор»: ≥N войск вне своих гексов и НОЛЬ на своих
        // (усил. 10.0: все они ещё и на РАЗНЫХ гексах)
        reg("units_off_own_hexes", true, (s, seat, j, p) -> {
            Set<String> ownBld = ownBuildingHexes(s, seat);
            int off = 0;
            int on = 0;
            Set<String> offHexes = new HashSet<>();
            for (UnitToken u : s.player(seat).unitsOnField()) {
                if (ownBld.contains(u.hexId)) {
                    on++;
                } else {
                    off++;
                    offHexes.add(u.hexId);
                }
            }
            if (on != 0 || off < intp(p, "count", 2)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("distinct_hexes"))
                || offHexes.size() >= intp(p, "count", 2);
        });

        // o30 «Мародёр»: контейнер подобран войском с поля
        reg("picked_container_by_unit", false, (s, seat, j, p) ->
            j.of(seat).containersPickedByUnit >= intp(p, "count", 1));

        // o33 «Биржа»: предложение карты маркета (усил: + печатная сделка)
        reg("used_market_card_offer", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            return f.usedMarketCardOffer
                && (!Boolean.TRUE.equals(p.get("and_printed")) || f.usedMarketPrintedRate);
        });

        // o37 «Три трека»: фишки на ≥N треках (усил: все на шаге ≥K)
        reg("tracks_occupied", false, (s, seat, j, p) -> {
            int minStep = intp(p, "min_step", 1);
            int n = 0;
            for (int v : s.player(seat).techSteps.values()) {
                if (v >= minStep) {
                    n++;
                }
            }
            return n >= intp(p, "tracks", 3);
        });

        // o38: запитанные добытчики (без привязки к грядкам)
        reg("powered_miners_count", false, (s, seat, j, p) -> {
            int n = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type == BuildingType.MINER && b.powered()) {
                    n++;
                }
            }
            return n >= intp(p, "count", 3);
        });

        // o40 «Ва-банк»: у тебя не больше указанного (0 монет, 0 БПР...)
        reg("resources_at_most", false, (s, seat, j, p) -> {
            for (var e : p.entrySet()) {
                Resource r;
                try {
                    r = Resource.fromCode(e.getKey());
                } catch (RuntimeException ex) {
                    continue;
                }
                int limit = e.getValue() instanceof Number n ? n.intValue() : 0;
                if (s.player(seat).resources.get(r) > limit) {
                    return false;
                }
            }
            return true;
        });

        // o07 «Засада»: войско укрыто в своём военном здании у фронта
        // («внутри» = свой род в своём здании того же рода на одном гексе, §5.3)
        reg("hidden_unit_near_enemy", true, (s, seat, j, p) -> {
            Map<UnitType, BuildingType> hide = Map.of(
                UnitType.INFANTRY, BuildingType.BARRACKS,
                UnitType.VEHICLE, BuildingType.FACTORY,
                UnitType.AIRCRAFT, BuildingType.AIRBASE);
            int n = 0;
            for (UnitToken u : s.player(seat).unitsOnField()) {
                BuildingType want = hide.get(u.type);
                if (want == null) {
                    continue;
                }
                for (BuildingToken b : s.player(seat).buildingsOnField()) {
                    if (b.type == want && b.hexId.equals(u.hexId)
                            && adjacentToEnemy(s, seat, u.hexId)) {
                        n++;
                        break;
                    }
                }
            }
            return n >= intp(p, "count", 1);
        });

        // n5: на здании (не ЦУ) стоит хотя бы один кубик энергии
        reg("non_cu_building_has_energy", false, (s, seat, j, p) -> {
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.COMMAND_CENTER && b.energyPlaced >= 1) {
                    return true;
                }
            }
            return false;
        });

        // === ГЕОМЕТРИЧЕСКИЕ ===
        reg("powered_miners_bordering_grids", true, (s, seat, j, p) -> {
            int n = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type == BuildingType.MINER && b.powered()) {
                    for (String nb : s.field.neighbors(b.hexId)) {
                        if (isGrid(s, nb)) {
                            n++;
                            break;
                        }
                    }
                }
            }
            return n >= intp(p, "count", 2);
        });

        reg("plant_and_miner_same_hex", true, (s, seat, j, p) -> {
            Map<String, List<BuildingToken>> byHex = new HashMap<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                byHex.computeIfAbsent(b.hexId, k -> new ArrayList<>()).add(b);
            }
            for (var e : byHex.entrySet()) {
                Set<BuildingType> types = new HashSet<>();
                for (BuildingToken b : e.getValue()) {
                    types.add(b.type);
                }
                if (types.contains(BuildingType.POWER_PLANT) && types.contains(BuildingType.MINER)) {
                    if (!Boolean.TRUE.equals(p.get("miner_powered_bordering_grid"))) {
                        return true;
                    }
                    for (BuildingToken b : e.getValue()) {
                        if (b.type == BuildingType.MINER && b.powered()) {
                            for (String nb : s.field.neighbors(e.getKey())) {
                                if (isGrid(s, nb)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        });

        reg("plant_borders_own_buildings", true, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> ownHexes = new HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                ownHexes.add(b.hexId);
            }
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.type != BuildingType.POWER_PLANT) {
                    continue;
                }
                int adj = 0;
                for (String nb : s.field.neighbors(b.hexId)) {
                    if (ownHexes.contains(nb)) {
                        adj++;
                    }
                }
                if (adj >= intp(p, "count", 2)) {
                    return true;
                }
            }
            return false;
        });

        reg("building_chain_adjacent", true, (s, seat, j, p) -> {
            Set<String> hexes = ownBuildingHexes(s, seat);
            return largestComponent(s, hexes, hexes) >= intp(p, "count", 3);
        });

        // ПРИМЫКАНИЕ СТЕНКОЙ: сколько своих зданий имеют ОБЩУЮ грань с другим
        // своим зданием (строже, чем «на соседних гексах» — нужны совпавшие
        // занятые стороны по общей стенке). count = требуемое число таких зданий.
        reg("buildings_share_wall", true, (s, seat, j, p) -> {
            int n = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                kelium.core.Hex h = s.field.get(b.hexId);
                if (h != null && shareWallWithAny(s, h, b.uid)) {
                    n++;
                }
            }
            return n >= intp(p, "count", 2);
        });

        reg("buildings_ring_around_hex", true, (s, seat, j, p) -> {
            Set<String> own = ownBuildingHexes(s, seat);
            int need = intp(p, "count", 3);
            for (String centre : s.field.hexes.keySet()) {
                if (!passable(s, centre)) {
                    continue;   // G1: кольцо вокруг FORBIDDEN не считается
                }
                Set<String> neigh = new HashSet<>(s.field.neighbors(centre));
                neigh.retainAll(own);
                if (neigh.size() >= need) {
                    if (Boolean.TRUE.equals(p.get("enemy_building_on_center"))) {
                        // o14 «Осадный лагерь»: в центре — ЗДАНИЕ противника
                        if (!enemyBuildingsOn(s, seat, centre).isEmpty()) {
                            return true;
                        }
                    } else if (Boolean.TRUE.equals(p.get("enemy_on_center"))) {
                        if (!enemyTokensOn(s, seat, centre).isEmpty()) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }
            return false;
        });

        reg("building_adjacent_to_enemy", true, (s, seat, j, p) -> {
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                for (String nb : s.field.neighbors(b.hexId)) {
                    List<Token> enemies = enemyTokensOn(s, seat, nb);
                    if (enemies.isEmpty()) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(p.get("enemy_no_units"))) {
                        List<Token> ebuild = new ArrayList<>();
                        boolean eunit = false;
                        for (Token t : enemies) {
                            if (t instanceof BuildingToken) {
                                ebuild.add(t);
                            } else {
                                eunit = true;
                            }
                        }
                        if (ebuild.isEmpty() || eunit) {
                            continue;
                        }
                        if (Boolean.TRUE.equals(p.get("enemy_building_damaged"))) {
                            boolean anyDmg = false;
                            for (Token t : ebuild) {
                                if (damage(t) > 0) {
                                    anyDmg = true;
                                    break;
                                }
                            }
                            if (!anyDmg) {
                                continue;
                            }
                        }
                        return true;
                    }
                    return true;
                }
            }
            return false;
        });

        reg("building_on_hex_with_enemy", true, (s, seat, j, p) -> {
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (!enemyTokensOn(s, seat, b.hexId).isEmpty()) {
                    return true;
                }
            }
            return false;
        });

        reg("building_on_enemy_structure_hex", true, (s, seat, j, p) -> {
            int n = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                Hex h = s.field.get(b.hexId);
                if (!enemyBuildingsOn(s, seat, b.hexId).isEmpty() || h.hasNeutral()) {
                    n++;
                }
            }
            return n >= intp(p, "count", 1);
        });

        reg("unit_far_from_own_buildings", true, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> ownBld = ownBuildingHexes(s, seat);
            if (ownBld.isEmpty()) {
                return false;
            }
            int distReq = intp(p, "distance", 4);
            for (UnitToken u : pl.unitsOnField()) {
                Integer best = bfsDist(s, u.hexId, ownBld);
                if (best != null && best >= distReq) {
                    if (Boolean.TRUE.equals(p.get("on_enemy_hex"))
                            && enemyTokensOn(s, seat, u.hexId).isEmpty()) {
                        continue;
                    }
                    return true;
                }
            }
            return false;
        });

        // === Узоры супер-заданий (победа) ===
        reg("sp_line_of_three_buildings_mid_adjacent_enemy", true, (s, seat, j, p) -> {
            Set<String> bh = ownBuildingHexes(s, seat);
            for (String mid : bh) {
                List<String> neigh = new ArrayList<>();
                for (String h : s.field.neighbors(mid)) {
                    if (bh.contains(h)) {
                        neigh.add(h);
                    }
                }
                for (int i = 0; i < neigh.size(); i++) {
                    for (int k = i + 1; k < neigh.size(); k++) {
                        String a = neigh.get(i);
                        String c = neigh.get(k);
                        if (!s.field.neighbors(a).contains(c) && adjacentToEnemy(s, seat, mid)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        reg("sp_three_buildings_one_hex_touching", true, (s, seat, j, p) -> {
            Map<String, Integer> c = new HashMap<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                c.merge(b.hexId, 1, Integer::sum);
            }
            return c.values().stream().anyMatch(v -> v >= 3);
        });

        reg("sp_triangle_three_buildings_one_adjacent_enemy", true, (s, seat, j, p) -> {
            List<String> bh = new ArrayList<>(ownBuildingHexes(s, seat));
            for (int i = 0; i < bh.size(); i++) {
                for (int k = i + 1; k < bh.size(); k++) {
                    for (int m = k + 1; m < bh.size(); m++) {
                        String a = bh.get(i);
                        String b = bh.get(k);
                        String c = bh.get(m);
                        if (s.field.neighbors(a).contains(b) && s.field.neighbors(a).contains(c)
                                && s.field.neighbors(b).contains(c)) {
                            if (adjacentToEnemy(s, seat, a) || adjacentToEnemy(s, seat, b)
                                    || adjacentToEnemy(s, seat, c)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });

        reg("sp_four_buildings_around_common_hex", true, (s, seat, j, p) -> {
            Set<String> bh = ownBuildingHexes(s, seat);
            for (String centre : s.field.hexes.keySet()) {
                if (!passable(s, centre)) {
                    continue;   // G1
                }
                Set<String> neigh = new HashSet<>(s.field.neighbors(centre));
                neigh.retainAll(bh);
                if (neigh.size() >= 4) {
                    return true;
                }
            }
            return false;
        });

        reg("sp_chain_three_miners_bordering_grids", true, (s, seat, j, p) -> {
            Set<String> minerHexes = new HashSet<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type == BuildingType.MINER) {
                    for (String nb : s.field.neighbors(b.hexId)) {
                        if (isGrid(s, nb)) {
                            minerHexes.add(b.hexId);
                            break;
                        }
                    }
                }
            }
            return largestComponent(s, minerHexes, minerHexes) >= 3;
        });

        reg("sp_three_unit_hexes_adjacent_one_enemy_hex", true, (s, seat, j, p) -> {
            Set<String> unitHexes = new HashSet<>();
            for (UnitToken u : s.player(seat).unitsOnField()) {
                unitHexes.add(u.hexId);
            }
            for (String centre : s.field.hexes.keySet()) {
                if (!enemyTokensOn(s, seat, centre).isEmpty()) {
                    Set<String> neigh = new HashSet<>(s.field.neighbors(centre));
                    neigh.retainAll(unitHexes);
                    if (neigh.size() >= 3) {
                        return true;
                    }
                }
            }
            return false;
        });

        reg("sp_four_hexes_diamond_each_with_unit", true, (s, seat, j, p) -> {
            List<String> uh = new ArrayList<>(new HashSet<>(unitHexes(s, seat)));
            int n = uh.size();
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    for (int c = b + 1; c < n; c++) {
                        for (int d = c + 1; d < n; d++) {
                            String[] quad = {uh.get(a), uh.get(b), uh.get(c), uh.get(d)};
                            int edges = 0;
                            for (int i = 0; i < 4; i++) {
                                for (int k = i + 1; k < 4; k++) {
                                    if (s.field.neighbors(quad[i]).contains(quad[k])) {
                                        edges++;
                                    }
                                }
                            }
                            if (edges >= 5) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });

        reg("sp_three_tower_building_pairs", true, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> towerHexes = new HashSet<>();
            for (UnitToken u : pl.unitsOnField()) {
                if (u.type == UnitType.TOWER) {
                    towerHexes.add(u.hexId);
                }
            }
            Set<String> bldHexes = ownBuildingHexes(s, seat);
            towerHexes.retainAll(bldHexes);
            return towerHexes.size() >= 3;
        });

        reg("aircraft_on_enemy_hex", true, (s, seat, j, p) -> {
            for (UnitToken u : s.player(seat).unitsOnField()) {
                if (u.type != UnitType.AIRCRAFT) {
                    continue;
                }
                List<Token> enemies = enemyTokensOn(s, seat, u.hexId);
                if (enemies.isEmpty()) {
                    continue;
                }
                if (Boolean.TRUE.equals(p.get("cu_on_hex"))) {
                    // o31-усил: на этом гексе стоит ЦУ противника
                    for (Token e : enemies) {
                        if (e instanceof BuildingToken eb && eb.type == BuildingType.COMMAND_CENTER) {
                            return true;
                        }
                    }
                } else if (Boolean.TRUE.equals(p.get("enemy_aircraft_damaged"))) {
                    for (Token e : enemies) {
                        if (e instanceof UnitToken eu && eu.type == UnitType.AIRCRAFT && eu.damage > 0) {
                            return true;
                        }
                    }
                } else {
                    return true;
                }
            }
            return false;
        });

        // ==================================================================
        //  ФИГУРЫ ИЗ ЖЕТОНОВ (заказ дизайнера 12.08.2026)
        // ==================================================================
        // На карте нарисована гексовая сетка и линия, проходящая через ячейки.
        // Фигуры чертятся по СОСЕДСТВУ ячеек: техника занимает две ячейки,
        // авиация стоит в воздушной и соединяет любые ячейки своего гекса.
        // Правила соседства и сам поиск — в engine/Shapes.

        /** Непрерывная линия из своих жетонов длиной не меньше {@code cells}. */
        reg("figure_chain", true, (s, seat, j, p) ->
            Shapes.longestChain(s, seat) >= intp(p, "cells", 5));

        /** Замкнутая фигура (кольцо) из своих жетонов длиной не меньше {@code cells}. */
        reg("figure_ring", true, (s, seat, j, p) ->
            Shapes.hasClosedRing(s, seat, intp(p, "cells", 4)));

        /** Связная группа своих жетонов покрывает не меньше {@code hexes} гексов. */
        reg("figure_front", true, (s, seat, j, p) ->
            Shapes.largestConnectedHexes(s, seat) >= intp(p, "hexes", 3));

        // ==================================================================
        //  КАТАЛОГ 10.0 (objectives 1.8.0) — ревью дизайнера 17.08.2026
        // ==================================================================

        // o03 «Опорный пункт»: N ВЫШЕК на РАЗНЫХ гексах, ни одна не на гексе
        // своего ЦУ. Вышка неподвижна (скорость 0), поэтому это состояние
        // копится Сборками ЦУ и не закрывается одним действием с пустого места.
        reg("towers_off_cu_hexes", true, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> cuHexes = new HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    cuHexes.add(b.hexId);
                }
            }
            Set<String> hexes = new HashSet<>();
            Set<String> onEnemy = new HashSet<>();
            for (UnitToken u : pl.unitsOnField()) {
                if (u.type != UnitType.TOWER || cuHexes.contains(u.hexId)) {
                    continue;
                }
                hexes.add(u.hexId);
                if (!enemyTokensOn(s, seat, u.hexId).isEmpty()) {
                    onEnemy.add(u.hexId);
                }
            }
            if (hexes.size() < intp(p, "count", 2)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("on_enemy_hex")) || !onEnemy.isEmpty();
        });

        // o12 «Наглая стройка»: В ЭТОТ ХОД здание поставлено на гекс, где стоят
        // войска противника. Была карта состояния с императивом «строй» — это
        // невозможно доказать за столом задним числом, поэтому теперь журнал.
        reg("built_on_hex_with_enemy_units", true, (s, seat, j, p) -> {
            int need = intp(p, "units", 1);
            for (String hid : j.of(seat).builtOnHexes) {
                int n = 0;
                for (Token t : enemyTokensOn(s, seat, hid)) {
                    if (t instanceof UnitToken) {
                        n++;
                    }
                }
                if (n >= need) {
                    return true;
                }
            }
            return false;
        });

        // o17 «Штаб на передовой»: В ЭТОТ ХОД ЦУ поставлено или перенесено так,
        // что до гекса с чужим ЦУ не больше distance гексов. Усиление — новое
        // место ПРИМЫКАЕТ к зданию противника.
        reg("cu_placed_near_enemy_cu", true, (s, seat, j, p) -> {
            Set<String> enemyCu = new HashSet<>();
            for (PlayerState other : s.players) {
                if (other.seat == seat) {
                    continue;
                }
                for (BuildingToken b : other.buildingsOnField()) {
                    if (b.type == BuildingType.COMMAND_CENTER) {
                        enemyCu.add(b.hexId);
                    }
                }
            }
            if (enemyCu.isEmpty()) {
                return false;
            }
            int limit = intp(p, "distance", 2);
            for (String hid : j.of(seat).cuPlacedHexes) {
                Integer d = bfsDist(s, hid, enemyCu);
                if (d == null || d > limit) {
                    continue;
                }
                if (!Boolean.TRUE.equals(p.get("adjacent_enemy_building"))) {
                    return true;
                }
                for (String nb : s.field.neighbors(hid)) {
                    if (!enemyBuildingsOn(s, seat, nb).isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        });

        // o22/o25/n10: ЧТО ЛЕЖИТ В ТРОФЕЙНОМ ПРОСТРАНСТВЕ. Проверяется глазами по
        // столу — трофеи лежат открыто перед игроком.
        reg("trophy_contains", false, (s, seat, j, p) -> {
            List<Token> trophies = s.player(seat).trophySpace;
            if (p.containsKey("any")) {
                return trophies.size() >= intp(p, "any", 1);
            }
            if (p.containsKey("building_types")) {
                Set<String> want = new HashSet<>();
                for (Object o : listp(p, "building_types")) {
                    want.add(o.toString());
                }
                for (Token t : trophies) {
                    if (t instanceof BuildingToken b && want.contains(b.type.code)) {
                        return true;
                    }
                }
                return false;
            }
            int need = intp(p, "building", 1);
            int n = 0;
            for (Token t : trophies) {
                if (t instanceof BuildingToken) {
                    n++;
                }
            }
            return n >= need;
        });

        // o46 «Трофейный обоз»: сколько РАЗНЫХ видов лежит в трофеях. Род войск и
        // каждый тип здания считаются отдельным видом.
        reg("trophy_distinct_kinds", false, (s, seat, j, p) -> {
            Set<String> kinds = new HashSet<>();
            for (Token t : s.player(seat).trophySpace) {
                if (t instanceof UnitToken u) {
                    kinds.add("unit:" + u.type.code);
                } else if (t instanceof BuildingToken b) {
                    kinds.add("building:" + b.type.code);
                }
            }
            return kinds.size() >= intp(p, "count", 3);
        });

        // o45 «Пристрелка»: N РАЗНЫХ чужих ЗДАНИЙ получили урон в этот ход.
        reg("damaged_distinct_enemy_buildings", false, (s, seat, j, p) ->
            j.of(seat).enemyBuildingsDamaged.size() >= intp(p, "count", 2));

        // o26 «Блицкриг» 10.0: N уничтожений ОДНИМ жетоном войска за ход
        // (усил. — этот жетон указанного рода).
        reg("kills_by_one_unit", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            int need = intp(p, "count", 2);
            Object wantType = p.get("unit_type");
            for (var e : f.killsByUnit.entrySet()) {
                if (e.getValue() < need) {
                    continue;
                }
                if (wantType == null
                        || wantType.toString().equals(f.killerUnitTypes.get(e.getKey()))) {
                    return true;
                }
            }
            return false;
        });

        // o02/n2 «найм»: сколько РАЗНЫХ РОДОВ войск нанято в этот ход, с
        // обязательными и запрещёнными родами. Заменяет прежний счёт «сколько
        // войск в скольких зданиях» — дизайнер просил считать РОДА.
        reg("hired_distinct_kinds", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            Set<String> forbid = new HashSet<>();
            for (Object o : listp(p, "forbid_kinds")) {
                forbid.add(o.toString());
            }
            Set<String> hired = new HashSet<>();
            for (var e : f.producedByType.entrySet()) {
                if (e.getValue() > 0) {
                    hired.add(e.getKey());
                }
            }
            for (String bad : forbid) {
                if (hired.contains(bad)) {
                    return false;
                }
            }
            if (hired.size() < intp(p, "count", 2)) {
                return false;
            }
            for (Object o : listp(p, "require_kinds")) {
                if (!hired.contains(o.toString())) {
                    return false;
                }
            }
            return true;
        });

        // o15 «Стройбум» 10.0: N строительных операций на ПОПАРНО НЕСОСЕДНИХ
        // гексах. Прежняя редакция считала только число операций и выполнялась
        // сама собой; здесь застройка обязана разъехаться вширь.
        reg("build_ops_on_nonadjacent_hexes", true, (s, seat, j, p) -> {
            List<String> hexes = new ArrayList<>(new java.util.LinkedHashSet<>(
                j.of(seat).buildOpHexes));
            int need = intp(p, "count", 2);
            return chooseNonAdjacent(s, hexes, 0, new ArrayList<>(), need);
        });

        // o16 «Переезд» 10.0: N перенесённых зданий за ход, усил. — среди них ЦУ.
        reg("moved_buildings_this_turn", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.movedAnyBuildingUids.size() < intp(p, "count", 2)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("include_cu")) || f.movedCuThisTurn;
        });

        // o19 «Военпром» 10.0: цепочка своих зданий, ПРИМЫКАЮЩИХ друг к другу
        // общей стенкой и стоящих на РАЗНЫХ гексах. Примыкание — термин §11:
        // соседняя ячейка чужого гекса через общее ребро.
        reg("buildings_wall_chain", true, (s, seat, j, p) -> {
            Set<BuildingType> mil = Set.of(BuildingType.BARRACKS, BuildingType.FACTORY,
                BuildingType.AIRBASE);
            boolean milOnly = Boolean.TRUE.equals(p.get("military_only"));
            List<BuildingToken> pool = new ArrayList<>();
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (!milOnly || mil.contains(b.type)) {
                    pool.add(b);
                }
            }
            // Граф: ребро между зданиями на РАЗНЫХ гексах, примыкающими стенкой.
            Map<Integer, List<Integer>> g = new HashMap<>();
            for (BuildingToken b : pool) {
                g.put(b.uid, new ArrayList<>());
            }
            for (int i = 0; i < pool.size(); i++) {
                for (int k = i + 1; k < pool.size(); k++) {
                    BuildingToken a = pool.get(i);
                    BuildingToken b = pool.get(k);
                    if (!a.hexId.equals(b.hexId) && abutsAcrossWall(s, a, b)) {
                        g.get(a.uid).add(b.uid);
                        g.get(b.uid).add(a.uid);
                    }
                }
            }
            Set<Integer> nodes = new HashSet<>(g.keySet());
            return largestComponentOf(g, nodes) >= intp(p, "count", 2);
        });

        // o20 «Коммутация» 10.0: на КАЖДОМ своём источнике энергии лежит ровно
        // один простаивающий кубик, и таких источников не меньше sources.
        // Источник — энергостанция или ЦУ (§1.1). Держать простой размазанным
        // неудобно намеренно: собрать его обратно стоит гекса в Смене энергии.
        reg("idle_cube_on_each_source", false, (s, seat, j, p) -> {
            int sources = 0;
            for (BuildingToken b : s.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.POWER_PLANT && b.type != BuildingType.COMMAND_CENTER) {
                    continue;
                }
                sources++;
                if (b.energyIdle != 1) {
                    return false;
                }
            }
            return sources >= intp(p, "sources", 2);
        });

        // o33 «Биржа» / o34 «Научный отдел»: сколько РАЗНЫХ предложений оплачено
        // за ход. Повтор одного и того же предложения не считается вторым.
        reg("market_offers_used", false, (s, seat, j, p) ->
            j.of(seat).marketOffersUsed.size() >= intp(p, "count", 3));
        reg("science_offers_used", false, (s, seat, j, p) ->
            j.of(seat).scienceOffersUsed.size() >= intp(p, "count", 3));

        // o39 «Сдача»: сдано N трофейных ЖЕТОНОВ действием Наука (усил. — все на
        // один трек; больше одного шага на трек за действие не делается, поэтому
        // «оба на один трек» дороже, чем выглядит).
        reg("science_trophies_spent", false, (s, seat, j, p) -> {
            TurnJournal.TurnFacts f = j.of(seat);
            if (f.scienceTrophiesSpent < intp(p, "count", 2)) {
                return false;
            }
            return !Boolean.TRUE.equals(p.get("same_track")) || f.scienceTracksUsed.size() == 1;
        });

        // n5 «Коммутация»: запитанное здание НЕ на гексе своего ЦУ.
        reg("powered_building_off_cu_hex", false, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> cuHexes = new HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    cuHexes.add(b.hexId);
                }
            }
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.powered() && !cuHexes.contains(b.hexId)) {
                    return true;
                }
            }
            return false;
        });

        // n6 «Выход»: своё войско в distance и больше гексах от гекса своего ЦУ.
        reg("unit_at_distance_from_cu", true, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            Set<String> cuHexes = new HashSet<>();
            for (BuildingToken b : pl.buildingsOnField()) {
                if (b.type == BuildingType.COMMAND_CENTER) {
                    cuHexes.add(b.hexId);
                }
            }
            if (cuHexes.isEmpty()) {
                return false;
            }
            int need = intp(p, "distance", 2);
            for (UnitToken u : pl.unitsOnField()) {
                Integer d = bfsDist(s, u.hexId, cuHexes);
                if (d != null && d >= need) {
                    return true;
                }
            }
            return false;
        });

        // n11 / n12: факты самого приказа этого хода.
        reg("lower_order_open_this_turn", false, (s, seat, j, p) -> j.of(seat).lowerOrderOpen);
        reg("order_coincided_this_turn", false, (s, seat, j, p) -> j.of(seat).orderBlocked);

        // o48 «Арсенальный набор»: карты арсенала на руках; усил. — ни одна не
        // установлена, все лежат рубашкой вверх.
        reg("arsenal_cards_held", false, (s, seat, j, p) -> {
            PlayerState pl = s.player(seat);
            int need = intp(p, "count", 3);
            if (Boolean.TRUE.equals(p.get("all_face_down"))) {
                return pl.allInstalledArsenal().isEmpty() && pl.arsenalHand.size() >= need;
            }
            return pl.arsenalHand.size() + pl.allInstalledArsenal().size() >= need;
        });

        // o50/o53/o54/o55 «рисунки» 10.0. Дизайнер: считать надо не жетоны, а
        // СВЯЗЬ. Фигура задаётся тем, ЧТО она соединяет, — тогда её нельзя
        // закрыть кучей жетонов на одном гексе, и требование остаётся честным
        // на любом поле. Вся геометрия — в {@link Shapes#chainConnects}.
        reg("chain_connects", true, (s, seat, j, p) -> Shapes.chainConnects(s, seat, p));
    }

    /**
     * Можно ли выбрать {@code need} ПОПАРНО НЕСОСЕДНИХ гексов из списка — перебор
     * с отсечением. Гексов операций за ход единицы, поэтому точный ответ дешевле
     * приближённого (o15 «Стройбум»).
     */
    private static boolean chooseNonAdjacent(GameState s, List<String> pool, int from,
                                             List<String> picked, int need) {
        if (picked.size() >= need) {
            return true;
        }
        if (pool.size() - from < need - picked.size()) {
            return false;
        }
        for (int i = from; i < pool.size(); i++) {
            String cand = pool.get(i);
            boolean ok = true;
            for (String taken : picked) {
                if (taken.equals(cand) || s.field.neighbors(taken).contains(cand)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            picked.add(cand);
            if (chooseNonAdjacent(s, pool, i + 1, picked, need)) {
                return true;
            }
            picked.remove(picked.size() - 1);
        }
        return false;
    }

    /**
     * ПРИМЫКАЮТ ЛИ ДВА ЗДАНИЯ на РАЗНЫХ гексах общей стенкой: A занимает сторону,
     * смотрящую на гекс B, а B — противоположную сторону той же грани.
     */
    private static boolean abutsAcrossWall(GameState s, BuildingToken a, BuildingToken b) {
        Hex ha = s.field.get(a.hexId);
        Hex hb = s.field.get(b.hexId);
        if (ha == null || hb == null) {
            return false;
        }
        for (int side = 0; side < 6; side++) {
            Integer owner = ha.sideOwner[side];
            if (owner == null || owner != a.uid) {
                continue;
            }
            if (!b.hexId.equals(ha.neighborBySide[side])) {
                continue;
            }
            Integer opp = hb.sideOwner[(side + 3) % 6];
            if (opp != null && opp == b.uid) {
                return true;
            }
        }
        return false;
    }

    /** Размер наибольшей связной компоненты в готовом графе смежности. */
    private static int largestComponentOf(Map<Integer, List<Integer>> g, Set<Integer> nodes) {
        Set<Integer> seen = new HashSet<>();
        int best = 0;
        for (Integer start : nodes) {
            if (seen.contains(start)) {
                continue;
            }
            int comp = 0;
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                Integer x = stack.pop();
                if (!seen.add(x)) {
                    continue;
                }
                comp++;
                for (Integer nb : g.getOrDefault(x, List.of())) {
                    if (!seen.contains(nb)) {
                        stack.push(nb);
                    }
                }
            }
            best = Math.max(best, comp);
        }
        return best;
    }

    // ---- геометрические помощники ------------------------------------------
    private static boolean isGrid(GameState s, String hexId) {
        return s.field.get(hexId).hasSpawnTile();
    }

    private static Set<String> ownBuildingHexes(GameState s, int seat) {
        Set<String> out = new HashSet<>();
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            out.add(b.hexId);
        }
        return out;
    }

    private static List<String> unitHexes(GameState s, int seat) {
        List<String> out = new ArrayList<>();
        for (UnitToken u : s.player(seat).unitsOnField()) {
            out.add(u.hexId);
        }
        return out;
    }

    private static List<Token> enemyTokensOn(GameState s, int seat, String hexId) {
        List<Token> out = new ArrayList<>();
        for (PlayerState pl : s.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (UnitToken u : pl.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    out.add(u);
                }
            }
            for (BuildingToken b : pl.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private static List<Token> enemyBuildingsOn(GameState s, int seat, String hexId) {
        List<Token> out = new ArrayList<>();
        for (PlayerState pl : s.players) {
            if (pl.seat == seat) {
                continue;
            }
            for (BuildingToken b : pl.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private static boolean adjacentToEnemy(GameState s, int seat, String hexId) {
        for (String nb : s.field.neighbors(hexId)) {
            if (!enemyTokensOn(s, seat, nb).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ПРИМЫКАНИЕ СТЕНКОЙ жетона {@code uidA} на гексе {@code hexA} к ЛЮБОМУ
     * ДРУГОМУ жетону. Два случая касания:
     * <ol>
     *   <li>МЕЖ-ГЕКСОВОЕ: A занимает сторону, смотрящую на соседний гекс, И
     *       сосед занял ПРОТИВОПОЛОЖНУЮ сторону (общая грань между гексами);
     *   <li>ВНУТРИ-ГЕКСОВОЕ: на ТОМ ЖЕ гексе другой жетон занимает СМЕЖНУЮ
     *       сторону (i±1). Здания на одном гексе касаются только если их ячейки
     *       соседние; если между ними свободная сторона — НЕ касаются.
     * </ol>
     */
    private static boolean shareWallWithAny(GameState s, kelium.core.Hex hexA, int uidA) {
        for (int side = 0; side < 6; side++) {
            Integer owner = hexA.sideOwner[side];
            if (owner == null || owner != uidA) {
                continue;
            }
            // (2) внутри-гексовое: смежные стороны заняты ДРУГИМ жетоном
            for (int d : new int[]{1, 5}) {          // side+1 и side-1 (mod 6)
                Integer adj = hexA.sideOwner[(side + d) % 6];
                if (adj != null && adj != uidA) {
                    return true;
                }
            }
            // (1) меж-гексовое: сосед занял противоположную сторону общей грани
            String nbId = hexA.neighborBySide[side];
            if (nbId == null) {
                continue;
            }
            kelium.core.Hex nb = s.field.get(nbId);
            if (nb == null) {
                continue;
            }
            if (nb.sideOwner[(side + 3) % 6] != null) {
                return true;
            }
        }
        return false;
    }

    private static int largestComponent(GameState s, Set<String> nodes, Set<String> allowed) {
        Set<String> seen = new HashSet<>();
        int best = 0;
        for (String start : nodes) {
            if (seen.contains(start)) {
                continue;
            }
            int comp = 0;
            Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String x = stack.pop();
                if (seen.contains(x)) {
                    continue;
                }
                seen.add(x);
                comp++;
                for (String nb : s.field.neighbors(x)) {
                    if (allowed.contains(nb) && !seen.contains(nb)) {
                        stack.push(nb);
                    }
                }
            }
            best = Math.max(best, comp);
        }
        return best;
    }

    /** G1: запрещённый гекс — не проходим и не бывает центром колец. */
    /** Проходим ли гекс — общая проверка движка (см. {@link Movement}). */
    private static boolean passable(GameState s, String hexId) {
        return Movement.passable(s, hexId);
    }

    /** Расстояние по полю — тем же поиском, которым ходят войска. */
    private static Integer bfsDist(GameState s, String from,
                                   java.util.Set<String> targets) {
        return Movement.distance(s, from, targets);
    }

    private static List<Token> allTokens(PlayerState pl) {
        List<Token> out = new ArrayList<>();
        out.addAll(pl.units);
        out.addAll(pl.buildings);
        return out;
    }

    private static int damage(Token t) {
        return t instanceof UnitToken u ? u.damage : ((BuildingToken) t).damage;
    }

    private static boolean alive(Token t) {
        return t instanceof UnitToken u ? u.alive() : ((BuildingToken) t).alive();
    }
}
