package kelium.engine.ability;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 6.0 — шесть новых низов по диктовке дизайнера 14.09.2026.
 *
 * <p>Все шесть написаны ПО КНИГЕ ПРАВИЛ (главы 5, 7, 8, 9), а не по прежним
 * редакциям свода, и там, где книга и движок расходились, прав считался книга.
 *
 * <p>ДВЕ СПОСОБНОСТИ — ПОМЕТКИ, а не правки значений: «снять урон в Обновлении»
 * и «ответный бой за боеприпас» срабатывают НА СОБЫТИЕ, а событие нельзя
 * выразить точкой правил. Они объявлены здесь пассивами без крючков, а работают
 * там, где событие происходит ({@code GameEngine.refresh} и
 * {@code CombatResolver}), — ровно так же, как сделаны событийные карты 5.0.
 */
public final class Arsenal6Abilities {

    private Arsenal6Abilities() {
    }

    /** Цена спец-способностей «Подрядчика» и «Комендатуры» — монета. */
    private static final int МОНЕТА = 1;

    /** Сколько кубиков энергии вмещает «Разрядник». */
    public static final int ЯЧЕЕК_НА_РАЗРЯДНИКЕ = 2;

    /** Идентификатор карты-носителя энергии: ключ в {@code arsenalCardEnergy}. */
    public static final String КАРТА_РАЗРЯДНИКА = "разрядник";

    public static void install() {
        Abilities.register(new RepairAllInRefresh());
        Abilities.register(new CounterBattleForAmmo());
        Abilities.register(new SpecNeutralNearOwn());
        Abilities.register(new SpecReleaseGarrison());
        Abilities.register(new KeliumInsteadOfAmmoInOperation());
        Abilities.register(new SpecEnergyOnCardFreeAttack());
    }

    // ==================================================================
    //  ПОМЕТКИ: срабатывают на событие
    // ==================================================================

    /**
     * В ОБНОВЛЕНИЕ СНИМИ ВЕСЬ УРОН СО СВОИХ ЖЕТОНОВ.
     *
     * <p>По книге (глава 9) урон ОСТАЁТСЯ на жетоне до самой его гибели, и
     * ничего, кроме этой карты, его не снимает: раненый жетон так и ходит
     * раненым до конца партии. Поэтому карта не «лечит понемногу», а один раз
     * в раунд возвращает всё войско и всю застройку в целое состояние — и тем
     * сильнее, чем дольше по владельцу били, не добив.
     *
     * <p>Работает в фазе Обновление ({@code GameEngine.refresh}), то есть ПОСЛЕ
     * чужих ходов и ДО своих: ранивший за раунд противник видит результат сразу,
     * а не через круг.
     */
    private static final class RepairAllInRefresh implements Ability {

        @Override public String id() {
            return "repair_all_in_refresh";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 2.0, Horizon.REST_OF_GAME, null,
                "каждое Обновление весь урон со своих жетонов снимается", false);
        }
    }

    /**
     * ЧУЖОЙ БОЙ ЗАКОНЧИЛСЯ, И В НЁМ ПОСТРАДАЛИ ТВОИ ЖЕТОНЫ — ЗАПЛАТИ 1 БОЕПРИПАС
     * И ПРОВЕДИ ОТВЕТНЫЙ БОЙ ПРОТИВ ЭТОГО ИГРОКА.
     *
     * <p>ПОЧЕМУ ЭТО ВООБЩЕ КАРТА. Ответный бой был правилом до 04.09.2026 и
     * оттуда убран: «бой целиком укладывается в ход того, кто его объявил, вся
     * оборона переехала в карты» ({@code combat.retaliation_enabled: false}).
     * Книга правил (глава 9) ответного боя не знает вовсе. Карта возвращает его
     * одному игроку и уже не даром.
     *
     * <p>Бой идёт по обычным правилам и за свои боеприпасы, но цель ограничена
     * тем, кто ударил: это ответ, а не бесплатный лишний ход по всему полю.
     */
    private static final class CounterBattleForAmmo implements Ability {

        @Override public String id() {
            return "counter_battle_for_ammo";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 2.5, Horizon.REST_OF_GAME, null,
                "за боеприпас можно ответить обидчику собственным Боем", false);
        }
    }

    /**
     * КЕЛЕМИЙ ВМЕСТО БОЕПРИПАСОВ В НАСТУПЛЕНИИ.
     *
     * <p>Приказ Наступление — это Манёвр и Бой (глава 9), и оба платят
     * боеприпасами: шаг чужого жетона — 1 боеприпас, универсальная атака — 2,
     * специальная — 1. Карта разрешает класть вместо них келемий, и только в
     * этих двух действиях: в остальном келемий остаётся рыночным товаром.
     *
     * <p>Пометка, а не правка цены: цена атаки не меняется ни на единицу,
     * меняется КОШЕЛЁК, из которого её платят. Точка правил
     * {@code ATTACK_AMMO_COST} для этого не годится — она про число, а не про
     * валюту.
     */
    private static final class KeliumInsteadOfAmmoInOperation implements Ability {

        @Override public String id() {
            return "kelium_instead_of_ammo_in_operation";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 2.0, Horizon.REST_OF_GAME, null,
                "келемий идёт в дело вместо боеприпасов в Манёвре и Бою", false);
        }
    }

    // ==================================================================
    //  СПЕЦ-ДЕЙСТВИЯ
    // ==================================================================

    /**
     * СПЕЦ: МИНУС МОНЕТА — НЕЙТРАЛЬНАЯ ПОСТРОЙКА НА СВОБОДНЫЙ СЕКТОР ГЕКСА,
     * СОСЕДНЕГО С ТВОИМ ЗДАНИЕМ.
     *
     * <p>Постройка ставится одинарная — на один сектор, прочность 1 (глава 9), —
     * и стоит стенкой ДЛЯ ВСЕХ, включая хозяина карты (глава 4). Это и есть
     * цена: за монету игрок закрывает соседу проход и выстрел, но закрывает и
     * себе, а зона стройки соседа при этом сужается.
     *
     * <p>Механика общая с утилем «постройка нейтрала», поэтому вызывается тот же
     * эффект {@code build_neutral} — но с двумя ограничениями карты: только
     * рядом со своим зданием и только один сектор.
     */
    private static final class SpecNeutralNearOwn implements Ability, OptionSource {

        @Override public String id() {
            return "spec_neutral_near_own_building";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.COIN, МОНЕТА)) {
                return List.of();
            }
            return List.of(new Choice("ability:" + id(), id(),
                "СПЕЦ (1 монета): нейтральная постройка у своего здания"));
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.COIN, МОНЕТА)) {
                return false;
            }
            var got = kelium.engine.Effects.apply("build_neutral", state, seat,
                Map.of("near_own_building", true, "max_sectors", 1));
            if (!Integer.valueOf(1).equals(got.get("built"))) {
                return false;
            }
            me.resources.pay(Resource.COIN, МОНЕТА);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 1.5, Horizon.REST_OF_GAME,
                (s, seat) -> s.player(seat).resources.canPay(Resource.COIN, МОНЕТА),
                "нет монеты на постройку", false);
        }
    }

    /**
     * СПЕЦ: МИНУС МОНЕТА — ВЫПУСТИ ВСЕ ВОЙСКА ИЗ ОДНОГО СВОЕГО ВОЕННОГО ЗДАНИЯ
     * НА СОСЕДНИЕ ГЕКСЫ, К КОТОРЫМ ОНО ПРИМЫКАЕТ СТЕНКОЙ.
     *
     * <p>ЗАЧЕМ. По книге (глава 8) новый жетон встаёт «на гекс сделавшего его
     * здания ИЛИ ВНУТРЬ этого здания, внутри жетонов сколько угодно». Гарнизон
     * поэтому копится: Снаряжение набивает здание войсками, а выйти они могут
     * только Манёвром, по жетону за шаг. Карта высыпает весь гарнизон разом и
     * сразу за стенку — то есть превращает военное здание в пусковую площадку,
     * не тратя на это ни Манёвра, ни боеприпасов.
     *
     * <p>КУДА ИМЕННО. На те соседние гексы, к которым здание обращено стенкой
     * (глава 4) — по тем же сторонам, которыми оно расширяет зону стройки.
     * Гекс, где стоят чужие войска, для наземных закрыт (глава 4), и туда
     * жетон не выходит. Некуда выйти — жетон остаётся внутри.
     */
    private static final class SpecReleaseGarrison implements Ability, OptionSource {

        @Override public String id() {
            return "spec_release_garrison";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        /** Военные здания игрока, внутри которых кто-то есть. */
        private static List<BuildingToken> сГарнизоном(GameState state, int seat) {
            List<BuildingToken> out = new ArrayList<>();
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (!военное(b.type)) {
                    continue;
                }
                for (UnitToken u : state.player(seat).unitsOnField()) {
                    if (u.insideBuildingUid != null && u.insideBuildingUid == b.uid) {
                        out.add(b);
                        break;
                    }
                }
            }
            return out;
        }

        private static boolean военное(BuildingType t) {
            return t == BuildingType.BARRACKS || t == BuildingType.FACTORY
                || t == BuildingType.AIRBASE || t == BuildingType.COMMAND_CENTER;
        }

        /** Соседние гексы, к которым здание обращено своей стенкой. */
        private static List<String> заСтенкой(GameState state, BuildingToken b) {
            List<String> out = new ArrayList<>();
            Hex self = b.hexId == null ? null : state.field.get(b.hexId);
            if (self == null) {
                return out;
            }
            for (int side = 0; side < 6; side++) {
                if (self.sideOwner[side] == null || self.sideOwner[side] != b.uid) {
                    continue;
                }
                String nb = self.neighborBySide[side];
                if (nb != null && !out.contains(nb)) {
                    out.add(nb);
                }
            }
            return out;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC
                    || !state.player(seat).resources.canPay(Resource.COIN, МОНЕТА)) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            for (BuildingToken b : сГарнизоном(state, seat)) {
                if (заСтенкой(state, b).isEmpty()) {
                    continue;
                }
                out.add(new Choice("ability:" + id(), b.uid,
                    "СПЕЦ (1 монета): выпустить гарнизон " + b.type.code + " @" + b.hexId));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            if (!(chosen != null && chosen.payload() instanceof Integer uid)) {
                return false;
            }
            PlayerState me = state.player(seat);
            if (!me.resources.canPay(Resource.COIN, МОНЕТА)) {
                return false;
            }
            BuildingToken здание = null;
            for (BuildingToken b : me.buildingsOnField()) {
                if (b.uid == uid) {
                    здание = b;
                    break;
                }
            }
            if (здание == null) {
                return false;
            }
            List<String> куда = заСтенкой(state, здание);
            if (куда.isEmpty()) {
                return false;
            }
            List<UnitToken> гарнизон = new ArrayList<>();
            for (UnitToken u : me.unitsOnField()) {
                if (u.insideBuildingUid != null && u.insideBuildingUid == здание.uid) {
                    гарнизон.add(u);
                }
            }
            if (гарнизон.isEmpty()) {
                return false;
            }
            int вышло = 0;
            for (UnitToken u : гарнизон) {
                List<Choice> opts = new ArrayList<>();
                for (String hid : куда) {
                    if (kelium.engine.Actions.canEnterHex(state, u, hid, seat)) {
                        opts.add(new Choice("garrison_out", hid,
                            u.type.code + " -> " + hid));
                    }
                }
                if (opts.isEmpty()) {
                    continue;           // выйти некуда — жетон остаётся внутри
                }
                Choice pick = agent != null
                    ? agent.choose(state, opts, Map.of("kind", "garrison_out",
                        "unit", u.type.code))
                    : opts.get(0);
                String hid = pick != null && pick.payload() != null
                    ? String.valueOf(pick.payload()) : String.valueOf(opts.get(0).payload());
                u.insideBuildingUid = null;
                u.setHexId(hid);
                вышло++;
            }
            if (вышло == 0) {
                return false;
            }
            me.resources.pay(Resource.COIN, МОНЕТА);
            return true;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.REACH, 2.0, Horizon.NOW, null,
                "гарнизон выходит за стенку разом и без Манёвра", false);
        }
    }

    /**
     * СПЕЦ «РАЗРЯДНИК»: ПЕРЕНЕСИ НА КАРТУ 1 ЭНЕРГИЮ (ячеек две) ЛИБО ВЕРНИ С
     * КАРТЫ 2 ЭНЕРГИИ НА ЛЮБОЙ СВОЙ ИСТОЧНИК, ЧТОБЫ ВЫПОЛНИТЬ ОДНУ ЛЮБУЮ АТАКУ
     * СВОИМ ВОЙСКОМ, НЕ ТРАТЯ БОЕПРИПАСОВ.
     *
     * <p>Энергию не тратят, её перекладывают (глава 6) — карта это правило не
     * нарушает: кубики уходят на карту и возвращаются на источник, из игры не
     * исчезая. Платой становится не энергия, а ВРЕМЯ: два спец-действия на
     * зарядку, третье на выстрел, и всё это время два кубика не питают здания.
     *
     * <p>Выстрел — обычный Бой (глава 9) с одной бесплатной атакой: цель
     * выбирается по правилам боя, стенки и щит зданий работают как всегда,
     * вторая и следующие атаки того же боя платятся боеприпасами.
     */
    private static final class SpecEnergyOnCardFreeAttack implements Ability, OptionSource {

        @Override public String id() {
            return "spec_energy_on_card_free_attack";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        private static int наКарте(GameState state, int seat) {
            return state.player(seat).arsenalCardEnergy.getOrDefault(КАРТА_РАЗРЯДНИКА, 0);
        }

        /**
         * ОТКУДА МОЖНО СНЯТЬ КУБИК. Сперва — свои источники, на которых кубики
         * ещё лежат неразложенными; если таких нет, годится и ЗАПИТАННОЕ
         * здание: энергию перекладывают (глава 6), и снять кубик с потребителя
         * — законный ход, просто здание после этого перестаёт работать.
         * Поэтому доноры перечисляются поимённо, а не берётся первый
         * попавшийся: выключить себе завод игрок должен решить сам.
         */
        private static List<BuildingToken> доноры(GameState state, int seat) {
            List<BuildingToken> свободные = new ArrayList<>();
            List<BuildingToken> занятые = new ArrayList<>();
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.energyIdle > 0) {
                    свободные.add(b);
                } else if (b.energyPlaced > 0) {
                    занятые.add(b);
                }
            }
            return свободные.isEmpty() ? занятые : свободные;
        }

        /**
         * СНЯТЬ ОДИН КУБИК С ЗДАНИЯ-ДОНОРА. Сначала неразложенный кубик
         * источника, потом — кубик, уже лежащий в ячейке энергии. У кубика в
         * ячейке есть хозяин-источник, и его учёт ведётся по источникам, иначе
         * возврат энергии после уничтожения здания посчитал бы лишнее.
         */
        private static boolean снятьКубик(BuildingToken b) {
            if (b.energyIdle > 0) {
                b.energyIdle -= 1;
                return true;
            }
            var it = b.energyBySource.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue() != null && e.getValue() > 0) {
                    if (e.getValue() == 1) {
                        it.remove();
                    } else {
                        e.setValue(e.getValue() - 1);
                    }
                    b.energyPlaced = Math.max(0, b.energyPlaced - 1);
                    return true;
                }
            }
            return false;
        }

        /** Свой источник, готовый принять вернувшиеся кубики. */
        private static BuildingToken принимающийИсточник(GameState state, int seat) {
            for (BuildingToken b : state.player(seat).buildingsOnField()) {
                if (b.type == BuildingType.POWER_PLANT
                        || b.type == BuildingType.COMMAND_CENTER) {
                    return b;
                }
            }
            return null;
        }

        @Override public List<Choice> options(GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC) {
                return List.of();
            }
            List<Choice> out = new ArrayList<>();
            if (наКарте(state, seat) < ЯЧЕЕК_НА_РАЗРЯДНИКЕ) {
                for (BuildingToken донор : доноры(state, seat)) {
                    out.add(new Choice("ability:" + id() + ":charge", донор.uid,
                        "СПЕЦ: перенести 1 энергию на «Разрядник» с "
                            + донор.type.code + " @" + донор.hexId));
                }
            }
            if (наКарте(state, seat) >= ЯЧЕЕК_НА_РАЗРЯДНИКЕ
                    && принимающийИсточник(state, seat) != null) {
                out.add(new Choice("ability:" + id() + ":fire", id(),
                    "СПЕЦ: разрядить 2 энергии — атака без боеприпасов"));
            }
            return out;
        }

        @Override public boolean perform(GameState state, int seat, Choice chosen, Agent agent) {
            PlayerState me = state.player(seat);
            String kind = chosen == null || chosen.kind() == null ? "" : chosen.kind();
            if (kind.endsWith(":charge")) {
                if (наКарте(state, seat) >= ЯЧЕЕК_НА_РАЗРЯДНИКЕ
                        || !(chosen.payload() instanceof Integer uid)) {
                    return false;
                }
                BuildingToken донор = null;
                for (BuildingToken b : доноры(state, seat)) {
                    if (b.uid == uid) {
                        донор = b;
                    }
                }
                if (донор == null) {
                    return false;
                }
                if (!снятьКубик(донор)) {
                    return false;
                }
                me.arsenalCardEnergy.merge(КАРТА_РАЗРЯДНИКА, 1, Integer::sum);
                return true;
            }
            if (kind.endsWith(":fire")) {
                BuildingToken куда = принимающийИсточник(state, seat);
                if (куда == null || наКарте(state, seat) < ЯЧЕЕК_НА_РАЗРЯДНИКЕ
                        || state.combat == null) {
                    return false;
                }
                me.arsenalCardEnergy.merge(КАРТА_РАЗРЯДНИКА, -ЯЧЕЕК_НА_РАЗРЯДНИКЕ,
                    Integer::sum);
                куда.energyIdle += ЯЧЕЕК_НА_РАЗРЯДНИКЕ;
                if (state.journal != null) {
                    state.journal.of(seat).freeAttacks += 1;
                }
                // Поле combat объявлено Object (служебная привязка движка),
                // поэтому приводим здесь — так же делает всё, что до него
                // дотягивается из способностей.
                boolean было = ((kelium.engine.CombatResolver) state.combat)
                    .runBattle(seat, agent);
                if (!было && state.journal != null) {
                    // Боя не случилось — бесплатная атака не сгорает.
                    state.journal.of(seat).freeAttacks =
                        Math.max(0, state.journal.of(seat).freeAttacks - 1);
                }
                return true;
            }
            return false;
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.AMMO, 1.5, Horizon.THIS_ROUND, null,
                "две энергии на карте покупают выстрел без боеприпасов", false);
        }
    }
}
