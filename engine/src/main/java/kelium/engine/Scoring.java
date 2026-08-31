package kelium.engine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;
import kelium.dataio.Ctx;

/**
 * Подсчёт победных очков — по компонентам в конце партии.
 *
 * <p>Все коэффициенты пересчёта берутся из секции {@code economy} ruleset.
 * Возвращает разбивку по источникам для каждого игрока, чтобы отчёты могли
 * объяснить, почему кто-то победил.
 */
public final class Scoring {

    private Scoring() {
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    /** Посчитать очки одного игрока (место {@code seat}) с разбивкой по источникам. */
    public static Map<String, Integer> scorePlayer(GameState state, int seat) {
        PlayerState p = state.player(seat);
        Ruleset rs = Ctx.rules(state);
        Map<String, Object> econ = rs.economy();

        int coinsPerVp = asInt(econ.get("coins_per_vp"));
        int perBuilding = asInt(econ.get("buildings_per_vp"));
        int perUnit = asInt(econ.get("units_per_vp"));
        // Келемий: 1 ПО за каждые kelium_per_vp куба (по умолчанию 2). Совместимость
        // со старым ключом kelium_vp_each (множитель) сохраняется как запасной.
        // kelium_per_vp = 0 ОТКЛЮЧАЕТ очки за келемий целиком (вариант правил
        // 2026-08-15 «келемий в хранилище = 0 ПО») — раньше 0 по ошибке уводил в
        // ветку kelium_vp_each вместо явного нуля.
        boolean hasKeliumPerVp = econ.containsKey("kelium_per_vp");
        int keliumPerVp = hasKeliumPerVp ? asInt(econ.get("kelium_per_vp")) : 0;

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int keliumVp;
        if (hasKeliumPerVp) {
            keliumVp = keliumPerVp > 0 ? p.resources.kelium() / keliumPerVp : 0;
        } else {
            keliumVp = p.resources.kelium() * asInt(econ.get("kelium_vp_each"));
        }
        breakdown.put("kelium", keliumVp);
        breakdown.put("coins", p.resources.coin() / coinsPerVp);
        // ОБЛОМКИ И НЕСДАННЫЕ ТРОФЕИ СЧИТАЮТСЯ ОДНИМ ИЗ ДВУХ КУРСОВ, НЕ ОБОИМИ
        // СРАЗУ. Найден и исправлен 18.08.2026: economy.debris_storage_vp_per_unit
        // задумывался как АЛЬТЕРНАТИВНЫЙ вариант курса trophy_per_vp («отсутствует/0
        // в обычном рулсете, тогда эта ветка не влияет ни на что» — так было
        // написано в комментарии), но реализован был как ДОБАВКА поверх старого
        // курса, а не замена. В действующих сводах 1.8.0-1.13.0 оба ключа стояли
        // одновременно (trophy_per_vp: 3, debris_storage_vp_per_unit: 1.0), и
        // трофей стоил 1/3 + 1.0 = 1.333 ПО вместо задуманного одного курса —
        // почти вчетверо дороже. Теперь как у kelium_per_vp чуть выше: НАЛИЧИЕ
        // ключа debris_storage_vp_per_unit ПОЛНОСТЬЮ ЗАМЕНЯЕТ курс trophy_per_vp,
        // а не складывается с ним. Старые своды без этого ключа (архив, версии
        // до 1.7.1) продолжают считать по trophy_per_vp, как и раньше.
        //
        // Подсчёт очков идёт ТОЛЬКО в момент истинного конца партии — а тогда
        // Возврат жетонов не делается (см. GameEngine.returnStep(gameEnding=true)),
        // и трофеи, ещё лежащие у игрока НЕСДАННЫМИ, считаются по ПОЛНОЙ печатной
        // ценности наравне с трофеями (не флат-1, как при мидгейм-конвертации).
        int trophyPool = p.resources.trophy() + p.trophySpacePoints();
        int trophyVp;
        if (econ.containsKey("debris_storage_vp_per_unit")) {
            trophyVp = (int) Math.floor(trophyPool
                * ((Number) econ.get("debris_storage_vp_per_unit")).doubleValue());
        } else {
            trophyVp = trophyPool / asInt(econ.get("trophy_per_vp"));
        }
        breakdown.put("debris", trophyVp);
        breakdown.put("buildings_on_field", p.buildingsOnField().size() / perBuilding);
        breakdown.put("units_on_field", p.unitsOnField().size() / perUnit);

        int techVp = 0;
        List<Integer> stepVp = rs.getIntList("tech.step_vp_cumulative");
        for (Map.Entry<String, Integer> e : p.techSteps.entrySet()) {
            int step = e.getValue();
            for (int i = 0; i < step; i++) {
                techVp += stepVp.get(i);
            }
        }
        breakdown.put("tech", techVp);

        breakdown.put("gold_modules", p.goldModules);
        // ПОБЕДНЫЕ ОЧКИ ЗА ТАЙЛЫ ЗАРОЖДЕНИЯ (правило дизайнера 12.08.2026):
        // очко даёт ТОЛЬКО ВЫРАБОТАННЫЙ ДО КОНЦА (оборот) БОЛЬШОЙ тайл — игрок
        // сохраняет его в запасе как очко. Малое (стартовое) зарождение очков не
        // даёт вовсе, и за ЛИЦО тайла очков тоже нет — только трофейные.
        //
        // Раньше здесь было наоборот: очки начислялись за ПЕРВОЕ исчерпание
        // (1 за стартовый, 2 за обычный) плюс ещё одно за снятие. Расхождение
        // нашлось при сверке со СВОДом и подтверждено дизайнером.
        int backVpSmall = econ.containsKey("spawn_back_vp_small")
            ? asInt(econ.get("spawn_back_vp_small")) : 0;
        int backVpBig = econ.containsKey("spawn_back_vp_big")
            ? asInt(econ.get("spawn_back_vp_big")) : 1;
        breakdown.put("spawn_tiles",
            p.claimedStartTiles * backVpSmall + p.claimedNormalTiles * backVpBig);
        // ЖЕТОН УНИЧТОЖЕНИЯ ЦУ ДВУСТОРОННИЙ (правило дизайнера 12.08.2026):
        //   СВОЙ жетон, оставшийся у тебя (ЦУ уцелело), лежит лицом — 1 ПО;
        //   ЧУЖОЙ, забранный за снос вражеского ЦУ, переворачивается — 3 ПО.
        // Разница и есть награда за войну: снести чужое ЦУ втрое ценнее, чем
        // просто уберечь своё.
        int cuTokenVp = p.cuDestructionTokens * rs.getInt("command_center.destruction_token_vp");
        if (p.ownCuTokenAvailable) {
            cuTokenVp += ((Number) rs.get("command_center.own_token_vp_if_cu_never_destroyed", 3)).intValue();
        }
        breakdown.put("cu_tokens", cuTokenVp);
        // ЭКСПЕРИМЕНТАЛЬНЫЙ КЛЮЧ (по умолчанию 0 — правила не меняются): очки за
        // каждое уничтожение. По правилам уничтожение очков не даёт вовсе — оно
        // даёт трофей, который ещё надо сдать в Науку, а несданный возвращается
        // владельцу. Ключ существует, чтобы балансовый стенд мог ПРОВЕРИТЬ, что
        // будет, если платить за агрессию напрямую.
        int killVp = ((Number) rs.get("economy.vp_per_kill", 0)).intValue() * p.killsTotal;
        breakdown.put("kills", killVp);
        // «Военный трек» (эксперимент leftover_trophy_vp_per): накоплено в Возврат.
        breakdown.put("war_track", p.warTrackVp);
        breakdown.put("objective_card_vp", p.objectiveCardVp);
        // Супер-арсенал: ПО, напечатанные на удерживаемых картах (vp_on_card у
        // супер-войск, vp_flat у «Мандата совета»).
        int saVp = 0;
        var saLib = Ctx.cards(state, "super_arsenal");
        for (String cid : p.superArsenalCards) {
            var card = saLib.find(cid);
            if (card == null) {
                continue;
            }
            if (card.get("vp_on_card") instanceof Number n) {
                saVp += n.intValue();
            }
            if (card.get("vp_flat") instanceof Number n) {
                saVp += n.intValue();
            }
        }
        breakdown.put("super_arsenal", saVp);

        // ПЕРВАЯ ЧАСТЬ СУПЕР ЗАДАНИЯ (решение дизайнера 13.08.2026): 2–5 очков по
        // стоимости сданного в лицо карты. Очки остаются у игрока, даже если
        // рубашка так и не сложилась: иначе вложенное в первую часть пропадает.
        breakdown.put("super_first_part", p.superFirstPartVp);
        // Супер-задания 5.0: накопитель платит, только если карта дожила до
        // конца партии нетронутой; сожжённая уже расплатилась суперутилём.
        int super5 = kelium.engine.Super5.stockpileVp(state, seat);
        if (super5 != 0) {
            breakdown.put("super5_stockpile", super5);
        }
        // КАРТЫ-ЦЕЛИ АРСЕНАЛА (2.1.0): установленная карта может не менять правил,
        // а считать очки в конце партии. Такого канала в игре не было вовсе —
        // замер 15.08.2026 показал, что ни одна карта очков не печатает, хотя
        // движок умеет их считать.
        breakdown.put("arsenal_vp", arsenalGoalVp(state, p));

        // УСТАНОВЛЕННАЯ КАРТА АРСЕНАЛА САМА СТОИТ ОЧКОВ (правило дизайнера
        // 21.08.2026).
        //
        // ЗАЧЕМ. Замер: за партию игрок берёт полторы карты арсенала и СЖИГАЕТ
        // их чаще, чем ставит (0.74 против 0.72), потому что утиль даёт понятную
        // выгоду сейчас, а установка — способность, которая может и не
        // пригодиться. Теперь установка платит сама по себе, и выбор «сжечь или
        // поставить» становится выбором между «сейчас» и «наверняка».
        //
        // Обычная карта и карта СУПЕР-арсенала считаются ОТДЕЛЬНЫМИ ключами:
        // супер-арсенал дороже (правило дизайнера — два очка), и смешивать их в
        // одну строку значило бы потерять это в разбивке «откуда очки».
        //
        // Мандат совета (sa8) держит карту у себя — она тоже установлена и
        // работает, поэтому считается вместе с обычными (allInstalledArsenal).
        int perCard = ((Number) rs.get("economy.vp_per_installed_arsenal", 0)).intValue();
        if (perCard != 0) {
            breakdown.put("installed_arsenal", perCard * p.allInstalledArsenal().size());
        }
        int perSuper = ((Number) rs.get("economy.vp_per_installed_super_arsenal", 0))
            .intValue();
        if (perSuper != 0) {
            breakdown.put("installed_super_arsenal", perSuper * p.superArsenalCards.size());
        }

        int star = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            if ((b.type == BuildingType.MINER || b.type == BuildingType.POWER_PLANT)
                    && b.level != null && b.level == 4) {
                star += 1;
            }
        }
        breakdown.put("level4_stars", star);

        // ТОЧКА ПРАВИЛ: НОВЫЙ источник победных очков от карты арсенала (заказ
        // дизайнера 13.08.2026 — «4 карты, меняющие подсчёт очков в конце партии
        // по новому критерию»). Отдельной строкой в разбивке, чтобы отчёт
        // «откуда очки» не врал.
        int cardVp = kelium.engine.ability.RuleQuery
            .of(state, seat, kelium.engine.ability.Hook.SCORING_VP_SOURCE)
            .base(0).ask();
        if (cardVp != 0) {
            // СЛОЖИТЬ, А НЕ ЗАТЕРЕТЬ: строка "arsenal_vp" уже занята очками карт-целей
            // (arsenalGoalVp, карты 2.1.0 v01-v06, data-driven bottom.scoring) — эта
            // точка правил считает ДРУГОЙ источник (способность из реестра ability),
            // и оба обязаны складываться, а не вытеснять друг друга.
            breakdown.merge("arsenal_vp", cardVp, Integer::sum);
        }

        int total = 0;
        for (int v : breakdown.values()) {
            total += v;
        }
        // ВТОРАЯ ВЕТКА УДАЛЕНА 18.08.2026 — это и была ДВОЙНАЯ УЧЁТКА. Курс
        // economy.debris_storage_vp_per_unit теперь применяется РОВНО ОДИН РАЗ,
        // выше, в строке "debris" breakdown'а (либо он, либо старый trophy_per_vp
        // — не оба сразу). Раньше здесь начислялась ЕЩЁ ОДНА, отдельная порция
        // очков по той же паре (trophy + trophySpacePoints) с тем же курсом —
        // при trophy_per_vp:3 и debris_storage_vp_per_unit:1.0 (действующий свод)
        // трофей стоил 1/3 + 1.0 = 1.333 ПО вместо одного курса.
        // ТОЧКА ПРАВИЛ: правка УЖЕ СУЩЕСТВУЮЩЕГО итога («улучшить существующий
        // критерий»). Спрашивается после суммы, поэтому карта видит полный счёт.
        total = kelium.engine.ability.RuleQuery
            .of(state, seat, kelium.engine.ability.Hook.SCORING_VP_MODIFIER)
            .base(total).ask();
        breakdown.put("total", total);
        return breakdown;
    }

    /**
     * ИСТОЧНИКИ, ЗА КОТОРЫЕ ОЧКО ВЫДАЁТСЯ ЖЕТОНОМ ПРЯМО В ПАРТИИ.
     *
     * <p>Победное очко в этой игре приходит двумя разными способами, и разница
     * физическая, а не бухгалтерская:
     * <ul>
     *   <li><b>Сразу</b> — игрок берёт со стола <b>жетон победного очка</b>
     *       (деревянная золотая пятиконечная звезда) и кладёт перед собой:
     *       выработал оборот тайла зарождения, выполнил задание с очками,
     *       сдал первую часть супер задания. Очко уже никуда не денется.</li>
     *   <li><b>В конце</b> — очки считаются по столу: монеты, келемий, трофеи,
     *       здания и войска на поле, шаги треков, карты-цели. Жетонов за это не
     *       выдают: пока партия идёт, эти очки ещё можно потерять.</li>
     * </ul>
     *
     * <p>Здесь перечислены источники ПЕРВОГО рода. Жетон уничтожения ЦУ и
     * золотой модуль в список не входят: они и так физические жетоны, звезда
     * их не дублирует.
     */
    public static final List<String> STAR_TOKEN_SOURCES =
        List.of("spawn_tiles", "objective_card_vp", "super_first_part", "war_track");

    /**
     * СКОЛЬКО ЖЕТОНОВ ПОБЕДНЫХ ОЧКОВ (звёзд) лежит перед игроком прямо сейчас.
     *
     * <p>Считается по той же разбивке, что и итог, — иначе звёзды на столе и
     * очки в отчёте разошлись бы, а это ровно тот сорт расхождения, который в
     * этом движке уже случался.
     */
    public static int starTokens(GameState state, int seat) {
        Map<String, Integer> breakdown = scorePlayer(state, seat);
        int stars = 0;
        for (String key : STAR_TOKEN_SOURCES) {
            stars += breakdown.getOrDefault(key, 0);
        }
        return stars;
    }

    /** Посчитать разбивку очков для всех игроков: {seat: breakdown}. */
    public static Map<Integer, Map<String, Integer>> scoreAll(GameState state) {
        Map<Integer, Map<String, Integer>> out = new HashMap<>();
        for (int seat = 0; seat < state.numPlayers(); seat++) {
            out.put(seat, scorePlayer(state, seat));
        }
        return out;
    }

    /**
     * ОЧКИ ЗА КАРТЫ-ЦЕЛИ АРСЕНАЛА, установленные перед игроком.
     *
     * <p>Две формы записи в данных:
     * <pre>
     *   scoring: {per: 2, of: buildings_on_field, vp: 1}    // 1 ПО за каждые 2
     *   scoring: {combo: [{of: unit_kinds, at_least: 3},
     *                     {of: buildings_on_field, at_least: 4}], vp: 4}
     * </pre>
     *
     * <p>Считается ТОЛЬКО по установленным картам: сожжённая на утиль карта
     * очков не даёт, и это единственный честный размен, который карта предлагает
     * — разовая выгода сейчас против очков в конце.
     */
    private static int arsenalGoalVp(GameState s, PlayerState p) {
        int total = 0;
        for (String cid : p.allInstalledArsenal()) {
            Map<String, Object> card = kelium.dataio.Ctx.cards(s, "arsenal").find(cid);
            if (card == null || !(card.get("bottom") instanceof Map<?, ?> bottom)
                    || !(bottom.get("scoring") instanceof Map<?, ?> sc)) {
                continue;
            }
            int vp = sc.get("vp") instanceof Number n ? n.intValue() : 0;
            if (sc.get("combo") instanceof java.util.List<?> combo) {
                boolean all = true;
                for (Object part : combo) {
                    if (!(part instanceof Map<?, ?> cond)) {
                        continue;
                    }
                    int need = cond.get("at_least") instanceof Number n ? n.intValue() : 0;
                    if (goalCount(s, p, String.valueOf(cond.get("of"))) < need) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    total += vp;
                }
            } else if (sc.get("per") instanceof Number per && per.intValue() > 0) {
                total += vp * (goalCount(s, p, String.valueOf(sc.get("of")))
                    / per.intValue());
            }
        }
        return total;
    }

    /** Есть ли на гексе хотя бы один ЧУЖОЙ жетон (карта-цель «Воздушное крыло»). */
    private static boolean enemyOnHex(GameState s, int seat, String hexId) {
        if (hexId == null) {
            return false;
        }
        for (PlayerState other : s.players) {
            if (other.seat == seat) {
                continue;
            }
            for (kelium.core.UnitToken u : other.unitsOnField()) {
                if (hexId.equals(u.hexId)) {
                    return true;
                }
            }
            for (kelium.core.BuildingToken b : other.buildingsOnField()) {
                if (hexId.equals(b.hexId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Стоит ли жетон {@code uid} на своём гексе ОДИН — то есть без других СВОИХ
     * войск и зданий. Чужие жетоны на гексе значения не имеют: карта платит за
     * то, что жетон оторвался от своей группы, а не за то, что вокруг пусто.
     */
    private static boolean aloneOnHex(PlayerState p, String hexId, int uid) {
        if (hexId == null) {
            return false;
        }
        for (kelium.core.UnitToken u : p.unitsOnField()) {
            if (u.uid != uid && hexId.equals(u.hexId)) {
                return false;
            }
        }
        for (kelium.core.BuildingToken b : p.buildingsOnField()) {
            if (hexId.equals(b.hexId)) {
                return false;
            }
        }
        return true;
    }

    /** Что именно считает карта-цель. */
    private static int goalCount(GameState s, PlayerState p, String what) {
        return switch (what) {
            case "buildings_on_field" -> p.buildingsOnField().size();
            case "aircraft_on_field" -> countUnits(p, kelium.core.UnitType.AIRCRAFT);
            case "vehicles_on_field" -> countUnits(p, kelium.core.UnitType.VEHICLE);
            case "airbase" -> countBuildings(p, kelium.core.BuildingType.AIRBASE);
            case "military_buildings" ->
                countBuildings(p, kelium.core.BuildingType.BARRACKS)
                + countBuildings(p, kelium.core.BuildingType.FACTORY)
                + countBuildings(p, kelium.core.BuildingType.AIRBASE);
            case "level2_economy" -> {
                int n = 0;
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    boolean economy = b.type == kelium.core.BuildingType.MINER
                        || b.type == kelium.core.BuildingType.POWER_PLANT;
                    if (economy && b.level != null && b.level == 2) {
                        n++;
                    }
                }
                yield n;
            }
            case "units_off_home" -> {
                java.util.Set<String> home = new java.util.HashSet<>();
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (b.hexId != null) {
                        home.add(b.hexId);
                    }
                }
                int n = 0;
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    if (u.hexId != null && !home.contains(u.hexId)) {
                        n++;
                    }
                }
                yield n;
            }
            case "units_on_field" -> p.unitsOnField().size();
            case "debris" -> p.resources.trophy();
            case "cu_tokens" -> p.cuDestructionTokens;
            case "unit_kinds" -> {
                java.util.Set<kelium.core.UnitType> kinds =
                    java.util.EnumSet.noneOf(kelium.core.UnitType.class);
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    kinds.add(u.type);
                }
                yield kinds.size();
            }
            // ==== КРИТЕРИИ КАРТ-ЦЕЛЕЙ 2.3.0 (ревью дизайнера 17.08.2026) ====
            // Прежние цели платили за НАКОПЛЕНИЕ («2 ПО за каждую авиацию на
            // поле») — то есть за то, что игрок и так делает, и платили щедро.
            // Новые платят за ПОЛОЖЕНИЕ жетона: за то, что он стоит там, где
            // стоять неудобно и рискованно. Считать это так же легко глазами.

            // Авиация на гексе, где есть жетоны противника.
            case "aircraft_on_enemy_hex" -> {
                int n = 0;
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    if (u.type == kelium.core.UnitType.AIRCRAFT
                            && enemyOnHex(s, p.seat, u.hexId)) {
                        n++;
                    }
                }
                yield n;
            }
            // Техника на гексе, где нет НИ ОДНОГО другого своего жетона —
            // ни войска, ни здания. Одинокий танк в чужом тылу.
            case "vehicles_alone_on_hex" -> {
                int n = 0;
                for (kelium.core.UnitToken v : p.unitsOnField()) {
                    if (v.type == kelium.core.UnitType.VEHICLE
                            && aloneOnHex(p, v.hexId, v.uid)) {
                        n++;
                    }
                }
                yield n;
            }
            // Гексы, где стоит твоя вышка и больше ничего твоего.
            case "lone_tower_hexes" -> {
                java.util.Set<String> hexes = new java.util.HashSet<>();
                for (kelium.core.UnitToken u : p.unitsOnField()) {
                    if (u.type == kelium.core.UnitType.TOWER
                            && aloneOnHex(p, u.hexId, u.uid)) {
                        hexes.add(u.hexId);
                    }
                }
                yield hexes.size();
            }
            // Пары «добытчик + энергостанция ОДНОГО уровня» на поле.
            case "miner_plant_level_pairs" -> {
                java.util.Map<Integer, Integer> miners = new java.util.HashMap<>();
                java.util.Map<Integer, Integer> plants = new java.util.HashMap<>();
                for (kelium.core.BuildingToken b : p.buildingsOnField()) {
                    if (b.level == null) {
                        continue;
                    }
                    if (b.type == kelium.core.BuildingType.MINER) {
                        miners.merge(b.level, 1, Integer::sum);
                    } else if (b.type == kelium.core.BuildingType.POWER_PLANT) {
                        plants.merge(b.level, 1, Integer::sum);
                    }
                }
                int pairs = 0;
                for (var e : miners.entrySet()) {
                    pairs += Math.min(e.getValue(), plants.getOrDefault(e.getKey(), 0));
                }
                yield pairs;
            }
            // Пары «келемий + боеприпас» в хранилище: считается по меньшему из
            // двух, то есть карта платит за РАВНОВЕСИЕ, а не за одну гору.
            case "kelium_ammo_pairs" ->
                Math.min(p.resources.kelium(), p.resources.ammo());
            case "tech_steps" -> {
                int n = 0;
                for (String track : s.tech.tracks) {
                    n += p.techSteps.getOrDefault(track, 0);
                }
                yield n;
            }
            default -> 0;
        };
    }

    private static int countUnits(PlayerState p, kelium.core.UnitType type) {
        int n = 0;
        for (kelium.core.UnitToken u : p.unitsOnField()) {
            if (u.type == type) {
                n++;
            }
        }
        return n;
    }

    private static int countBuildings(PlayerState p, kelium.core.BuildingType type) {
        int n = 0;
        for (kelium.core.BuildingToken b : p.buildingsOnField()) {
            if (b.type == type) {
                n++;
            }
        }
        return n;
    }
}
