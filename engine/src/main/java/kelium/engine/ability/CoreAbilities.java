package kelium.engine.ability;

import java.util.EnumSet;
import java.util.Set;

import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * НАБОР СПОСОБНОСТЕЙ, переведённых на реестр. Пополняется по одной, с тестом на
 * каждую (порядок перехода — в «АРСЕНАЛ КАК СИСТЕМА СПОСОБНОСТЕЙ — проект движка»).
 *
 * <p>Первой перенесена {@code plus1_storage_cell} — она была МЁРТВОЙ: обёртка
 * {@code Passives.storageCellBonus} существовала, но её никто не вызывал, а бот в
 * скорере ценил карту в 2.4 балла. То есть игрок платил СПЕЦ-действием за ячейку,
 * которой не появлялось. Ревизия 13.08.2026.
 */
public final class CoreAbilities {

    private CoreAbilities() {
    }

    /** Зарегистрировать все перенесённые способности (идемпотентно). */
    public static void install() {
        Abilities.register(new PlusOneStorageCell());
        Abilities.register(new SpeedPlusOne("speed_plus1_ground",
            kelium.core.UnitType.INFANTRY, kelium.core.UnitType.VEHICLE));
        Abilities.register(new SpeedPlusOne("speed_plus1_air_tower",
            kelium.core.UnitType.AIRCRAFT, kelium.core.UnitType.TOWER));
        Abilities.register(new CheaperBuild());
        Abilities.register(new OneAttackSpec());
    }

    /**
     * «Войско делает одну атаку» (as1 «Ударное звено», a03 «Штурмовая группа») —
     * ПЕРВОЕ ДЕЙСТВИЕ, ПРИШЕДШЕЕ ОТ КАРТЫ, а не зашитое в движок. Пассивка была
     * мёртвой у двух карт сразу: движок про неё просто не знал, и обе карты
     * изымались из колоды.
     *
     * <p>Одна атака = один бой без наценки за право боя: это дар карты, а не
     * продолжение действия Бой, поэтому счётчик наценок не трогаем.
     */
    private static final class OneAttackSpec implements Ability, OptionSource {

        @Override public String id() {
            return "unit_makes_one_attack";
        }

        @Override public Trigger trigger() {
            return Trigger.SPEC;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);   // не правит числа — добавляет вариант
        }

        @Override public java.util.List<kelium.core.Choice> options(
                kelium.core.GameState state, int seat, Slot slot) {
            if (slot != Slot.SPEC || !canStrike(state, seat)) {
                return java.util.List.of();
            }
            return java.util.List.of(new kelium.core.Choice(
                "ability:" + id(), id(), "СПЕЦ: войско делает одну атаку"));
        }

        @Override public boolean perform(kelium.core.GameState state, int seat,
                                         kelium.core.Choice chosen, kelium.core.Agent agent) {
            if (!canStrike(state, seat)) {
                return false;
            }
            boolean did = ((kelium.engine.CombatResolver) state.combat).runBattle(seat, agent);
            if (did && state.journal instanceof kelium.core.TurnJournal tj) {
                tj.of(seat).battlesOpened += 1;
            }
            return did;
        }

        private static boolean canStrike(kelium.core.GameState state, int seat) {
            return state.combat instanceof kelium.engine.CombatResolver r
                && r.anyAttackPossible(seat);
        }

        @Override public Hint hint() {
            // Расшивает ДОСЯГАЕМОСТЬ и трофеи: лишняя атака в ход — это удар,
            // который иначе стоил бы целого приказа Бой (и наценки за второй бой).
            return new Hint(Bottleneck.TROPHY, 2.5, Horizon.THIS_ROUND,
                (s, seat) -> canStrike(s, seat), "некого бить", false);
        }
    }

    /**
     * «Промышленник» (a12): стройка дешевле на 2 монеты. Пассивка НЕ БЫЛА НАПИСАНА
     * вовсе — карта изымалась из колоды при подготовке (ревизия 13.08.2026).
     * Теперь это точка правил {@link Hook#BUILD_PRICE}, и цена спрашивается в
     * одном месте — и при постройке, и при переносе здания.
     */
    private static final class CheaperBuild implements Ability {

        @Override public String id() {
            return "build_minus2_coin";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.BUILD_PRICE);
        }

        @Override public void modify(RuleQuery q) {
            q.add(-2).by(id());
        }

        @Override public Hint hint() {
            // Расшивает монеты: за партию игрок строит 6–9 раз, то есть скидка
            // окупается многократно. Условие — иметь на что строить.
            return new Hint(Bottleneck.COINS, 2.0, Horizon.REST_OF_GAME,
                (s, seat) -> s.player(seat).resources.coin() >= 0, "", false);
        }
    }

    /**
     * «+1 к скорости» указанным родам войск. Одна реализация на две карты:
     * различаются только тем, кого ускоряют. Точка правил одна — {@link Hook#UNIT_SPEED},
     * поэтому прибавка работает и в Движении, и в манёвре, и в эффектах карт: все
     * они спрашивают скорость через {@link kelium.engine.Speed}.
     */
    private static final class SpeedPlusOne implements Ability {
        private final String id;
        private final Set<kelium.core.UnitType> kinds;

        SpeedPlusOne(String id, kelium.core.UnitType... kinds) {
            this.id = id;
            this.kinds = EnumSet.of(kinds[0], kinds);
        }

        @Override public String id() {
            return id;
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.UNIT_SPEED);
        }

        @Override public void modify(RuleQuery q) {
            if (q.unitType() instanceof kelium.core.UnitType t && kinds.contains(t)) {
                q.add(1).by(id);
            }
        }

        @Override public Hint hint() {
            // Скорость расшивает ДОСЯГАЕМОСТЬ: замер 12.08.2026 показал, что треть
            // войск к концу партии стоит ровно в двух гексах от врага — на шаг
            // короче удара. Один лишний шаг за раунд снимает это узкое место.
            return new Hint(Bottleneck.REACH, 1.0, Horizon.REST_OF_GAME,
                (s, seat) -> !s.player(seat).unitsOnField().isEmpty(),
                "нет войск на поле", false);
        }
    }

    static {
        install();
    }

    /** «+1 ячейка хранилища» — универсальная ячейка под келемий ИЛИ боеприпас. */
    private static final class PlusOneStorageCell implements Ability {

        @Override public String id() {
            return "plus1_storage_cell";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.STORAGE_CELLS);
        }

        @Override public void modify(RuleQuery q) {
            // Ячейка УНИВЕРСАЛЬНАЯ: годится и под келемий, и под боеприпасы,
            // поэтому прибавка идёт к общему числу открытых ячеек.
            q.add(1).by(id());
        }

        @Override public Hint hint() {
            // Ячейка расшивает хранение: без неё добытый келемий и собранные
            // боеприпасы упираются в предел и пропадают. Пользы примерно на один
            // ресурс за раунд, работает до конца партии, условий нет.
            return new Hint(Bottleneck.KELIUM, 1.0, Horizon.REST_OF_GAME,
                null, "", false);
        }
    }
}
