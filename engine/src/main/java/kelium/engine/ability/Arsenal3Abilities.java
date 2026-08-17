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
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Placement;
import kelium.engine.Storage;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 2.3 — способности по ревью дизайнера 17.08.2026.
 *
 * <p>Ревью прошло по двум спискам сразу: по подключённой колоде (что переделать)
 * и по заметкам, до колоды не дошедшим (что добавить). Общий приговор повторялся
 * почти дословно: «имба». Половина сильных карт давала БЕЗУСЛОВНОЕ улучшение —
 * «вся техника быстрее», «строй где хочешь», «здания прочнее», — и такую карту
 * не выбирают, её просто берут. Здесь у каждой такой способности появилось
 * УСЛОВИЕ, за которое игрок платит расстановкой:
 *
 * <ul>
 *   <li>скорость даётся не роду, а жетону, который идёт НЕ ОДИН;</li>
 *   <li>вольная застройка требует, чтобы на гексе уже стояли твои войска;</li>
 *   <li>прочность даётся только зданиям экономики и стоит их возврата в запас;</li>
 *   <li>захват чужого жетона стоит своего.</li>
 * </ul>
 *
 * <p>Второй сквозной мотив ревью — карта обязана быть ВЫБОРОМ, а не подарком.
 * Поэтому «Двойной протокол» больше не добавляет действие, а МЕНЯЕТ действие
 * верхнего приказа на действие нижнего: пользоваться этим можно, а можно и нет.
 */
public final class Arsenal3Abilities {

    private Arsenal3Abilities() {
    }

    public static void install() {
        Abilities.register(new SpeedTogetherGround());
        Abilities.register(new TowerRidesWithAircraft());
        Abilities.register(new AircraftSlowTough());
        Abilities.register(new BuildingsHp1BecomeHp2());
        Abilities.register(new EconomyPlus1HpFragile());
        Abilities.register(new BuildOnAdjacentWithOwnUnits());
        Abilities.register(new PayEnergyWithCoin());
        Abilities.register(new DebrisCellsPlus2());
        Abilities.register(new SecondEnergyHexFree());
        Abilities.register(new InfantryJumpsSpawnTile());
        Abilities.register(new TwoSpecWithoutSecurity());
        Abilities.register(new BottomOrderInsteadOfTop());
        Abilities.register(new SpecHireForAmmo());
        Abilities.register(new SpecSwapGroundUnit());
        Abilities.register(new SpecShiftMinerCell());
        Abilities.register(new SpecBoarding());
        Abilities.register(new SpecRansomPrisoners());
        Abilities.register(new SpecKeliumRain());
        Abilities.register(new SpecNuclearStrike());
        Abilities.register(new AmmoOnRetaliationKill());
        // Супер-арсенал 2.0 (17.08.2026): у каждого супер-войска своя способность.
        Abilities.register(new SuperInfantryDemolition());
        Abilities.register(new SuperVehicleHarvester());
        Abilities.register(new SuperAircraftTransport());
        Abilities.register(new SuperTowerBarrage());
    }

    static {
        install();
    }

    // ==================================================================
    //  СКОРОСТЬ — теперь за строй, а не просто так
    // ==================================================================

    /**
     * «Ускоренный марш»: пехота и техника получают +1 к скорости, ЕСЛИ идут
     * вместе — то есть на их гексе стоит и пехота, и техника.
     *
     * <p>Было «+1 всей пехоте и технике» без условий, дизайнер отверг: такую
     * карту не выбирают, её берут. Условие «вместе» стоит игроку планировки —
     * два рода надо свести на один гекс и вести колонной, а колонна и бьётся
     * вся сразу.
     */
    private static final class SpeedTogetherGround implements Ability {

        @Override public String id() {
            return "speed_plus1_ground_together";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED);
        }

        @Override public void modify(RuleQuery q) {
            UnitToken u = q.unitToken();
            if (u == null || u.hexId == null) {
                return;   // спросили про род вообще — условие непроверяемо
            }
            if (u.type != UnitType.INFANTRY && u.type != UnitType.VEHICLE) {
                return;
            }
            PlayerState me = q.state().player(q.seat());
            boolean infantry = false;
            boolean vehicle = false;
            for (UnitToken other : me.unitsOnField()) {
                if (!u.hexId.equals(other.hexId)) {
                    continue;
                }
                infantry |= other.type == UnitType.INFANTRY;
                vehicle |= other.type == UnitType.VEHICLE;
            }
            if (infantry && vehicle) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.0, Horizon.REST_OF_GAME,
                (s, seat) -> !s.player(seat).unitsOnField().isEmpty(),
                "нет войск на поле", false);
        }
    }

    /**
     * «Форсаж»: вышка получает +1 к скорости, если на её гексе есть своя авиация.
     *
     * <p>Вышка неподвижна по планшету (скорость 0) — это и есть её плата за
     * дешевизну. Карта делает её подвижной ровно там, где рядом своя авиация:
     * авиацию надо привести и держать, то есть заплатить самым редким родом.
     */
    private static final class TowerRidesWithAircraft implements Ability {

        @Override public String id() {
            return "tower_rides_with_aircraft";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED);
        }

        @Override public void modify(RuleQuery q) {
            UnitToken u = q.unitToken();
            if (u == null || u.type != UnitType.TOWER || u.hexId == null) {
                return;
            }
            for (UnitToken other : q.state().player(q.seat()).unitsOnField()) {
                if (other.type == UnitType.AIRCRAFT && u.hexId.equals(other.hexId)) {
                    q.add(1).by(id());
                    return;
                }
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 0.8, Horizon.REST_OF_GAME, null,
                "вышка едет только под своей авиацией", false);
        }
    }

    /**
     * «Тяжёлое крыло»: вся своя авиация −1 к скорости и +1 к прочности.
     *
     * <p>Зеркало «Облегчённой брони» (техника быстрее и хрупче) — дизайнер
     * попросил парную карту. Авиация — самый редкий род на поле именно потому,
     * что дорога и умирает с одного попадания; эта карта меняет её роль с
     * налётчика на держателя воздушной ячейки.
     */
    private static final class AircraftSlowTough implements Ability {

        @Override public String id() {
            return "aircraft_slow_tough";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED, Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.hook() == Hook.UNIT_SPEED) {
                if (q.unitType() == UnitType.AIRCRAFT) {
                    // Скорость не опускается ниже 1: жетон, который не двигается
                    // вовсе, — это уже другая механика (вышка), и превращать в неё
                    // авиацию карта не должна.
                    q.atLeast(1);
                    q.add(-1).by(id());
                }
                return;
            }
            if (q.subject() instanceof UnitToken u && u.type == UnitType.AIRCRAFT) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.2, Horizon.REST_OF_GAME, null,
                "авиация живучее, но медленнее", false);
        }
    }

    // ==================================================================
    //  ПРОЧНОСТЬ ЗДАНИЙ
    // ==================================================================

    /**
     * «Укреплённые перекрытия»: свои здания с прочностью 1 получают прочность 2.
     *
     * <p>Прямая просьба дизайнера — такой карты в колоде не было. Бьёт по самому
     * тонкому месту застройки: добытчики и энергостанции нечётных номеров сносятся
     * одним попаданием, и потому экономику невыгодно выносить к фронту.
     */
    private static final class BuildingsHp1BecomeHp2 implements Ability {

        @Override public String id() {
            return "buildings_hp1_become_hp2";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof BuildingToken b && q.current() <= 1
                    && b.type != BuildingType.COMMAND_CENTER) {
                q.atLeast(2);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.5, Horizon.REST_OF_GAME, null,
                "здания в один удар больше не сносятся", false);
        }
    }

    /**
     * «Аварийные щиты» (переделаны): добытчики и энергостанции с прочностью 1
     * получают +1 к прочности, но ПОСЛЕ БОЯ, если на таком здании оказался урон,
     * оно немедленно возвращается владельцу в запас.
     *
     * <p>Прежняя редакция давала +1 прочности ВСЕМ зданиям до конца раунда —
     * дизайнер: «имба лютейшая». Теперь карта не спасает здание, а МЕНЯЕТ ФОРМУ
     * его потери: вместо уничтожения (чужой трофей, чужие очки) здание уходит к
     * себе в запас. Противник тратит боеприпасы и не получает ничего, а игрок
     * теряет постройку и место на поле. Ровно та формулировка, которую просил
     * дизайнер, и она умещается на карту.
     */
    private static final class EconomyPlus1HpFragile implements Ability {

        @Override public String id() {
            return "economy_plus1_hp_returns_on_damage";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        /** Здание экономики с ПЕЧАТНОЙ прочностью 1 — только оно под щитом. */
        static boolean covered(Token t, int printedHp) {
            return t instanceof BuildingToken b && printedHp <= 1
                && (b.type == BuildingType.MINER || b.type == BuildingType.POWER_PLANT);
        }

        @Override public void modify(RuleQuery q) {
            if (covered((Token) q.subject(), (int) q.current())) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.0, Horizon.REST_OF_GAME, null,
                "экономика переживает удар, но уходит в запас", false);
        }
    }

    // ==================================================================
    //  СТРОЙКА И ЭНЕРГИЯ
    // ==================================================================

    /**
     * «Вольная застройка» (переделана): строить на соседнем гексе БЕЗ примыкания
     * стенкой можно, только если на этом гексе уже стоят твои войска.
     *
     * <p>Прежняя редакция снимала требование примыкания вообще — дизайнер:
     * «имба имбой». Теперь зона стройки растёт не сама, а следом за армией:
     * сначала туда надо дойти войском и удержать гекс.
     */
    private static final class BuildOnAdjacentWithOwnUnits implements Ability {

        @Override public String id() {
            return "build_on_adjacent_with_own_units";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.BUILD_ZONE);
        }

        // Саму зону считает Placement.buildableHexes: список гексов через точку
        // правил не проходит (она отвечает числом), поэтому расширение сделано
        // там, а здесь способность только объявляет себя — чтобы карта прошла
        // отсев на подготовке и попала в колоду.

        @Override public Hint hint() {
            return new Hint(Bottleneck.UNITS, 1.2, Horizon.REST_OF_GAME, null,
                "база идёт следом за армией", false);
        }
    }

    /**
     * «Аварийное питание»: разрешает закрывать недостающие ячейки ЭНР монетами.
     *
     * <p>ЭТО ЕДИНСТВЕННЫЙ ИСТОЧНИК ТАКОГО ПРАВА В ИГРЕ (решение дизайнера
     * 17.08.2026). Раньше доплата монетами была ОБЩИМ правилом, и это
     * обесценивало и Смену энергии, и планировку базы: дефицит энергии можно было
     * просто выкупить. Теперь общего правила нет, а карта его возвращает — и
     * потому стоит ровно столько, сколько стоил весь этот дефицит.
     */
    private static final class PayEnergyWithCoin implements Ability {

        @Override public String id() {
            return "pay_energy_with_coin";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ASSEMBLY_PAY_ENERGY_WITH_COIN);
        }

        @Override public void modify(RuleQuery q) {
            q.atLeast(1);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 2.5, Horizon.REST_OF_GAME, null,
                "нехватку энергии можно закрыть деньгами", false);
        }
    }

    /**
     * «Второй контур»: ВТОРОЙ выбранный гекс в Смене энергии не стоит надбавки.
     *
     * <p>Третий и далее — по обычной надбавке: карта снимает ровно одну ступень,
     * а не отменяет правило (вопрос дизайнеру закрыт этим словом).
     */
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
            // subject — номер гекса в этом действии (1 — первый, он и так даром).
            if (q.subject() instanceof Number n && n.intValue() == 2) {
                q.atMost(0).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 1.0, Horizon.THIS_ROUND, null,
                "второй гекс в Смене энергии бесплатен", false);
        }
    }

    /**
     * «Десантные тропы»: пехота может ПЕРЕПРЫГНУТЬ гекс с тайлом зарождения —
     * за ОДНО перемещение, сразу на противоположный гекс.
     *
     * <p>Цена прыжка — один шаг (решение дизайнера), то есть тайл перестаёт быть
     * стеной, но и бесплатной дорогой не становится. Встать на сам тайл
     * по-прежнему нельзя.
     */
    private static final class InfantryJumpsSpawnTile implements Ability {

        @Override public String id() {
            return "infantry_jumps_spawn_tile";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.MOVEMENT_JUMP_OVER);
        }

        @Override public void modify(RuleQuery q) {
            UnitToken u = q.unitToken();
            if (u == null || u.type == UnitType.INFANTRY) {
                q.atLeast(1);
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.0, Horizon.REST_OF_GAME, null,
                "пехота ходит через зарождения", false);
        }
    }

    /**
     * «Параллельные штабы»: два СПЕЦ-действия за ход — но только в тот ход, когда
     * ты НЕ сыграл карту БЕЗОПАСНОСТЬ.
     *
     * <p>Оговорка про Безопасность и делает карту выбором: страховка приказа или
     * лишнее спец-действие, не то и другое сразу.
     */
    private static final class TwoSpecWithoutSecurity implements Ability {

        @Override public String id() {
            return "two_spec_without_security";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ORDER_SPEC_COUNT);
        }

        @Override public void modify(RuleQuery q) {
            for (String cid : q.state().player(q.seat()).orderPlayed) {
                if (cid != null && cid.toLowerCase(java.util.Locale.ROOT).contains("security")) {
                    return;   // Безопасность сыграна — второго СПЕЦ не будет
                }
            }
            q.atLeast(2);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 2.0, Horizon.THIS_ROUND, null,
                "второе спец-действие вместо страховки приказа", false);
        }
    }

    /**
     * «Двойной протокол» / «Параллельный контур» (переделаны): можно сыграть ещё
     * одно действие НИЖНЕГО приказа ВМЕСТО одного действия верхнего.
     *
     * <p>Прежняя редакция просто добавляла действие — дизайнер: «немного имбово».
     * Теперь это размен, а не подарок: верх теряет действие, низ получает, и
     * повторить внизу можно даже то, что уже сыграно. Пользоваться необязательно.
     */
    private static final class BottomOrderInsteadOfTop implements Ability {

        @Override public String id() {
            return "bottom_order_instead_of_top";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.ORDER_BOTTOM_ACTIONS);
        }

        @Override public void modify(RuleQuery q) {
            q.atLeast(2);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 2.0, Horizon.THIS_ROUND, null,
                "действие нижнего приказа вместо верхнего", false);
        }
    }

    // ==================================================================
    //  СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * «Ремонтная летучка» (переделана): СПЕЦ за 1 боеприпас — найм любым своим
     * запитанным зданием.
     *
     * <p>Починки в игре не будет (решение дизайнера): урон копится и снимается
     * только правилами боя. Вместо неё карта даёт то, чего действительно не
     * хватает вне приказа Разработка, — один жетон войска в нужный момент.
     */
    private static final class SpecHireForAmmo implements Ability, OptionSource {

        @Override public String id() {
            return "spec_hire_for_ammo";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Свои запитанные здания, которым есть кого и куда нанять. */
        private static List<BuildingToken> ready(GameState state, int seat) {
            PlayerState me = state.player(seat);
            List<BuildingToken> out = new ArrayList<>();
            for (BuildingToken b : me.buildingsOnField()) {
                UnitType ut = kelium.engine.Actions.ASSEMBLY_UNIT.get(b.type);
                if (ut == null || !b.powered()) {
                    continue;
                }
                if (me.unitsOfKind(ut) < state.tokenStats.unitStock(ut)) {
                    out.add(b);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (BuildingToken b : ready(state, seat)) {
                out.add(new Choice("ability:" + id(), b.uid,
                    "СПЕЦ (1 БПР): найм в " + b.type.code + " @" + b.hexId));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.AMMO, 1)) {
                return false;
            }
            BuildingToken target = null;
            for (BuildingToken b : ready(state, seat)) {
                if (chosen != null && Integer.valueOf(b.uid).equals(chosen.payload())) {
                    target = b;
                    break;
                }
            }
            if (target == null) {
                List<BuildingToken> all = ready(state, seat);
                if (all.isEmpty()) {
                    return false;
                }
                target = all.get(0);
            }
            UnitType ut = kelium.engine.Actions.ASSEMBLY_UNIT.get(target.type);
            // НАЙМ ИДЁТ НА ГЕКС СО ЗДАНИЕМ (правило 17.08.2026): внутрь здания
            // войско на найме не встаёт нигде, включая карты.
            if (!Placement.hasRoomOnHex(state, me, target.hexId, ut)) {
                return false;
            }
            me.resources.pay(Resource.AMMO, 1);
            UnitToken u = state.tokenStats.makeUnit(ut, seat, Placement.nextUid(state),
                me.unitsOfKind(ut));
            u.hexId = target.hexId;
            me.units.add(u);
            kelium.engine.PrintedContainers.onUnitPlaced(state, me, target.hexId, ut);
            return true;
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            return perform(state, seat, null, agent);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 1.5, Horizon.THIS_ROUND, null,
                "найм вне приказа Разработка", false);
        }
    }

    /**
     * «Переформирование» (бывшие «Геологи»): СПЕЦ за 2 монеты — заменить на поле
     * один свой жетон НАЗЕМНОГО войска на другой наземный жетон из запаса.
     *
     * <p>Прежние «Геологи» давали лишнее трофейное очко за каждый переворот тайла
     * — дизайнер: «имба, мусор». Замена жетона не добавляет игроку ничего, она
     * ПЕРЕСОБИРАЕТ уже стоящее: пехота на месте техники и наоборот. Полезно ровно
     * тогда, когда рода войск оказались не там, где нужны.
     */
    private static final class SpecSwapGroundUnit implements Ability, OptionSource {

        private static final Set<UnitType> GROUND = EnumSet.of(UnitType.INFANTRY, UnitType.VEHICLE);

        @Override public String id() {
            return "spec_swap_ground_unit";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.COIN, 2)) {
                return List.of();
            }
            PlayerState me = state.player(seat);
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : me.unitsOnField()) {
                if (!GROUND.contains(u.type)) {
                    continue;
                }
                for (UnitType want : GROUND) {
                    if (want == u.type
                            || me.unitsOfKind(want) >= state.tokenStats.unitStock(want)) {
                        continue;
                    }
                    out.add(new Choice("ability:" + id(), new int[]{u.uid, want.ordinal()},
                        "СПЕЦ (2 МОН): " + u.type.code + " @" + u.hexId + " -> " + want.code));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof int[] pick)) {
                return false;
            }
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.COIN, 2)) {
                return false;
            }
            UnitToken old = null;
            for (UnitToken u : me.unitsOnField()) {
                if (u.uid == pick[0]) {
                    old = u;
                    break;
                }
            }
            if (old == null) {
                return false;
            }
            UnitType want = UnitType.values()[pick[1]];
            String hex = old.hexId;
            // Сначала снять старый жетон: место под новый считается уже без него
            // (техника занимает две ячейки, пехота одну — иначе размен «техника на
            // пехоту» упирался бы в собственный след).
            old.setHexId(null);
            old.resetDamage();
            if (!Placement.hasRoomOnHex(state, me, hex, want)) {
                old.setHexId(hex);   // откат: места новому жетону нет
                return false;
            }
            me.resources.pay(Resource.COIN, 2);
            UnitToken fresh = state.tokenStats.makeUnit(want, seat, Placement.nextUid(state),
                me.unitsOfKind(want));
            fresh.hexId = hex;
            me.units.add(fresh);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ACTIONS, 1.0, Horizon.THIS_ROUND, null,
                "пересобрать наземный строй на месте", false);
        }
    }

    /**
     * «Маркшейдер»: СПЕЦ — бесплатно переставить один свой добытчик на другую
     * ячейку его гекса либо на ячейку соседнего гекса, к которой он примыкает
     * своей широкой стенкой.
     *
     * <p>Добытчик тянется к тайлу зарождения ПРИМЫКАНИЕМ (§11), и потому его
     * поворот решает всё. Обычная Стройка двигает здания целыми гексами и за
     * монету; эта карта двигает по ячейкам и даром — но только добытчик.
     */
    private static final class SpecShiftMinerCell implements Ability, OptionSource {

        @Override public String id() {
            return "spec_shift_miner_cell";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.MINER) {
                    continue;
                }
                for (String hid : List.of(b.hexId)) {
                    Hex h = state.field.get(hid);
                    if (h == null) {
                        continue;
                    }
                    for (int side = 0; side < 6; side++) {
                        if (h.sideOwner[side] == null) {
                            out.add(new Choice("ability:" + id(), new int[]{b.uid, side},
                                "СПЕЦ: развернуть добытчик @" + hid + " на ячейку " + side));
                        }
                    }
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof int[] pick)) {
                return false;
            }
            PlayerState me = state.player(seat);
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.uid != pick[0]) {
                    continue;
                }
                Hex h = state.field.get(b.hexId);
                if (h == null || h.sideOwner[pick[1]] != null) {
                    return false;
                }
                for (int side = 0; side < 6; side++) {
                    if (h.sideOwner[side] != null && h.sideOwner[side] == b.uid) {
                        h.sideOwner[side] = null;
                    }
                }
                h.occupySides(b.uid, List.of(pick[1]));
                return true;
            }
            return false;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 1.5, Horizon.REST_OF_GAME, null,
                "дотянуть добытчик до жилы без перестройки", false);
        }
    }

    /**
     * «Абордаж»: СПЕЦ — забери в свои трофеи любой чужой жетон с гекса, где стоит
     * твоя пехота, и верни эту пехоту в свой запас.
     *
     * <p>Размен своего жетона на чужой без выстрела. Захват НЕ считается
     * уничтожением: он не открывает ответный бой, не идёт в счётчик убийств и не
     * засчитывается заданиям на уничтожение — иначе карта стала бы обходом
     * штурма ЦУ. В трофейное пространство жетон ложится обычным порядком, то
     * есть на Науку он годится и владельцу вернётся в Возврат.
     */
    private static final class SpecBoarding implements Ability, OptionSource {

        @Override public String id() {
            return "spec_boarding";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Пары «моя пехота — чужой жетон на её гексе». */
        private static List<int[]> pairs(GameState state, int seat) {
            List<int[]> out = new ArrayList<>();
            PlayerState me = state.player(seat);
            for (UnitToken u : me.unitsOnField()) {
                if (u.type != UnitType.INFANTRY || u.inside()) {
                    continue;
                }
                for (PlayerState other : state.players) {
                    if (other.seat == seat) {
                        continue;
                    }
                    for (UnitToken v : other.unitsOnField()) {
                        if (u.hexId.equals(v.hexId)) {
                            out.add(new int[]{u.uid, v.uid});
                        }
                    }
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (int[] pair : pairs(state, seat)) {
                out.add(new Choice("ability:" + id(), pair,
                    "СПЕЦ: абордаж — забрать чужой жетон, пехота уходит в запас"));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            int[] pick = chosen != null && chosen.payload() instanceof int[] p ? p : null;
            if (pick == null) {
                List<int[]> all = pairs(state, seat);
                if (all.isEmpty()) {
                    return false;
                }
                pick = all.get(0);
            }
            PlayerState me = state.player(seat);
            UnitToken mine = null;
            for (UnitToken u : me.unitsOnField()) {
                if (u.uid == pick[0]) {
                    mine = u;
                    break;
                }
            }
            if (mine == null) {
                return false;
            }
            for (PlayerState other : state.players) {
                if (other.seat == seat) {
                    continue;
                }
                for (UnitToken v : other.unitsOnField()) {
                    if (v.uid != pick[1]) {
                        continue;
                    }
                    v.setHexId(null);
                    v.resetDamage();
                    v.setCapturedBy(seat);
                    me.trophySpace.add(v);
                    mine.setHexId(null);   // своя пехота ушла вместе с добычей
                    mine.resetDamage();
                    return true;
                }
            }
            return false;
        }

        @Override public boolean apply(GameState state, int seat, Agent agent) {
            return perform(state, seat, null, agent);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 2.0, Horizon.THIS_ROUND, null,
                "чужой жетон в трофеи ценой своей пехоты", false);
        }
    }

    /**
     * «Обмен пленными»: СПЕЦ — верни себе в запас свои жетоны с трофейного места
     * ОДНОГО противника, отдав ему за КАЖДЫЙ жетон РАЗНЫЙ ресурс.
     *
     * <p>Ресурсов четыре — монета, боеприпас, келемий, обломок, — и каждый идёт
     * ровно за один жетон, поэтому выкупить можно не больше четырёх, и цена
     * растёт не количеством, а разнообразием: последний жетон обходится в
     * келемий, то есть в победное очко. Прежняя редакция брала любой ресурс за
     * каждого и потому выкупала всех за горсть монет.
     */
    private static final class SpecRansomPrisoners implements Ability, OptionSource {

        private static final List<Resource> PRICE =
            List.of(Resource.COIN, Resource.AMMO, Resource.DEBRIS, Resource.KELIUM);

        @Override public String id() {
            return "spec_ransom_prisoners";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Мои жетоны в трофейном пространстве места {@code holder}. */
        private static List<Token> mine(GameState state, int seat, PlayerState holder) {
            List<Token> out = new ArrayList<>();
            for (Token t : holder.trophySpace) {
                if (t.owner() == seat) {
                    out.add(t);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (PlayerState holder : state.players) {
                if (holder.seat == seat || mine(state, seat, holder).isEmpty()) {
                    continue;
                }
                out.add(new Choice("ability:" + id(), holder.seat,
                    "СПЕЦ: выкупить своих у места " + (holder.seat + 1)));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer holderSeat)) {
                return false;
            }
            PlayerState me = state.player(seat);
            PlayerState holder = state.player(holderSeat);
            List<Token> prisoners = mine(state, seat, holder);
            int freed = 0;
            for (Token t : prisoners) {
                if (freed >= PRICE.size()) {
                    break;      // ресурсов четыре — больше четверых не выкупить
                }
                Resource pay = PRICE.get(freed);
                if (!me.resources.canPay(pay, 1)) {
                    break;      // нечем платить именно этим ресурсом — выкуп встал
                }
                me.resources.pay(pay, 1);
                holder.resources.add(pay, 1);
                holder.trophySpace.remove(t);
                t.setCapturedBy(null);
                t.resetDamage();
                t.setHexId(null);
                freed++;
            }
            return freed > 0;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.5, Horizon.REST_OF_GAME, null,
                "вернуть своих из чужих трофеев", false);
        }
    }

    /**
     * «Келемиевый дождь»: СПЕЦ — заплати 2 келемия, выбери гекс, верни ВСЕ
     * стоящие на нём жетоны владельцам в запас и положи туда СТАРТОВЫЙ тайл
     * зарождения.
     *
     * <p>Единственный способ создать источник келемия там, где его не было, и
     * единственный способ стереть чужую застройку, не воюя. Цена — два келемия,
     * то есть два победных очка, и она намеренно кусается: карта переписывает
     * поле, а не помогает по мелочи.
     *
     * <p>Тайл кладётся СТАРТОВЫЙ (малый): большой тайл даёт победное очко за
     * оборот, и раздавать такой источник картой было бы слишком.
     */
    private static final class SpecKeliumRain implements Ability, OptionSource {

        @Override public String id() {
            return "spec_kelium_rain";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Гексы, куда можно пролить дождь: без тайла и не запретные. */
        private static List<String> targets(GameState state) {
            List<String> out = new ArrayList<>();
            for (Hex h : state.field.hexes.values()) {
                if (!h.hasSpawnTile() && h.kind != kelium.core.HexKind.FORBIDDEN
                        && !h.hasNeutral()) {
                    out.add(h.id);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.KELIUM, 2)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (String hid : targets(state)) {
                out.add(new Choice("ability:" + id(), hid,
                    "СПЕЦ (2 КЕЛ): келемиевый дождь на " + hid));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof String hexId)) {
                return false;
            }
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.KELIUM, 2)) {
                return false;
            }
            Hex h = state.field.get(hexId);
            if (h == null || h.hasSpawnTile()) {
                return false;
            }
            me.resources.pay(Resource.KELIUM, 2);
            // ВСЕ жетоны с гекса — владельцам в запас. Это НЕ уничтожение:
            // трофеев никто не получает, ответного боя не происходит.
            for (PlayerState pl : state.players) {
                for (UnitToken u : new ArrayList<>(pl.unitsOnField())) {
                    if (hexId.equals(u.hexId)) {
                        u.setHexId(null);
                        u.resetDamage();
                    }
                }
                for (BuildingToken b : new ArrayList<>(pl.buildingsOnField())) {
                    if (hexId.equals(b.hexId)) {
                        // ownTurnChoice: закрытие ячеек своего склада игрок
                        // выбирает сам (это его СПЕЦ-действие), чужого —
                        // фиксированным порядком (не его ход).
                        kelium.engine.Actions.returnOwnBuildingToReserve(
                            state, pl, b, pl.seat == seat);
                    }
                }
            }
            for (int side = 0; side < 6; side++) {
                h.sideOwner[side] = null;
            }
            h.groundTokens.clear();
            h.airToken = null;
            // СТАРТОВЫЙ (малый) тайл: лицо 2 келемия, оборот 1, одна штука.
            // Большой тайл даёт за оборот победное очко — такой источник картой
            // не раздаётся.
            h.spawnTile = new kelium.core.SpawnTile(true, 2, 1, 1);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 3.0, Horizon.REST_OF_GAME, null,
                "новый источник келемия ценой двух очков", false);
        }
    }

    /**
     * «Ядерный удар»: СПЕЦ — заплати 4 боеприпаса, выбери гекс, положи на него
     * тайл ЗАПРЕТНОГО гекса. Все стоявшие там жетоны возвращаются владельцам в
     * запас; за каждый ЧУЖОЙ жетон ты получаешь 1 обломок, но не больше 4.
     *
     * <p>Пара к «Келемиевому дождю» и его противоположность. Дождь платит
     * келемием — то есть победными очками — и СОЗДАЁТ источник; удар платит
     * боеприпасами — то есть военным ресурсом — и НАВСЕГДА ВЫЧЁРКИВАЕТ гекс из
     * игры. Обе карты стирают с гекса всё, и обе делают это МИМО боя: жетоны не
     * уничтожаются, а возвращаются владельцам, значит ни трофеев, ни ответного
     * боя, ни зачёта заданиям на уничтожение. Плата обломками идёт только за
     * ЧУЖИЕ жетоны — своими игрок платит, а не зарабатывает.
     *
     * <p>Потолок в 4 обломка нужен, чтобы удар по чужой базе не превращался в
     * разовый скачок по науке: четыре обломка — это ровно один дорогой шаг.
     */
    private static final class SpecNuclearStrike implements Ability, OptionSource {

        private static final int AMMO_COST = 4;
        private static final int MAX_DEBRIS = 4;

        @Override public String id() {
            return "spec_nuclear_strike";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Куда можно бить: любой гекс, ещё не выжженный и без тайла зарождения. */
        private static List<String> targets(GameState state) {
            List<String> out = new ArrayList<>();
            for (Hex h : state.field.hexes.values()) {
                if (h.kind != kelium.core.HexKind.FORBIDDEN && !h.hasSpawnTile()) {
                    out.add(h.id);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.AMMO, AMMO_COST)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (String hid : targets(state)) {
                out.add(new Choice("ability:" + id(), hid,
                    "СПЕЦ (" + AMMO_COST + " БПР): ядерный удар по " + hid));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof String hexId)) {
                return false;
            }
            PlayerState me = state.player(seat);
            Hex h = state.field.get(hexId);
            if (h == null || h.kind == kelium.core.HexKind.FORBIDDEN || h.hasSpawnTile()
                    || !me.resources.canPay(Resource.AMMO, AMMO_COST)) {
                return false;
            }
            me.resources.pay(Resource.AMMO, AMMO_COST);

            int enemyTokens = 0;
            for (PlayerState pl : state.players) {
                boolean foreign = pl.seat != seat;
                for (UnitToken u : new ArrayList<>(pl.unitsOnField())) {
                    if (hexId.equals(u.hexId)) {
                        u.setHexId(null);
                        u.resetDamage();
                        if (foreign) {
                            enemyTokens++;
                        }
                    }
                }
                for (BuildingToken b : new ArrayList<>(pl.buildingsOnField())) {
                    if (hexId.equals(b.hexId)) {
                        kelium.engine.Actions.returnOwnBuildingToReserve(
                            state, pl, b, !foreign);
                        if (foreign) {
                            enemyTokens++;
                        }
                    }
                }
            }
            // Нейтралы с гекса тоже уходят: там больше нечему стоять. Обломков за
            // них не дают — платят только за ЧУЖИЕ жетоны.
            h.neutrals.clear();
            for (int side = 0; side < 6; side++) {
                h.sideOwner[side] = null;
            }
            h.groundTokens.clear();
            h.airToken = null;
            h.kind = kelium.core.HexKind.FORBIDDEN;

            int debris = Math.min(enemyTokens, MAX_DEBRIS);
            if (debris > 0) {
                Storage.addDebrisCapped(state, me, debris);
            }
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 3.0, Horizon.REST_OF_GAME, null,
                "выжечь гекс и снять обломки с чужих жетонов", false);
        }
    }

    /**
     * «Ответный залп» (переделан): боеприпас приходит, только если контратака
     * УНИЧТОЖИЛА твой жетон.
     *
     * <p>Прежняя редакция платила за сам факт контратаки — то есть за то, что
     * противник потратил боеприпасы и, возможно, ничего не добился. Дизайнер:
     * платить надо за понесённую потерю. Реакция считается в
     * {@link kelium.engine.CombatResolver} по журналу хода.
     */
    private static final class AmmoOnRetaliationKill implements Ability {

        @Override public String id() {
            return "ammo_on_retaliation_kill";
        }

        @Override public Trigger trigger() {
            return Trigger.ON_EVENT;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 0.8, Horizon.THIS_ROUND, null,
                "потеря в ответном бою возвращает боеприпас", false);
        }
    }

    // ==================================================================
    //  ХРАНЕНИЕ
    // ==================================================================

    /**
     * «Трофейный сейф»: +2 ячейки склада, но ТОЛЬКО под обломки.
     *
     * <p>Заметка предлагала «три кубика сверх обычного места на любых ячейках» —
     * дизайнер свёл к простому и печатаемому: две ячейки, только обломки, и
     * больше ничего. Смысл: не терять накопленное на дорогой шаг науки к концу
     * раунда.
     */
    private static final class DebrisCellsPlus2 implements Ability {

        @Override public String id() {
            return "debris_cells_plus2";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.STORAGE_CELLS);
        }

        @Override public void modify(RuleQuery q) {
            if (Resource.DEBRIS.equals(q.subject())
                    || "debris".equals(String.valueOf(q.subject()))) {
                q.add(2).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 1.2, Horizon.REST_OF_GAME, null,
                "обломки доживают до дорогого шага науки", false);
        }
    }

    // ==================================================================
    //  СУПЕР-АРСЕНАЛ 2.0 (17.08.2026) — способности четырёх супер-войск
    // ==================================================================

    /**
     * sa1 «Гвардия «Кель»» — супер-пехота: СПЕЦ (1 БПР) сносит здание
     * противника прямо на своём гексе, без боя и без ответки, и забирает его
     * в трофеи. ЦУ обрабатывается тем же путём, что и в обычном бою
     * ({@link kelium.engine.CombatResolver#destroy}) — жетон уничтожения ЦУ, компенсация
     * контейнерами, проверка военной победы. Скорость 2 — печатная
     * характеристика жетона, см. {@link kelium.engine.Speed#of(GameState,
     * int, kelium.core.UnitToken)}.
     */
    private static final class SuperInfantryDemolition implements Ability, OptionSource {

        @Override public String id() {
            return "super_infantry_demolition";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static BuildingToken enemyBuildingOn(GameState state, String hexId, int seat) {
            if (hexId == null) {
                return null;
            }
            for (PlayerState pl : state.players) {
                if (pl.seat == seat) {
                    continue;
                }
                for (BuildingToken b : pl.buildingsOnField()) {
                    if (hexId.equals(b.hexId)) {
                        return b;
                    }
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!u.superUnit || !"sa1".equals(u.superCardId)) {
                    continue;
                }
                BuildingToken b = enemyBuildingOn(state, u.hexId, seat);
                if (b != null) {
                    out.add(new Choice("ability:" + id(), u.uid,
                        "СПЕЦ (1 БПР): подрыв — снести " + b.type.code
                            + " игрока " + b.owner + " на " + u.hexId));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer uid)) {
                return false;
            }
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.AMMO, 1)) {
                return false;
            }
            UnitToken u = null;
            for (UnitToken x : me.unitsOnField()) {
                if (x.uid == uid) {
                    u = x;
                    break;
                }
            }
            if (u == null || !u.superUnit || !"sa1".equals(u.superCardId)) {
                return false;
            }
            BuildingToken b = enemyBuildingOn(state, u.hexId, seat);
            if (b == null) {
                return false;
            }
            me.resources.pay(Resource.AMMO, 1);
            ((kelium.engine.CombatResolver) state.combat).destroy(b, seat);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 3.0, Horizon.REST_OF_GAME, null,
                "снос здания без боя за 1 боеприпас", false);
        }
    }

    /**
     * sa2 «Тяжёлый танк «Раздор»» — супер-техника: СПЕЦ (бесплатно) добывает
     * 1 келемий с тайла зарождения на СОСЕДНЕМ гексе, без добытчика и без
     * проверки запитанности — та же выработка/переворот тайла, что и у
     * обычной добычи ({@link kelium.engine.Actions#mineFlatFromTile}).
     */
    private static final class SuperVehicleHarvester implements Ability, OptionSource {

        @Override public String id() {
            return "super_vehicle_harvester";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static String adjacentTileWithKelium(GameState state, String hexId) {
            if (hexId == null) {
                return null;
            }
            for (String nb : state.field.neighborsView(hexId)) {
                Hex h = state.field.get(nb);
                if (h != null && h.spawnTile != null && h.spawnTile.kelium > 0) {
                    return nb;
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!u.superUnit || !"sa2".equals(u.superCardId)) {
                    continue;
                }
                String tile = adjacentTileWithKelium(state, u.hexId);
                if (tile != null) {
                    out.add(new Choice("ability:" + id(), u.uid,
                        "СПЕЦ (бесплатно): добыть 1 келемий с " + tile));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer uid)) {
                return false;
            }
            PlayerState me = state.player(seat);
            UnitToken u = null;
            for (UnitToken x : me.unitsOnField()) {
                if (x.uid == uid) {
                    u = x;
                    break;
                }
            }
            if (u == null || !u.superUnit || !"sa2".equals(u.superCardId)) {
                return false;
            }
            String tile = adjacentTileWithKelium(state, u.hexId);
            if (tile == null) {
                return false;
            }
            return kelium.engine.Actions.mineFlatFromTile(state, me, tile, 1) > 0;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 1.0, Horizon.REST_OF_GAME, null,
                "1 келемий без добытчика и без запитанности", false);
        }
    }

    /**
     * sa3 «Штурмовик «Гроза»» — супер-авиация: СПЕЦ перемещает жетон на его
     * скорость (те же правила входа на гекс, что у обычного Движения —
     * {@link kelium.engine.Movement#canEnter}) и берёт с собой ЛЮБОЕ число
     * своих наземных жетонов (пехота, техника) с исходного гекса — они летят
     * вместе с ним, минуя обычные ограничения по вместимости гекса, ровно как
     * велит текст карты («перенесённые жетоны игнорируют здания на пути»).
     */
    private static final class SuperAircraftTransport implements Ability, OptionSource {

        @Override public String id() {
            return "super_aircraft_transport";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Все гексы, куда жетон дойдёт за свою скорость (BFS по canEnter). */
        private static List<String> destinations(GameState state, UnitToken u, int seat) {
            if (u.hexId == null) {
                return List.of();
            }
            int speed = kelium.engine.Speed.of(state, seat, u);
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            seen.add(u.hexId);
            List<String> frontier = new ArrayList<>(seen);
            for (int step = 0; step < speed; step++) {
                List<String> next = new ArrayList<>();
                for (String hid : frontier) {
                    for (String nb : state.field.neighborsView(hid)) {
                        if (seen.contains(nb)) {
                            continue;
                        }
                        if (kelium.engine.Movement.canEnter(state, u, nb, seat)) {
                            seen.add(nb);
                            next.add(nb);
                        }
                    }
                }
                frontier = next;
            }
            seen.remove(u.hexId);
            return new ArrayList<>(seen);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!u.superUnit || !"sa3".equals(u.superCardId)) {
                    continue;
                }
                for (String dest : destinations(state, u, seat)) {
                    out.add(new Choice("ability:" + id(), new Object[] {u.uid, dest},
                        "СПЕЦ: перелёт «Грозы» на " + dest + " с десантом наземных войск"));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Object[] pl) || pl.length != 2
                    || !(pl[0] instanceof Integer uid) || !(pl[1] instanceof String dest)) {
                return false;
            }
            PlayerState me = state.player(seat);
            UnitToken u = null;
            for (UnitToken x : me.unitsOnField()) {
                if (x.uid == uid) {
                    u = x;
                    break;
                }
            }
            if (u == null || !u.superUnit || !"sa3".equals(u.superCardId) || u.hexId == null) {
                return false;
            }
            if (!destinations(state, u, seat).contains(dest)) {
                return false;
            }
            String origin = u.hexId;
            u.hexId = dest;
            for (UnitToken g : me.unitsOnField()) {
                if (g.uid == u.uid) {
                    continue;
                }
                if (origin.equals(g.hexId)
                        && (g.type == UnitType.INFANTRY || g.type == UnitType.VEHICLE)) {
                    g.hexId = dest;
                }
            }
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 2.0, Horizon.THIS_ROUND, null,
                "десант поверх чужой застройки", false);
        }
    }

    /**
     * sa4 «Цитадель» — супер-вышка: СПЕЦ бьёт залпом по X РАЗНЫМ соседним
     * гексам, X — сколько боеприпасов игрок платит (1 за гекс, 1 удар на
     * гекс). Цель на каждом гексе — любой жетон, выбор игрока. «Удар» — тот же
     * урон, что у обычной атаки ({@link kelium.engine.CombatResolver#hit}), включая проверку
     * жетона щита и уничтожение при исчерпании прочности; список целей
     * спрашивается ЗАНОВО каждый удар, пока хватает боеприпасов и свободных
     * (ещё не битых в этом залпе) соседних гексов с целями.
     */
    private static final class SuperTowerBarrage implements Ability, OptionSource {

        @Override public String id() {
            return "super_tower_barrage";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static List<Token> targetsOn(GameState state, String hexId) {
            List<Token> out = new ArrayList<>();
            for (PlayerState pl : state.players) {
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

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources.canPay(Resource.AMMO, 1)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (UnitToken u : state.player(seat).unitsOnField()) {
                if (!u.superUnit || !"sa4".equals(u.superCardId) || u.hexId == null) {
                    continue;
                }
                boolean anyTarget = false;
                for (String nb : state.field.neighborsView(u.hexId)) {
                    if (!targetsOn(state, nb).isEmpty()) {
                        anyTarget = true;
                        break;
                    }
                }
                if (anyTarget) {
                    out.add(new Choice("ability:" + id(), u.uid,
                        "СПЕЦ (X БПР): залп «Цитадели» с " + u.hexId
                            + " по X разным соседним гексам"));
                }
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer uid)) {
                return false;
            }
            PlayerState me = state.player(seat);
            UnitToken u = null;
            for (UnitToken x : me.unitsOnField()) {
                if (x.uid == uid) {
                    u = x;
                    break;
                }
            }
            if (u == null || !u.superUnit || !"sa4".equals(u.superCardId) || u.hexId == null) {
                return false;
            }
            java.util.Set<String> struck = new java.util.HashSet<>();
            int hits = 0;
            // Потолок в 6 — просто число соседей гекса, не игровое ограничение.
            for (int i = 0; i < 6 && me.resources.canPay(Resource.AMMO, 1); i++) {
                List<Choice> opts = new ArrayList<>();
                opts.add(new Choice("pass", null, "прекратить залп"));
                for (String nb : state.field.neighborsView(u.hexId)) {
                    if (struck.contains(nb)) {
                        continue;
                    }
                    for (Token t : targetsOn(state, nb)) {
                        String label = t instanceof UnitToken ut ? ut.type.code
                            : ((BuildingToken) t).type.code;
                        opts.add(new Choice("barrage_hit", t,
                            "ударить по " + nb + ": " + label + " игрока " + t.owner()));
                    }
                }
                if (opts.size() == 1) {
                    break;
                }
                Choice pick = agent.choose(state, opts,
                    java.util.Map.of("kind", "barrage_hit", "hits", hits));
                if (pick == null || !"barrage_hit".equals(pick.kind())) {
                    break;
                }
                Token victim = (Token) pick.payload();
                String hex = victim instanceof UnitToken ut ? ut.hexId
                    : ((BuildingToken) victim).hexId;
                me.resources.pay(Resource.AMMO, 1);
                ((kelium.engine.CombatResolver) state.combat).hit(victim, seat);
                struck.add(hex);
                hits++;
            }
            return hits > 0;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 3.0, Horizon.REST_OF_GAME, null,
                "залп по X соседним гексам за X боеприпасов", false);
        }
    }
}
