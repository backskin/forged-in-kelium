package kelium.gui.replay2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * УСЛОВИЯ И НАГРАДЫ КАРТ — ПО-РУССКИ, игровыми словами.
 *
 * <p>ЗАЧЕМ. В данных требование карты записано идентификатором предиката и его
 * числами: {@code moved_buildings_this_turn · count 2}. Это запись для движка, и
 * показывать её человеку нельзя — за столом никто не читает по-английски и не
 * знает, что такое предикат. Здесь та же запись превращается в фразу, которую
 * можно прочитать вслух: «в этот ход перенеси два своих здания на другие гексы».
 *
 * <p>СЛОВАРЬ СТРОГИЙ. Термины берутся ровно те, что в правилах: ЦУ, гекс, сектор,
 * добытчик, энергостанция, казарма, завод, авиабаза, вышка, пехота, техника,
 * авиация, келемий, боеприпас, трофей, монета, трофей, приказ, СПЕЦ, Снаряжение,
 * Стройка, Добыча, Смена энергии, Движение, Бой, Наука, Рынок, тайл зарождения,
 * нейтрал, контейнер, арсенал, модуль, трек. Никаких «юнитов» и «тайлов ресурса».
 *
 * <p>ПАРАМЕТРЫ ЧИТАЮТСЯ ТЕ, ЧТО ДЕЙСТВИТЕЛЬНО ЛЕЖАТ В ДАННЫХ. Первая версия этого
 * словаря брала у всех предикатов {@code count}, а в данных у них {@code enemy},
 * {@code hexes}, {@code units}, {@code min_buildings}, {@code step},
 * {@code tracks}, {@code anchors}. Последствие было заметно сразу: у двадцати пяти
 * карт из сорока УСИЛЕННОЕ требование дословно повторяло обычное, хотя в данных
 * они разные. Имена параметров сверены по {@code Predicates} и {@code Shapes} —
 * по тому, что код читает, а не по догадке.
 *
 * <p>Предикат, которого здесь нет, показывается своим идентификатором. Это
 * НАРОЧНО видно: непереведённое условие должно бросаться в глаза, а не молча
 * притворяться пустым.
 */
public final class CardWords {

    private CardWords() {
    }

    // ==================================================================
    //  ЧТЕНИЕ ПАРАМЕТРОВ
    // ==================================================================

    private static int ч(Map<String, Object> p, String ключ, int иначе) {
        Object v = p == null ? null : p.get(ключ);
        return v instanceof Number n ? n.intValue() : иначе;
    }

    private static boolean да(Map<String, Object> p, String ключ) {
        return p != null && Boolean.TRUE.equals(p.get(ключ));
    }

    private static String строка(Map<String, Object> p, String ключ, String иначе) {
        Object v = p == null ? null : p.get(ключ);
        return v == null || String.valueOf(v).isBlank() ? иначе : String.valueOf(v);
    }

    private static List<String> список(Map<String, Object> p, String ключ) {
        List<String> out = new ArrayList<>();
        if (p != null && p.get(ключ) instanceof List<?> l) {
            for (Object o : l) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    // ==================================================================
    //  СЧЁТ И СЛОВА
    // ==================================================================

    /** «2 здания» / «1 здание» / «5 зданий». */
    private static String счёт(int n, String один, String два, String много) {
        int сто = n % 100;
        int дес = n % 10;
        String слово = сто >= 11 && сто <= 14 ? много
            : дес == 1 ? один
            : дес >= 2 && дес <= 4 ? два : много;
        return n + " " + слово;
    }

    private static String зданий(int n) {
        return счёт(n, "здание", "здания", "зданий");
    }

    private static String жетонов(int n) {
        return счёт(n, "жетон", "жетона", "жетонов");
    }

    private static String гексов(int n) {
        return счёт(n, "гекс", "гекса", "гексов");
    }

    private static String войск(int n) {
        return счёт(n, "войско", "войска", "войск");
    }

    private static String вышек(int n) {
        return счёт(n, "вышку", "вышки", "вышек");
    }

    private static String карт(int n) {
        return счёт(n, "карту", "карты", "карт");
    }

    private static String треков(int n) {
        return счёт(n, "треке", "треках", "треках");
    }

    private static String добытчиков(int n) {
        return счёт(n, "добытчик", "добытчика", "добытчиков");
    }

    private static String контейнеров(int n) {
        return счёт(n, "контейнер", "контейнера", "контейнеров");
    }

    private static String предложений(int n) {
        return счёт(n, "предложение", "предложения", "предложений");
    }

    private static String трофеев(int n) {
        return счёт(n, "уничтоженный жетон", "трофейных жетона", "уничтоженных жетонов");
    }

    private static String источников(int n) {
        return счёт(n, "источника", "источников", "источников");
    }

    /** Название рода войск в единственном числе. */
    private static String род(String код) {
        return switch (код == null ? "" : код) {
            case "infantry", "INFANTRY", "inf" -> "пехотой";
            case "vehicle", "VEHICLE", "veh" -> "техникой";
            case "aircraft", "AIRCRAFT", "air" -> "авиацией";
            case "tower", "TOWER", "twr" -> "вышкой";
            default -> код;
        };
    }

    /** Названия родов через запятую в именительном падеже. */
    private static String родыСписком(List<String> коды) {
        List<String> из = new ArrayList<>();
        for (String к : коды) {
            из.add(switch (к) {
                case "infantry" -> "пехота";
                case "vehicle" -> "техника";
                case "aircraft" -> "авиация";
                case "tower" -> "вышка";
                default -> к;
            });
        }
        return String.join(", ", из);
    }

    /** Название типа здания в именительном падеже. */
    private static String здание(String код) {
        return switch (код == null ? "" : код) {
            case "miner" -> "добытчик";
            case "power_plant", "plant" -> "энергостанция";
            case "barracks" -> "казарма";
            case "factory" -> "завод";
            case "airbase" -> "авиабаза";
            case "command_center" -> "ЦУ";
            default -> код;
        };
    }

    private static String зданияСписком(List<String> коды) {
        List<String> из = new ArrayList<>();
        for (String к : коды) {
            из.add(здание(к));
        }
        return String.join(", ", из);
    }

    /** Название трека по внутреннему имени. */
    private static String трек(String id) {
        return switch (id == null ? "" : id) {
            case "left", "red" -> "красному";
            case "middle", "green" -> "зелёному";
            case "right", "blue" -> "синему";
            default -> "любому";
        };
    }

    /** Ресурс по-русски в нужном числе: «3 боеприпаса». */
    private static String ресурс(String ключ, int n) {
        return switch (ключ == null ? "" : ключ) {
            case "coin", "coins" -> счёт(n, "монету", "монеты", "монет");
            case "ammo" -> счёт(n, "боеприпас", "боеприпаса", "боеприпасов");
            case "kelium" -> счёт(n, "келемий", "келемия", "келемиев");
            case "trophy" -> счёт(n, "трофей", "трофея", "трофеев");
            case "containers" -> контейнеров(n);
            case "objective_card", "objective_cards" ->
                счёт(n, "карта задания", "карты задания", "карт заданий");
            case "arsenal" -> счёт(n, "карта арсенала", "карты арсенала", "карт арсенала");
            case "vp" -> счёт(n, "победное очко", "победных очка", "победных очков");
            case "energy" -> счёт(n, "кубик энергии", "кубика энергии", "кубиков энергии");
            case "module", "module_half" -> "жетон модуля";
            case "gild_module" -> "позолоту модуля";
            case "storage_token" -> "жетон модуля хранилища";
            // ЖЕТОНЫ, КОТОРЫМИ ПЛАТЯТ — ячейки супер-заданий и жертвы заданий.
            case "enemy_unit_token" -> счёт(n, "чужой жетон войск из своих трофеев",
                "чужих жетона войск из своих трофеев", "чужих жетонов войск из своих трофеев");
            case "enemy_building_token" -> счёт(n, "чужое здание из своих трофеев",
                "чужих здания из своих трофеев", "чужих зданий из своих трофеев");
            case "enemy_token" -> счёт(n, "чужой жетон из своих трофеев",
                "чужих жетона из своих трофеев", "чужих жетонов из своих трофеев");
            case "own_building" -> счёт(n, "своё здание с поля",
                "своих здания с поля", "своих зданий с поля");
            case "own_miner" -> счёт(n, "свой добытчик с поля",
                "своих добытчика с поля", "своих добытчиков с поля");
            case "own_power_plant" -> счёт(n, "свою энергостанцию с поля",
                "своих энергостанции с поля", "своих энергостанций с поля");
            case "own_unit" -> счёт(n, "своё войско с поля",
                "своих войска с поля", "своих войск с поля");
            case "container", "unopened_container" -> счёт(n, "свой невскрытый контейнер",
                "своих невскрытых контейнера", "своих невскрытых контейнеров");
            case "trophies" -> счёт(n, "уничтоженный жетон со своего места уничтоженных жетонов",
                "трофейных жетона со своего места уничтоженных жетонов",
                "уничтоженных жетонов со своего места уничтоженных жетонов");
            case "units_off_base" -> счёт(n,
                "своё войско с гекса, где нет твоих зданий",
                "своих войска с разных гексов, где нет твоих зданий",
                "своих войск с разных гексов, где нет твоих зданий");
            default -> n + " " + ключ;
        };
    }

    // ==================================================================
    //  ТРЕБОВАНИЕ
    // ==================================================================

    /**
     * ТРЕБОВАНИЕ КАРТЫ ЖИВЫМИ СЛОВАМИ.
     *
     * @param предикат идентификатор из данных
     * @param п        его параметры (может быть {@code null})
     */
    public static String условие(String предикат, Map<String, Object> п) {
        if (предикат == null || предикат.isBlank()) {
            return "";
        }
        return switch (предикат) {
            // ---- рисунки: связь жетонов ----
            case "chain_connects" -> связь(п);
            case "buildings_wall_chain" -> "выстрой "
                + зданий(ч(п, "count", 2)) + (да(п, "military_only")
                    ? " из числа казарм, заводов и авиабаз" : "")
                + " в непрерывную цепь по соседним гексам";
            case "buildings_ring_around_hex" -> "поставь свои здания на "
                + гексов(ч(п, "count", 2)) + ", соседних с одним и тем же гексом"
                + (да(п, "enemy_building_on_center") ? ", где стоит здание противника"
                    : да(п, "enemy_on_center") ? ", где есть жетоны противника" : "");

            // ---- жертвы: плата вносится в момент розыгрыша ----
            case "sacrifice_paid" -> "сдай плату, названную на карте; действий это не требует";
            case "sacrifice_enhanced" -> "сдай " + запас(п);

            // ---- стройка и здания ----
            case "buildings_on_field_count" -> "имей на поле " + зданий(ч(п, "count", 1))
                + видыЗданий(п);
            case "built_bordering_enemy" -> "построй здание на гексе, соседнем с гексом, где "
                + ("building".equals(строка(п, "enemy", "units"))
                    ? "стоит здание противника" : "стоят войска противника");
            case "built_on_hex_with_enemy_units" -> "построй здание на гексе, где стоит "
                + не_меньше(ч(п, "units", 1)) + " " + войск(ч(п, "units", 1))
                + " противника";
            case "build_ops_on_nonadjacent_hexes" -> "в этот ход построй на "
                + гексов(ч(п, "count", 2)) + ", ни один из которых не соседствует с другим";
            case "moved_buildings_this_turn" -> "в этот ход перенеси "
                + зданий(ч(п, "count", 1)) + " на другие гексы"
                + (да(п, "include_cu") ? ", и одно из них — твоё ЦУ" : "");
            case "towers_off_cu_hexes" -> "имей на поле " + вышек(ч(п, "count", 1))
                + " на разных гексах, и ни одна не стоит на гексе с твоим ЦУ"
                + (да(п, "on_enemy_hex")
                    ? "; хотя бы одна — на гексе, где есть жетоны противника" : "");
            case "cu_placed_near_enemy_cu" -> "перенеси своё ЦУ на гекс не дальше "
                + гексов(ч(п, "distance", 2)) + " от чужого ЦУ"
                + (да(п, "adjacent_enemy_building")
                    ? " и по соседству с чужим зданием" : "");
            case "powered_building_off_cu_hex" ->
                "имей запитанное здание на гексе, где нет твоего ЦУ";

            // ---- энергия ----
            case "no_unpowered_buildings" -> "имей на поле "
                + не_меньше(ч(п, "min_buildings", 1)) + " " + зданий(ч(п, "min_buildings", 1))
                + ", и ни одно из твоих зданий не осталось без энергии";
            case "idle_cube_on_each_source" -> "оставь по простаивающему кубику энергии "
                + "на каждом из " + не_меньше(ч(п, "sources", 2)) + " своих "
                + источников(ч(п, "sources", 2));

            // ---- добыча и келемий ----
            case "powered_miners_count" -> "имей " + добытчиков(ч(п, "count", 1))
                + " запитанными";
            case "powered_miners_distinct_spawns" -> "имей " + добытчиков(ч(п, "count", 2))
                + " запитанными у РАЗНЫХ тайлов зарождения"
                + (да(п, "nonstart_all") ? ", и ни один из этих тайлов не стартовый"
                    : да(п, "nonstart") ? ", и хотя бы один тайл не стартовый" : "");
            case "last_kelium_nonstart" -> "забери ПОСЛЕДНИЙ келемий с тайла зарождения, "
                + "который не является стартовым"
                + (да(п, "claimed") ? ", и этот тайл уже был занят тобой" : "");
            case "miner_took_container" -> "возьми печатный контейнер добытчиком"
                + уровниДобытчика(п)
                + (да(п, "no_kelium") ? ", не взяв в этот ход ни одного келемия" : "");
            case "picked_container_by_unit" -> "возьми "
                + контейнеров(ч(п, "count", 1)) + " войском";
            case "has_unopened_container" -> "держи "
                + контейнеров(ч(п, "count", 1)) + " невскрытыми";

            // ---- войска и движение ----
            case "hired_distinct_kinds" -> "в этот ход найми войска "
                + ч(п, "count", 2) + " разных родов" + родыОграничения(п);
            case "units_off_own_hexes" -> "имей " + войск(ч(п, "count", 1))
                + " на гексах, где нет твоих зданий"
                + (да(п, "distinct_hexes") ? ", и все они на разных гексах" : "");
            case "unit_at_distance_from_cu" -> "отведи войско на "
                + гексов(ч(п, "distance", 1)) + " или дальше от своего ЦУ";
            case "hidden_unit_near_enemy" -> "держи " + войск(ч(п, "count", 1))
                + " в гарнизоне своего военного здания того же рода, стоящего на гексе, "
                + "соседнем с гексом, где есть жетоны противника";
            case "unit_on_hex_with_enemy_units" -> "заведи " + войск(ч(п, "count", 1))
                + " на гекс, где стоят войска противника";
            case "units_bordering_enemy_units_hex" -> "поставь свои войска на "
                + гексов(ч(п, "hexes", 2))
                + ", соседних с одним и тем же гексом, где стоят войска противника";
            case "aircraft_on_enemy_hex" -> "заведи свою авиацию в сектор Неба на гексе, "
                + "где есть жетоны противника"
                + (да(п, "cu_on_hex") ? ", и на этом гексе стоит его ЦУ" : "")
                + (да(п, "enemy_aircraft_damaged")
                    ? ", нанеся урон его авиации" : "");

            // ---- бой ----
            case "destroyed_enemy_this_turn" -> "в этот ход уничтожь "
                + жетонов(ч(п, "count", 1)) + " противника" + видыЖертв(п)
                + (ч(п, "min_hp", 0) > 1
                    ? " прочностью не меньше " + ч(п, "min_hp", 0) : "")
                + (да(п, "lost_none") ? ", не потеряв ни одного своего жетона" : "");
            case "kills_by_one_unit" -> "уничтожь " + жетонов(ч(п, "count", 2))
                + " ОДНИМ своим войском"
                + (п != null && п.get("unit_type") != null
                    ? ", и это " + род(строка(п, "unit_type", "")) : "");
            case "damaged_distinct_no_kills" -> "в этот ход нанеси урон "
                + жетонов(ч(п, "count", 2)) + " противника, никого не уничтожив";
            case "damaged_distinct_enemy_buildings" -> "в этот ход нанеси урон "
                + зданий(ч(п, "count", 2)) + " противника";
            case "destroyed_in_retaliation" -> "в этот ход уничтожь "
                + жетонов(ч(п, "count", 1)) + " противника ОТВЕТНЫМ боем"
                + (да(п, "no_losses") ? ", не потеряв в этом бою ни одного своего жетона" : "");
            // ТОЛЬКО ДОБЫТЧИК, и это решение дизайнера: «запитанная энергостанция» —
            // не состояние игры, станция энергию производит, а не потребляет.
            case "destroyed_enemy_economy" -> "в этот ход уничтожь добытчик противника"
                + (да(п, "powered") ? ", и он был запитан" : "");
            // ПО ЗДАНИЯМ, А НЕ ПО ОЧКАМ: карта переписана ровно за этим — очки в
            // середине партии никто не знает, а здания на поле пересчитываются.
            case "destroyed_stronger_player_token" -> "в этот ход уничтожь жетон игрока, "
                + "у которого на поле больше зданий, чем у тебя"
                + (да(п, "building") ? ", и этот жетон был зданием" : "");

            // ---- трофеи ----
            case "destroyed_contains" -> трофейноеМесто(п);
            case "destroyed_distinct_kinds" -> "имей на месте уничтоженных жетонов жетоны "
                + ч(п, "count", 2) + " разных видов";

            // ---- наука ----
            case "tech_step_reached" -> "дойди до " + ч(п, "step", 2) + "-го шага на "
                + треков(ч(п, "tracks", 1)) + " " + (ч(п, "tracks", 1) == 1
                    ? "любом треке технологий" : "разных треках технологий");
            case "tracks_occupied" -> "займи место не ниже " + ч(п, "min_step", 1)
                + "-го шага на " + треков(ч(п, "tracks", 2)) + " разных треках технологий";
            case "science_trophies_spent" -> "в этот ход сдай в Науку "
                + трофеев(ч(п, "count", 2))
                + (да(п, "same_track") ? ", шагая по одному и тому же треку" : "");
            case "science_offers_used" -> "в этот ход возьми "
                + предложений(ч(п, "count", 2)) + " планшета технологий";

            // ---- рынок ----
            case "market_offers_used" -> "в этот ход возьми "
                + предложений(ч(п, "count", 2)) + " рынка";
            case "used_market_card_offer" -> "возьми предложение с карты рынка"
                + (да(п, "and_printed")
                    ? " и в тот же ход воспользуйся печатным обменом планшета" : "");

            // ---- приказы ----
            case "assembly_all_chose_ammo" -> "проведи Снаряжение, в котором ВСЕ твои "
                + "запитанные здания выбрали боеприпасы, и таких зданий было "
                + не_меньше(ч(п, "count", 2)) + " " + зданий(ч(п, "count", 2))
                + видыСнаряжения(п);
            case "lower_order_open_this_turn" -> "сыграй карту приказа, НИЖНИЙ приказ "
                + "которой в этот ход вскрыт другим игроком";
            case "order_coincided_this_turn" -> "сыграй ВЕРХНИМ тот же приказ, "
                + "что и другой игрок в этот ход";

            // ---- запасы ----
            case "resource_at_least" -> "имей в хранилище " + не_меньше(ч(п, "amount", 1))
                + " " + запас(п);
            case "resources_at_most" -> "не имей в хранилище больше чем " + запас(п);
            case "arsenal_cards_held" -> "держи " + карт(ч(п, "count", 1)) + " арсенала"
                + (да(п, "all_face_down") ? " неустановленными" : "");

            default -> предикат + (п == null || п.isEmpty() ? "" : " " + п);
        };
    }

    /** «не меньше двух» — словом, чтобы число не читалось как «ровно». */
    private static String не_меньше(int n) {
        return n <= 1 ? "хотя бы" : "не меньше чем";
    }

    private static String видыЗданий(Map<String, Object> п) {
        List<String> т = список(п, "types");
        return т.isEmpty() ? "" : " из числа: " + зданияСписком(т);
    }

    private static String видыСнаряжения(Map<String, Object> п) {
        List<String> т = список(п, "include_types");
        return т.isEmpty() ? "" : ", и среди них были " + зданияСписком(т);
    }

    private static String видыЖертв(Map<String, Object> п) {
        List<String> т = список(п, "victim_types");
        return т.isEmpty() ? "" : " из числа: " + родыСписком(т);
    }

    private static String уровниДобытчика(Map<String, Object> п) {
        List<String> у = список(п, "levels");
        return у.isEmpty() ? "" : " уровня " + String.join(" или ", у);
    }

    private static String родыОграничения(Map<String, Object> п) {
        List<String> нужны = список(п, "require_kinds");
        List<String> нельзя = список(п, "forbid_kinds");
        String s = "";
        if (!нужны.isEmpty()) {
            s += ", и среди них " + родыСписком(нужны);
        }
        if (!нельзя.isEmpty()) {
            s += ", и без " + родыСписком(нельзя);
        }
        return s;
    }

    /** Что должно лежать на месте уничтоженных жетонов. */
    private static String трофейноеМесто(Map<String, Object> п) {
        List<String> типы = список(п, "building_types");
        if (!типы.isEmpty()) {
            return "имей на месте уничтоженных жетонов чужое здание из числа: " + зданияСписком(типы);
        }
        if (п != null && п.containsKey("any")) {
            return "имей на месте уничтоженных жетонов " + жетонов(ч(п, "any", 1)) + " противника";
        }
        int n = ч(п, "building", 1);
        return "имей на месте уничтоженных жетонов " + счёт(n, "чужое здание", "чужих здания",
            "чужих зданий");
    }

    /**
     * «3 боеприпаса и 2 монеты» из карты параметров запаса.
     *
     * <p>В данных встречаются ДВЕ ЗАПИСИ, и обе законные: карта «ресурс → число»
     * ({@code {ammo: 3}}) и пара ключей ({@code {resource: ammo, amount: 3}}).
     */
    static String запас(Map<String, Object> п) {
        if (п == null || п.isEmpty()) {
            return "—";
        }
        if (п.get("resource") != null) {
            return ресурс(String.valueOf(п.get("resource")), ч(п, "amount", 1));
        }
        StringBuilder sb = new StringBuilder();
        for (var e : п.entrySet()) {
            if ("count".equals(e.getKey()) || !(e.getValue() instanceof Number num)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" и ");
            }
            sb.append(ресурс(e.getKey(), num.intValue()));
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    /** ПЛАТА КАРТЫ-ЖЕРТВЫ: то, что игрок сдаёт в момент розыгрыша. */
    public static String жертва(Map<String, Object> s) {
        return s == null || s.isEmpty() ? "" : запас(s);
    }

    /**
     * УСЛОВИЕ-РИСУНОК: какие гексы соединяет непрерывное соседство жетонов.
     *
     * <p>Параметры настоящие: {@code what} — чем связывать, {@code anchors} — какие
     * гексы связать, {@code anchor_count} — сколько их, плюс уточнения на группу.
     */
    private static String связь(Map<String, Object> п) {
        String чем = switch (строка(п, "what", "any")) {
            case "building:any" -> "зданиями";
            case "unit:any" -> "войсками";
            default -> "своими жетонами";
        };
        int n = ч(п, "anchor_count", 2);
        String что = switch (строка(п, "anchors", "own_miner_hexes")) {
            case "own_miner_hexes" -> гексов(n) + ", на которых стоят твои добытчики";
            case "straight_line" -> гексов(n) + ", лежащих по одной прямой";
            case "opposite_around_spawn" ->
                "два противоположных гекса вокруг одного тайла зарождения";
            case "own_cu_and_enemy" -> "гекс своего ЦУ и гекс, где есть жетоны противника";
            default -> гексов(n) + ", названных на карте";
        };
        String хвост = "";
        List<String> нельзя = список(п, "forbid_kinds");
        if (!нельзя.isEmpty()) {
            хвост += "; в цепи не должно быть таких родов: " + родыСписком(нельзя);
        }
        List<String> нужны = список(п, "require_types");
        if (!нужны.isEmpty()) {
            int сколько = ч(п, "require_count", 1);
            хвост += "; в цепи должно быть " + сколько + " из числа: "
                + зданияСписком(нужны);
        }
        List<String> роды = список(п, "require_unit_kinds");
        if (!роды.isEmpty()) {
            хвост += "; в цепи должны быть все эти рода: " + родыСписком(роды);
        }
        return "свяжи " + чем + " " + что + хвост;
    }

    // ==================================================================
    //  НАГРАДА
    // ==================================================================

    /** «3 боеприпаса · жетон модуля АТАКИ · 1 карта задания». */
    public static String награда(Map<String, Object> rew) {
        if (rew == null || rew.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : rew.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            Object v = e.getValue();
            if ("module".equals(e.getKey())) {
                sb.append("жетон модуля ").append(модуль(String.valueOf(v)));
            } else if (v instanceof Number num) {
                sb.append(ресурс(e.getKey(), num.intValue()));
            } else if (v instanceof Boolean b) {
                sb.append(b ? ресурс(e.getKey(), 1) : "нет " + ресурс(e.getKey(), 1));
            } else {
                sb.append(ресурс(e.getKey(), 1)).append(": ").append(v);
            }
        }
        return sb.toString();
    }

    private static String модуль(String вид) {
        return switch (вид == null ? "" : вид) {
            case "attack", "red" -> "АТАКИ";
            case "assembly", "blue" -> "СНАРЯЖЕНИЯ";
            case "choice" -> "на выбор";
            default -> вид;
        };
    }
}
