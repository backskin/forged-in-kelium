package kelium.agents;

import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.engine.ability.Hint;

/**
 * Bottlenecks — НАСКОЛЬКО СИЛЬНО у игрока СЕЙЧАС жмёт то или иное узкое место.
 *
 * <p>Зачем это нужно. Карта арсенала рассказывает о себе не «я стою 2.4 балла», а
 * «я расшиваю боеприпасы, сила 2.5, горизонт — этот раунд» ({@link Hint}). Такой
 * язык позволяет добавлять карты, не трогая оценщик бота: бот сам смотрит, жмёт ли
 * у него сейчас именно это место. Иначе получается то, что вскрылось 13.08.2026 —
 * новое спец-действие от карты появилось в меню, а бот его не выбирал ни разу,
 * потому что про такой вариант в его правилах оценки ничего не было.
 *
 * <p>Значение — множитель: около 0.6 — «не жмёт, могу и без этого», около 1.0 —
 * «пригодится», 1.6–2.0 — «упёрся, это расшивает мой главный тормоз».
 */
public final class Bottlenecks {

    private Bottlenecks() {
    }

    /** Насколько жмёт узкое место {@code b} у игрока {@code seat}. */
    public static double pressure(GameState s, int seat, Hint.Bottleneck b) {
        PlayerState p = s.player(seat);
        return switch (b) {
            case AMMO -> {
                int a = p.resources.ammo();
                yield a <= 1 ? 2.0 : a <= 3 ? 1.3 : 0.7;
            }
            case COINS -> {
                int c = p.resources.coin();
                yield c <= 2 ? 1.6 : c <= 5 ? 1.1 : 0.8;
            }
            case KELIUM -> {
                // Жмёт не «мало келемия», а НЕКУДА КЛАСТЬ: склад полон.
                int max = kelium.engine.Storage.keliumMax(s, p);
                yield p.resources.kelium() >= max ? 1.8
                    : p.resources.kelium() >= max - 1 ? 1.2 : 0.8;
            }
            case UNITS -> {
                int n = p.unitsOnField().size();
                yield n <= 1 ? 1.8 : n <= 3 ? 1.2 : 0.8;
            }
            case TROPHY, REACH -> {
                // И трофеи, и досягаемость меряются одним: есть ли по кому ударить.
                // Если цель есть — карта, дающая удар или шаг, ценна прямо сейчас;
                // если целей нет — она пока ни к чему.
                boolean canHit = s.combat instanceof kelium.engine.CombatResolver r
                    && r.anyAttackPossible(seat);
                yield canHit ? 1.7 : (p.unitsOnField().isEmpty() ? 0.4 : 0.9);
            }
            case DEFENCE -> {
                boolean hurt = false;
                for (var bl : p.buildingsOnField()) {
                    if (bl.damage > 0) {
                        hurt = true;
                        break;
                    }
                }
                yield hurt ? 1.6 : 0.8;
            }
            case ENERGY -> {
                // Энергия жмёт, когда её не хватает на запитку зданий.
                int plants = 0;
                for (var bl : p.buildingsOnField()) {
                    if (bl.type == kelium.core.BuildingType.POWER_PLANT) {
                        plants++;
                    }
                }
                yield plants == 0 ? 1.6 : 1.0;
            }
            case ACTIONS -> 1.2;
            case VP -> 1.0;
        };
    }

    /** Вес горизонта: разовое «сейчас» ценится выше отложенного. */
    public static double horizonWeight(Hint.Horizon h) {
        return switch (h) {
            case NOW -> 1.0;
            case THIS_ROUND -> 0.9;
            case REST_OF_GAME -> 0.75;
        };
    }

    /**
     * Ценность способности для игрока СЕЙЧАС по её собственной подсказке.
     * {@code aggression}/{@code economy} — веса характера бота: воинственный
     * дороже ценит удар и досягаемость, хозяйственный — монеты и хранение.
     */
    public static double value(GameState s, int seat, Hint hint,
                               double aggression, double economy) {
        if (hint == null) {
            return 1.0;
        }
        if (hint.needs() != null && !hint.needs().test(s, seat)) {
            return 0.05;    // условие не выполнено — карта сейчас бесполезна
        }
        double v = hint.strength()
            * pressure(s, seat, hint.relieves())
            * horizonWeight(hint.horizon());
        switch (hint.relieves()) {
            case TROPHY, REACH, AMMO -> v *= 1.0 + 0.5 * aggression;
            case COINS, KELIUM, ENERGY -> v *= 1.0 + 0.5 * economy;
            default -> { }
        }
        return v;
    }
}
