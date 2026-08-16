package kelium.cards.objectives;

import java.util.HashSet;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.cards.CardContext;

/**
 * ВОЕННЫЕ КАРТЫ-ОБЪЕКТЫ (o41–o43), условие которых живёт в коде, а не в реестре
 * предикатов.
 *
 * <p>Заведены, чтобы довести долю военных карт до половины колоды: в 1.6.0 война
 * оплачивалась шестью картами из 54 (11%), и каталог платил игроку за то, чтобы
 * НЕ воевать. Каждая из четырёх бьёт по своей причине, по которой война не
 * окупалась:
 *
 * <ul>
 *   <li><b>Ответный удар</b> — оборона перестаёт быть чистым убытком;</li>
 *   <li><b>Разорение</b> — платит за помеху чужому развитию, а не за размен
 *       войсками;</li>
 *   <li><b>Охота на сильного</b> — превращает «бей оторвавшегося» из тайного
 *       расчёта в общий интерес, то есть чинит главную беду игры на четверых:
 *       гонку в одну калитку.</li>
 * </ul>
 */
public final class GroupNewWar {

    private GroupNewWar() {
    }

    // ==================================================================
    //  o41 «Ответный удар»
    // ==================================================================

    /**
     * Уничтожить чужой жетон В ОТВЕТНОМ БОЮ. Усиленно — не потеряв при этом
     * ничего своего.
     *
     * <p>Смысл карты: сделать оборону выгодной. Сейчас ответный бой — это чистые
     * расходы боеприпасов, и бот в него не вкладывается; карта платит именно за
     * то, что нападающий уходит без жетона.
     */
    public static final class Riposte extends Objective {

        public Riposte() {
            super("o41");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedInRetaliation >= порог("count", 1);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).lostOwnThisTurn == 0;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // На меня напали — половина дела: случай представился, осталось попасть.
            return ход(ctx).lostOwnThisTurn > 0 || !ход(ctx).enemyTokensDamaged.isEmpty()
                ? 0.5 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            return satisfied(ctx) ? "готово"
                : "уничтожить нападавшего в ответном бою";
        }
    }

    // ==================================================================
    //  o42 «Разорение»
    // ==================================================================

    /**
     * Уничтожить чужой ДОБЫТЧИК. Усиленно — запитанный.
     *
     * <p>ЭНЕРГОСТАНЦИЯ УБРАНА ИЗ УСЛОВИЯ (ревью дизайнера 17.08.2026):
     * «запитанная энергостанция» — не термин игры, станция энергию производит, а
     * не потребляет, и усиление на ней читалось как ошибка. Осталась одна цель, и
     * она же самая болезненная: отнятый добытчик стоит владельцу всех оставшихся
     * раундов добычи.
     */
    public static final class Devastation extends Objective {

        public Devastation() {
            super("o42");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains("miner");
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return ход(ctx).destroyedPoweredEconomy;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Есть ли вообще до кого дотянуться: чужая экономика рядом с моими
            // войсками. Без этого карта невыполнима, и бот должен это знать.
            return economyInReach(ctx) ? 0.4 : 0.0;
        }

        private boolean economyInReach(CardContext ctx) {
            Set<String> mine = new HashSet<>();
            for (UnitToken u : моиВойска(ctx)) {
                if (u.hexId != null) {
                    mine.add(u.hexId);
                    mine.addAll(ctx.state().field.neighbors(u.hexId));
                }
            }
            for (PlayerState other : ctx.state().players) {
                if (other.seat == ctx.seat()) {
                    continue;
                }
                for (BuildingToken b : other.buildingsOnField()) {
                    if (b.type == BuildingType.MINER && b.hexId != null && mine.contains(b.hexId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            return economyInReach(ctx)
                ? "снести чужой добытчик — цель рядом"
                : "подвести войска к чужому добытчику";
        }
    }

    // ==================================================================
    //  o43 «Охота на сильного»
    // ==================================================================

    /**
     * Уничтожить жетон игрока, у которого НА ПОЛЕ БОЛЬШЕ ЗДАНИЙ, чем у тебя.
     * Усиленно — если уничтоженный жетон был зданием.
     *
     * <p>ПЕРЕПИСАНА ЦЕЛИКОМ (ревью дизайнера 17.08.2026). Прежняя редакция
     * требовала бить того, кто ведёт по ПОБЕДНЫМ ОЧКАМ. За столом это условие
     * непроверяемо: суммарные очки в середине партии никто не считает, они
     * складываются из треков, келемия, монет, зданий, войск, тайлов и жетонов.
     * Такое условие работает только внутри симуляции, а карта печатается для
     * людей. Новое мерило видно глазами — здания на поле пересчитываются за
     * секунду, — и сохраняет смысл прежней карты: догоняющему платят за то, что
     * он бьёт оторвавшегося, и коалиция против лидера складывается сама.
     */
    public static final class StrongerHunt extends Objective {

        public StrongerHunt() {
            super("o43");
        }

        private int buildingsOf(CardContext ctx, int seat) {
            return ctx.state().player(seat).buildingsOnField().size();
        }

        /** Места соперников, у которых зданий на поле больше, чем у меня. */
        private Set<Integer> stronger(CardContext ctx) {
            int mine = buildingsOf(ctx, ctx.seat());
            Set<Integer> out = new HashSet<>();
            for (PlayerState p : ctx.state().players) {
                if (p.seat != ctx.seat() && buildingsOf(ctx, p.seat) > mine) {
                    out.add(p.seat);
                }
            }
            return out;
        }

        @Override public boolean satisfied(CardContext ctx) {
            for (int seat : stronger(ctx)) {
                if (ход(ctx).destroyedOwners.contains(seat)) {
                    return true;
                }
            }
            return false;
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            if (!satisfied(ctx)) {
                return false;
            }
            // Усиление: снесённый жетон был ЗДАНИЕМ. Здание — это ещё и минус
            // одно строение у того, за счёт кого условие сработало.
            for (String t : ход(ctx).destroyedTypes) {
                if (!"infantry".equals(t) && !"vehicle".equals(t)
                        && !"aircraft".equals(t) && !"tower".equals(t)) {
                    return true;
                }
            }
            return false;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            if (stronger(ctx).isEmpty()) {
                return 0.0;      // сильнее меня по застройке никого нет
            }
            return ход(ctx).enemyTokensDamaged.isEmpty() ? 0.2 : 0.5;
        }

        @Override public String needed(CardContext ctx) {
            if (satisfied(ctx)) {
                return "готово";
            }
            Set<Integer> s = stronger(ctx);
            if (s.isEmpty()) {
                return "сейчас ни у кого нет зданий на поле больше, чем у тебя";
            }
            StringBuilder sb = new StringBuilder("уничтожить жетон игрока на месте");
            for (int seat : s) {
                sb.append(' ').append(seat + 1);
            }
            sb.append(" — у него зданий на поле больше");
            return sb.toString();
        }
    }
}
