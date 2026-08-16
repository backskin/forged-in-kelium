package kelium.cards.objectives;

import java.util.HashSet;
import java.util.Set;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.engine.Scoring;
import kelium.engine.cards.CardContext;

/**
 * ЧЕТЫРЕ НОВЫЕ ВОЕННЫЕ КАРТЫ каталога 1.7.0 (o41–o44).
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
 *   <li><b>Охота на лидера</b> — превращает «бей ведущего» из тайного расчёта
 *       в общий интерес, то есть чинит главную беду многопользовательской игры:
 *       гонку в одну калитку;</li>
 *   <li><b>Блокада</b> — платит за давление БЕЗ штурма, самый дешёвый вход в
 *       войну для того, у кого мало войск.</li>
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
     * Уничтожить чужой ДОБЫТЧИК или ЭНЕРГОСТАНЦИЮ. Усиленно — запитанный.
     *
     * <p>Самая прямая помеха сопернику в игре. Отнятый добытчик стоит владельцу
     * всех оставшихся раундов добычи, и именно этой ценности не видел оценщик
     * ботов: он считал трофей, а не то, чего противник лишился.
     */
    public static final class Devastation extends Objective {

        public Devastation() {
            super("o42");
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedTypes.contains("miner")
                || ход(ctx).destroyedTypes.contains("power_plant");
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
                    boolean economy = b.type == BuildingType.MINER
                        || b.type == BuildingType.POWER_PLANT;
                    if (economy && b.hexId != null && mine.contains(b.hexId)) {
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
                ? "снести чужой добытчик или энергостанцию — цель рядом"
                : "подвести войска к чужому добытчику или энергостанции";
        }
    }

    // ==================================================================
    //  o43 «Охота на лидера»
    // ==================================================================

    /**
     * Уничтожить жетон игрока, который ВЕДЁТ по победным очкам. Усиленно — если
     * это было здание.
     *
     * <p>Любимая карта этого каталога. Она делает то, чего не делает ни одно
     * другое задание: платит за подавление сильнейшего. В игре на четверых это
     * лечит главную беду — партию, которая превращается в гонку в одну калитку,
     * потому что бить лидера каждому по отдельности невыгодно (тратишь своё
     * действие, а обгоняет тебя всё равно кто-то третий). Карта превращает эту
     * трату в награду, и коалиция против лидера складывается сама.
     */
    public static final class LeaderHunt extends Objective {

        public LeaderHunt() {
            super("o43");
        }

        /** Кто ведёт по очкам среди СОПЕРНИКОВ (не я). */
        private int leader(CardContext ctx) {
            int best = -1;
            int bestVp = Integer.MIN_VALUE;
            for (PlayerState p : ctx.state().players) {
                if (p.seat == ctx.seat()) {
                    continue;
                }
                int vp = Scoring.scorePlayer(ctx.state(), p.seat).getOrDefault("total", 0);
                if (vp > bestVp) {
                    bestVp = vp;
                    best = p.seat;
                }
            }
            return best;
        }

        @Override public boolean satisfied(CardContext ctx) {
            return ход(ctx).destroyedOwners.contains(leader(ctx));
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && ход(ctx).destroyedLeaderBuilding;
        }

        @Override public double progress(CardContext ctx) {
            if (satisfied(ctx)) {
                return 1.0;
            }
            // Ударил по лидеру, но не добил — половина.
            return ход(ctx).damagedLeader ? 0.5 : 0.0;
        }

        @Override public String needed(CardContext ctx) {
            int l = leader(ctx);
            return satisfied(ctx) ? "готово"
                : "уничтожить жетон игрока на месте " + (l + 1) + " — он ведёт по очкам";
        }
    }

    // ==================================================================
    //  o44 «Блокада»
    // ==================================================================

    /**
     * Держать свои войска у ДВУХ РАЗНЫХ чужих зданий. Усиленно — если хотя бы
     * одно из них запитано.
     *
     * <p>Осада без штурма: соперник вынужден тратить действия на разблокировку.
     * Самый дешёвый вход в войну — не нужно ни боеприпасов, ни выигранного боя,
     * только выведенные в поле войска. Ровно то, чего боты не делали.
     */
    public static final class Blockade extends Objective {

        public Blockade() {
            super("o44");
        }

        /** Сколько РАЗНЫХ чужих зданий блокировано моими войсками. */
        private int blockaded(CardContext ctx, boolean poweredOnly) {
            Set<String> myHexes = new HashSet<>();
            for (UnitToken u : моиВойска(ctx)) {
                if (u.hexId != null) {
                    myHexes.add(u.hexId);
                }
            }
            Set<Integer> hit = new HashSet<>();
            for (PlayerState other : ctx.state().players) {
                if (other.seat == ctx.seat()) {
                    continue;
                }
                for (BuildingToken b : other.buildingsOnField()) {
                    if (b.hexId == null || (poweredOnly && !b.powered())) {
                        continue;
                    }
                    for (String nb : ctx.state().field.neighbors(b.hexId)) {
                        if (myHexes.contains(nb)) {
                            hit.add(b.uid());
                            break;
                        }
                    }
                }
            }
            return hit.size();
        }

        @Override public boolean satisfied(CardContext ctx) {
            return blockaded(ctx, false) >= порог("count", 2);
        }

        @Override public boolean satisfiedEnhanced(CardContext ctx) {
            return satisfied(ctx) && blockaded(ctx, true) >= 1;
        }

        @Override public double progress(CardContext ctx) {
            return ratio(blockaded(ctx, false), порог("count", 2));
        }

        @Override public String needed(CardContext ctx) {
            int need = порог("count", 2) - blockaded(ctx, false);
            return need <= 0 ? "готово"
                : "подвести войска ещё к " + need + " чужому зданию";
        }
    }
}
