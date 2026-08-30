package kelium.engine.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * НОВЫЙ АРСЕНАЛ — способности из списка дизайнера (12–13.08.2026).
 *
 * <p>Первая партия: способности, которые вводят НОВЫЕ игровые ходы, а не прибавку
 * к числу. Каждая объявляет свою точку правил ({@link Hook}) и своё самоописание
 * для бота ({@link Hint}), поэтому карта становится видимой боту без правки
 * оценщика — иначе новая карта висит в меню и не выбирается ни разу (так уже
 * было, см. журнал ревизии).
 *
 * <p>Порядок здесь — от самых «механических» к самым простым, чтобы читать файл
 * можно было как список того, что арсенал умеет добавлять к правилам.
 */
public final class ArsenalAbilities {

    private ArsenalAbilities() {
    }

    /** Зарегистрировать всю партию (идемпотентно). */
    public static void install() {
        Abilities.register(new AircraftProtectsHex());
        Abilities.register(new AttackRangeTwoWithAircraft());
        Abilities.register(new AirbaseEnergyMinusOne());
        Abilities.register(new SecondEnergyHexFree());
        Abilities.register(new PlantsPayCoins());
        Abilities.register(new SciencePayWithKelium());
        Abilities.register(new VehicleFastAndFragile());
        Abilities.register(new InfantryJumpsSpawn());
        Abilities.register(new HealOneForAmmo());
        Abilities.register(new MoveEnergyCubeSpec());
        Abilities.register(new ExtraCell("cell_plus1_ammo", Resource.AMMO));
        Abilities.register(new ExtraCell("cell_plus1_kelium", Resource.KELIUM));
    }

    // ==================================================================
    //  БОЙ
    // ==================================================================

    /**
     * «Ваша авиация защищает собой все ваши жетоны на своём гексе от атаки по
     * ним» — жетоны на гексе с авиацией нельзя выбрать целью, пока авиация жива.
     */
    private static final class AircraftProtectsHex implements Ability {

        @Override public String id() {
            return "aircraft_protects_hex";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ATTACK_PROTECT_HEX);
        }

        @Override public void modify(RuleQuery q) {
            // Спрашивают про КОНКРЕТНЫЙ гекс: защищён ли он. Значение 1 = да.
            if (!(q.subject() instanceof String hexId)) {
                return;
            }
            for (UnitToken u : q.state().player(q.seat()).units) {
                if (u.type == UnitType.AIRCRAFT && u.alive() && hexId.equals(u.hexId)) {
                    q.atLeast(1.0);
                    return;
                }
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 2.5, Horizon.REST_OF_GAME, null,
                "жетоны на гексе с моей авиацией нельзя атаковать", false);
        }
    }

    /**
     * «В действие Бой можешь выбрать целью гекс на расстоянии 2 вместо 1, если на
     * твоём атакующем гексе есть авиация» — авиация как целеуказатель.
     */
    private static final class AttackRangeTwoWithAircraft implements Ability {

        @Override public String id() {
            return "attack_range2_with_aircraft";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ATTACK_RANGE);
        }

        @Override public void modify(RuleQuery q) {
            if (!(q.subject() instanceof String sourceHex)) {
                return;
            }
            for (UnitToken u : q.state().player(q.seat()).units) {
                if (u.type == UnitType.AIRCRAFT && u.alive() && sourceHex.equals(u.hexId)) {
                    q.atLeast(2.0);
                    return;
                }
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 3.0, Horizon.REST_OF_GAME, null,
                "с гекса с авиацией бью на два гекса", false);
        }
    }

    // ==================================================================
    //  СНАРЯЖЕНИЕ И ЭНЕРГИЯ
    // ==================================================================

    /** «Ваша авиабаза требует на одну энергию меньше для действия Снаряжение». */
    private static final class AirbaseEnergyMinusOne implements Ability {

        @Override public String id() {
            return "airbase_energy_minus1";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ASSEMBLY_ENERGY_NEEDED);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof BuildingToken b && b.type == BuildingType.AIRBASE) {
                q.add(-1).atLeast(0);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 3.0, Horizon.REST_OF_GAME, null,
                "авиабаза собирает на одну энергию дешевле", false);
        }
    }

    /** «Ваше второе перемещение энергии в Смену энергии тоже бесплатно». */
    private static final class SecondEnergyHexFree implements Ability {

        @Override public String id() {
            return "second_energy_hex_free";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ENERGY_SWAP_COST);
        }

        @Override public void modify(RuleQuery q) {
            // Спрашивают цену ВТОРОГО и следующих гексов: номер в subject.
            if (q.subject() instanceof Number n && n.intValue() == 2) {
                q.atMost(0);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 2.0, Horizon.REST_OF_GAME, null,
                "второй гекс в Смене энергии бесплатен", false);
        }
    }

    /**
     * «Ваши энергостанции в этап Обновления приносят доход в монетах за каждый
     * кубик энергии на них» — источники начинают кормить казну.
     */
    private static final class PlantsPayCoins implements Ability {

        @Override public String id() {
            return "plants_pay_coins";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.REFRESH_INCOME);
        }

        @Override public void modify(RuleQuery q) {
            int coins = 0;
            for (BuildingToken b : q.state().player(q.seat()).buildingsOnField()) {
                if (b.type == BuildingType.POWER_PLANT) {
                    coins += b.energyIdle + b.energyPlaced;
                }
            }
            q.add(coins);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.COINS, 3.5, Horizon.REST_OF_GAME, null,
                "энергостанции платят монетами каждый раунд", false);
        }
    }

    /** «Можете использовать келемий вместо трофейных очков в действие Наука». */
    private static final class SciencePayWithKelium implements Ability {

        @Override public String id() {
            return "science_pay_with_kelium";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.SCIENCE_PAY_WITH);
        }

        @Override public void modify(RuleQuery q) {
            q.atLeast(1.0);   // 1 = «келемий тоже годится»
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 4.0, Horizon.REST_OF_GAME, null,
                "за шаги науки можно платить келемием", false);
        }
    }

    // ==================================================================
    //  ЖЕТОНЫ И ДВИЖЕНИЕ
    // ==================================================================

    /** «Ваша техника получает +1 к скорости и −1 к здоровью». */
    private static final class VehicleFastAndFragile implements Ability {

        @Override public String id() {
            return "vehicle_speed_plus1_hp_minus1";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED, Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.hook() == Hook.UNIT_SPEED && q.unitType() == UnitType.VEHICLE) {
                q.add(1);
            }
            if (q.hook() == Hook.TOKEN_HP && q.subject() instanceof UnitToken u
                    && u.type == UnitType.VEHICLE) {
                q.add(-1).atLeast(1);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 2.0, Horizon.REST_OF_GAME, null,
                "техника быстрее, но хрупче", false);
        }
    }

    /**
     * «Ваша пехота может при движении перепрыгнуть через гекс с тайлом зарождения
     * на противоположный гекс» — тайл перестаёт быть стеной для пехоты.
     */
    private static final class InfantryJumpsSpawn implements Ability {

        @Override public String id() {
            return "infantry_jumps_spawn";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.MOVEMENT_JUMP_OVER);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof UnitToken u && u.type == UnitType.INFANTRY) {
                q.atLeast(1.0);   // 1 = «через тайл зарождения можно»
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 2.0, Horizon.REST_OF_GAME, null,
                "пехота перепрыгивает тайлы зарождения", false);
        }
    }

    // ==================================================================
    //  СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * «СПЕЦ: потратьте 1 боеприпас и снимите кубик урона с любого своего жетона».
     *
     * <p>С правилами 1.7.0 (урон не снимается в Обновление) это единственный
     * способ починить подбитый жетон — цена карты выросла сама собой.
     */
    private static final class HealOneForAmmo implements Ability, OptionSource {

        @Override public String id() {
            return "heal_one_for_ammo";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || damaged(state, seat).isEmpty()
                    || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: 1 боеприпас — снять кубик урона"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen,
                                         Agent agent) {
            return apply(state, seat, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            List<Token> hurt = damaged(state, seat);
            if (hurt.isEmpty() || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return false;
            }
            // Кого лечить — решает игрок: самый ценный подбитый жетон не всегда
            // тот, у кого больше урона.
            List<Choice> opts = new ArrayList<>();
            for (Token t : hurt) {
                opts.add(new Choice("heal_target", t.uid(),
                    "снять урон с жетона " + t.uid()));
            }
            Choice pick = agent == null ? opts.get(0)
                : agent.choose(state, opts, java.util.Map.of("kind", "heal_target"));
            int uid = pick.payload() instanceof Number n ? n.intValue()
                : ((Number) opts.get(0).payload()).intValue();
            PlayerState p = state.player(seat);
            for (UnitToken u : p.units) {
                if (u.uid == uid && u.damage > 0) {
                    p.resources.pay(Resource.AMMO, 1);
                    u.damage -= 1;         // ровно один кубик, как на карте
                    return true;
                }
            }
            for (BuildingToken b : p.buildings) {
                if (b.uid == uid && b.damage > 0) {
                    p.resources.pay(Resource.AMMO, 1);
                    b.damage -= 1;
                    return true;
                }
            }
            return false;
        }

        private static List<Token> damaged(GameState state, int seat) {
            List<Token> out = new ArrayList<>();
            PlayerState p = state.player(seat);
            for (UnitToken u : p.units) {
                if (u.hexId != null && u.damage > 0) {
                    out.add(u);
                }
            }
            for (BuildingToken b : p.buildings) {
                if (b.hexId != null && b.damage > 0) {
                    out.add(b);
                }
            }
            return out;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 3.5, Horizon.NOW, null,
                "единственный ремонт в игре: снять кубик урона за боеприпас", false);
        }
    }

    /** «СПЕЦ: перемести 1 свой кубик энергии на любое своё здание». */
    private static final class MoveEnergyCubeSpec implements Ability, OptionSource {

        @Override public String id() {
            return "spec_move_energy_cube";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || source(state, seat) == null || target(state, seat) == null) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: перенести кубик энергии на своё здание"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen,
                                         Agent agent) {
            return apply(state, seat, agent);
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            BuildingToken from = source(state, seat);
            BuildingToken to = target(state, seat);
            if (from == null || to == null) {
                return false;
            }
            if (from.energyIdle > 0) {
                from.energyIdle -= 1;
            } else {
                from.energyPlaced -= 1;
            }
            to.energyPlaced += 1;
            return true;
        }

        /** Откуда снять: сперва простаивающий кубик, иначе с запитанного здания. */
        private static BuildingToken source(GameState state, int seat) {
            BuildingToken fallback = null;
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.energyIdle > 0) {
                    return b;
                }
                if (b.energyPlaced > 0 && b.type != BuildingType.COMMAND_CENTER) {
                    fallback = b;
                }
            }
            return fallback;
        }

        /** Куда положить: здание, которому не хватает энергии. */
        private static BuildingToken target(GameState state, int seat) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.energyPlaced < b.energySlots) {
                    return b;
                }
            }
            return null;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 2.5, Horizon.NOW, null,
                "кубик энергии переезжает без действия Смена энергии", false);
        }
    }

    /**
     * «У тебя на этой карте одна дополнительная ячейка под боеприпас (или под
     * келемий)» — карта расширяет склад, оставаясь установленной.
     */
    private static final class ExtraCell implements Ability {

        private final String id;
        private final Resource what;

        ExtraCell(String id, Resource what) {
            this.id = id;
            this.what = what;
        }

        @Override public String id() {
            return id;
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.STORAGE_CELLS);
        }

        @Override public void modify(RuleQuery q) {
            // Ячейка ИМЕННОГО вида: спрашивают про конкретный ресурс.
            if (q.subject() == what || q.subject() == null) {
                q.add(1);
            }
        }

        @Override public Hint hint() {
            return new Hint(what == Resource.AMMO ? Bottleneck.AMMO : Bottleneck.KELIUM,
                2.5, Horizon.REST_OF_GAME, null,
                what == Resource.AMMO ? "лишняя ячейка под боеприпас"
                    : "лишняя ячейка под келемий", false);
        }
    }
}
