package kelium.gui.replay2;

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
 * <p>СЛОВАРЬ СТРОГИЙ. Термины берутся ровно те, что в правилах: ЦУ, гекс, СЕКТОР,
 * добытчик, энергостанция, казарма, завод, авиабаза, вышка, пехота, техника,
 * авиация, келемий, боеприпас, обломок, монета, трофей, приказ, СПЕЦ, Сборка,
 * Стройка, Добыча, Смена энергии, Движение, Бой, Наука, Рынок, тайл зарождения,
 * нейтрал, контейнер, арсенал, модуль, трек. Никаких «юнитов» и «тайлов ресурса».
 *
 * <p>ПОЧЕМУ ЗДЕСЬ, А НЕ В ДАННЫХ КАРТЫ. У карты есть {@code описание} — живой
 * текст дизайнера, и он показывается целиком отдельным блоком. Но описание
 * рассказывает карту ЦЕЛИКОМ, одним куском, а на карте требование, награда,
 * усиление и награда за усиление — четыре разных строки в четырёх местах. Чтобы
 * разложить их по местам, фразу нужно собирать из того же, из чего её собирает
 * движок, — из предиката и его чисел.
 *
 * <p>Предикат, которого здесь нет, показывается своим идентификатором. Это
 * НАРОЧНО видно: непереведённое условие должно бросаться в глаза, а не молча
 * притворяться пустым.
 */
final class CardWords {

    private CardWords() {
    }

    /** Число из параметров или {@code иначе}, если его там нет. */
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

    /** «2 здания» / «1 здание» / «5 зданий». */
    private static String счёт(int n, String один, String два, String много) {
        int сто = n % 100;
        int дес = n % 10;
        String слово = сто >= 11 && сто <= 14 ? много
            : дес == 1 ? один
            : desTwoToFour(дес) ? два : много;
        return n + " " + слово;
    }

    private static boolean desTwoToFour(int дес) {
        return дес >= 2 && дес <= 4;
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

    private static String шагов(int n) {
        return счёт(n, "шаг", "шага", "шагов");
    }

    private static String треков(int n) {
        return счёт(n, "трек", "трека", "треков");
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
        return счёт(n, "трофейный жетон", "трофейных жетона", "трофейных жетонов");
    }

    /** Ресурс по-русски в нужном числе: «3 боеприпаса». */
    private static String ресурс(String ключ, int n) {
        return switch (ключ == null ? "" : ключ) {
            case "coin", "coins" -> счёт(n, "монету", "монеты", "монет");
            case "ammo" -> счёт(n, "боеприпас", "боеприпаса", "боеприпасов");
            case "kelium" -> счёт(n, "келемий", "келемия", "келемиев");
            case "debris" -> счёт(n, "обломок", "обломка", "обломков");
            case "container", "containers" -> контейнеров(n);
            case "objective_card", "objective_cards" ->
                счёт(n, "карта задания", "карты задания", "карт заданий");
            case "arsenal" -> счёт(n, "карта арсенала", "карты арсенала", "карт арсенала");
            case "vp" -> счёт(n, "победное очко", "победных очка", "победных очков");
            case "energy" -> счёт(n, "кубик энергии", "кубика энергии", "кубиков энергии");
            case "module" -> "жетон модуля";
            case "module_half" -> "жетон модуля";
            case "gild_module" -> "позолоту модуля";
            case "storage_token" -> "жетон модуля хранилища";
            default -> n + " " + ключ;
        };
    }

    /**
     * ТРЕБОВАНИЕ КАРТЫ ЖИВЫМИ СЛОВАМИ.
     *
     * @param предикат идентификатор из данных
     * @param п        его параметры (может быть {@code null})
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    static String условие(String предикат, Map<String, Object> п) {
        if (предикат == null || предикат.isBlank()) {
            return "";
        }
        int n = ч(п, "count", 1);
        return switch (предикат) {
            // ---- рисунки: связь жетонов ----
            case "chain_connects" -> связь(п);
            case "buildings_wall_chain" -> "выстрой " + зданий(n)
                + " в непрерывную цепь по соседним гексам";
            case "buildings_ring_around_hex" -> "окружи один гекс своими зданиями с "
                + гексов(n) + " вокруг него";

            // ---- жертвы ----
            case "sacrifice_paid" -> "внеси плату, указанную на карте (действий не требует)";
            case "sacrifice_enhanced" -> "внеси усиленную плату, указанную на карте";

            // ---- стройка и здания ----
            case "buildings_on_field_count" -> "имей на поле " + зданий(n);
            case "built_bordering_enemy" -> "построй " + зданий(n)
                + " на гексе, соседнем с гексом, где есть жетоны противника";
            case "built_on_hex_with_enemy_units" -> "построй " + зданий(n)
                + " на гексе, где стоят войска противника";
            case "build_ops_on_nonadjacent_hexes" -> "в этот ход построй на "
                + гексов(n) + ", не соседствующих между собой";
            case "moved_buildings_this_turn" -> "в этот ход перенеси " + зданий(n)
                + " на другие гексы" + (да(п, "include_cu") ? ", и одно из них — твоё ЦУ" : "");
            case "towers_off_cu_hexes" -> "имей на поле " + вышек(n)
                + " на разных гексах, и ни одна не стоит на гексе с твоим ЦУ"
                + (да(п, "on_enemy_hex") ? "; хотя бы одна — на гексе с жетонами противника" : "");
            case "cu_placed_near_enemy_cu" -> "перенеси своё ЦУ на гекс на расстоянии "
                + n + " от чужого ЦУ";
            case "powered_building_off_cu_hex" -> "имей запитанное здание вне гекса своего ЦУ";

            // ---- энергия ----
            case "no_unpowered_buildings" -> "к концу хода ни одно твоё здание "
                + "не осталось без энергии";
            case "idle_cube_on_each_source" -> "оставь по простаивающему кубику "
                + "на каждом своём источнике энергии";

            // ---- добыча и келемий ----
            case "powered_miners_count" -> "имей " + добытчиков(n) + " запитанными";
            case "powered_miners_distinct_spawns" -> "имей " + добытчиков(n)
                + " запитанными у РАЗНЫХ тайлов зарождения";
            case "last_kelium_nonstart" -> "забери последний келемий с тайла зарождения, "
                + "который не является стартовым";
            case "miner_took_container" -> "возьми печатный контейнер добытчиком";
            case "picked_container_by_unit" -> "возьми печатный контейнер войском";
            case "has_unopened_container" -> "держи " + контейнеров(n) + " невскрытыми";

            // ---- войска и движение ----
            case "hired_distinct_kinds" -> "в этот ход найми войска " + n
                + " разных родов";
            case "units_off_own_hexes" -> "имей " + войск(n)
                + " на гексах, где нет твоих зданий";
            case "unit_at_distance_from_cu" -> "отведи войско на расстояние " + n
                + " от своего ЦУ";
            case "hidden_unit_near_enemy" -> "поставь " + войск(n)
                + " в укрытие на гексе, соседнем с жетонами противника";
            case "unit_on_hex_with_enemy_units" -> "заведи " + войск(n)
                + " на гекс, где стоят войска противника";
            case "units_bordering_enemy_units_hex" -> "окружи гекс с войсками противника "
                + "своими войсками с " + гексов(n);
            case "aircraft_on_enemy_hex" -> "заведи авиацию в небо " + гексов(n)
                + " противника";

            // ---- бой ----
            case "destroyed_enemy_this_turn" -> "в этот ход уничтожь " + жетонов(n)
                + " противника";
            case "kills_by_one_unit" -> "уничтожь " + жетонов(n) + " ОДНИМ своим войском";
            case "damaged_distinct_no_kills" -> "в этот ход нанеси урон " + жетонов(n)
                + " противника, никого не уничтожив";
            case "damaged_distinct_enemy_buildings" -> "в этот ход нанеси урон "
                + зданий(n) + " противника";
            case "destroyed_in_retaliation" -> "уничтожь " + жетонов(n)
                + " противника ОТВЕТНЫМ боем";
            case "destroyed_enemy_economy" -> "уничтожь " + зданий(n)
                + " противника из числа добытчиков и энергостанций";
            case "destroyed_stronger_player_token" -> "уничтожь жетон игрока, "
                + "который идёт впереди тебя по очкам";

            // ---- трофеи ----
            case "trophy_contains" -> "положи на трофейную карту " + трофейноеЧто(п);
            case "trophy_distinct_kinds" -> "набери в трофеи жетоны " + n
                + " разных видов";

            // ---- наука ----
            case "tech_step_reached" -> "поднимись на " + шагов(ч(п, "step", n))
                + " по треку " + трек(строка(п, "track", ""));
            case "tracks_occupied" -> "займи место на " + треков(n) + " науки";
            case "science_trophies_spent" -> "в этот ход сдай в Науку " + трофеев(n);
            case "science_offers_used" -> "в этот ход возьми " + предложений(n)
                + " планшета технологий";

            // ---- рынок ----
            case "market_offers_used" -> "в этот ход возьми " + предложений(n) + " рынка";
            case "used_market_card_offer" -> "возьми предложение с карты рынка";

            // ---- приказы ----
            case "assembly_all_chose_ammo" -> "проведи Сборку, в которой ВСЕ здания "
                + "выбрали боеприпасы";
            case "lower_order_open_this_turn" -> "сыграй карту приказа, нижний приказ "
                + "которой вскрыт другим игроком";
            case "order_coincided_this_turn" -> "сыграй верхом тот же приказ, "
                + "что и другой игрок";

            // ---- запасы ----
            case "resource_at_least" -> "имей в хранилище не меньше чем " + запас(п);
            case "resources_at_most" -> "имей в хранилище не больше чем " + запас(п);
            case "arsenal_cards_held" -> "держи " + карт(n) + " арсенала"
                + (да(п, "all_face_down") ? " неустановленными" : "");

            default -> предикат + (п == null || п.isEmpty() ? "" : " " + п);
        };
    }

    /** Что именно требует лечь на трофейную карту. */
    private static String трофейноеЧто(Map<String, Object> п) {
        int n = ч(п, "count", 1);
        String вид = строка(п, "kind", "");
        String что = switch (вид) {
            case "building" -> зданий(n) + " противника";
            case "unit" -> войск(n) + " противника";
            default -> жетонов(n) + " противника";
        };
        return что;
    }

    /** Название трека по внутреннему имени. */
    private static String трек(String id) {
        return switch (id == null ? "" : id) {
            case "left", "red" -> "красному";
            case "middle", "green" -> "зелёному";
            case "right", "blue" -> "синему";
            case "" -> "технологий";
            default -> id;
        };
    }

    /** «3 боеприпаса и 2 монеты» из карты параметров запаса. */
    private static String запас(Map<String, Object> п) {
        if (п == null) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (var e : п.entrySet()) {
            if ("count".equals(e.getKey())) {
                continue;
            }
            if (!(e.getValue() instanceof Number num)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" и ");
            }
            sb.append(ресурс(e.getKey(), num.intValue()));
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    /** Условие-рисунок: какие гексы соединяет непрерывное соседство жетонов. */
    private static String связь(Map<String, Object> п) {
        String чем = switch (строка(п, "tokens", "any")) {
            case "buildings" -> "зданиями";
            case "units" -> "войсками";
            default -> "жетонами";
        };
        String что = switch (строка(п, "endpoints", "")) {
            case "miners" -> "два гекса со своими добытчиками";
            case "line" -> "три гекса, лежащих по прямой";
            case "spawn_opposite" -> "два противоположных гекса вокруг тайла зарождения";
            case "cu_and_enemy" -> "свой гекс ЦУ и гекс с жетонами противника";
            default -> гексов(ч(п, "count", 2)) + ", названных на карте";
        };
        return "свяжи " + чем + " " + что;
    }

    /**
     * НАГРАДА ЖИВЫМИ СЛОВАМИ: «3 боеприпаса · жетон модуля атаки · 1 карта задания».
     */
    static String награда(Map<String, Object> rew) {
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
            case "assembly", "blue" -> "СБОРКИ";
            case "choice" -> "на выбор";
            default -> вид;
        };
    }
}
