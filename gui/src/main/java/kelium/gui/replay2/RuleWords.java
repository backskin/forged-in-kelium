package kelium.gui.replay2;

import java.util.List;
import java.util.Map;

/**
 * RuleWords — ЧЕЛОВЕЧЕСКИЕ ПОДПИСИ К ПРАВИЛАМ.
 *
 * <p>Правила лежат в {@code data/rulesets/*.yaml} и адресуются точечными путями
 * ({@code actions.combat.retaliation_enabled}). Справочник показывает и значения,
 * и отличия версий — но не сами пути: правило проекта то же, что у
 * {@link Names}, — <b>ни один внутренний ключ не показывается человеку</b>. Нет
 * подписи — пишем «не описано».
 *
 * <p>Числа сюда не переносятся никогда: слово берётся отсюда, значение — из
 * файла правил. Иначе справочник начнёт спорить с данными.
 */
public final class RuleWords {

    private RuleWords() {
    }

    /** Название раздела правил (верхний уровень файла). */
    public static String group(String key) {
        return switch (key) {
            case "meta" -> "О версии";
            case "setup" -> "Подготовка";
            case "economy" -> "Экономика и обмен на очки";
            case "rounds" -> "Раунды и круги";
            case "end_conditions" -> "Условия конца партии";
            case "asymmetry" -> "Асимметрия планшетов";
            case "actions" -> "Действия";
            case "combat_model" -> "Бой";
            case "command_center" -> "Центр управления";
            case "building_compensation_containers" -> "Контейнеры за снесённые здания";
            case "containers" -> "Контейнеры";
            case "containers_storage" -> "Где держат контейнеры";
            case "symbols" -> "Символы супер-заданий";
            case "contested_cards" -> "Спорные карты";
            case "market" -> "Рынок";
            case "tech" -> "Наука";
            case "energy" -> "Энергия";
            case "return_step" -> "Конец раунда";
            case "expansions" -> "Дополнения";
            case "super_objectives" -> "Супер-задания";
            case "modules" -> "Модули";
            case "content_versions" -> "Версии наборов карт";
            default -> "не описано";
        };
    }

    /**
     * Подпись к правилу по его точечному пути. Покрыты все пути, встречающиеся в
     * наборах правил; для нового ключа честно возвращается «не описано» — это
     * сигнал дописать сюда строку, а не показать путь на экране.
     */
    public static String rule(String path) {
        String known = fixed(path);
        if (known != null) {
            return known;
        }
        // Приз за первый шаг трека: путь собран из трека, очереди и ресурса.
        if (path.startsWith("tech.step1_prize.")) {
            String[] p = path.split("\\.");
            if (p.length == 5) {
                String rank = switch (p[3]) {
                    case "first" -> "кто занял первую ячейку";
                    case "second" -> "кто занял вторую ячейку";
                    case "third" -> "кто занял третью ячейку";
                    default -> "по ячейке " + p[3];
                };
                return "приз за первый шаг " + Names.track(p[2]) + " трека, "
                    + rank + ": " + resource(p[4]);
            }
        }
        if (path.startsWith("tech.step_capacity_by_players.")) {
            return "сколько игроков влезает на шаг трека при "
                + path.substring(path.lastIndexOf('.') + 1) + " игроках";
        }
        if (path.startsWith("content_versions.")) {
            return "версия набора «" + contentType(path.substring("content_versions.".length()))
                + "»";
        }
        return "не описано";
    }

    /** Название ресурса по внутреннему имени. */
    public static String resource(String key) {
        return switch (key == null ? "" : key) {
            case "coin", "coins" -> "монеты";
            case "ammo" -> "боеприпасы";
            case "kelium" -> "келемий";
            case "debris" -> "обломки";
            case "container", "containers" -> "контейнеры";
            case "objective_card", "objective_cards" -> "карты заданий";
            case "vp" -> "победные очки";
            case "energy" -> "энергия";
            default -> "не описано";
        };
    }

    /** Тип карточного набора по-человечески. */
    public static String contentType(String type) {
        return switch (type == null ? "" : type) {
            case "objectives" -> "задания";
            case "arsenal" -> "арсенал";
            case "super_arsenal" -> "супер-арсенал";
            case "expansions" -> "дополнения";
            case "super_objectives" -> "супер-задания";
            case "containers" -> "контейнеры";
            case "market" -> "рынок";
            case "orders" -> "приказы";
            case "boards" -> "планшеты";
            case "symbols" -> "символы супер-заданий";
            case "modules" -> "наборы жетонов модулей";
            case "scenarios" -> "раскладки поля";
            default -> "не описано";
        };
    }

    /** Значение правила словами: «да/нет», список, «не задано». */
    public static String value(Object v) {
        if (v == null) {
            return "не задано";
        }
        if (v instanceof Boolean b) {
            return b ? "да" : "нет";
        }
        if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                return "пусто";
            }
            StringBuilder sb = new StringBuilder();
            for (Object o : l) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(value(o));
            }
            return sb.toString();
        }
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                return "пусто";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(value(e.getValue()));
            }
            return sb.toString();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "не задано" : s;
    }

    /** Все известные пути. Один switch — чтобы список было видно целиком. */
    private static String fixed(String path) {
        return switch (path) {
            // ---------- О версии ----------
            case "meta.id" -> "номер версии правил";
            case "meta.based_on" -> "от какой версии отсчитана";
            case "meta.description" -> "о чём эта версия";

            // ---------- Подготовка ----------
            case "setup.start_miner" -> "добытчик стоит на поле с самого начала";
            case "setup.start_coins" -> "монеты на старте, по местам";

            // ---------- Экономика ----------
            case "economy.coins_per_vp" -> "монет за одно победное очко";
            case "economy.trophy_per_vp" -> "обломков за одно победное очко";
            case "economy.kelium_per_vp" ->
                "кубов келемия в хранилище за одно победное очко (0 = вариант правил: очков не даёт)";
            case "economy.kelium_value_coins" -> "во сколько монет ценим келемий при расчётах";
            case "economy.kelium_value_ue" -> "цена келемия в условных единицах карт";
            case "economy.trophy_value_coins_via_kelium" -> "цена обломка в монетах";
            case "economy.debris_storage_vp_per_unit" ->
                "победных очков за каждый обломок в хранилище (вариант правил, дробное число)";
            case "economy.buildings_per_vp" -> "зданий на поле за одно победное очко";
            case "economy.units_per_vp" -> "войск на поле за одно победное очко";
            case "economy.spawn_face_trophy_big" ->
                "обломков за исчерпанное лицо большого тайла зарождения";
            case "economy.spawn_face_trophy_small" ->
                "обломков за исчерпанное лицо малого тайла зарождения";
            case "economy.spawn_back_trophy_big" ->
                "обломков за исчерпанный оборот большого тайла";
            case "economy.spawn_back_trophy_small" ->
                "обломков за исчерпанный оборот малого тайла";
            case "economy.spawn_back_vp_big" -> "победных очков за оборот большого тайла";
            case "economy.spawn_back_vp_small" -> "победных очков за оборот малого тайла";
            case "economy.spawn_flip_normal_vp" ->
                "победных очков за переворот обычного тайла (прежний счёт)";
            case "economy.spawn_flip_start_vp" ->
                "победных очков за переворот стартового тайла (прежний счёт)";

            // ---------- Раунды ----------
            case "rounds.min" -> "наименьшее число раундов (в игре больше не действует)";
            case "rounds.max" -> "наибольшее число раундов (в игре больше не действует)";
            case "rounds.circles_per_round" -> "кругов в раунде";
            case "rounds.reserve_cap" ->
                "предел раундов: сколько кругов вообще отпущено партии";
            case "energy.plant_off_cell_gives" ->
                "сколько энергии даёт станция ВНЕ жёлтой ячейки (на ней — номинал уровня)";
            case "rounds.order_hand_size" -> "приказов в руке";
            case "rounds.objective_hand_limit" -> "предел заданий в руке";
            case "rounds.blind_discard_choice" ->
                "отложенный приказ игрок выбирает сам, а не наугад";

            // ---------- Асимметрия ----------
            case "asymmetry.mode" ->
                "режим планшетов: А — у всех одинаковые, Б — у каждого свой";
            case "asymmetry.board_sides" -> "стороны планшетов, назначенные по местам";
            case "asymmetry.token_hp_bonus_all" -> "надбавка прочности всем жетонам сразу";
            case "asymmetry.token_overrides" ->
                "переопределения печатных характеристик жетонов";

            // ---------- Действия ----------
            case "actions.spec_per_turn" -> "спец-действий за ход";
            case "actions.coincidence_rule_enabled" -> "правило совпадения приказов";
            case "actions.top_actions_per_turn" -> "действий с верхнего приказа за ход";
            case "actions.empty_energy_slot_coin_cost" ->
                "монет за работу здания с пустой ячейкой энергии";
            case "actions.build.surcharge_coins" ->
                "надбавка монетами за каждую лишнюю стройку в одном действии";
            case "actions.build.demolish_refund_coins" -> "возврат монет за снос своего здания";
            case "actions.build.demolish_cu_allowed" ->
                "своё ЦУ можно снести в запас, как любое здание";
            case "actions.build.one_op_per_building" ->
                "над одним зданием за действие Стройка только одна операция";
            case "actions.build.move_cost_coins" ->
                "монет за перенос любого стоящего на поле здания";
            case "actions.build.move_building_repays_full_price" ->
                "перенос здания оплачивается полной ценой (выключено: перенос стоит монету)";
            case "actions.build.cu_moves_per_turn" ->
                "переносов центра управления за ход";
            // Ключ прежних версий свода (до 1.10.0 перенос ЦУ был бесплатным).
            // Подпись нужна, пока эти версии лежат в наборах правил.
            case "actions.build.cu_free_move_per_turn" ->
                "бесплатных переносов центра управления за ход";
            // Пары ОСН/ВТР остались только у планшетов 1.0.x — их всё ещё
            // можно поставить в content_versions.boards.
            case "actions.combat.primary_row_ammo_cost" ->
                "боеприпасов за выстрел из первого ряда (старые планшеты)";
            case "actions.combat.secondary_row_ammo_cost" ->
                "боеприпасов за выстрел из второго ряда (старые планшеты)";
            // ДВЕ АТАКИ (диктовка 24.08.2026): у каждого рода войск
            // универсальная по любому типу и специальная по одному типу, и
            // только специальную улучшает жетон модуля атаки.
            case "actions.combat.universal_ammo_cost" ->
                "боеприпасов за универсальную атаку (любая цель)";
            case "actions.combat.specialized_ammo_cost" ->
                "боеприпасов за специальную атаку (один тип цели)";
            case "actions.combat.tower_specialized_free" ->
                "специальная атака вышки бесплатна";
            case "actions.combat.module_on_universal" ->
                "жетон модуля кладётся на универсальную атаку, а не на специальную";
            case "actions.combat.open_battle_surcharge_ammo" ->
                "надбавка боеприпасами за начало боя";
            case "actions.combat.retaliation_enabled" -> "ответный удар разрешён";
            case "actions.combat.retaliation_is_free" -> "ответный удар не стоит боеприпасов";
            case "actions.combat.retaliation_to_retaliation" ->
                "на ответный удар можно ответить снова";
            case "actions.combat.surcharge_model" -> "как считается надбавка в бою";
            case "actions.movement.cost_model" -> "как считается цена перемещений";
            case "actions.movement.first_hex_free" -> "первый гекс бесплатно";
            case "actions.movement.flat_ammo_per_extra_move" ->
                "боеприпасов за каждое лишнее перемещение";
            case "actions.movement.escalating_surcharge_ammo" ->
                "растущая надбавка боеприпасами за перемещения";
            case "actions.energy_swap.surcharge_coins" ->
                "надбавка монетами за лишние обмены энергии";

            // ---------- Бой ----------
            case "combat_model.all_attacks_damage" -> "любая атака наносит урон";
            case "combat_model.damage_persists_until_refresh" ->
                "урон держится до этапа обновления";
            case "combat_model.heal_per_refresh" -> "сколько урона снимается в обновление";
            case "combat_model.walls_block_shots" -> "стенка не пропускает выстрел";

            // ---------- Центр управления ----------
            case "command_center.build_price_coins" -> "цена постройки центра управления";
            case "command_center.respawns" -> "снесённый центр управления возвращается в игру";
            case "command_center.returns_to_reserve" ->
                "снесённый центр управления уходит в личный запас";
            case "command_center.must_replace_cu_with_spec" ->
                "ЦУ из запаса обязано вернуться на поле спец-действием";
            case "command_center.cu_buildable_by_build_action" ->
                "ЦУ можно поставить и обычным действием Стройка";
            case "command_center.destruction_token_vp" ->
                "победных очков тому, кто снёс центр управления";
            case "command_center.own_token_vp_if_cu_never_destroyed" ->
                "победных очков за свой центр управления, если его так и не снесли";
            case "command_center.military_win_on_second_cu_kill" ->
                "военная победа за второй снесённый центр управления";
            case "command_center.owner_compensation_containers" ->
                "контейнеров хозяину за снесённый центр управления";
            case "command_center.destruction_token_seals_cell" ->
                "жетон-заглушка занимает ячейку красного модуля и закрывает собой "
                    + "ту спец-атаку, на которой лежит";


            // ---------- Контейнеры за снос ----------
            case "building_compensation_containers.barracks" -> "контейнеров за казарму";
            case "building_compensation_containers.factory" -> "контейнеров за завод";
            case "building_compensation_containers.airbase" -> "контейнеров за авиабазу";
            case "building_compensation_containers.miner_by_level" ->
                "контейнеров за добытчика, по уровням";
            case "building_compensation_containers.power_station_by_level" ->
                "контейнеров за энергостанцию, по уровням";

            // ---------- Контейнеры ----------
            case "containers.mode" -> "откуда берутся контейнеры";
            case "containers.printed_requires_empty_hex" ->
                "печатный контейнер берут только с пустого гекса";
            case "containers_storage.arsenal_cells" ->
                "ячеек под контейнеры на картах арсенала";
            case "symbols.tuck_is_free" ->
                "подсунуть карту под планшет — свободное решение, не действие";
            case "symbols.reveal_is_spec" ->
                "вскрыть подложенную карту — спец-действие, по одной за раз";
            case "symbols.installed_arsenal_counts" ->
                "символ установленной карты арсенала тоже считается";
            case "containers_storage.mass_open" -> "можно вскрыть сразу несколько";
            case "containers_storage.open_is_spec" -> "вскрытие — спец-действие";
            case "containers_storage.slots_on_open_card_with_slot" ->
                "мест на открытой карте с нарисованной ячейкой";
            case "containers_storage.slots_per_free_cell" ->
                "мест на каждую свободную ячейку хранилища";

            // ---------- Спорные карты ----------
            case "contested_cards.attack_first_initiative_enabled" ->
                "карта «бью первым» действует";
            case "contested_cards.effect_survives_round_enabled" ->
                "эффект карты живёт до конца раунда";
            case "contested_cards.energy_without_source_enabled" ->
                "энергия без источника разрешена";

            // ---------- Рынок ----------
            case "market.cell_cost_kelium" -> "келемия за одну ячейку предложения";
            case "market.base_exchanges" -> "печатные обмены рынка";
            case "market.pair_bonus_coin" -> "монет за парный обмен";

            // ---------- Наука ----------
            case "tech.tracks" -> "треки науки";
            case "tech.steps_per_track" -> "шагов на треке";
            case "tech.step_cells" -> "ячеек на шагах";
            case "tech.step_capacity" -> "сколько игроков влезает на шаг";
            case "tech.step_cost_trophy" -> "трофеев/обломков за шаг (тратится общий пул)";
            case "tech.step_vp_cumulative" -> "победные очки по шагам, накопительно";
            case "tech.step_rewards" -> "награды шагов трека (перебивает доску)";
            case "end_conditions.last_spawn_tile_threshold" ->
                "сколько источников келемия осталось, когда партия кончается";
            case "tech.science_exchanges" -> "постоянные обмены научного отдела";
            case "tech.science_one_step_per_track_per_action" ->
                "за одно действие — не больше шага на трек";
            case "tech.tracks_per_action" ->
                "сколько РАЗНЫХ треков берёт одно действие Науки";
            case "tech.pay_with_debris_only" ->
                "за науку платят только обломками, трофейный жетон не сдаётся";
            case "actions.combat.as_spec_ammo" ->
                "боеприпасов за Бой спец-действием (0 — так нельзя)";
            case "economy.vp_per_kill" ->
                "победных очков за каждый уничтоженный чужой жетон";
            case "economy.vp_per_installed_arsenal" ->
                "победных очков за каждую установленную карту арсенала";
            case "economy.vp_per_installed_super_arsenal" ->
                "победных очков за каждую установленную карту супер-арсенала";
            case "tech.pair_bonus_coin" -> "монет за парный обмен науки";
            case "tech.gild_trophy_cost" -> "обломков за позолоту модуля";
            case "tech.cubes_are_permanent" -> "кубик занимает ячейку навсегда";
            case "tech.cube_supply" -> "кубиков науки в запасе игрока";

            // ---------- Конец раунда ----------
            case "return_step.return_destroyed_tokens" ->
                "уничтоженные жетоны возвращаются в запас";
            case "return_step.refill_objectives_to_limit" ->
                "задания добираются до предела руки";
            case "return_step.trophy_to_upgrade_exchange_enabled" ->
                "трофеи/обломки можно обменять на улучшение";

            // ---------- Супер-задания ----------
            case "expansions.super_objectives" ->
                "дополнение «Супер задания»: проекты, супероружие и победа по счётчику";
            case "expansions.starting_objectives" ->
                "дополнение «Начальные задания»: простые карты первого раунда";
            case "expansions.super_arsenal" ->
                "дополнение «Супер-арсенал»: карты на вершинах треков технологий";
            case "super_objectives.enabled" -> "супер-задания в игре";
            case "super_objectives.mode" -> "режим супер-заданий";
            case "super_objectives.deal" -> "сколько карт раздаётся игроку";
            case "super_objectives.choose" -> "сколько из них он оставляет";
            case "super_objectives.require_symbols" ->
                "для развёртывания нужен набор символов";

            // ---------- Модули ----------
            case "modules.from_bag" -> "жетоны модулей тянутся из мешка";
            case "modules.red_bag" -> "состав красного мешка";
            case "modules.blue_bag" -> "состав синего мешка";
            default -> null;
        };
    }
}
