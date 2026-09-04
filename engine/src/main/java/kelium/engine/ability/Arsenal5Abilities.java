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
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Placement;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 5.0 — низы карт по таблице дизайнера 02.09.2026.
 *
 * <p>Одиннадцать установок из двадцати двух были новыми. Три из них обошлись без
 * кода вовсе: «+ ячейка для контейнера» — это печатный признак карты
 * ({@code container_slot}), а «1 ячейка хранилища» уже была способностью ядра.
 * Ещё две (келемий в ячейку энергии и Смена модулей) достались от карт рынка,
 * потому что механика у них общая. Остальные живут здесь.
 *
 * <p>ЧАСТЬ СПОСОБНОСТЕЙ — ПОМЕТКИ, а не правки правил: способность нельзя
 * выразить точкой правил, если она срабатывает НА СОБЫТИЕ («в фазу Возврата», «если
 * твоё здание уничтожили»). Такие объявлены здесь пассивами без крючков, а
 * работают в том месте движка, где событие происходит, — через
 * {@code Passives.hasPassive}. Так же сделаны и прежние событийные карты
 * («монета за убийство», «боеприпас за контратаку»), и заводить ради них по
 * крючку на каждое событие значило бы плодить точки правил, у которых один
 * читатель.
 */
public final class Arsenal5Abilities {

    private Arsenal5Abilities() {
    }

    public static void install() {
        Abilities.register(new TowersFastFragile());
        Abilities.register(new InfantryHp2ReturnsOnDamage());
        Abilities.register(new SpecKeliumOnEnergyCell());
        Abilities.register(new SpecMoveMinerAnywhere());
        Abilities.register(new GroundIgnoresBuildings());
        Abilities.register(new ScienceTrophyToCoin());
        Abilities.register(new TrophyPerReturnedEnemy());
        Abilities.register(new ContainerOnOwnBuildingLost());
        Abilities.register(new SpecNuclearStrikeBurn());
    }

    // ==================================================================
    //  ХАРАКТЕРИСТИКИ РОДОВ
    // ==================================================================

    /**
     * ВЫШКИ: +1 скорости, −1 прочности. Тот же размен, что у техники и авиации,
     * только для вышек — и он для них крупнее, чем кажется: вышка медленная по
     * печати, и лишний шаг меняет её роль с «поставил и забыл» на «перекатывать
     * по фронту». Прочность ниже единицы не опускается: жетон, который умирает
     * от взгляда, — это уже не размен, а изъятие рода из игры.
     */
    private static final class TowersFastFragile implements Ability {

        @Override public String id() {
            return "towers_fast_fragile";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED, Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.hook() == Hook.UNIT_SPEED) {
                if (q.unitType() == UnitType.TOWER) {
                    q.add(1).by(id());
                }
                return;
            }
            if (q.subject() instanceof UnitToken u && u.type == UnitType.TOWER) {
                q.atLeast(1);
                q.add(-1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.0, Horizon.REST_OF_GAME, null,
                "вышки быстрее, но хрупче", false);
        }
    }

    /**
     * ПЕХОТА ЖИВЁТ ДВА УДАРА, но раненая уходит в запас: в конце боя вся своя
     * пехота с уроном возвращается владельцу. То есть карта не делает пехоту
     * прочнее — она даёт ей ПЕРЕЖИТЬ удар и уйти, а не умереть на месте.
     *
     * <p>Возврат раненых делает бой (CombatResolver), потому что «конец боя» —
     * событие, а не значение правила; здесь только прибавка к прочности.
     */
    private static final class InfantryHp2ReturnsOnDamage implements Ability {

        @Override public String id() {
            return "infantry_hp2_returns_on_damage";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        /** Пехота с ПЕЧАТНОЙ прочностью 1 — только она под щитом. */
        public static boolean covered(Token t, int printedHp) {
            return t instanceof UnitToken u && u.type == UnitType.INFANTRY && printedHp <= 1;
        }

        @Override public void modify(RuleQuery q) {
            if (covered((Token) q.subject(), (int) q.current())) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.0, Horizon.REST_OF_GAME, null,
                "пехота переживает удар, но уходит в запас", false);
        }
    }

    // ==================================================================
    //  СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * СПЕЦ: КЕЛЕМИЙ В СВОБОДНУЮ ЯЧЕЙКУ ЭНЕРГИИ — ячейка считается занятой.
     * Механика общая с картой рынка «Энергетическая контора», поэтому вызывается
     * тот же эффект: кубик встаёт навсегда, Смена энергии его не снимет.
     */
    private static final class SpecKeliumOnEnergyCell implements Ability, OptionSource {

        @Override public String id() {
            return "spec_kelium_on_energy_cell";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static boolean естьГолодное(GameState state, int seat) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.energySlots > b.energyPlaced) {
                    return true;
                }
            }
            return false;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !естьГолодное(state, seat)
                    || state.player(seat).resources.kelium() < 1) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ: келемий в свободную ячейку энергии"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            var got = kelium.engine.Effects.apply("place_on_energy_cell", state, seat,
                java.util.Map.of("pay_kelium", true));
            return Integer.valueOf(1).equals(got.get("placed"));
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.ENERGY, 1.0, Horizon.NOW, null,
                "келемий заменяет кубик энергии", false);
        }
    }

    /**
     * СПЕЦ: ПЕРЕНЕСТИ ДОБЫТЧИК НА ЛЮБОЙ ГЕКС, ГДЕ ДОСТУПНА СТРОЙКА.
     *
     * <p>Отличие от «Маркшейдера» (тот разворачивает добытчик на другую ячейку
     * ЕГО ЖЕ гекса): здесь добытчик уезжает на другой гекс целиком — туда, где
     * игрок и так мог бы построиться. Значит карта не расширяет зону стройки, она
     * экономит стройку: жила выработалась — переставь добытчик, а не строй
     * заново.
     */
    private static final class SpecMoveMinerAnywhere implements Ability, OptionSource {

        @Override public String id() {
            return "spec_move_miner_anywhere";
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
            List<String> куда = Placement.buildableHexes(state, seat);
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.type != BuildingType.MINER) {
                    continue;
                }
                for (String hid : куда) {
                    if (hid.equals(b.hexId)) {
                        continue;       // стоять на месте — не перенос
                    }
                    Hex h = state.field.get(hid);
                    if (h == null || свободнаяСторона(h) < 0) {
                        continue;
                    }
                    out.add(new Choice("ability:" + id(), new Object[]{b.uid, hid},
                        "СПЕЦ: добытчик @" + b.hexId + " -> " + hid));
                }
            }
            return out;
        }

        private static int свободнаяСторона(Hex h) {
            for (int side = 0; side < 6; side++) {
                if (h.sideOwner[side] == null) {
                    return side;
                }
            }
            return -1;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Object[] pick)) {
                return false;
            }
            int uid = (Integer) pick[0];
            String куда = String.valueOf(pick[1]);
            PlayerState me = state.player(seat);
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.uid != uid) {
                    continue;
                }
                Hex было = state.field.get(b.hexId);
                Hex стало = state.field.get(куда);
                int side = стало == null ? -1 : свободнаяСторона(стало);
                if (было == null || side < 0) {
                    return false;
                }
                for (int i = 0; i < 6; i++) {
                    if (было.sideOwner[i] != null && было.sideOwner[i] == b.uid) {
                        было.sideOwner[i] = null;
                    }
                }
                b.hexId = куда;
                стало.sideOwner[side] = b.uid;
                return true;
            }
            return false;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.KELIUM, 1.5, Horizon.REST_OF_GAME, null,
                "добытчик переезжает к новой жиле", false);
        }
    }

    // ==================================================================
    //  ПОМЕТКИ: способность срабатывает на СОБЫТИЕ, а не меняет значение
    // ==================================================================

    /**
     * В МАНЕВРЕ НАЗЕМНЫЕ ВОЙСКА ИГНОРИРУЮТ ЗДАНИЯ КАК ПРЕПЯТСТВИЕ. Стенки
     * зданий и нейтралов перестают закрывать проход — проверку прохода делает
     * {@code Actions.canEnterHex}, там и стоит эта пометка. Место на гексе
     * по-прежнему нужно: карта снимает стенку, а не законы физики.
     */
    private static final class GroundIgnoresBuildings implements Ability {

        @Override public String id() {
            return "ground_ignores_buildings_in_maneuver";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 1.5, Horizon.REST_OF_GAME, null,
                "стенки не держат наземные войска", false);
        }
    }

    /**
     * В НАУКЕ МОЖНО ОБМЕНИВАТЬ ТРОФЕИ НА МОНЕТЫ по курсу 1→1 и 2→3. Этот обмен
     * был напечатан на планшете науки и снят с него 02.09.2026 — теперь он живёт
     * на карте. Курс тот же, включая скидку за пару, поэтому владелец карты
     * играет как раньше, а остальные — уже нет.
     */
    private static final class ScienceTrophyToCoin implements Ability {

        @Override public String id() {
            return "science_trophy_to_coin";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.COINS, 1.0, Horizon.REST_OF_GAME, null,
                "трофеи снова обращаются в монеты", false);
        }
    }

    /**
     * В ФАЗУ ВОЗВРАТА — ТРОФЕЙ ЗА КАЖДЫЙ ВОЗВРАЩЁННЫЙ ЖЕТОН ВРАГА. Считаются
     * чужие жетоны, УШЕДШИЕ ВЛАДЕЛЬЦАМ С ТВОИХ ГЕКСОВ: карта платит за то, что
     * ты держал поле, а не за то, что кто-то где-то что-то вернул.
     */
    private static final class TrophyPerReturnedEnemy implements Ability {

        @Override public String id() {
            return "trophy_per_returned_enemy";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 2.0, Horizon.REST_OF_GAME, null,
                "чужие жетоны на твоих гексах превращаются в трофеи", false);
        }
    }


    /**
     * ЯДЕРНЫЙ УДАР 5.0 — редакция заказа 02.09.2026. Три отличия от прежнего:
     * трофей дают за КАЖДЫЙ удалённый жетон, а не только за чужой; потолка в
     * четыре трофея больше нет; и карта после применения УДАЛЯЕТСЯ ИЗ ИГРЫ.
     *
     * <p>ПОЧЕМУ НОВЫЙ НОМЕР СПОСОБНОСТИ, а не правка старой. Способность с
     * номером {@code spec_nuclear_strike} стоит на карте действующей колоды
     * арсенала 4.2.0: правь её — и колода, на которой сыграны замеры, задним
     * числом начнёт играть иначе. Здесь свой номер, и обе редакции живут рядом.
     *
     * <p>СВОИ ЖЕТОНЫ ТОЖЕ СЧИТАЮТСЯ, и это не описка заказа: удар по своему
     * гексу становится осмысленным ходом - разобрать собственную застройку на
     * трофеи, когда она уже не нужна. Цена та же: гекс выжжен навсегда, карта
     * ушла из игры.
     */
    private static final class SpecNuclearStrikeBurn implements Ability, OptionSource {

        private static final int AMMO_COST = 4;

        @Override public String id() {
            return "spec_nuclear_strike_burn";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static List<String> цели(GameState state) {
            List<String> out = new ArrayList<>();
            for (Hex h : state.field.hexes.values()) {
                if (h.kind != kelium.core.HexKind.FORBIDDEN && !h.hasSpawnTile()) {
                    out.add(h.id);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !state.player(seat).resources
                    .canPay(kelium.core.Resource.AMMO, AMMO_COST)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (String hid : цели(state)) {
                out.add(new Choice("ability:" + id(), hid,
                    "СПЕЦ (" + AMMO_COST + " БПР): ядерный удар по " + hid
                        + ", затем карта уходит из игры"));
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
                    || !me.resources.canPay(kelium.core.Resource.AMMO, AMMO_COST)) {
                return false;
            }
            me.resources.pay(kelium.core.Resource.AMMO, AMMO_COST);

            int жетонов = 0;
            for (PlayerState pl : state.players) {
                boolean чужой = pl.seat != seat;
                for (UnitToken u : new ArrayList<>(pl.unitsOnField())) {
                    if (hexId.equals(u.hexId)) {
                        u.setHexId(null);
                        u.resetDamage();
                        жетонов++;
                    }
                }
                for (BuildingToken b : new ArrayList<>(pl.buildingsOnField())) {
                    if (hexId.equals(b.hexId)) {
                        kelium.engine.Actions.returnOwnBuildingToReserve(state, pl, b, !чужой);
                        жетонов++;
                    }
                }
            }
            // Нейтралы уходят вместе с гексом: стоять на выжженном негде.
            // Трофеев за них не дают - они ничьи.
            h.neutrals.clear();
            for (int side = 0; side < 6; side++) {
                h.sideOwner[side] = null;
            }
            h.groundTokens.clear();
            h.airToken = null;
            h.kind = kelium.core.HexKind.FORBIDDEN;

            if (жетонов > 0) {
                kelium.engine.Storage.addTrophyCapped(state, me, жетонов);
            }
            // КАРТА УХОДИТ ИЗ ИГРЫ. Ищем её по способности: номер карты зависит
            // от версии колоды, а способность - нет.
            снятьКарту(state, seat);
            return true;
        }

        /** Убрать со стола установленную карту, несущую эту способность. */
        private void снятьКарту(GameState state, int seat) {
            PlayerState me = state.player(seat);
            var lib = kelium.dataio.Ctx.cards(state, "arsenal");
            for (String cid : new ArrayList<>(me.arsenalInstalled)) {
                var card = lib.find(cid);
                if (card == null || !(card.get("bottom") instanceof java.util.Map<?, ?> bm)) {
                    continue;
                }
                if (id().equals(String.valueOf(bm.get("passive")))) {
                    me.arsenalInstalled.remove(cid);
                    return;
                }
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.TROPHY, 3.0, Horizon.NOW, null,
                "выжечь гекс и разобрать всё на трофеи, но карта сгорит", true);
        }
    }

    /**
     * ЗДАНИЕ УНИЧТОЖИЛИ — ПОЛУЧИ КОНТЕЙНЕР ИЗ ЗАПАСА. Утешение за снос: жетон
     * потерян, но не даром. Считается уничтожение СВОЕГО здания кем угодно,
     * включая своё же ядерное оружие: карта про потерю, а не про виновника.
     */
    private static final class ContainerOnOwnBuildingLost implements Ability {

        @Override public String id() {
            return "container_on_own_building_lost";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.COINS, 1.5, Horizon.REST_OF_GAME, null,
                "за снесённое здание дают контейнер", false);
        }
    }
}
