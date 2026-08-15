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
        // Ключ рулсета остаётся "trophy_per_vp" ради обратной совместимости со
        // всеми старыми файлами rulesets/*.yaml (переименование ресурса не
        // трогает YAML-ключи конфига — это внутренний идентификатор, игрок его
        // не видит; человекочитаемая подпись переименована в RuleWords).
        int debrisPerVp = asInt(econ.get("trophy_per_vp"));
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
        // ПРАВИЛО (уточнение 2026-08-15): подсчёт очков идёт ТОЛЬКО в момент
        // истинного конца партии — а тогда Возврат жетонов не делается (см.
        // GameEngine.returnStep(gameEnding=true)), и трофеи, ещё лежащие у
        // игрока НЕСДАННЫМИ, считаются по ПОЛНОЙ печатной ценности наравне с
        // обломками (не флат-1, как при обычной мидгейм-конвертации в Возврат).
        breakdown.put("debris", (p.resources.debris() + p.trophySpacePoints()) / debrisPerVp);
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
            breakdown.put("arsenal_vp", cardVp);
        }

        int total = 0;
        for (int v : breakdown.values()) {
            total += v;
        }
        // ВАРИАНТ ПРАВИЛ (2026-08-15): ПО за КАЖДЫЙ обломок в хранилище, по
        // ДРОБНОЙ ставке economy.debris_storage_vp_per_unit (например 0.5) —
        // отсутствует/0 в обычном рулсете, тогда эта ветка не влияет ни на что.
        // Округление вниз — ТОЛЬКО на итоговой сумме партии, не на этом
        // источнике отдельно: остальные компоненты уже целые (int), поэтому
        // достаточно один раз сложить их с дробным вкладом и один раз floor().
        double debrisStorageVpPerUnit = ((Number) rs.get("economy.debris_storage_vp_per_unit", 0))
            .doubleValue();
        // Как и выше (см. правило про несданные трофеи на конец партии) — этот
        // источник тоже смотрит на полную ценность несданных трофеев, не только
        // на уже сконвертированные обломки в хранилище.
        double debrisStorageVpRaw = (p.resources.debris() + p.trophySpacePoints())
            * debrisStorageVpPerUnit;
        if (debrisStorageVpRaw != 0) {
            breakdown.put("debris_storage_vp", (int) Math.floor(debrisStorageVpRaw));
            total = (int) Math.floor(total + debrisStorageVpRaw);
        }
        // ТОЧКА ПРАВИЛ: правка УЖЕ СУЩЕСТВУЮЩЕГО итога («улучшить существующий
        // критерий»). Спрашивается после суммы, поэтому карта видит полный счёт.
        total = kelium.engine.ability.RuleQuery
            .of(state, seat, kelium.engine.ability.Hook.SCORING_VP_MODIFIER)
            .base(total).ask();
        breakdown.put("total", total);
        return breakdown;
    }

    /** Посчитать разбивку очков для всех игроков: {seat: breakdown}. */
    public static Map<Integer, Map<String, Integer>> scoreAll(GameState state) {
        Map<Integer, Map<String, Integer>> out = new HashMap<>();
        for (int seat = 0; seat < state.numPlayers(); seat++) {
            out.put(seat, scorePlayer(state, seat));
        }
        return out;
    }
}
