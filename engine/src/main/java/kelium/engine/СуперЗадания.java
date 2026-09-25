package kelium.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.dataio.Ctx;

/**
 * СУПЕР-ЗАДАНИЯ — ТОЛЬКО ПОБЕДНЫЕ ОЧКИ (правило дизайнера 14.09.2026).
 *
 * <p>Карта раздаётся по одной втайне на подготовке и ещё приходит за третью
 * ступень трека технологий. Её НЕ выполняют, не сжигают и не сбрасывают: она
 * лежит в руке до конца партии и в подсчёте очков даёт очки за свои разделы.
 *
 * <p>НАБОР 9.0.0 (шаблоны дизайнера 25.09.2026): на карте два раздела с
 * флажком финала. Верхний — «Награда: по ★», лёгкое условие, 1 ПО за каждую
 * единицу счёта; нижний — «Награда: по ★★», трудное, 2 ПО за каждую. Число
 * звёзд раздела лежит в данных полем {@code vp}; у набора 8.0.0 его нет, и
 * каждая категория там платит 1 ПО.
 *
 * <p>ЧТО УДАЛЕНО ВМЕСТЕ С РЕДАКЦИЕЙ 7.0: множитель верха, жёсткое требование
 * низа с разовой наградой, СПЕЦ-действие {@code spec_super6_claim}, режимы
 * {@code solo5}/{@code solo6}, раздача «две на выбор» и вкладка супер-заданий
 * в проигрывателе. Базовые источники очков (монеты, келемий, трофеи, здания и
 * войска на поле) из общих правил вынесены в эти самые категории.
 *
 * <p>Категории и их пары живут в данных ({@code data/cards/super_objectives.*}),
 * здесь — только счёт.
 */
public final class СуперЗадания {

    private СуперЗадания() {
    }

    /**
     * РАЗДАТЬ ПО ОДНОЙ КАРТЕ ВТАЙНЕ. Карта ложится игроку в руку и остаётся там
     * до конца партии; соперники её не видят.
     */
    public static void deal(GameState s, List<String> ids, Random rng) {
        List<String> колода = new ArrayList<>(ids);
        Collections.shuffle(колода, rng);
        int at = 0;
        for (PlayerState p : s.players) {
            if (at >= колода.size()) {
                return;               // карт не хватило — играем без них
            }
            p.superObjectives.add(колода.get(at++));
        }
    }

    /**
     * ОЧКИ ЗА ВСЕ СУПЕР-ЗАДАНИЯ ИГРОКА. Карт может быть несколько (одна с
     * подготовки, ещё по одной за третьи ступени треков), и каждая платит за
     * обе свои категории.
     */
    public static int vp(GameState s, int seat) {
        PlayerState p = s.player(seat);
        int всего = 0;
        for (String id : p.superObjectives) {
            всего += очкиКарты(s, p, id);
        }
        return всего;
    }

    /**
     * ОЧКИ ОДНОЙ КАРТЫ: каждая её категория платит столько очков за единицу
     * счёта, сколько звёзд на плашке раздела («Награда: по ★» — 1,
     * «Награда: по ★★» — 2; набор 9.0.0, поле {@code vp}). У карт без поля
     * {@code vp} (набор 8.0.0) каждая категория платит 1.
     */
    public static int очкиКарты(GameState s, PlayerState p, String id) {
        List<String> категории = категорииКарты(s, id);
        List<Integer> веса = весаКарты(s, id);
        int всего = 0;
        for (int i = 0; i < категории.size(); i++) {
            всего += веса.get(i) * очкиКатегории(s, p, категории.get(i));
        }
        return всего;
    }

    /**
     * ЗВЁЗДЫ НА ПЛАШКАХ РАЗДЕЛОВ КАРТЫ — по одной записи на категорию, в том же
     * порядке. Поля {@code vp} нет — все единицы.
     */
    public static List<Integer> весаКарты(GameState s, String id) {
        int n = категорииКарты(s, id).size();
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(id);
        List<Integer> out = new ArrayList<>();
        Object vp = card == null ? null : card.get("vp");
        for (int i = 0; i < n; i++) {
            int w = 1;
            if (vp instanceof List<?> list && i < list.size()
                    && list.get(i) instanceof Number num) {
                w = num.intValue();
            }
            out.add(w);
        }
        return out;
    }

    /** Пара категорий карты; пусто — карты нет или она старой редакции. */
    @SuppressWarnings("unchecked")
    public static List<String> категорииКарты(GameState s, String id) {
        Map<String, Object> card = Ctx.cards(s, "super_objectives").find(id);
        if (card == null || !(card.get("categories") instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /** ВСЕ КАТЕГОРИИ СЧЁТА — единственный список, по которому платит игра. */
    public static final List<String> КАТЕГОРИИ = List.of(
        // набор 8.0.0 (остаётся читаемым для записей и сохранений прежних партий)
        "buildings_3", "units_4", "kelium_4", "trophy_tokens_4",
        "tech_steps_4", "red_modules", "blue_modules", "units_at_enemy",
        "coins_4", "ammo_kelium_pairs", "plants_off_cell", "miners_on_container",
        // набор 9.0.0: лёгкие разделы «по ★»
        "units_2", "unit_kinds", "modules_on_board", "tech_cubes", "storage_cubes_2",
        "arsenal_installed", "coins_2", "powered_buildings", "miners_at_spawn",
        "hexes_2", "military_3",
        // набор 9.0.0: трудные разделы «по ★★»
        "buildings_at_enemy", "full_unit_sets", "gold_modules", "top_steps",
        "resource_sets", "super_arsenal_cards", "kelium", "powered_two_cell",
        "spawn_double", "track_leader", "dump_tokens");

    /**
     * Знает ли движок эту категорию. Нужно сторожу каталога: неизвестная
     * категория молча платила бы ноль и выглядела рабочей.
     */
    public static boolean знаетКатегорию(String категория) {
        return КАТЕГОРИИ.contains(категория);
    }

    /**
     * СКОЛЬКО ЕДИНИЦ СЧЁТА У ОДНОЙ КАТЕГОРИИ (до умножения на звёзды раздела).
     * Считается по состоянию на конец партии: все категории смотрят на то, что
     * лежит на поле, на планшетах и в хранилище, и ни одна не зависит от
     * истории партии.
     */
    public static int очкиКатегории(GameState s, PlayerState p, String категория) {
        return switch (категория) {
            case "buildings_3" -> p.buildingsOnField().size() / 3;
            case "units_4" -> p.unitsOnField().size() / 4;
            case "kelium_4" -> Math.min(4, p.resources.kelium());
            case "trophy_tokens_4" -> Math.min(4, p.destroyedTokens.size());
            case "tech_steps_4" -> Math.min(4, занятыеСтупени(p));
            case "red_modules" -> жетоновНаПланшете(p.redPlacements.values());
            case "blue_modules" -> жетоновНаПланшете(p.bluePlacements.values());
            case "units_at_enemy" -> войскаУЧужихЗданий(s, p);
            case "coins_4" -> p.resources.coin() / 4;
            case "ammo_kelium_pairs" -> Math.min(p.resources.ammo(), p.resources.kelium());
            case "plants_off_cell" -> энергостанцииВнеКруга(s, p);
            case "miners_on_container" -> добытчикиНаКонтейнере(s, p);
            // ---- набор 9.0.0, лёгкие ----
            case "units_2" -> p.unitsOnField().size() / 2;
            case "unit_kinds" -> родовНаПоле(p).size();
            case "modules_on_board" -> жетоновНаПланшете(p.redPlacements.values())
                + жетоновНаПланшете(p.bluePlacements.values());
            case "tech_cubes" -> кубиковНаТреках(s, p, 1);
            case "storage_cubes_2" -> (p.resources.kelium() + p.resources.ammo()
                + p.resources.trophy()) / 2;
            case "arsenal_installed" -> p.allInstalledArsenal().size();
            case "coins_2" -> p.resources.coin() / 2;
            case "powered_buildings" -> запитанные(p, false);
            case "miners_at_spawn" -> добытчикиУТайла(s, p);
            case "hexes_2" -> гексовСоСвоими(p) / 2;
            case "military_3" -> казармЗаводовАвиабаз(p);
            // ---- набор 9.0.0, трудные ----
            case "buildings_at_enemy" -> зданияУЧужих(s, p);
            case "full_unit_sets" -> полныхНаборовВойск(p);
            case "gold_modules" -> p.goldModules;
            case "top_steps" -> кубиковНаТреках(s, p, 3);
            case "resource_sets" -> Math.min(p.resources.kelium(),
                Math.min(p.resources.ammo(), p.resources.trophy()));
            case "super_arsenal_cards" -> p.superArsenalCards.size();
            case "powered_two_cell" -> запитанные(p, true);
            case "kelium" -> p.resources.kelium();
            case "track_leader" -> трековВпереди(s, p);
            case "dump_tokens" -> p.destroyedTokens.size();
            case "spawn_double" -> тайловСДвумяДобытчиками(s, p);
            default -> 0;
        };
    }

    /**
     * ЗАПИТАННЫЕ ЗДАНИЯ — только те, на которых НАРИСОВАНЫ ячейки энергии
     * (потребители, глава 6): у энергостанции ячеек нет, запитанной она не
     * бывает. {@code дваЯчейки} — только здания ровно с двумя ячейками.
     */
    private static int запитанные(PlayerState p, boolean дваЯчейки) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.energySlots <= 0 || (дваЯчейки && b.energySlots != 2)) {
                continue;
            }
            if (b.powered()) {
                n++;
            }
        }
        return n;
    }

    /**
     * КУБИКИ ИГРОКА НА ТРЕКАХ, начиная со ступени {@code сСтупени} (1 — все).
     * Считаются сами кубики на планшете науки, а не «дошёл до ступени»:
     * перепрыгнутая ступень кубика не имеет.
     */
    private static int кубиковНаТреках(GameState s, PlayerState p, int сСтупени) {
        int n = 0;
        if (s.tech == null) {
            return 0;
        }
        for (List<List<Integer>> поСтупеням : s.tech.occupancy.values()) {
            for (int step = Math.max(1, сСтупени); step <= поСтупеням.size(); step++) {
                for (Integer seat : поСтупеням.get(step - 1)) {
                    if (seat != null && seat == p.seat) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Самая высокая ступень трека, где лежит кубик игрока (0 — кубиков нет). */
    private static int верхнийКубик(List<List<Integer>> поСтупеням, int seat) {
        for (int step = поСтупеням.size(); step >= 1; step--) {
            if (поСтупеням.get(step - 1).contains(seat)) {
                return step;
            }
        }
        return 0;
    }

    /**
     * ТРЕКИ, ГДЕ ИГРОК ВПЕРЕДИ ВСЕХ: его верхний кубик стоит на ступени выше
     * верхнего кубика каждого соперника. Поровну — не впереди.
     */
    private static int трековВпереди(GameState s, PlayerState p) {
        if (s.tech == null) {
            return 0;
        }
        int n = 0;
        for (List<List<Integer>> поСтупеням : s.tech.occupancy.values()) {
            int мой = верхнийКубик(поСтупеням, p.seat);
            if (мой == 0) {
                continue;
            }
            boolean впереди = true;
            for (PlayerState other : s.players) {
                if (other.seat != p.seat && верхнийКубик(поСтупеням, other.seat) >= мой) {
                    впереди = false;
                }
            }
            if (впереди) {
                n++;
            }
        }
        return n;
    }

    private static Set<kelium.core.UnitType> родовНаПоле(PlayerState p) {
        Set<kelium.core.UnitType> роды = java.util.EnumSet.noneOf(kelium.core.UnitType.class);
        for (UnitToken u : p.unitsOnField()) {
            роды.add(u.type);
        }
        return роды;
    }

    /**
     * ПОЛНЫЕ НАБОРЫ ВОЙСК: пехота, техника, авиация и вышка — по одной каждого
     * рода. Наборов столько, сколько жетонов самого редкого из четырёх родов.
     */
    private static int полныхНаборовВойск(PlayerState p) {
        int min = Integer.MAX_VALUE;
        for (kelium.core.UnitType t : List.of(kelium.core.UnitType.INFANTRY,
                kelium.core.UnitType.VEHICLE, kelium.core.UnitType.AIRCRAFT,
                kelium.core.UnitType.TOWER)) {
            int n = 0;
            for (UnitToken u : p.unitsOnField()) {
                if (u.type == t) {
                    n++;
                }
            }
            min = Math.min(min, n);
        }
        return min;
    }

    /** Гексы, где стоит хоть один свой жетон — войско или здание. */
    private static int гексовСоСвоими(PlayerState p) {
        Set<String> гексы = new HashSet<>();
        for (UnitToken u : p.unitsOnField()) {
            if (u.hexId != null) {
                гексы.add(u.hexId);
            }
        }
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.hexId != null) {
                гексы.add(b.hexId);
            }
        }
        return гексы.size();
    }

    /** Свои казармы, заводы и авиабазы на поле (без ЦУ). */
    private static int казармЗаводовАвиабаз(PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE) {
                n++;
            }
        }
        return n;
    }

    /** Свои здания на гексах, где стоит здание другого игрока. */
    private static int зданияУЧужих(GameState s, PlayerState p) {
        Set<String> чужиеГексы = new HashSet<>();
        for (PlayerState other : s.players) {
            if (other.seat == p.seat) {
                continue;
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (b.hexId != null) {
                    чужиеГексы.add(b.hexId);
                }
            }
        }
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.hexId != null && чужиеГексы.contains(b.hexId)) {
                n++;
            }
        }
        return n;
    }

    /** Гексы с тайлом зарождения прямо за стенкой добытчика (для прочих — пусто). */
    private static Set<String> тайлыЗаСтенкой(GameState s, BuildingToken b) {
        Set<String> out = new HashSet<>();
        if (b.type != BuildingType.MINER || b.hexId == null) {
            return out;
        }
        Hex h = s.field.get(b.hexId);
        if (h == null) {
            return out;
        }
        for (int side = 0; side < 6; side++) {
            if (h.sideOwner[side] == null || h.sideOwner[side] != b.uid
                    || h.neighborBySide[side] == null) {
                continue;
            }
            Hex nb = s.field.get(h.neighborBySide[side]);
            if (nb != null && nb.spawnTile != null) {
                out.add(nb.id);
            }
        }
        return out;
    }

    /** Тайлы зарождения, к которым обращены стенкой два и больше своих добытчика. */
    private static int тайловСДвумяДобытчиками(GameState s, PlayerState p) {
        Map<String, Integer> сколько = new java.util.HashMap<>();
        for (BuildingToken b : p.buildingsOnField()) {
            for (String hex : тайлыЗаСтенкой(s, b)) {
                сколько.merge(hex, 1, Integer::sum);
            }
        }
        int n = 0;
        for (int v : сколько.values()) {
            if (v >= 2) {
                n++;
            }
        }
        return n;
    }

    /**
     * ДОБЫТЧИКИ У ТАЙЛА ЗАРОЖДЕНИЯ: тайл лежит на соседнем гексе прямо за
     * стенкой добытчика — ровно то место, откуда добытчик берёт келемий.
     */
    private static int добытчикиУТайла(GameState s, PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (!тайлыЗаСтенкой(s, b).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сколько жетонов модуля лежит на планшете. Жетонов модулей в запасе не
     * бывает (правило дизайнера 14.09.2026): вытянутый жетон сразу ложится на
     * ячейку, поэтому считать больше нечего. Глухой жетон уничтожения ЦУ —
     * не модуль и в счёт не идёт.
     */
    private static int жетоновНаПланшете(java.util.Collection<Map<String, Object>> раскладка) {
        int n = 0;
        for (Map<String, Object> pl : раскладка) {
            if (!Boolean.TRUE.equals(pl.get("blocks"))) {
                n++;
            }
        }
        return n;
    }

    /**
     * СКОЛЬКО РАЗНЫХ СТУПЕНЕЙ занято кубиками игрока. Кубик остаётся на ступени
     * навсегда, поэтому игрок, дошедший на треке до третьей ступени, занимает
     * ступени 1, 2 и 3; разные треки на одной ступени считаются за одну.
     */
    private static int занятыеСтупени(PlayerState p) {
        Set<Integer> ступени = new HashSet<>();
        for (Integer дошёл : p.techSteps.values()) {
            for (int step = 1; step <= (дошёл == null ? 0 : дошёл); step++) {
                ступени.add(step);
            }
        }
        return ступени.size();
    }

    /** Свои войска, стоящие на гексе, где есть здание другого игрока. */
    private static int войскаУЧужихЗданий(GameState s, PlayerState p) {
        Set<String> чужиеГексы = new HashSet<>();
        for (PlayerState other : s.players) {
            if (other.seat == p.seat) {
                continue;
            }
            for (BuildingToken b : other.buildingsOnField()) {
                if (b.hexId != null) {
                    чужиеГексы.add(b.hexId);
                }
            }
        }
        int n = 0;
        for (UnitToken u : p.unitsOnField()) {
            if (u.hexId != null && чужиеГексы.contains(u.hexId)) {
                n++;
            }
        }
        return n;
    }

    /** Свои энергостанции, стоящие НЕ на круге энергии своего гекса. */
    private static int энергостанцииВнеКруга(GameState s, PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.POWER_PLANT && !Power.onEnergyCell(s, b)) {
                n++;
            }
        }
        return n;
    }

    /** Свои добытчики, занявшие ячейку печатного контейнера своего гекса. */
    private static int добытчикиНаКонтейнере(GameState s, PlayerState p) {
        int n = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type != BuildingType.MINER || b.hexId == null) {
                continue;
            }
            Hex h = s.field.get(b.hexId);
            // Ячейка контейнера бывает не размечена (−1) и бывает НЕ НАЗЕМНОЙ
            // (6 — небо): наземных сторон у гекса шесть, и только они имеют
            // владельца.
            if (h == null || h.containerCell < 0 || h.containerCell >= h.sideOwner.length) {
                continue;
            }
            Integer owner = h.sideOwner[h.containerCell];
            if (owner != null && owner == b.uid) {
                n++;
            }
        }
        return n;
    }
}
