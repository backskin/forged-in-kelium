package kelium.engine.ability;

import java.util.function.BiPredicate;

import kelium.core.GameState;

/**
 * САМООПИСАНИЕ СПОСОБНОСТИ ДЛЯ БОТА (заказ дизайнера 13.08.2026: «бот в любой
 * момент должен ознакомиться со своим арсеналом и понять — когда он мне нужен,
 * что он мне даст, можно ли строить план в расчёте на него»).
 *
 * <p>Прежде у бота было рукописное число на каждую пассивку: незнакомая карта
 * получала случайную оценку. Теперь карта САМА говорит, какое узкое место она
 * снимает, насколько сильно, на каком горизонте и при каком условии — а бот
 * сверяет это со своим планом.
 *
 * @param relieves узкое место, которое способность расшивает
 * @param strength насколько сильно — в единицах этого узкого места ЗА РАУНД
 *                 (энергия/боеприпасы/монеты/войска/шаги — в штуках, ACTIONS — в
 *                 действиях, VP — в победных очках)
 * @param horizon  когда польза случается
 * @param needs    условие: выполнимо ли это прямо сейчас (state, seat)
 * @param needsSaid то же условие СЛОВАМИ — уходит и в подсказку боту, и в метрики
 *                 («нет авиации на гексе», «нет монеты»)
 * @param oneShot  срабатывает один раз (утиль) или работает постоянно
 */
public record Hint(Bottleneck relieves, double strength, Horizon horizon,
                   BiPredicate<GameState, Integer> needs, String needsSaid,
                   boolean oneShot) {

    /** Узкое место игрока — то, чего может не хватать плану. */
    public enum Bottleneck {
        ENERGY("энергия"), AMMO("боеприпасы"), COINS("монеты"), KELIUM("келемий"),
        UNITS("войска"), REACH("досягаемость"), TROPHY("трофеи"),
        ACTIONS("действия"), VP("победные очки"), DEFENCE("оборона");

        private final String ru;

        Bottleneck(String ru) {
            this.ru = ru;
        }

        public String ru() {
            return ru;
        }
    }

    /** Горизонт пользы: чем он дальше, тем важнее поставить способность раньше. */
    public enum Horizon {
        NOW, THIS_ROUND, REST_OF_GAME
    }

    /** Условие выполнено прямо сейчас. */
    public boolean ready(GameState s, int seat) {
        return needs == null || needs.test(s, seat);
    }

    /** Способность без условий, работающая до конца партии. */
    public static Hint permanent(Bottleneck what, double strength) {
        return new Hint(what, strength, Horizon.REST_OF_GAME, null, "", false);
    }
}
