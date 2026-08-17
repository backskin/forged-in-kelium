package kelium.engine;

import kelium.core.GameState;
import kelium.core.UnitType;
import kelium.engine.ability.Hook;
import kelium.engine.ability.RuleQuery;

/**
 * СКОРОСТЬ РОДА ВОЙСК — единственное место, где движок её узнаёт.
 *
 * <p>Прежде скорость читалась прямо с планшета в четырёх местах
 * ({@code side.speed(type)} в Движении, манёвре и двух эффектах). Значит любая
 * карта или жетон «+1 к скорости» требовали правки всех четырёх — и любой
 * пропущенный вызов давал ровно ту беду, что ревизия 13.08.2026 нашла у шести
 * пассивок: способность есть, эффекта нет.
 *
 * <p>Теперь скорость собирается здесь и спрашивает точку правил
 * {@link Hook#UNIT_SPEED}, поэтому прибавку могут давать сразу три источника:
 * <ul>
 *   <li>карты арсенала («+1 пехоте и технике», «+1 авиации и вышкам»,
 *       «техника +1 скорости и −1 здоровья»);</li>
 *   <li>ХАРАКТЕРИСТИЧЕСКИЕ жетоны модулей набора R2 — их прибавка считалась
 *       {@code Modules.statBonus}, но её никто не спрашивал: жетоны лежали на
 *       планшете мёртвыми;</li>
 *   <li>пассивка «авиация 3» — прежний особый случай, теперь такой же источник.</li>
 * </ul>
 */
public final class Speed {

    private Speed() {
    }

    /**
     * СКОРОСТЬ КОНКРЕТНОГО ЖЕТОНА.
     *
     * <p>Заведено потому, что часть карт арсенала разгоняет войска НЕ ПО РОДУ, а
     * по обстановке: «пехота и техника быстрее, если идут вместе из одного
     * гекса», «вышка едет, если на её гексе есть авиация». Такое условие нельзя
     * проверить, зная только род, — нужен сам жетон и его гекс. Поэтому вопрос
     * задаётся о жетоне, а способность уже сама смотрит на поле.
     *
     * <p>Версия «по роду» ниже остаётся: её спрашивают там, где конкретного
     * жетона нет (оценка позиции ботом, подсказки).
     */
    public static int of(GameState state, int seat, kelium.core.UnitToken unit) {
        int base = baseFor(state, seat, unit.type);
        // sa1 «Гвардия «Кель»» (супер-пехота, редакция 17.08.2026): карта печатает
        // «Скорость 2» как АБСОЛЮТНОЕ значение этого конкретного жетона, а не
        // прибавку к строю — хардкод здесь, а не через RuleQuery/Hook.UNIT_SPEED,
        // потому что это не пассивка карты арсенала (одна на игрока и правящая
        // ВЕСЬ род), а печатная характеристика одного конкретного 5-го жетона —
        // как «Прочность на 1 больше обычной» у него же, только для скорости
        // «фиксировано 2», а не «+1».
        if (unit.superUnit && "sa1".equals(unit.superCardId)) {
            base = Math.max(base, 2);
        }
        return Math.max(0, RuleQuery.of(state, seat, Hook.UNIT_SPEED)
            .about(unit)
            .base(base)
            .ask());
    }

    /** Скорость рода войск у игрока с учётом всех прибавок. */
    public static int of(GameState state, int seat, UnitType type) {
        return Math.max(0, RuleQuery.of(state, seat, Hook.UNIT_SPEED)
            .about(type)
            .base(baseFor(state, seat, type))
            .ask());
    }

    /** Скорость рода ДО вопроса способностям: планшет, модуль, разовый эффект. */
    private static int baseFor(GameState state, int seat, UnitType type) {
        var p = state.player(seat);
        int base = p.board.troop.speed(type);
        // особый случай авиации остался особым правилом планшета/пассивки
        Integer airOverride = Passives.aircraftSpeedOverride(state, seat);
        if (airOverride != null && type == UnitType.AIRCRAFT) {
            base = airOverride;
        }
        // ЖЕТОН МОДУЛЯ на строке этого рода войск (набор R2)
        base += Modules.statBonus(p, type, "speed");
        // РАЗОВЫЙ ЭФФЕКТ «+1 к скорости до конца хода» (Effects.speedBoost).
        // Скорость 0 не разгоняется: вышка — дот, она не двигается ничем.
        if (base > 0 && state.journal != null
                && type.code.equals(state.journal.of(seat).speedBoostKind)) {
            base += 1;
        }
        return base;
    }
}
