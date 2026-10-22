package kelium.engine.ability;

import java.util.EnumSet;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.engine.ability.Hint.Bottleneck;
import kelium.engine.ability.Hint.Horizon;

/**
 * АРСЕНАЛ 7.1.0 И СУПЕР-АРСЕНАЛ 4.0.0 — способности карт 25.09.2026.
 *
 * <p>Два начальных низа с печати 25.09.2026 новых способностей не требуют: их
 * текст дословно совпадает с начальными картами набора 6.0.0
 * ({@code coin_on_kill}, {@code spec_swap_ground_unit}). Здесь — то, чего в
 * движке ещё не было:
 * <ul>
 *   <li>две способности с печати супер-арсенала: «Штабная директива» и
 *       «Келемиевый рудник» (у прежних одноимённых карт набора 2.0.0 другой
 *       текст и другие способности — те остаются как были);</li>
 *   <li>пять придуманных на пустых заготовках (начальная №7, обычная №33,
 *       три супер-способности).</li>
 * </ul>
 *
 * <p>ГДЕ ОНИ СРАБАТЫВАЮТ. Большинство — пометки: движок спрашивает их ровно
 * в том месте, о котором говорит карта, через {@code Passives.hasPassive} /
 * {@code Passives.superArsenalPassive} (как «в фазу Возвращение» и «в
 * действии Наука» у 7.0.0). Место указано у каждой способности. Прочность
 * зданий — обычная точка правил {@link Hook#TOKEN_HP}.
 */
public final class Arsenal71Abilities {

    private Arsenal71Abilities() {
    }

    public static void install() {
        // начальная №7 «Сдача тары»
        Abilities.register(new Пометка("coin_on_container_open", Ability.Trigger.ON_EVENT,
            new Hint(Bottleneck.COINS, 1.0, Horizon.REST_OF_GAME, null,
                "каждый вскрытый контейнер приносит ещё монету", false)));
        // обычная №33 «Боевое довольствие»
        Abilities.register(new Пометка("ammo_on_objective_done", Ability.Trigger.ON_EVENT,
            new Hint(Bottleneck.AMMO, 1.0, Horizon.REST_OF_GAME, null,
                "каждое выполненное задание приносит боеприпас", false)));
        // супер «Штабная директива» (печать)
        Abilities.register(new Пометка("directive_coincidence_both_bottom_spec",
            Ability.Trigger.PASSIVE,
            new Hint(Bottleneck.ACTIONS, 1.5, Horizon.REST_OF_GAME, null,
                "совпадение приказов не режет ход; открытый нижний даёт спец-действие",
                false)));
        // супер «Келемиевый рудник» (печать)
        Abilities.register(new Пометка("kelium_per_mining_action", Ability.Trigger.ON_EVENT,
            new Hint(Bottleneck.KELIUM, 1.0, Horizon.REST_OF_GAME, null,
                "каждая Добыча приносит ещё келемий", false)));
        // супер «Оружейный конвейер»
        Abilities.register(new Пометка("ammo_per_assembly_action", Ability.Trigger.ON_EVENT,
            new Hint(Bottleneck.AMMO, 1.0, Horizon.REST_OF_GAME, null,
                "каждое Снаряжение приносит ещё боеприпас", false)));
        // супер «Бронированные цеха»
        Abilities.register(new ЗданияКрепче());
        // супер «Баллистический расчёт»
        Abilities.register(new Пометка("universal_attack_one_ammo", Ability.Trigger.PASSIVE,
            new Hint(Bottleneck.AMMO, 1.5, Horizon.REST_OF_GAME, null,
                "универсальная атака за 1 боеприпас", false)));
    }

    /**
     * СПОСОБНОСТЬ-ПОМЕТКА: своего кода вмешательства у неё нет, движок
     * проверяет её в одном названном месте.
     * <ul>
     *   <li>{@code coin_on_container_open} — {@code GameEngine.offerOpenContainer}:
     *       за каждый вскрытый контейнер +1 монета;</li>
     *   <li>{@code ammo_on_objective_done} — {@code Objectives.playObjective}:
     *       выполнено задание (не сожжено) — +1 боеприпас в хранилище;</li>
     *   <li>{@code directive_coincidence_both_bottom_spec} —
     *       {@code GameEngine.resolveTurn}: при совпадении верхнего приказа оба
     *       действия; открыт нижний приказ — +1 спец-действие в этот ход;</li>
     *   <li>{@code kelium_per_mining_action} — {@code Actions.MiningAction}:
     *       +1 келемий из общего запаса за каждое выполненное действие Добыча
     *       (и полное, и с карты), но не за срабатывание добытчика при
     *       постройке — это не действие;</li>
     *   <li>{@code ammo_per_assembly_action} — {@code Actions.AssemblyAction}:
     *       то же для Снаряжения и боеприпаса;</li>
     *   <li>{@code universal_attack_one_ammo} —
     *       {@code CombatResolver.dualCellAttackRows}: цена универсальной
     *       строки атаки не больше 1.</li>
     * </ul>
     */
    private static final class Пометка implements Ability {

        private final String id;
        private final Trigger trigger;
        private final Hint hint;

        Пометка(String id, Trigger trigger, Hint hint) {
            this.id = id;
            this.trigger = trigger;
            this.hint = hint;
        }

        @Override public String id() {
            return id;
        }

        @Override public Trigger trigger() {
            return trigger;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.noneOf(Hook.class);
        }

        @Override public Hint hint() {
            return hint;
        }
    }

    /**
     * «БРОНИРОВАННЫЕ ЦЕХА» (супер-арсенал 4.0.0): «Твои здания: +1 прочности».
     * Все свои здания, ЦУ тоже; чужие — нет (точку спрашивают от владельца
     * жетона).
     */
    private static final class ЗданияКрепче implements Ability {

        @Override public String id() {
            return "super_buildings_plus1_hp";
        }

        @Override public Trigger trigger() {
            return Trigger.PASSIVE;
        }

        @Override public Set<Hook> hooks() {
            return EnumSet.of(Hook.TOKEN_HP);
        }

        @Override public void modify(RuleQuery q) {
            if (q.subject() instanceof BuildingToken b && b.owner() == q.seat()) {
                q.add(1).by(id());
            }
        }

        @Override public Hint hint() {
            return new Hint(Bottleneck.DEFENCE, 2.0, Horizon.REST_OF_GAME, null,
                "каждое здание держит на одно попадание больше", false);
        }
    }
}
