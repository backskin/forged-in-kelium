package kelium.engine.ability;

import java.util.ArrayList;
import java.util.List;

import kelium.core.GameState;

/**
 * ЗАПРОС ЗНАЧЕНИЯ ПРАВИЛА в точке {@link Hook}: движок объявляет базовое значение,
 * способности игрока его правят, движок берёт итог.
 *
 * <p>Так выглядит единственный законный способ спросить способности:
 * <pre>
 *   int need = RuleQuery.of(state, seat, Hook.ASSEMBLY_ENERGY_NEEDED)
 *                       .about(building)          // о чём речь (не обязательно)
 *                       .base(building.energySlots)
 *                       .ask();
 * </pre>
 *
 * <p>Каждый запрос ОТМЕЧАЕТСЯ в реестре как «эту точку движок спрашивает». Тест
 * {@link Abilities#unaskedHooks()} валится, если у зарегистрированной способности
 * есть точка, которую никто не спросил ни разу — именно так шесть пассивок из 29
 * оказались мёртвыми до 13.08.2026.
 */
public final class RuleQuery {

    private final GameState state;
    private final int seat;
    private final Hook hook;
    private Object subject;
    private double value;
    private boolean allowed = true;
    private final List<String> touchedBy = new ArrayList<>();

    private RuleQuery(GameState state, int seat, Hook hook) {
        this.state = state;
        this.seat = seat;
        this.hook = hook;
    }

    public static RuleQuery of(GameState state, int seat, Hook hook) {
        Abilities.markAsked(hook);
        return new RuleQuery(state, seat, hook);
    }

    /** О чём вопрос: здание, войско, гекс — способность сама разберёт тип. */
    public RuleQuery about(Object subject) {
        this.subject = subject;
        return this;
    }

    public RuleQuery base(double v) {
        this.value = v;
        return this;
    }

    // ---- то, чем пользуются способности внутри modify() ----

    public GameState state() {
        return state;
    }

    public int seat() {
        return seat;
    }

    /**
     * ТЕКУЩЕЕ значение правила — база плюс всё, что уже добавили способности до
     * этой. Нужно тем картам, чьё условие смотрит на само число («здания с
     * прочностью 1 получают 2»), а не только прибавляет к нему.
     */
    public double current() {
        return value;
    }

    public Hook hook() {
        return hook;
    }

    /**
     * РОД ВОЙСК, О КОТОРОМ ИДЁТ РЕЧЬ, кем бы ни был subject.
     *
     * <p>Скорость спрашивается двумя способами: о РОДЕ (там, где конкретного
     * жетона нет — оценка позиции, подсказки) и о ЖЕТОНЕ (Движение и манёвр,
     * потому что часть карт смотрит на его гекс). Способности, которым важен
     * только род, обязаны понимать оба случая — иначе карта работает в одном
     * месте движка и молчит в другом. Ровно эта беда уже случалась с шестью
     * пассивками, поэтому нормализация живёт здесь, а не в каждой карте.
     *
     * @return род войск либо null, если вопрос вообще не про войска
     */
    public kelium.core.UnitType unitType() {
        if (subject instanceof kelium.core.UnitType t) {
            return t;
        }
        if (subject instanceof kelium.core.UnitToken u) {
            return u.type;
        }
        return null;
    }

    /** Жетон, о котором идёт речь, либо null (спрашивали про род вообще). */
    public kelium.core.UnitToken unitToken() {
        return subject instanceof kelium.core.UnitToken u ? u : null;
    }

    public Object subject() {
        return subject;
    }

    public double value() {
        return value;
    }

    /** Прибавить (или убавить) значение. */
    public RuleQuery add(double delta) {
        value += delta;
        return this;
    }

    /** Поднять значение не ниже указанного. */
    public RuleQuery atLeast(double v) {
        value = Math.max(value, v);
        return this;
    }

    /** Опустить значение не выше указанного (но не ниже нуля). */
    public RuleQuery atMost(double v) {
        value = Math.max(0, Math.min(value, v));
        return this;
    }

    /** Запретить то, о чём спрашивают (для точек-разрешений). */
    public RuleQuery deny() {
        allowed = false;
        return this;
    }

    /** Разрешить то, о чём спрашивают. */
    public RuleQuery allow() {
        allowed = true;
        return this;
    }

    /** Пометить, что эта способность вмешалась — попадёт в метрики. */
    public RuleQuery by(String abilityId) {
        touchedBy.add(abilityId);
        return this;
    }

    // ---- итог ----

    /** Спросить способности игрока и вернуть итоговое ЧИСЛО. */
    public double askDouble() {
        for (Ability a : Abilities.activeFor(state, seat)) {
            if (a.trigger() == Ability.Trigger.PASSIVE && a.hooks().contains(hook)) {
                a.modify(this);
            }
        }
        return value;
    }

    /** То же, но целым числом (значения правил в игре целые). */
    public int ask() {
        return (int) Math.round(askDouble());
    }

    /** Спросить способности и вернуть РАЗРЕШЕНИЕ. */
    public boolean askAllowed() {
        askDouble();
        return allowed;
    }

    /** Кто из способностей вмешался в этот запрос (для метрик и объяснений). */
    public List<String> touchedBy() {
        return List.copyOf(touchedBy);
    }
}
