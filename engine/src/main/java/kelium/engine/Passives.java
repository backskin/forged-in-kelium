package kelium.engine;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.dataio.GameConfig;
import kelium.dataio.Ctx;

/**
 * Пассивные эффекты арсенала — нижняя (POST/SPEC) половина установленных карт.
 *
 * <p>Нижний эффект установленной карты арсенала — это именованный пассив. Модуль
 * читает список игрока {@code arsenalInstalled} и отвечает «есть ли у игрока
 * пассив X?», а также даёт числовые модификаторы, чтобы движок/действия/бой
 * учитывали их без жёсткой привязки к id карт.
 *
 * <p>Порт из forge/engine/passives.py. Немеханизированные пассивы инертны, но
 * видимы через {@link #hasPassive}.
 */
public final class Passives {

    private Passives() {
    }

    /** Собрать множество id пассивов из установленных карт арсенала игрока. */
    @SuppressWarnings("unchecked")
    private static Set<String> installedPassives(GameState s, int seat) {
        Set<String> out = new HashSet<>();
        PlayerState p = s.player(seat);
        var content = Ctx.cards(s, "arsenal");
        for (String cid : p.arsenalInstalled) {
            Map<String, Object> card;
            try {
                card = content.byId(cid);
            } catch (RuntimeException e) {
                continue;
            }
            Object bottomObj = card.get("bottom");
            if (bottomObj instanceof Map<?, ?> bottom) {
                Object pid = ((Map<String, Object>) bottom).get("passive");
                if (pid != null) {
                    out.add(pid.toString());
                }
            }
        }
        return out;
    }

    /** Есть ли у игрока установленный пассив с данным id. */
    public static boolean hasPassive(GameState s, int seat, String passiveId) {
        return installedPassives(s, seat).contains(passiveId)
            || superArsenalPassive(s, seat, passiveId);
    }

    /** Пассивка с карты СУПЕР-АРСЕНАЛА (вершина трека), удерживаемой игроком. */
    @SuppressWarnings("unchecked")
    public static boolean superArsenalPassive(GameState s, int seat, String passiveId) {
        var content = Ctx.content(s);
        for (String cid : s.player(seat).superArsenalCards) {
            var card = content.get("super_arsenal").find(cid);
            if (card != null && passiveId.equals(card.get("passive"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * E2: пассивки, РЕАЛЬНО подключённые к движку. Карты с прочими пассивками
     * отсеиваются из колоды на сетапе (Setup) — игрок не должен ставить карту,
     * которая ничего не делает.
     */
    public static final java.util.Set<String> IMPLEMENTED_PASSIVES = java.util.Set.of(
        "buildings_plus1_hp", "cu_plus2_hp", "plants_plus1_energy",
        "two_spec_actions", "no_second_battle_surcharge",
        "bonus_trophy_on_kill", "ammo_on_kill", "defenders_cost_more",
        "first_attack_minus1_ammo", "anti_armor_minus1_ammo",
        "aircraft_speed3_spawn", "first_extra_move_free", "first_two_moves_free",
        "extraction_flip_bonus_trophy", "miner_takes_kelium_and_container",
        // E2: SPEC-пассивки установленных карт (разовое действие в СПЕЦ-фазе,
        // реализованы в GameEngine.useInstalledSpec) — не отсеивать из колоды.
        // miner_takes_container и grab_adjacent_container УБРАНЫ 13.08.2026 вместе
        // с картами as6/a08/a19: обе опирались на «взять контейнер из запаса» и
        // «дотянуться с соседнего гекса» — отменено КОНТЕЙНЕРАМИ 2.0.
        "move_one_unit_1", "heal_one_damage",
        "deploy_1_unit", "move_one_module",
        // Стартовый арсенал 8 карт (2026-08-11): «Штаб связи» и «Изыскатели»
        "objective_hand_plus1", "science_first_step_discount");

    /** E2: реализована ли пассивка с данным id. */
    public static boolean isImplemented(String passiveId) {
        if (passiveId == null) {
            return false;
        }
        // РЕЕСТР СПОСОБНОСТЕЙ — первый источник истины (13.08.2026). Пассивка,
        // переведённая на реестр, реализована по определению: у неё есть точка
        // правил, и движок эту точку спрашивает (иначе валится тест-детектор
        // сирот). Прежний список остаётся для ещё не переведённых пассивок.
        if (kelium.engine.ability.Abilities.implemented(passiveId)) {
            return true;
        }
        return IMPLEMENTED_PASSIVES.contains(passiveId);
    }

    // --- числовые модификаторы, к которым обращается движок ------------------

    /** Бонус HP ко всем зданиям игрока (+1 при buildings_plus1_hp). */
    public static int buildingHpBonus(GameState s, int seat) {
        return hasPassive(s, seat, "buildings_plus1_hp") ? 1 : 0;
    }

    /** Бонус HP командному центру (+2 при cu_plus2_hp). */
    public static int cuHpBonus(GameState s, int seat) {
        return hasPassive(s, seat, "cu_plus2_hp") ? 2 : 0;
    }

    /** Бонус энергии каждой энергостанции (+1 при plants_plus1_energy). */
    public static int plantEnergyBonus(GameState s, int seat) {
        return hasPassive(s, seat, "plants_plus1_energy") ? 1 : 0;
    }

    /** Число доступных SPEC-действий за ход (2 при two_spec_actions, иначе 1). */
    public static int specActions(GameState s, int seat) {
        return hasPassive(s, seat, "two_spec_actions") ? 2 : 1;
    }

    /** Отменяет наценку за второй бой в ход (пассив no_second_battle_surcharge). */
    public static boolean noSecondBattleSurcharge(GameState s, int seat) {
        return hasPassive(s, seat, "no_second_battle_surcharge");
    }

    /** Доп. трофейное очко за убийство (+1 при bonus_trophy_on_kill). */
    public static int bonusTrophyOnKill(GameState s, int seat) {
        return hasPassive(s, seat, "bonus_trophy_on_kill") ? 1 : 0;
    }

    /** Доп. боеприпас за убийство (+1 при ammo_on_kill). */
    public static int ammoOnKill(GameState s, int seat) {
        return hasPassive(s, seat, "ammo_on_kill") ? 1 : 0;
    }

    /**
     * Печатное HP + бонусы HP от арсенала ВЛАДЕЛЬЦА жетона; вычисляется вживую,
     * чтобы установленные в середине игры пассивы (здания +1, ЦУ +2) учитывались
     * в момент уничтожения.
     */
    public static int effectiveHp(GameState s, Token token) {
        // B7: бонусы HP от пассивок теперь ВШИВАЮТСЯ в поле hp жетона в момент
        // установки/снятия карты (GameEngine.applyHpPassive) — «живость» едина
        // для всех проверок (alive(), фильтры целей, закрытый гекс, движение).
        int printed = token instanceof BuildingToken b ? b.hp
            : ((kelium.core.UnitToken) token).hp;
        // ТОЧКА ПРАВИЛ: прочность жетона может править карта арсенала
        // («техника +1 скорость, −1 прочность»). Спрашиваем ВЛАДЕЛЬЦА жетона:
        // способность чужой карты на мой жетон не действует.
        return (int) Math.round(kelium.engine.ability.RuleQuery
            .of(s, token.owner(), kelium.engine.ability.Hook.TOKEN_HP)
            .about(token).base(printed).atLeast(1).ask());
    }

    /**
     * Арсенал «Заграждения»: атака по твоим юнитам на гексе с твоим зданием
     * стоит атакующему +1 боеприпас.
     */
    public static int defenderAmmoSurcharge(GameState s, int defenderSeat, String hexId) {
        if (!hasPassive(s, defenderSeat, "defenders_cost_more")) {
            return 0;
        }
        for (BuildingToken b : s.player(defenderSeat).buildingsOnField()) {
            if (hexId.equals(b.hexId)) {
                return 1;
            }
        }
        return 0;
    }

    /** Активна ли скидка на первую атаку в бою (first_attack_minus1_ammo). */
    public static boolean firstAttackDiscountActive(GameState s, int seat) {
        return hasPassive(s, seat, "first_attack_minus1_ammo");
    }

    /** Активна ли скидка «противобронебойность» (-1 боеприпас по технике/зданиям). */
    public static boolean antiArmorDiscountActive(GameState s, int seat) {
        return hasPassive(s, seat, "anti_armor_minus1_ammo");
    }

    /** Бьёт ли ответка первой (пассив retaliation_strikes_first). */
    public static boolean retaliationFirst(GameState s, int seat) {
        return hasPassive(s, seat, "retaliation_strikes_first");
    }

    /** Переопределение скорости авиации (3 при aircraft_speed3_spawn, иначе null). */
    public static Integer aircraftSpeedOverride(GameState s, int seat) {
        return hasPassive(s, seat, "aircraft_speed3_spawn") ? Integer.valueOf(3) : null;
    }

    /** Бесплатен ли первый дополнительный шаг движения (first_extra_move_free). */
    public static boolean firstExtraMoveFree(GameState s, int seat) {
        return hasPassive(s, seat, "first_extra_move_free");
    }

    /** Бесплатны ли первые два дополнительных шага движения (first_two_moves_free). */
    public static boolean firstTwoMovesFree(GameState s, int seat) {
        return hasPassive(s, seat, "first_two_moves_free");
    }

    // Пассивка market_second_kelium_full и карта a07 «Перекупщик» УБРАНЫ 13.08.2026
    // (решение дизайнера). Геттер тоже убран: он никем не вызывался и три месяца
    // создавал видимость реализованной карты.

    /** Доп. ТО за переворот тайла зарождения (+1 при extraction_flip_bonus_trophy). */
    public static int extractionFlipBonusTrophy(GameState s, int seat) {
        // "Геологи" (арсенал 2.0.0) — та же награда под другим именем карты.
        return hasPassive(s, seat, "extraction_flip_bonus_trophy")
            || hasPassive(s, seat, "extra_trophy_on_spawn_flip") ? 1 : 0;
    }

    /** Берёт ли добытчик за раз и келемий, и контейнер («Глубокое бурение»). */
    public static boolean minerTakesKeliumAndContainer(GameState s, int seat) {
        return hasPassive(s, seat, "miner_takes_kelium_and_container");
    }

    /** Бонус ячейки хранилища (+1 при plus1_storage_cell). */
    public static int storageCellBonus(GameState s, int seat) {
        return hasPassive(s, seat, "plus1_storage_cell") ? 1 : 0;
    }
}
